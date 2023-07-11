/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_NOT_SUPPORTED;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED;
import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;
import static androidx.media3.session.MediaConstants.ERROR_CODE_AUTHENTICATION_EXPIRED_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/* package */ class MediaLibrarySessionImpl extends MediaSessionImpl {

  private static final String RECENT_LIBRARY_ROOT_MEDIA_ID = "androidx.media3.session.recent.root";
  private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";

  private final MediaLibrarySession instance;
  private final MediaLibrarySession.Callback callback;

  @GuardedBy("lock")
  private final ArrayMap<ControllerCb, Set<String>> subscriptions;

  /** Creates an instance. */
  public MediaLibrarySessionImpl(
      MediaLibrarySession instance,
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      ImmutableList<CommandButton> customLayout,
      MediaLibrarySession.Callback callback,
      Bundle tokenExtras,
      BitmapLoader bitmapLoader) {
    super(
        instance,
        context,
        id,
        player,
        sessionActivity,
        customLayout,
        callback,
        tokenExtras,
        bitmapLoader);
    this.instance = instance;
    this.callback = callback;
    subscriptions = new ArrayMap<>();
  }

  @Override
  public List<ControllerInfo> getConnectedControllers() {
    List<ControllerInfo> list = super.getConnectedControllers();
    @Nullable MediaLibraryServiceLegacyStub legacyStub = getLegacyBrowserService();
    if (legacyStub != null) {
      list.addAll(legacyStub.getConnectedControllersManager().getConnectedControllers());
    }
    return list;
  }

  @Override
  public boolean isConnected(ControllerInfo controller) {
    if (super.isConnected(controller)) {
      return true;
    }
    @Nullable MediaLibraryServiceLegacyStub legacyStub = getLegacyBrowserService();
    return legacyStub != null
        && legacyStub.getConnectedControllersManager().isConnected(controller);
  }

  public void notifyChildrenChanged(
      String parentId, int itemCount, @Nullable LibraryParams params) {
    dispatchRemoteControllerTaskWithoutReturn(
        (callback, seq) -> {
          if (isSubscribed(callback, parentId)) {
            callback.onChildrenChanged(seq, parentId, itemCount, params);
          }
        });
  }

  public void notifyChildrenChanged(
      ControllerInfo browser, String parentId, int itemCount, @Nullable LibraryParams params) {
    dispatchRemoteControllerTaskWithoutReturn(
        browser,
        (callback, seq) -> {
          if (!isSubscribed(callback, parentId)) {
            return;
          }
          callback.onChildrenChanged(seq, parentId, itemCount, params);
        });
  }

  public void notifySearchResultChanged(
      ControllerInfo browser, String query, int itemCount, @Nullable LibraryParams params) {
    dispatchRemoteControllerTaskWithoutReturn(
        browser, (callback, seq) -> callback.onSearchResultChanged(seq, query, itemCount, params));
  }

  public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRootOnHandler(
      ControllerInfo browser, @Nullable LibraryParams params) {
    if (params != null
        && params.isRecent
        && Objects.equals(browser.getPackageName(), SYSTEM_UI_PACKAGE_NAME)) {
      // Advertise support for playback resumption, if enabled.
      return !canResumePlaybackOnStart()
          ? Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED))
          : Futures.immediateFuture(
              LibraryResult.ofItem(
                  new MediaItem.Builder()
                      .setMediaId(RECENT_LIBRARY_ROOT_MEDIA_ID)
                      .setMediaMetadata(
                          new MediaMetadata.Builder()
                              .setIsBrowsable(true)
                              .setIsPlayable(false)
                              .build())
                      .build(),
                  params));
    }
    ListenableFuture<LibraryResult<MediaItem>> future =
        callback.onGetLibraryRoot(instance, browser, params);
    future.addListener(
        () -> {
          @Nullable LibraryResult<MediaItem> result = tryGetFutureResult(future);
          if (result != null) {
            maybeUpdateLegacyErrorState(result);
          }
        },
        (Runnable r) -> postOrRun(getApplicationHandler(), r));
    return future;
  }

  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildrenOnHandler(
      ControllerInfo browser,
      String parentId,
      int page,
      int pageSize,
      @Nullable LibraryParams params) {
    if (Objects.equals(parentId, RECENT_LIBRARY_ROOT_MEDIA_ID)) {
      if (!canResumePlaybackOnStart()) {
        return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
      }
      // Advertise support for playback resumption. If STATE_IDLE, the request arrives at boot time
      // to get the full item data to build a notification. If not STATE_IDLE we don't need to
      // deliver the full media item, so we do the minimal viable effort.
      return getPlayerWrapper().getPlaybackState() == Player.STATE_IDLE
          ? getRecentMediaItemAtDeviceBootTime(browser, params)
          : Futures.immediateFuture(
              LibraryResult.ofItemList(
                  ImmutableList.of(
                      new MediaItem.Builder()
                          .setMediaId("androidx.media3.session.recent.item")
                          .setMediaMetadata(
                              new MediaMetadata.Builder()
                                  .setIsBrowsable(false)
                                  .setIsPlayable(true)
                                  .build())
                          .build()),
                  params));
    }
    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
        callback.onGetChildren(instance, browser, parentId, page, pageSize, params);
    future.addListener(
        () -> {
          @Nullable LibraryResult<ImmutableList<MediaItem>> result = tryGetFutureResult(future);
          if (result != null) {
            maybeUpdateLegacyErrorState(result);
            verifyResultItems(result, pageSize);
          }
        },
        (Runnable r) -> postOrRun(getApplicationHandler(), r));
    return future;
  }

  public ListenableFuture<LibraryResult<MediaItem>> onGetItemOnHandler(
      ControllerInfo browser, String mediaId) {
    ListenableFuture<LibraryResult<MediaItem>> future =
        callback.onGetItem(instance, browser, mediaId);
    future.addListener(
        () -> {
          @Nullable LibraryResult<MediaItem> result = tryGetFutureResult(future);
          if (result != null) {
            maybeUpdateLegacyErrorState(result);
          }
        },
        (Runnable r) -> postOrRun(getApplicationHandler(), r));
    return future;
  }

  public ListenableFuture<LibraryResult<Void>> onSubscribeOnHandler(
      ControllerInfo browser, String parentId, @Nullable LibraryParams params) {
    ControllerCb controller = checkStateNotNull(browser.getControllerCb());
    synchronized (lock) {
      @Nullable Set<String> subscription = subscriptions.get(controller);
      if (subscription == null) {
        subscription = new HashSet<>();
        subscriptions.put(controller, subscription);
      }
      subscription.add(parentId);
    }

    // Call callbacks after adding it to the subscription list because library session may want
    // to call notifyChildrenChanged() in the callback.
    //
    // onSubscribe is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    ListenableFuture<LibraryResult<Void>> future =
        checkNotNull(
            callback.onSubscribe(instance, browser, parentId, params),
            "onSubscribe must return non-null future");

    // When error happens, remove from the subscription list.
    future.addListener(
        () -> {
          @Nullable LibraryResult<Void> result = tryGetFutureResult(future);
          if (result == null || result.resultCode != RESULT_SUCCESS) {
            removeSubscription(controller, parentId);
          }
        },
        MoreExecutors.directExecutor());

    return future;
  }

  public ListenableFuture<LibraryResult<Void>> onUnsubscribeOnHandler(
      ControllerInfo browser, String parentId) {
    ListenableFuture<LibraryResult<Void>> future =
        callback.onUnsubscribe(instance, browser, parentId);

    future.addListener(
        () -> removeSubscription(checkStateNotNull(browser.getControllerCb()), parentId),
        MoreExecutors.directExecutor());

    return future;
  }

  public ListenableFuture<LibraryResult<Void>> onSearchOnHandler(
      ControllerInfo browser, String query, @Nullable LibraryParams params) {
    ListenableFuture<LibraryResult<Void>> future =
        callback.onSearch(instance, browser, query, params);
    future.addListener(
        () -> {
          @Nullable LibraryResult<Void> result = tryGetFutureResult(future);
          if (result != null) {
            maybeUpdateLegacyErrorState(result);
          }
        },
        (Runnable r) -> postOrRun(getApplicationHandler(), r));
    return future;
  }

  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResultOnHandler(
      ControllerInfo browser,
      String query,
      int page,
      int pageSize,
      @Nullable LibraryParams params) {
    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
        callback.onGetSearchResult(instance, browser, query, page, pageSize, params);
    future.addListener(
        () -> {
          @Nullable LibraryResult<ImmutableList<MediaItem>> result = tryGetFutureResult(future);
          if (result != null) {
            maybeUpdateLegacyErrorState(result);
            verifyResultItems(result, pageSize);
          }
        },
        (Runnable r) -> postOrRun(getApplicationHandler(), r));
    return future;
  }

  @Override
  @Nullable
  protected MediaLibraryServiceLegacyStub getLegacyBrowserService() {
    return (MediaLibraryServiceLegacyStub) super.getLegacyBrowserService();
  }

  @Override
  protected MediaSessionServiceLegacyStub createLegacyBrowserService(
      MediaSessionCompat.Token compatToken) {
    MediaLibraryServiceLegacyStub stub = new MediaLibraryServiceLegacyStub(this);
    stub.initialize(compatToken);
    return stub;
  }

  @Override
  protected void dispatchRemoteControllerTaskWithoutReturn(RemoteControllerTask task) {
    super.dispatchRemoteControllerTaskWithoutReturn(task);
    @Nullable MediaLibraryServiceLegacyStub legacyStub = getLegacyBrowserService();
    if (legacyStub != null) {
      try {
        task.run(legacyStub.getBrowserLegacyCbForBroadcast(), /* seq= */ 0);
      } catch (RemoteException e) {
        Log.e(TAG, "Exception in using media1 API", e);
      }
    }
  }

  private boolean isSubscribed(ControllerCb callback, String parentId) {
    synchronized (lock) {
      @Nullable Set<String> subscriptions = this.subscriptions.get(callback);
      if (subscriptions == null || !subscriptions.contains(parentId)) {
        return false;
      }
    }
    return true;
  }

  private void maybeUpdateLegacyErrorState(LibraryResult<?> result) {
    PlayerWrapper playerWrapper = getPlayerWrapper();
    if (result.resultCode == RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED
        && result.params != null
        && result.params.extras.containsKey(EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT)) {
      // Mapping this error to the legacy error state provides backwards compatibility for the
      // Automotive OS sign-in.
      MediaSessionCompat mediaSessionCompat = getSessionCompat();
      if (playerWrapper.getLegacyStatusCode() != RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED) {
        playerWrapper.setLegacyErrorStatus(
            ERROR_CODE_AUTHENTICATION_EXPIRED_COMPAT,
            getContext().getString(R.string.authentication_required),
            result.params.extras);
        mediaSessionCompat.setPlaybackState(playerWrapper.createPlaybackStateCompat());
      }
    } else if (playerWrapper.getLegacyStatusCode() != RESULT_SUCCESS) {
      playerWrapper.clearLegacyErrorStatus();
      getSessionCompat().setPlaybackState(playerWrapper.createPlaybackStateCompat());
    }
  }

  @Nullable
  private static <T> T tryGetFutureResult(Future<T> future) {
    checkState(future.isDone());
    try {
      return future.get();
    } catch (CancellationException | ExecutionException | InterruptedException e) {
      Log.w(TAG, "Library operation failed", e);
      return null;
    }
  }

  private static void verifyResultItems(
      LibraryResult<ImmutableList<MediaItem>> result, int pageSize) {
    if (result.resultCode == RESULT_SUCCESS) {
      List<MediaItem> items = checkNotNull(result.value);
      if (items.size() > pageSize) {
        throw new IllegalStateException("Invalid size=" + items.size() + ", pageSize=" + pageSize);
      }
    }
  }

  private void removeSubscription(ControllerCb controllerCb, String parentId) {
    synchronized (lock) {
      @Nullable Set<String> subscription = subscriptions.get(controllerCb);
      if (subscription != null) {
        subscription.remove(parentId);
        if (subscription.isEmpty()) {
          subscriptions.remove(controllerCb);
        }
      }
    }
  }

  private ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
      getRecentMediaItemAtDeviceBootTime(
          ControllerInfo controller, @Nullable LibraryParams params) {
    SettableFuture<LibraryResult<ImmutableList<MediaItem>>> settableFuture =
        SettableFuture.create();
    ListenableFuture<MediaSession.MediaItemsWithStartPosition> future =
        callback.onPlaybackResumption(instance, controller);
    Futures.addCallback(
        future,
        new FutureCallback<MediaSession.MediaItemsWithStartPosition>() {
          @Override
          public void onSuccess(MediaSession.MediaItemsWithStartPosition playlist) {
            if (playlist.mediaItems.isEmpty()) {
              settableFuture.set(
                  LibraryResult.ofError(LibraryResult.RESULT_ERROR_INVALID_STATE, params));
              return;
            }
            int sanitizedStartIndex =
                max(0, min(playlist.startIndex, playlist.mediaItems.size() - 1));
            settableFuture.set(
                LibraryResult.ofItemList(
                    ImmutableList.of(playlist.mediaItems.get(sanitizedStartIndex)), params));
          }

          @Override
          public void onFailure(Throwable t) {
            settableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN, params));
            Log.e(TAG, "Failed fetching recent media item at boot time: " + t.getMessage(), t);
          }
        },
        MoreExecutors.directExecutor());
    return settableFuture;
  }
}

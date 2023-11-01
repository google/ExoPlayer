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

import static android.support.v4.media.MediaBrowserCompat.EXTRA_PAGE;
import static android.support.v4.media.MediaBrowserCompat.EXTRA_PAGE_SIZE;
import static androidx.media.utils.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;
import static androidx.media3.session.MediaUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link MediaBrowserServiceCompat} for interoperability between {@link
 * MediaLibraryService} and {@link MediaBrowserCompat}.
 */
/* package */ class MediaLibraryServiceLegacyStub extends MediaSessionServiceLegacyStub {

  private static final String TAG = "MLSLegacyStub";

  private final ControllerCb browserLegacyCbForBroadcast;

  private final MediaLibrarySessionImpl librarySessionImpl;

  /** Creates a new instance. Caller must call {@link #initialize} to the instance. */
  public MediaLibraryServiceLegacyStub(MediaLibrarySessionImpl session) {
    super(session);
    librarySessionImpl = session;
    browserLegacyCbForBroadcast = new BrowserLegacyCbForBroadcast();
  }

  @Override
  @Nullable
  public BrowserRoot onGetRoot(
      String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
    @Nullable BrowserRoot browserRoot = super.onGetRoot(clientPackageName, clientUid, rootHints);
    if (browserRoot == null) {
      return null;
    }
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      return null;
    }
    if (!getConnectedControllersManager()
        .isSessionCommandAvailable(
            controller, SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)) {
      return null;
    }
    @Nullable
    LibraryParams params =
        LegacyConversions.convertToLibraryParams(librarySessionImpl.getContext(), rootHints);
    AtomicReference<ListenableFuture<LibraryResult<MediaItem>>> futureReference =
        new AtomicReference<>();
    ConditionVariable haveFuture = new ConditionVariable();
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          futureReference.set(librarySessionImpl.onGetLibraryRootOnHandler(controller, params));
          haveFuture.open();
        });
    @Nullable LibraryResult<MediaItem> result = null;
    try {
      haveFuture.block();
      result = checkNotNull(futureReference.get().get(), "LibraryResult must not be null");
    } catch (CancellationException | ExecutionException | InterruptedException e) {
      Log.e(TAG, "Couldn't get a result from onGetLibraryRoot", e);
    }
    if (result != null && result.resultCode == RESULT_SUCCESS && result.value != null) {
      @Nullable
      Bundle extras =
          result.params != null
              ? LegacyConversions.convertToRootHints(result.params)
              : new Bundle();
      boolean isSearchSessionCommandAvailable =
          getConnectedControllersManager()
              .isSessionCommandAvailable(controller, SessionCommand.COMMAND_CODE_LIBRARY_SEARCH);
      checkNotNull(extras)
          .putBoolean(BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, isSearchSessionCommandAvailable);
      return new BrowserRoot(result.value.mediaId, extras);
    }
    // No library root, but keep browser compat connected to allow getting session unless the
    // `Callback` implementation has not returned a `RESULT_SUCCESS`.
    return result != null && result.resultCode != RESULT_SUCCESS
        ? null
        : MediaUtils.defaultBrowserRoot;
  }

  // TODO(b/192455639): Optimize potential multiple calls of
  //                    MediaBrowserCompat.SubscriptionCallback#onChildrenLoaded() with the same
  //                    content.
  @SuppressLint("RestrictedApi")
  @Override
  public void onSubscribe(String id, Bundle option) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      return;
    }
    if (TextUtils.isEmpty(id)) {
      Log.w(TAG, "onSubscribe(): Ignoring empty id from " + controller);
      return;
    }
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          if (!getConnectedControllersManager()
              .isSessionCommandAvailable(
                  controller, SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)) {
            return;
          }
          @Nullable
          LibraryParams params =
              LegacyConversions.convertToLibraryParams(librarySessionImpl.getContext(), option);
          ignoreFuture(librarySessionImpl.onSubscribeOnHandler(controller, id, params));
        });
  }

  @SuppressLint("RestrictedApi")
  @Override
  public void onUnsubscribe(String id) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      return;
    }
    if (TextUtils.isEmpty(id)) {
      Log.w(TAG, "onUnsubscribe(): Ignoring empty id from " + controller);
      return;
    }
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          if (!getConnectedControllersManager()
              .isSessionCommandAvailable(
                  controller, SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)) {
            return;
          }
          ignoreFuture(librarySessionImpl.onUnsubscribeOnHandler(controller, id));
        });
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result) {
    onLoadChildren(parentId, result, /* options= */ null);
  }

  @Override
  public void onLoadChildren(
      String parentId,
      Result<List<MediaBrowserCompat.MediaItem>> result,
      @Nullable Bundle options) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      result.sendResult(/* result= */ null);
      return;
    }
    if (TextUtils.isEmpty(parentId)) {
      Log.w(TAG, "onLoadChildren(): Ignoring empty parentId from " + controller);
      result.sendResult(/* result= */ null);
      return;
    }
    result.detach();
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          if (!getConnectedControllersManager()
              .isSessionCommandAvailable(
                  controller, SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)) {
            result.sendResult(/* result= */ null);
            return;
          }
          if (options != null) {
            options.setClassLoader(librarySessionImpl.getContext().getClassLoader());
            try {
              int page = options.getInt(EXTRA_PAGE);
              int pageSize = options.getInt(EXTRA_PAGE_SIZE);
              if (page >= 0 && pageSize > 0) {
                // Requesting the list of children through pagination.
                @Nullable
                LibraryParams params =
                    LegacyConversions.convertToLibraryParams(
                        librarySessionImpl.getContext(), options);
                ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                    librarySessionImpl.onGetChildrenOnHandler(
                        controller, parentId, page, pageSize, params);
                ListenableFuture<@NullableType List<MediaBrowserCompat.MediaItem>>
                    browserItemsFuture =
                        Util.transformFutureAsync(
                            future, createMediaItemsToBrowserItemsAsyncFunction());
                sendLibraryResultWithMediaItemsWhenReady(result, browserItemsFuture);
                return;
              }
              // Cannot distinguish onLoadChildren() why it's called either by
              // {@link MediaBrowserCompat#subscribe()} or
              // {@link MediaBrowserServiceCompat#notifyChildrenChanged}.
            } catch (BadParcelableException e) {
              // pass-through.
            }
          }
          // A MediaBrowserCompat called loadChildren with no pagination option.
          ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
              librarySessionImpl.onGetChildrenOnHandler(
                  controller,
                  parentId,
                  /* page= */ 0,
                  /* pageSize= */ Integer.MAX_VALUE,
                  /* params= */ null);
          ListenableFuture<@NullableType List<MediaBrowserCompat.MediaItem>> browserItemsFuture =
              Util.transformFutureAsync(future, createMediaItemsToBrowserItemsAsyncFunction());
          sendLibraryResultWithMediaItemsWhenReady(result, browserItemsFuture);
        });
  }

  @Override
  public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      result.sendResult(/* result= */ null);
      return;
    }
    if (TextUtils.isEmpty(itemId)) {
      Log.w(TAG, "Ignoring empty itemId from " + controller);
      result.sendResult(/* result= */ null);
      return;
    }
    result.detach();
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          if (!getConnectedControllersManager()
              .isSessionCommandAvailable(
                  controller, SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)) {
            result.sendResult(/* result= */ null);
            return;
          }
          ListenableFuture<LibraryResult<MediaItem>> future =
              librarySessionImpl.onGetItemOnHandler(controller, itemId);
          ListenableFuture<MediaBrowserCompat.@NullableType MediaItem> browserItemFuture =
              Util.transformFutureAsync(future, createMediaItemToBrowserItemAsyncFunction());
          sendLibraryResultWithMediaItemWhenReady(result, browserItemFuture);
        });
  }

  @Override
  public void onSearch(
      String query, @Nullable Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      result.sendResult(/* result= */ null);
      return;
    }
    if (TextUtils.isEmpty(query)) {
      Log.w(TAG, "Ignoring empty query from " + controller);
      result.sendResult(/* result= */ null);
      return;
    }
    if (!(controller.getControllerCb() instanceof BrowserLegacyCb)) {
      return;
    }
    result.detach();
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          if (!getConnectedControllersManager()
              .isSessionCommandAvailable(controller, SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)) {
            result.sendResult(/* result= */ null);
            return;
          }
          BrowserLegacyCb cb = (BrowserLegacyCb) checkStateNotNull(controller.getControllerCb());
          cb.registerSearchRequest(controller, query, extras, result);
          @Nullable
          LibraryParams params =
              LegacyConversions.convertToLibraryParams(librarySessionImpl.getContext(), extras);
          ignoreFuture(librarySessionImpl.onSearchOnHandler(controller, query, params));
          // Actual search result will be sent by notifySearchResultChanged().
        });
  }

  @Override
  public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
    @Nullable ControllerInfo controller = getCurrentController();
    if (controller == null) {
      result.sendError(/* extras= */ null);
      return;
    }
    result.detach();
    postOrRun(
        librarySessionImpl.getApplicationHandler(),
        () -> {
          SessionCommand command = new SessionCommand(action, /* extras= */ Bundle.EMPTY);
          if (!getConnectedControllersManager().isSessionCommandAvailable(controller, command)) {
            result.sendError(/* extras= */ null);
            return;
          }
          ListenableFuture<SessionResult> future =
              librarySessionImpl.onCustomCommandOnHandler(controller, command, extras);
          sendCustomActionResultWhenReady(result, future);
        });
  }

  @Override
  public ControllerInfo createControllerInfo(RemoteUserInfo remoteUserInfo, Bundle rootHints) {
    return new ControllerInfo(
        remoteUserInfo,
        ControllerInfo.LEGACY_CONTROLLER_VERSION,
        ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION,
        getMediaSessionManager().isTrustedForMediaControl(remoteUserInfo),
        new BrowserLegacyCb(remoteUserInfo),
        /* connectionHints= */ rootHints);
  }

  public ControllerCb getBrowserLegacyCbForBroadcast() {
    return browserLegacyCbForBroadcast;
  }

  @Nullable
  private ControllerInfo getCurrentController() {
    return getConnectedControllersManager().getController(getCurrentBrowserInfo());
  }

  private static void sendCustomActionResultWhenReady(
      Result<Bundle> result, ListenableFuture<SessionResult> future) {
    future.addListener(
        () -> {
          try {
            SessionResult sessionResult =
                checkNotNull(future.get(), "SessionResult must not be null");
            result.sendResult(sessionResult.extras);
          } catch (CancellationException | ExecutionException | InterruptedException e) {
            Log.w(TAG, "Custom action failed", e);
            result.sendError(/* extras= */ null);
          }
        },
        MoreExecutors.directExecutor());
  }

  private static void sendLibraryResultWithMediaItemWhenReady(
      Result<MediaBrowserCompat.MediaItem> result,
      ListenableFuture<MediaBrowserCompat.@NullableType MediaItem> future) {
    future.addListener(
        () -> {
          try {
            MediaBrowserCompat.MediaItem mediaItem = future.get();
            result.sendResult(mediaItem);
          } catch (CancellationException | ExecutionException | InterruptedException e) {
            Log.w(TAG, "Library operation failed", e);
            result.sendResult(/* result= */ null);
          }
        },
        MoreExecutors.directExecutor());
  }

  private static void sendLibraryResultWithMediaItemsWhenReady(
      Result<List<MediaBrowserCompat.MediaItem>> result,
      ListenableFuture<@NullableType List<MediaBrowserCompat.MediaItem>> future) {
    future.addListener(
        () -> {
          try {
            List<MediaBrowserCompat.MediaItem> mediaItems = future.get();
            result.sendResult(
                (mediaItems == null)
                    ? null
                    : MediaUtils.truncateListBySize(mediaItems, TRANSACTION_SIZE_LIMIT_IN_BYTES));
          } catch (CancellationException | ExecutionException | InterruptedException e) {
            Log.w(TAG, "Library operation failed", e);
            result.sendResult(/* result= */ null);
          }
        },
        MoreExecutors.directExecutor());
  }

  private AsyncFunction<
          LibraryResult<ImmutableList<MediaItem>>, @NullableType List<MediaBrowserCompat.MediaItem>>
      createMediaItemsToBrowserItemsAsyncFunction() {
    return result -> {
      checkNotNull(result, "LibraryResult must not be null");
      SettableFuture<@NullableType List<MediaBrowserCompat.MediaItem>> outputFuture =
          SettableFuture.create();
      if (result.resultCode != RESULT_SUCCESS || result.value == null) {
        outputFuture.set(null);
        return outputFuture;
      }

      ImmutableList<MediaItem> mediaItems = result.value;
      if (mediaItems.isEmpty()) {
        outputFuture.set(new ArrayList<>());
        return outputFuture;
      }

      List<@NullableType ListenableFuture<Bitmap>> bitmapFutures = new ArrayList<>();
      outputFuture.addListener(
          () -> {
            if (outputFuture.isCancelled()) {
              cancelAllFutures(bitmapFutures);
            }
          },
          MoreExecutors.directExecutor());

      final AtomicInteger resultCount = new AtomicInteger(0);
      Runnable handleBitmapFuturesTask =
          () -> {
            int completedBitmapFutureCount = resultCount.incrementAndGet();
            if (completedBitmapFutureCount == mediaItems.size()) {
              handleBitmapFuturesAllCompletedAndSetOutputFuture(
                  bitmapFutures, mediaItems, outputFuture);
            }
          };

      for (int i = 0; i < mediaItems.size(); i++) {
        MediaItem mediaItem = mediaItems.get(i);
        MediaMetadata metadata = mediaItem.mediaMetadata;
        if (metadata.artworkData == null) {
          bitmapFutures.add(null);
          handleBitmapFuturesTask.run();
        } else {
          ListenableFuture<Bitmap> bitmapFuture =
              librarySessionImpl.getBitmapLoader().decodeBitmap(metadata.artworkData);
          bitmapFutures.add(bitmapFuture);
          bitmapFuture.addListener(handleBitmapFuturesTask, MoreExecutors.directExecutor());
        }
      }
      return outputFuture;
    };
  }

  private void handleBitmapFuturesAllCompletedAndSetOutputFuture(
      List<@NullableType ListenableFuture<Bitmap>> bitmapFutures,
      List<MediaItem> mediaItems,
      SettableFuture<@NullableType List<MediaBrowserCompat.MediaItem>> outputFuture) {
    List<MediaBrowserCompat.MediaItem> outputMediaItems = new ArrayList<>();
    for (int i = 0; i < bitmapFutures.size(); i++) {
      @Nullable ListenableFuture<Bitmap> future = bitmapFutures.get(i);
      @Nullable Bitmap bitmap = null;
      if (future != null) {
        try {
          bitmap = Futures.getDone(future);
        } catch (CancellationException | ExecutionException e) {
          Log.d(TAG, "Failed to get bitmap", e);
        }
      }
      outputMediaItems.add(LegacyConversions.convertToBrowserItem(mediaItems.get(i), bitmap));
    }
    outputFuture.set(outputMediaItems);
  }

  private static <T> void cancelAllFutures(List<@NullableType ListenableFuture<T>> futures) {
    for (int i = 0; i < futures.size(); i++) {
      if (futures.get(i) != null) {
        futures.get(i).cancel(/* mayInterruptIfRunning= */ false);
      }
    }
  }

  private AsyncFunction<LibraryResult<MediaItem>, MediaBrowserCompat.@NullableType MediaItem>
      createMediaItemToBrowserItemAsyncFunction() {
    return result -> {
      checkNotNull(result, "LibraryResult must not be null");
      SettableFuture<MediaBrowserCompat.@NullableType MediaItem> outputFuture =
          SettableFuture.create();
      if (result.resultCode != RESULT_SUCCESS || result.value == null) {
        outputFuture.set(null);
        return outputFuture;
      }

      MediaItem mediaItem = result.value;
      MediaMetadata metadata = mediaItem.mediaMetadata;
      if (metadata.artworkData == null) {
        outputFuture.set(
            LegacyConversions.convertToBrowserItem(mediaItem, /* artworkBitmap= */ null));
        return outputFuture;
      }

      ListenableFuture<Bitmap> bitmapFuture =
          librarySessionImpl.getBitmapLoader().decodeBitmap(metadata.artworkData);
      outputFuture.addListener(
          () -> {
            if (outputFuture.isCancelled()) {
              bitmapFuture.cancel(/* mayInterruptIfRunning= */ false);
            }
          },
          MoreExecutors.directExecutor());
      bitmapFuture.addListener(
          () -> {
            @Nullable Bitmap bitmap = null;
            try {
              bitmap = Futures.getDone(bitmapFuture);
            } catch (CancellationException | ExecutionException e) {
              Log.d(TAG, "failed to get bitmap", e);
            }
            outputFuture.set(LegacyConversions.convertToBrowserItem(mediaItem, bitmap));
          },
          MoreExecutors.directExecutor());
      return outputFuture;
    };
  }

  private static <T> void ignoreFuture(Future<T> unused) {
    // no-op
  }

  private static class SearchRequest {

    public final ControllerInfo controller;
    public final RemoteUserInfo remoteUserInfo;
    public final String query;
    @Nullable public final Bundle extras;
    public final Result<List<MediaBrowserCompat.MediaItem>> result;

    public SearchRequest(
        ControllerInfo controller,
        RemoteUserInfo remoteUserInfo,
        String query,
        @Nullable Bundle extras,
        Result<List<MediaBrowserCompat.MediaItem>> result) {
      this.controller = controller;
      this.remoteUserInfo = remoteUserInfo;
      this.query = query;
      this.extras = extras;
      this.result = result;
    }
  }

  private final class BrowserLegacyCb implements ControllerCb {

    private final Object lock;
    private final RemoteUserInfo remoteUserInfo;

    @GuardedBy("lock")
    private final List<SearchRequest> searchRequests;

    public BrowserLegacyCb(RemoteUserInfo remoteUserInfo) {
      // Initialize default values.
      lock = new Object();
      searchRequests = new ArrayList<>();

      // Initialize members with params.
      this.remoteUserInfo = remoteUserInfo;
    }

    @Override
    public void onChildrenChanged(
        int seq, String parentId, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {
      @Nullable Bundle extras = params != null ? params.extras : null;
      notifyChildrenChanged(remoteUserInfo, parentId, extras != null ? extras : Bundle.EMPTY);
    }

    @Override
    public void onSearchResultChanged(
        int seq, String query, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {
      // In MediaLibrarySession/MediaBrowser, we have two different APIs for getting size of
      // search result (and also starting search) and getting result.
      // However, MediaBrowserService/MediaBrowserCompat only have one search API for getting
      // search result.
      List<SearchRequest> searchRequests = new ArrayList<>();
      synchronized (lock) {
        for (int i = this.searchRequests.size() - 1; i >= 0; i--) {
          SearchRequest iter = this.searchRequests.get(i);
          if (Util.areEqual(remoteUserInfo, iter.remoteUserInfo) && iter.query.equals(query)) {
            searchRequests.add(iter);
            this.searchRequests.remove(i);
          }
        }
        if (searchRequests.size() == 0) {
          return;
        }
      }

      postOrRun(
          librarySessionImpl.getApplicationHandler(),
          () -> {
            for (int i = 0; i < searchRequests.size(); i++) {
              SearchRequest request = searchRequests.get(i);
              int page = 0;
              int pageSize = Integer.MAX_VALUE;
              if (request.extras != null) {
                try {
                  request.extras.setClassLoader(librarySessionImpl.getContext().getClassLoader());
                  page = request.extras.getInt(MediaBrowserCompat.EXTRA_PAGE, -1);
                  pageSize = request.extras.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1);
                } catch (BadParcelableException e) {
                  request.result.sendResult(/* result= */ null);
                  return;
                }
              }
              if (page < 0 || pageSize < 1) {
                page = 0;
                pageSize = Integer.MAX_VALUE;
              }
              @Nullable
              LibraryParams libraryParams =
                  LegacyConversions.convertToLibraryParams(
                      librarySessionImpl.getContext(), request.extras);
              ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                  librarySessionImpl.onGetSearchResultOnHandler(
                      request.controller, request.query, page, pageSize, libraryParams);
              ListenableFuture<@NullableType List<MediaBrowserCompat.MediaItem>> mediaItemsFuture =
                  Util.transformFutureAsync(future, createMediaItemsToBrowserItemsAsyncFunction());
              sendLibraryResultWithMediaItemsWhenReady(request.result, mediaItemsFuture);
            }
          });
    }

    private void registerSearchRequest(
        ControllerInfo controller,
        String query,
        @Nullable Bundle extras,
        Result<List<MediaBrowserCompat.MediaItem>> result) {
      synchronized (lock) {
        searchRequests.add(
            new SearchRequest(controller, controller.getRemoteUserInfo(), query, extras, result));
      }
    }

    @Override
    public int hashCode() {
      return ObjectsCompat.hash(remoteUserInfo);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof BrowserLegacyCb)) {
        return false;
      }
      BrowserLegacyCb other = (BrowserLegacyCb) obj;
      return Util.areEqual(remoteUserInfo, other.remoteUserInfo);
    }
  }

  private final class BrowserLegacyCbForBroadcast implements ControllerCb {

    @Override
    public void onChildrenChanged(
        int seq, String parentId, int itemCount, @Nullable LibraryParams libraryParams)
        throws RemoteException {
      // This will trigger {@link MediaLibraryServiceLegacyStub#onLoadChildren}.
      if (libraryParams == null || libraryParams.extras == null) {
        notifyChildrenChanged(parentId);
      } else {
        notifyChildrenChanged(parentId, castNonNull(libraryParams.extras));
      }
    }

    @Override
    public void onSearchResultChanged(
        int seq, String query, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {
      // Shouldn't be called. If it's called, it's bug.
      // This method in the base class is introduced to internally send return of
      // {@link MediaLibrarySessionCallback#onSearchResultChanged}. However, for
      // BrowserCompat, it should be done by {@link Result#sendResult} from
      // {@link MediaLibraryServiceLegacyStub#onSearch} instead.
    }
  }
}

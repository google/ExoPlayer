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
import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/* package */ class MediaLibrarySessionImpl extends MediaSessionImpl {

  private final MediaLibrarySession instance;
  private final MediaLibrarySession.MediaLibrarySessionCallback callback;

  @GuardedBy("lock")
  private final ArrayMap<ControllerCb, Set<String>> subscriptions;

  public MediaLibrarySessionImpl(
      MediaLibrarySession instance,
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      MediaLibrarySession.MediaLibrarySessionCallback callback,
      MediaSession.MediaItemFiller mediaItemFiller,
      Bundle tokenExtras) {
    super(instance, context, id, player, sessionActivity, callback, mediaItemFiller, tokenExtras);
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
    // onGetLibraryRoot is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    return checkNotNull(
        callback.onGetLibraryRoot(instance, browser, params),
        "onGetLibraryRoot must return non-null future");
  }

  public ListenableFuture<LibraryResult<MediaItem>> onGetItemOnHandler(
      ControllerInfo browser, String mediaId) {
    // onGetItem is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    return checkNotNull(
        callback.onGetItem(instance, browser, mediaId), "onGetItem must return non-null future");
  }

  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildrenOnHandler(
      ControllerInfo browser,
      String parentId,
      int page,
      int pageSize,
      @Nullable LibraryParams params) {
    // onGetChildren is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
        checkNotNull(
            callback.onGetChildren(instance, browser, parentId, page, pageSize, params),
            "onGetChildren must return non-null future");
    future.addListener(
        () -> {
          @Nullable LibraryResult<ImmutableList<MediaItem>> result = tryGetFutureResult(future);
          if (result != null) {
            verifyResultItems(result, pageSize);
          }
        },
        MoreExecutors.directExecutor());
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
            synchronized (lock) {
              subscriptions.remove(checkStateNotNull(browser.getControllerCb()));
            }
          }
        },
        MoreExecutors.directExecutor());

    return future;
  }

  public ListenableFuture<LibraryResult<Void>> onUnsubscribeOnHandler(
      ControllerInfo browser, String parentId) {
    // onUnsubscribe is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    ListenableFuture<LibraryResult<Void>> future =
        checkNotNull(
            callback.onUnsubscribe(instance, browser, parentId),
            "onUnsubscribe must return non-null future");

    future.addListener(
        () -> {
          synchronized (lock) {
            subscriptions.remove(checkStateNotNull(browser.getControllerCb()));
          }
        },
        MoreExecutors.directExecutor());

    return future;
  }

  public ListenableFuture<LibraryResult<Void>> onSearchOnHandler(
      ControllerInfo browser, String query, @Nullable LibraryParams params) {
    // onSearch is defined to return a non-null result but it's implemented by applications,
    // so we explicitly null-check the result to fail early if an app accidentally returns null.
    return checkNotNull(
        callback.onSearch(instance, browser, query, params),
        "onSearch must return non-null future");
  }

  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResultOnHandler(
      ControllerInfo browser,
      String query,
      int page,
      int pageSize,
      @Nullable LibraryParams params) {
    // onGetSearchResult is defined to return a non-null result but it's implemented by
    // applications, so we explicitly null-check the result to fail early if an app accidentally
    // returns null.
    ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
        checkNotNull(
            callback.onGetSearchResult(instance, browser, query, page, pageSize, params),
            "onGetSearchResult must return non-null future");
    future.addListener(
        () -> {
          @Nullable LibraryResult<ImmutableList<MediaItem>> result = tryGetFutureResult(future);
          if (result != null) {
            verifyResultItems(result, pageSize);
          }
        },
        MoreExecutors.directExecutor());
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

  @Nullable
  private static <T> T tryGetFutureResult(Future<T> future) {
    checkState(future.isDone());
    try {
      return future.get();
    } catch (CancellationException | ExecutionException | InterruptedException unused) {
      return null;
    }
  }

  private static void verifyResultItems(
      LibraryResult<ImmutableList<MediaItem>> result, int pageSize) {
    if (result.resultCode == RESULT_SUCCESS) {
      List<MediaItem> items = checkNotNull(result.value);
      if (items.size() > pageSize) {
        throw new AssertionError(
            "The number of items must be less than or equal to the pageSize"
                + ", size="
                + items.size()
                + ", pageSize="
                + pageSize);
      }
    }
  }
}

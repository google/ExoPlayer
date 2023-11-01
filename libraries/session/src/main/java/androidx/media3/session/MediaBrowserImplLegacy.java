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

import static androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_PERMISSION_DENIED;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_UNKNOWN;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.ItemCallback;
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/** Implementation of MediaBrowser with the {@link MediaBrowserCompat} for legacy support. */
/* package */ class MediaBrowserImplLegacy extends MediaControllerImplLegacy
    implements MediaBrowser.MediaBrowserImpl {

  private static final String TAG = "MB2ImplLegacy";

  private final HashMap<LibraryParams, MediaBrowserCompat> browserCompats = new HashMap<>();

  private final HashMap<String, List<SubscribeCallback>> subscribeCallbacks = new HashMap<>();

  private final MediaBrowser instance;

  MediaBrowserImplLegacy(
      Context context,
      @UnderInitialization MediaBrowser instance,
      SessionToken token,
      Looper applicationLooper,
      BitmapLoader bitmapLoader) {
    super(context, instance, token, applicationLooper, bitmapLoader);
    this.instance = instance;
  }

  @Override
  /* package*/ MediaBrowser getInstance() {
    return instance;
  }

  @Override
  public void release() {
    for (MediaBrowserCompat browserCompat : browserCompats.values()) {
      browserCompat.disconnect();
    }
    browserCompats.clear();
    // Ensure that MediaController.Listener#onDisconnected() is called by super.release().
    super.release();
  }

  @Override
  public SessionCommands getAvailableSessionCommands() {
    @Nullable MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat != null) {
      return super.getAvailableSessionCommands().buildUpon().addAllLibraryCommands().build();
    }
    return super.getAvailableSessionCommands();
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getLibraryRoot(@Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    SettableFuture<LibraryResult<MediaItem>> result = SettableFuture.create();
    MediaBrowserCompat browserCompat = getBrowserCompat(params);
    if (browserCompat != null) {
      // Already connected with the given extras.
      result.set(LibraryResult.ofItem(createRootMediaItem(browserCompat), null));
    } else {
      Bundle rootHints = LegacyConversions.convertToRootHints(params);
      MediaBrowserCompat newBrowser =
          new MediaBrowserCompat(
              getContext(),
              getConnectedToken().getComponentName(),
              new GetLibraryRootCallback(result, params),
              rootHints);
      browserCompats.put(params, newBrowser);
      newBrowser.connect();
    }
    return result;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> subscribe(
      String parentId, @Nullable LibraryParams params) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }
    SettableFuture<LibraryResult<Void>> future = SettableFuture.create();
    SubscribeCallback callback = new SubscribeCallback(future);
    List<SubscribeCallback> list = subscribeCallbacks.get(parentId);
    if (list == null) {
      list = new ArrayList<>();
      subscribeCallbacks.put(parentId, list);
    }
    list.add(callback);
    browserCompat.subscribe(parentId, createOptions(params), callback);
    return future;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> unsubscribe(String parentId) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }
    // Note: don't use MediaBrowserCompat#unsubscribe(String) here, to keep the subscription
    // callback for getChildren.
    List<SubscribeCallback> list = subscribeCallbacks.get(parentId);
    if (list == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
    }
    for (int i = 0; i < list.size(); i++) {
      browserCompat.unsubscribe(parentId, list.get(i));
    }

    // No way to get result. Just return success.
    return Futures.immediateFuture(LibraryResult.ofVoid());
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getChildren(
      String parentId, int page, int pageSize, @Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }

    SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future = SettableFuture.create();
    Bundle options = createOptions(params, page, pageSize);
    browserCompat.subscribe(parentId, options, new GetChildrenCallback(future, parentId));
    return future;
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getItem(String mediaId) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }
    SettableFuture<LibraryResult<MediaItem>> result = SettableFuture.create();
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
            if (item != null) {
              result.set(
                  LibraryResult.ofItem(
                      LegacyConversions.convertToMediaItem(item), /* params= */ null));
            } else {
              result.set(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
            }
          }

          @Override
          public void onError(String itemId) {
            result.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
          }
        });
    return result;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> search(
      String query, @Nullable LibraryParams params) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }
    browserCompat.search(
        query,
        getExtras(params),
        new MediaBrowserCompat.SearchCallback() {
          @Override
          public void onSearchResult(
              String query, Bundle extras, List<MediaBrowserCompat.MediaItem> items) {
            getInstance()
                .notifyBrowserListener(
                    listener -> {
                      // Set extra null here, because 'extra' have different meanings between old
                      // API and new API as follows.
                      // - Old API: Extra/Option specified with search().
                      // - New API: Extra from MediaLibraryService to MediaBrowser
                      // TODO(b/193193565): Cache search result for later getSearchResult() calls.
                      listener.onSearchResultChanged(getInstance(), query, items.size(), null);
                    });
          }

          @Override
          public void onError(String query, Bundle extras) {
            getInstance()
                .notifyBrowserListener(
                    listener -> {
                      // Set extra null here, because 'extra' have different meanings between old
                      // API and new API as follows.
                      // - Old API: Extra/Option specified with search().
                      // - New API: Extra from MediaLibraryService to MediaBrowser
                      listener.onSearchResultChanged(getInstance(), query, 0, null);
                    });
          }
        });
    // No way to get result. Just return success.
    return Futures.immediateFuture(LibraryResult.ofVoid());
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSearchResult(
      String query, int page, int pageSize, @Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
    }

    SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future = SettableFuture.create();
    Bundle options = createOptions(params, page, pageSize);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    browserCompat.search(
        query,
        options,
        new MediaBrowserCompat.SearchCallback() {
          @Override
          public void onSearchResult(
              String query, Bundle extrasSent, List<MediaBrowserCompat.MediaItem> items) {
            future.set(
                LibraryResult.ofItemList(
                    LegacyConversions.convertBrowserItemListToMediaItemList(items),
                    /* params= */ null));
          }

          @Override
          public void onError(String query, Bundle extrasSent) {
            future.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
          }
        });
    return future;
  }

  private MediaBrowserCompat getBrowserCompat(LibraryParams extras) {
    return browserCompats.get(extras);
  }

  private static Bundle createOptions(@Nullable LibraryParams params) {
    return params == null ? new Bundle() : new Bundle(params.extras);
  }

  private static Bundle createOptions(@Nullable LibraryParams params, int page, int pageSize) {
    Bundle options = createOptions(params);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    return options;
  }

  private static Bundle getExtras(@Nullable LibraryParams params) {
    return params != null ? params.extras : null;
  }

  private MediaItem createRootMediaItem(MediaBrowserCompat browserCompat) {
    // TODO(b/193193690): Query again with getMediaItem() to get real media item.
    String mediaId = browserCompat.getRoot();
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setIsPlayable(false)
            .setExtras(browserCompat.getExtras())
            .build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  private class GetLibraryRootCallback extends MediaBrowserCompat.ConnectionCallback {
    private final SettableFuture<LibraryResult<MediaItem>> result;
    private final LibraryParams params;

    public GetLibraryRootCallback(
        SettableFuture<LibraryResult<MediaItem>> result, LibraryParams params) {
      super();
      this.result = result;
      this.params = params;
    }

    @Override
    public void onConnected() {
      MediaBrowserCompat browserCompat = browserCompats.get(params);
      if (browserCompat == null) {
        // Shouldn't be happen. Internal error?
        result.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
      } else {
        result.set(
            LibraryResult.ofItem(
                createRootMediaItem(browserCompat),
                LegacyConversions.convertToLibraryParams(context, browserCompat.getExtras())));
      }
    }

    @Override
    public void onConnectionSuspended() {
      onConnectionFailed();
    }

    @Override
    public void onConnectionFailed() {
      // Unknown extra field.
      result.set(LibraryResult.ofError(RESULT_ERROR_BAD_VALUE));
      release();
    }
  }

  private class SubscribeCallback extends SubscriptionCallback {

    private final SettableFuture<LibraryResult<Void>> future;

    public SubscribeCallback(SettableFuture<LibraryResult<Void>> future) {
      this.future = future;
    }

    @Override
    public void onError(String parentId) {
      onErrorInternal();
    }

    @Override
    public void onError(String parentId, Bundle options) {
      onErrorInternal();
    }

    @Override
    public void onChildrenLoaded(String parentId, List<MediaBrowserCompat.MediaItem> children) {
      onChildrenLoadedInternal(parentId, children);
    }

    @Override
    public void onChildrenLoaded(
        String parentId, List<MediaBrowserCompat.MediaItem> children, Bundle options) {
      onChildrenLoadedInternal(parentId, children);
    }

    private void onErrorInternal() {
      // Don't need to unsubscribe here, because MediaBrowserServiceCompat can notify children
      // changed after the initial failure and MediaBrowserCompat could receive the changes.
      future.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
    }

    private void onChildrenLoadedInternal(
        String parentId, @Nullable List<MediaBrowserCompat.MediaItem> children) {
      if (TextUtils.isEmpty(parentId)) {
        Log.w(TAG, "SubscribeCallback.onChildrenLoaded(): Ignoring empty parentId");
        return;
      }
      MediaBrowserCompat browserCompat = getBrowserCompat();
      if (browserCompat == null) {
        // Browser is closed.
        return;
      }
      int itemCount;
      if (children != null) {
        itemCount = children.size();
      } else {
        // Currently no way to tell failures in MediaBrowser#subscribe().
        return;
      }

      LibraryParams params =
          LegacyConversions.convertToLibraryParams(
              context, browserCompat.getNotifyChildrenChangedOptions());
      getInstance()
          .notifyBrowserListener(
              listener -> {
                // TODO(b/193193565): Cache children result for later getChildren() calls.
                listener.onChildrenChanged(getInstance(), parentId, itemCount, params);
              });
      future.set(LibraryResult.ofVoid());
    }
  }

  private class GetChildrenCallback extends SubscriptionCallback {

    private final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future;
    private final String parentId;

    public GetChildrenCallback(
        SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future, String parentId) {
      super();
      this.future = future;
      this.parentId = parentId;
    }

    @Override
    public void onError(String parentId) {
      onErrorInternal();
    }

    @Override
    public void onError(String parentId, Bundle options) {
      onErrorInternal();
    }

    @Override
    public void onChildrenLoaded(String parentId, List<MediaBrowserCompat.MediaItem> children) {
      onChildrenLoadedInternal(parentId, children);
    }

    @Override
    public void onChildrenLoaded(
        String parentId, List<MediaBrowserCompat.MediaItem> children, Bundle options) {
      onChildrenLoadedInternal(parentId, children);
    }

    private void onErrorInternal() {
      future.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
    }

    private void onChildrenLoadedInternal(
        String parentId, List<MediaBrowserCompat.MediaItem> children) {
      if (TextUtils.isEmpty(parentId)) {
        Log.w(TAG, "GetChildrenCallback.onChildrenLoaded(): Ignoring empty parentId");
        return;
      }
      MediaBrowserCompat browserCompat = getBrowserCompat();
      if (browserCompat == null) {
        future.set(LibraryResult.ofError(RESULT_ERROR_SESSION_DISCONNECTED));
        return;
      }
      browserCompat.unsubscribe(this.parentId, GetChildrenCallback.this);

      if (children == null) {
        // list are non-Null, so it must be internal error.
        future.set(LibraryResult.ofError(RESULT_ERROR_UNKNOWN));
      } else {
        // Don't set extra here, because 'extra' have different meanings between old
        // API and new API as follows.
        // - Old API: Extra/Option specified with subscribe().
        // - New API: Extra from MediaLibraryService to MediaBrowser
        future.set(
            LibraryResult.ofItemList(
                LegacyConversions.convertBrowserItemListToMediaItemList(children),
                /* params= */ null));
      }
    }
  }
}

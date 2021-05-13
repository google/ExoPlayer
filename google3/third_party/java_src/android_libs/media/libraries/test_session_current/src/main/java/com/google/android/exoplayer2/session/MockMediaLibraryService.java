/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.LibraryResult.RESULT_ERROR_BAD_VALUE;
import static com.google.android.exoplayer2.session.LibraryResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.session.MediaTestUtils.assertLibraryParamsEquals;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION_ASSERT_PARAMS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.GET_CHILDREN_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.MEDIA_ID_GET_NULL_ITEM;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_ITEM_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID_ERROR;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID_LONG_LIST;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_TAKES_TIME;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_RESULT_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_TIME_IN_MS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.session.MediaLibraryService.MediaLibrarySession.MediaLibrarySessionCallback;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.Log;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/** A mock MediaLibraryService */
public class MockMediaLibraryService extends MediaLibraryService {
  /** ID of the session that this service will create. */
  public static final String ID = "TestLibrary";

  // TODO(b/180293668): Set browsable property to the media metadata.
  public static final MediaItem ROOT_ITEM =
      new MediaItem.Builder()
          .setMediaId(ROOT_ID)
          .setMediaMetadata(new MediaMetadata.Builder().build())
          .build();
  public static final LibraryParams ROOT_PARAMS =
      new LibraryParams.Builder().setExtras(ROOT_EXTRAS).build();
  private static final LibraryParams NOTIFY_CHILDREN_CHANGED_PARAMS =
      new LibraryParams.Builder().setExtras(NOTIFY_CHILDREN_CHANGED_EXTRAS).build();

  private static final String TAG = "MockMediaLibrarySvc2";

  @GuardedBy("MockMediaLibraryService.class")
  private static boolean assertLibraryParams;

  @GuardedBy("MockMediaLibraryService.class")
  private static LibraryParams expectedParams;

  MediaLibrarySession session;
  TestHandler handler;
  HandlerThread handlerThread;

  @Override
  public void onCreate() {
    TestServiceRegistry.getInstance().setServiceInstance(this);
    super.onCreate();
    handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    handler = new TestHandler(handlerThread.getLooper());
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    synchronized (MockMediaLibraryService.class) {
      assertLibraryParams = false;
      expectedParams = null;
    }
    TestServiceRegistry.getInstance().cleanUp();
    if (Build.VERSION.SDK_INT >= 18) {
      handler.getLooper().quitSafely();
    } else {
      handler.getLooper().quit();
    }
  }

  @Override
  public MediaLibrarySession onGetSession(@NonNull ControllerInfo controllerInfo) {
    TestServiceRegistry registry = TestServiceRegistry.getInstance();
    TestServiceRegistry.OnGetSessionHandler onGetSessionHandler = registry.getOnGetSessionHandler();
    if (onGetSessionHandler != null) {
      return (MediaLibrarySession) onGetSessionHandler.onGetSession(controllerInfo);
    }

    MockPlayer player =
        new MockPlayer.Builder().setLatchCount(1).setApplicationLooper(handler.getLooper()).build();

    MediaLibrarySessionCallback callback = registry.getSessionCallback();
    session =
        new MediaLibrarySession.Builder(
                MockMediaLibraryService.this,
                player,
                callback != null ? callback : new TestLibrarySessionCallback())
            .setId(ID)
            .build();
    return session;
  }

  /**
   * This changes the visibility of {@link Service#attachBaseContext(Context)} to public. This is a
   * workaround for creating {@link MediaLibrarySession} without starting a service.
   */
  @Override
  public void attachBaseContext(Context base) {
    super.attachBaseContext(base);
  }

  public static void setAssertLibraryParams(LibraryParams expectedParams) {
    synchronized (MockMediaLibraryService.class) {
      assertLibraryParams = true;
      MockMediaLibraryService.expectedParams = expectedParams;
    }
  }

  private class TestLibrarySessionCallback extends MediaLibrarySessionCallback {

    @Override
    @Nullable
    public MediaSession.ConnectResult onConnect(MediaSession session, ControllerInfo controller) {
      if (!SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
        return null;
      }
      MediaSession.ConnectResult connectResult = super.onConnect(session, controller);
      SessionCommands.Builder builder =
          new SessionCommands.Builder(connectResult.availableSessionCommands);
      builder.add(new SessionCommand(CUSTOM_ACTION, null));
      builder.add(new SessionCommand(CUSTOM_ACTION_ASSERT_PARAMS, null));
      return new MediaSession.ConnectResult(builder.build(), connectResult.availablePlayerCommands);
    }

    @Override
    @NonNull
    public ListenableFuture<LibraryResult> onGetLibraryRoot(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        LibraryParams params) {
      assertLibraryParams(params);
      return new LibraryResult(RESULT_SUCCESS, ROOT_ITEM, ROOT_PARAMS).asFuture();
    }

    @Override
    @NonNull
    public ListenableFuture<LibraryResult> onGetItem(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        @NonNull String mediaId) {
      switch (mediaId) {
        case MEDIA_ID_GET_ITEM:
          return new LibraryResult(
                  RESULT_SUCCESS, createPlayableMediaItem(mediaId), /* params= */ null)
              .asFuture();
        case MEDIA_ID_GET_NULL_ITEM:
          return new LibraryResult(RESULT_SUCCESS).asFuture();
      }
      return new LibraryResult(RESULT_ERROR_BAD_VALUE).asFuture();
    }

    @Override
    @NonNull
    public ListenableFuture<LibraryResult> onGetChildren(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        @NonNull String parentId,
        int page,
        int pageSize,
        LibraryParams params) {
      assertLibraryParams(params);
      if (PARENT_ID.equals(parentId)) {
        return new LibraryResult(
                RESULT_SUCCESS,
                getPaginatedResult(GET_CHILDREN_RESULT, page, pageSize),
                /* params= */ null)
            .asFuture();
      } else if (PARENT_ID_LONG_LIST.equals(parentId)) {
        List<MediaItem> list = new ArrayList<>(LONG_LIST_COUNT);
        for (int i = 0; i < LONG_LIST_COUNT; i++) {
          list.add(createPlayableMediaItem(TestUtils.getMediaIdInFakeTimeline(i)));
        }
        return new LibraryResult(RESULT_SUCCESS, list, /* params= */ null).asFuture();
      } else if (PARENT_ID_ERROR.equals(parentId)) {
        return new LibraryResult(RESULT_ERROR_BAD_VALUE).asFuture();
      }
      // Includes the case of PARENT_ID_NO_CHILDREN.
      return new LibraryResult(RESULT_SUCCESS, Collections.emptyList(), /* params= */ null)
          .asFuture();
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    @NonNull
    public ListenableFuture<LibraryResult> onSearch(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        @NonNull String query,
        LibraryParams params) {
      assertLibraryParams(params);
      if (SEARCH_QUERY.equals(query)) {
        MockMediaLibraryService.this.session.notifySearchResultChanged(
            browser, query, SEARCH_RESULT_COUNT, params);
      } else if (SEARCH_QUERY_LONG_LIST.equals(query)) {
        MockMediaLibraryService.this.session.notifySearchResultChanged(
            browser, query, LONG_LIST_COUNT, params);
      } else if (SEARCH_QUERY_TAKES_TIME.equals(query)) {
        // Searching takes some time. Notify after 5 seconds.
        Executors.newSingleThreadScheduledExecutor()
            .schedule(
                new Runnable() {
                  @Override
                  public void run() {
                    MockMediaLibraryService.this.session.notifySearchResultChanged(
                        browser, query, SEARCH_RESULT_COUNT, params);
                  }
                },
                SEARCH_TIME_IN_MS,
                MILLISECONDS);
      } else {
        // SEARCH_QUERY_EMPTY_RESULT and SEARCH_QUERY_ERROR will be handled here.
        MockMediaLibraryService.this.session.notifySearchResultChanged(browser, query, 0, params);
      }
      return new LibraryResult(RESULT_SUCCESS).asFuture();
    }

    @Override
    @NonNull
    public ListenableFuture<LibraryResult> onGetSearchResult(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        @NonNull String query,
        int page,
        int pageSize,
        LibraryParams params) {
      assertLibraryParams(params);
      if (SEARCH_QUERY.equals(query)) {
        return new LibraryResult(
                RESULT_SUCCESS,
                getPaginatedResult(SEARCH_RESULT, page, pageSize),
                /* params= */ null)
            .asFuture();
      } else if (SEARCH_QUERY_LONG_LIST.equals(query)) {
        List<MediaItem> list = new ArrayList<>(LONG_LIST_COUNT);
        for (int i = 0; i < LONG_LIST_COUNT; i++) {
          list.add(createPlayableMediaItem(TestUtils.getMediaIdInFakeTimeline(i)));
        }
        return new LibraryResult(RESULT_SUCCESS, list, /* params= */ null).asFuture();
      } else if (SEARCH_QUERY_EMPTY_RESULT.equals(query)) {
        return new LibraryResult(RESULT_SUCCESS, Collections.emptyList(), /* params= */ null)
            .asFuture();
      } else {
        // SEARCH_QUERY_ERROR will be handled here.
        return new LibraryResult(RESULT_ERROR_BAD_VALUE).asFuture();
      }
    }

    @Override
    @NonNull
    public ListenableFuture<LibraryResult> onSubscribe(
        @NonNull MediaLibrarySession session,
        @NonNull ControllerInfo browser,
        @NonNull String parentId,
        LibraryParams params) {
      assertLibraryParams(params);
      String unsubscribedId = "unsubscribedId";
      switch (parentId) {
        case SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL:
          MockMediaLibraryService.this.session.notifyChildrenChanged(
              parentId, NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, NOTIFY_CHILDREN_CHANGED_PARAMS);
          return new LibraryResult(RESULT_SUCCESS).asFuture();
        case SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE:
          MockMediaLibraryService.this.session.notifyChildrenChanged(
              MediaTestUtils.getTestControllerInfo(MockMediaLibraryService.this.session),
              parentId,
              NOTIFY_CHILDREN_CHANGED_ITEM_COUNT,
              NOTIFY_CHILDREN_CHANGED_PARAMS);
          return new LibraryResult(RESULT_SUCCESS).asFuture();
        case SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID:
          MockMediaLibraryService.this.session.notifyChildrenChanged(
              unsubscribedId, NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, NOTIFY_CHILDREN_CHANGED_PARAMS);
          return new LibraryResult(RESULT_SUCCESS).asFuture();
        case SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID:
          MockMediaLibraryService.this.session.notifyChildrenChanged(
              MediaTestUtils.getTestControllerInfo(MockMediaLibraryService.this.session),
              unsubscribedId,
              NOTIFY_CHILDREN_CHANGED_ITEM_COUNT,
              NOTIFY_CHILDREN_CHANGED_PARAMS);
          return new LibraryResult(RESULT_SUCCESS).asFuture();
      }
      return new LibraryResult(RESULT_ERROR_BAD_VALUE).asFuture();
    }

    @Override
    @NonNull
    public ListenableFuture<SessionResult> onCustomCommand(
        @NonNull MediaSession session,
        @NonNull ControllerInfo controller,
        @NonNull SessionCommand sessionCommand,
        Bundle args) {
      switch (sessionCommand.customAction) {
        case CUSTOM_ACTION:
          return new SessionResult(RESULT_SUCCESS, CUSTOM_ACTION_EXTRAS).asFuture();
        case CUSTOM_ACTION_ASSERT_PARAMS:
          LibraryParams params =
              BundleableUtils.fromNullableBundle(
                  LibraryParams.CREATOR, args.getBundle(CUSTOM_ACTION_ASSERT_PARAMS));
          setAssertLibraryParams(params);
          return new SessionResult(RESULT_SUCCESS).asFuture();
      }
      return new SessionResult(RESULT_ERROR_BAD_VALUE).asFuture();
    }

    private void assertLibraryParams(LibraryParams params) {
      synchronized (MockMediaLibraryService.class) {
        if (assertLibraryParams) {
          assertLibraryParamsEquals(expectedParams, params);
        }
      }
    }
  }

  private List<MediaItem> getPaginatedResult(List<String> items, int page, int pageSize) {
    if (items == null) {
      return null;
    } else if (items.size() == 0) {
      return new ArrayList<>();
    }

    int totalItemCount = items.size();
    int fromIndex = page * pageSize;
    int toIndex = Math.min((page + 1) * pageSize, totalItemCount);

    List<String> paginatedMediaIdList = new ArrayList<>();
    try {
      // The case of (fromIndex >= totalItemCount) will throw exception below.
      paginatedMediaIdList = items.subList(fromIndex, toIndex);
    } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
      Log.d(
          TAG,
          "Result is empty for given pagination arguments: totalItemCount="
              + totalItemCount
              + ", page="
              + page
              + ", pageSize="
              + pageSize,
          e);
    }

    // Create a list of MediaItem from the list of media IDs.
    List<MediaItem> result = new ArrayList<>();
    for (int i = 0; i < paginatedMediaIdList.size(); i++) {
      result.add(createPlayableMediaItem(paginatedMediaIdList.get(i)));
    }
    return result;
  }

  private static MediaItem createPlayableMediaItem(String mediaId) {
    // TODO(b/180293668): Set playable property to the media metadata.
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }
}

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
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_LIBRARY_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION_ASSERT_PARAMS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_ITEM_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.session.MediaBrowser.BrowserCallback;
import com.google.android.exoplayer2.session.MediaLibraryService.LibraryParams;
import com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaBrowser.BrowserCallback}.
 *
 * <p>This test inherits {@link MediaControllerCallbackTest} to ensure that inherited APIs from
 * {@link MediaController} works cleanly.
 */
// TODO: (internal cleanup) Move tests that aren't related with callbacks.
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserCallbackTest extends MediaControllerCallbackTest {
  private static final String TAG = "MediaBrowserCallbackTest";

  @Before
  public void setControllerType() {
    controllerTestRule.setControllerType(MediaBrowser.class);
  }

  private MediaBrowser createBrowser() throws Exception {
    return createBrowser(null, null);
  }

  private MediaBrowser createBrowser(
      @Nullable Bundle connectionHints, @Nullable BrowserCallback callback) throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA2_LIBRARY_SERVICE);
    return (MediaBrowser)
        controllerTestRule.createController(token, true, connectionHints, callback);
  }

  @Test
  public void getLibraryRoot() throws Exception {
    LibraryParams params =
        new LibraryParams.Builder()
            .setOffline(true)
            .setRecent(true)
            .setExtras(new Bundle())
            .build();

    MediaBrowser browser = createBrowser();
    setExpectedLibraryParam(browser, params);
    LibraryResult result = browser.getLibraryRoot(params).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.item.mediaId).isEqualTo(ROOT_ID);
    assertThat(TestUtils.equals(ROOT_EXTRAS, result.params.extras)).isTrue();
  }

  @Test
  public void getItem() throws Exception {
    String mediaId = MediaBrowserConstants.MEDIA_ID_GET_ITEM;

    LibraryResult result = createBrowser().getItem(mediaId).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.item.mediaId).isEqualTo(mediaId);
  }

  @Test
  public void getItem_unknownId() throws Exception {
    String mediaId = "random_media_id";

    LibraryResult result = createBrowser().getItem(mediaId).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_ERROR_BAD_VALUE);
    assertThat(result.item).isNull();
  }

  @Test
  public void getItem_nullResult() throws Exception {
    String mediaId = MediaBrowserConstants.MEDIA_ID_GET_NULL_ITEM;

    // Exception will be thrown in the service side, and the process will be crashed.
    // In that case one of following will happen
    //   Case 1) Process is crashed. Pending ListenableFuture will get error
    //   Case 2) Due to the frequent crashes with other tests, process may not crash immediately
    //           because the Android shows dialog 'xxx keeps stopping' and defer sending
    //           SIG_KILL until the user's explicit action.
    try {
      LibraryResult result = createBrowser().getItem(mediaId).get(TIMEOUT_MS, MILLISECONDS);
      // Case 1.
      assertThat(result.resultCode).isNotEqualTo(RESULT_SUCCESS);
    } catch (TimeoutException e) {
      // Case 2.
    }

    // Clean up RemoteMediaSession proactively to avoid crash at cleanUp()
    try {
      remoteSession.cleanUp();
    } catch (RemoteException e) {
      // Expected
    }
    remoteSession = null;
  }

  @Test
  public void getChildren() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID;
    int page = 4;
    int pageSize = 10;
    LibraryParams params = MediaTestUtils.createLibraryParams();

    MediaBrowser browser = createBrowser();
    setExpectedLibraryParam(browser, params);

    LibraryResult result =
        browser.getChildren(parentId, page, pageSize, params).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.params).isNull();

    MediaTestUtils.assertPaginatedListHasIds(
        result.items, MediaBrowserConstants.GET_CHILDREN_RESULT, page, pageSize);
  }

  @Test
  @LargeTest
  public void getChildren_withLongList() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    LibraryParams params = MediaTestUtils.createLibraryParams();

    MediaBrowser browser = createBrowser();
    setExpectedLibraryParam(browser, params);

    LibraryResult result =
        browser.getChildren(parentId, page, pageSize, params).get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.params).isNull();

    assertThat(result.items).hasSize(LONG_LIST_COUNT);
    for (int i = 0; i < result.items.size(); i++) {
      assertThat(result.items.get(i).mediaId).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void getChildren_emptyResult() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID_NO_CHILDREN;

    MediaBrowser browser = createBrowser();
    LibraryResult result = browser.getChildren(parentId, 1, 1, null).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.items.size()).isEqualTo(0);
  }

  @Test
  public void getChildren_nullResult() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID_ERROR;

    MediaBrowser browser = createBrowser();
    LibraryResult result = browser.getChildren(parentId, 1, 1, null).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isNotEqualTo(RESULT_SUCCESS);
    assertThat(result.items).isNull();
  }

  @Test
  public void searchCallbacks() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latchForSearch = new CountDownLatch(1);
    BrowserCallback callback =
        new BrowserCallback() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            assertThat(queryOut).isEqualTo(query);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            assertThat(itemCount).isEqualTo(MediaBrowserConstants.SEARCH_RESULT_COUNT);
            latchForSearch.countDown();
          }
        };

    // Request the search.
    MediaBrowser browser = createBrowser(null, callback);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult result = browser.search(query, testParams).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // Get the search result.
    result =
        browser.getSearchResult(query, page, pageSize, testParams).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    MediaTestUtils.assertPaginatedListHasIds(
        result.items, MediaBrowserConstants.SEARCH_RESULT, page, pageSize);
  }

  @Test
  @LargeTest
  public void searchCallbacks_withLongList() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    BrowserCallback callback =
        new BrowserCallback() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            assertThat(queryOut).isEqualTo(query);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            assertThat(itemCount).isEqualTo(MediaBrowserConstants.LONG_LIST_COUNT);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, callback);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult result = browser.search(query, testParams).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    result =
        browser
            .getSearchResult(query, page, pageSize, testParams)
            .get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    for (int i = 0; i < result.items.size(); i++) {
      assertThat(result.items.get(i).mediaId).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  @LargeTest
  public void onSearchResultChanged_searchTakesTime() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_TAKES_TIME;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    BrowserCallback callback =
        new BrowserCallback() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            assertThat(queryOut).isEqualTo(query);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            assertThat(itemCount).isEqualTo(MediaBrowserConstants.SEARCH_RESULT_COUNT);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, callback);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult result =
        browser
            .search(query, testParams)
            .get(MediaBrowserConstants.SEARCH_TIME_IN_MS + TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
  }

  @Test
  public void onSearchResultChanged_emptyResult() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    BrowserCallback callback =
        new BrowserCallback() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            assertThat(queryOut).isEqualTo(query);
            MediaTestUtils.assertLibraryParamsEquals(testParams, params);
            assertThat(itemCount).isEqualTo(0);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, callback);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult result = browser.search(query, testParams).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
  }

  @Test
  public void onChildrenChanged_calledWhenSubscribed() throws Exception {
    // This test uses MediaLibrarySession.notifyChildrenChanged().
    String expectedParentId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL;

    CountDownLatch latch = new CountDownLatch(1);
    BrowserCallback controllerCallbackProxy =
        new BrowserCallback() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            assertThat(parentId).isEqualTo(expectedParentId);
            assertThat(itemCount).isEqualTo(NOTIFY_CHILDREN_CHANGED_ITEM_COUNT);
            MediaTestUtils.assertLibraryParamsEquals(params, NOTIFY_CHILDREN_CHANGED_EXTRAS);
            latch.countDown();
          }
        };

    LibraryResult result =
        createBrowser(null, controllerCallbackProxy)
            .subscribe(expectedParentId, null)
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // The MediaLibrarySession in MockMediaLibraryService is supposed to call
    // notifyChildrenChanged() in its callback onSubscribe().
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onChildrenChanged_calledWhenSubscribed2() throws Exception {
    // This test uses MediaLibrarySession.notifyChildrenChanged(ControllerInfo).
    String expectedParentId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE;

    CountDownLatch latch = new CountDownLatch(1);
    BrowserCallback controllerCallbackProxy =
        new BrowserCallback() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            assertThat(parentId).isEqualTo(expectedParentId);
            assertThat(itemCount).isEqualTo(NOTIFY_CHILDREN_CHANGED_ITEM_COUNT);
            MediaTestUtils.assertLibraryParamsEquals(params, NOTIFY_CHILDREN_CHANGED_EXTRAS);
            latch.countDown();
          }
        };

    LibraryResult result =
        createBrowser(null, controllerCallbackProxy)
            .subscribe(expectedParentId, null)
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // The MediaLibrarySession in MockMediaLibraryService is supposed to call
    // notifyChildrenChanged(ControllerInfo) in its callback onSubscribe().
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onChildrenChanged_notCalledWhenNotSubscribed() throws Exception {
    // This test uses MediaLibrarySession.notifyChildrenChanged().
    String subscribedMediaId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID;
    CountDownLatch latch = new CountDownLatch(1);

    BrowserCallback controllerCallbackProxy =
        new BrowserCallback() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            latch.countDown();
          }
        };

    LibraryResult result =
        createBrowser(null, controllerCallbackProxy)
            .subscribe(subscribedMediaId, null)
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // The MediaLibrarySession in MockMediaLibraryService is supposed to call
    // notifyChildrenChanged() in its callback onSubscribe(), but with a different media ID.
    // Therefore, onChildrenChanged() should not be called.
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void onChildrenChanged_notCalledWhenNotSubscribed2() throws Exception {
    // This test uses MediaLibrarySession.notifyChildrenChanged(ControllerInfo).
    String subscribedMediaId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID;
    CountDownLatch latch = new CountDownLatch(1);

    BrowserCallback controllerCallbackProxy =
        new BrowserCallback() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            latch.countDown();
          }
        };

    LibraryResult result =
        createBrowser(null, controllerCallbackProxy)
            .subscribe(subscribedMediaId, null)
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // The MediaLibrarySession in MockMediaLibraryService is supposed to call
    // notifyChildrenChanged(ControllerInfo) in its callback onSubscribe(),
    // but with a different media ID.
    // Therefore, onChildrenChanged() should not be called.
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  private void setExpectedLibraryParam(MediaBrowser browser, LibraryParams params)
      throws Exception {
    SessionCommand command = new SessionCommand(CUSTOM_ACTION_ASSERT_PARAMS, null);
    Bundle args = new Bundle();
    args.putBundle(CUSTOM_ACTION_ASSERT_PARAMS, params.toBundle());
    SessionResult result = browser.sendCustomCommand(command, args).get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS);
  }
}

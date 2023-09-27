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
package androidx.media3.session;

import static androidx.media3.session.LibraryResult.RESULT_ERROR_BAD_VALUE;
import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MockMediaLibraryService.createNotifyChildrenChangedBundle;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION_ASSERT_PARAMS;
import static androidx.media3.test.session.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_KEY;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_VALUE;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_1;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_2;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.test.session.common.MediaBrowserConstants;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaBrowser.Listener}.
 *
 * <p>This test inherits {@link MediaControllerListenerTest} to ensure that inherited APIs from
 * {@link MediaController} works cleanly.
 */
// TODO: (internal cleanup) Move tests that aren't related with listener methods.
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserListenerTest extends MediaControllerListenerTest {

  @Before
  public void setControllerType() {
    controllerTestRule.setControllerType(MediaBrowser.class);
  }

  private MediaBrowser createBrowser() throws Exception {
    return createBrowser(null, null);
  }

  private MediaBrowser createBrowser(
      @Nullable Bundle connectionHints, @Nullable MediaBrowser.Listener listener) throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA3_LIBRARY_SERVICE);
    return (MediaBrowser) controllerTestRule.createController(token, connectionHints, listener);
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
    LibraryResult<MediaItem> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getLibraryRoot(params))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.value.mediaId).isEqualTo(ROOT_ID);
    assertThat(TestUtils.equals(ROOT_EXTRAS, result.params.extras)).isTrue();
  }

  @Test
  public void getLibraryRoot_correctExtraKeyAndValue() throws Exception {
    MediaBrowser browser = createBrowser();

    LibraryResult<MediaItem> resultForLibraryRoot =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get(TIMEOUT_MS, MILLISECONDS);

    Bundle extras = resultForLibraryRoot.params.extras;

    assertThat(extras.getInt(ROOT_EXTRAS_KEY, /* defaultValue= */ ROOT_EXTRAS_VALUE + 1))
        .isEqualTo(ROOT_EXTRAS_VALUE);
  }

  @Test
  public void getChildren_correctMetadataExtras() throws Exception {
    LibraryParams params = MediaTestUtils.createLibraryParams();
    MediaBrowser browser = createBrowser();

    LibraryResult<ImmutableList<MediaItem>> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> browser.getChildren(PARENT_ID, /* page= */ 4, /* pageSize= */ 10, params))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(libraryResult.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(libraryResult.value).isNotEmpty();
    for (MediaItem mediaItem : libraryResult.value) {
      int status =
          mediaItem.mediaMetadata.extras.getInt(
              EXTRAS_KEY_COMPLETION_STATUS,
              /* defaultValue= */ EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED + 1);
      assertThat(status).isEqualTo(EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
    }
  }

  @Test
  public void getItem_browsable() throws Exception {
    String mediaId = MediaBrowserConstants.MEDIA_ID_GET_BROWSABLE_ITEM;

    MediaBrowser browser = createBrowser();
    LibraryResult<MediaItem> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getItem(mediaId))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.value.mediaId).isEqualTo(mediaId);
    assertThat(result.value.mediaMetadata.isBrowsable).isTrue();
  }

  @Test
  public void getItem_playable() throws Exception {
    String mediaId = MediaBrowserConstants.MEDIA_ID_GET_PLAYABLE_ITEM;

    MediaBrowser browser = createBrowser();
    LibraryResult<MediaItem> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getItem(mediaId))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.value.mediaId).isEqualTo(mediaId);
    assertThat(result.value.mediaMetadata.isPlayable).isEqualTo(true);
  }

  @Test
  public void getItem_unknownId() throws Exception {
    String mediaId = "random_media_id";

    MediaBrowser browser = createBrowser();
    LibraryResult<MediaItem> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getItem(mediaId))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_ERROR_BAD_VALUE);
    assertThat(result.value).isNull();
  }

  @Test
  public void getChildren_correctLibraryResultWithExtras() throws Exception {
    String parentId = PARENT_ID;
    int page = 4;
    int pageSize = 10;
    LibraryParams params = MediaTestUtils.createLibraryParams();

    MediaBrowser browser = createBrowser();
    setExpectedLibraryParam(browser, params);

    LibraryResult<ImmutableList<MediaItem>> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getChildren(parentId, page, pageSize, params))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    MediaTestUtils.assertLibraryParamsEquals(params, result.params);
    MediaTestUtils.assertPaginatedListHasIds(
        result.value, MediaBrowserConstants.GET_CHILDREN_RESULT, page, pageSize);
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
    LibraryResult<ImmutableList<MediaItem>> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getChildren(parentId, page, pageSize, params))
            .get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    MediaTestUtils.assertLibraryParamsEquals(params, result.params);

    assertThat(result.value).hasSize(LONG_LIST_COUNT);
    for (int i = 0; i < result.value.size(); i++) {
      assertThat(result.value.get(i).mediaId).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void getChildren_emptyResult() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID_NO_CHILDREN;

    MediaBrowser browser = createBrowser();
    LibraryResult<ImmutableList<MediaItem>> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getChildren(parentId, 1, 1, null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(result.value.size()).isEqualTo(0);
  }

  @Test
  public void getChildren_nullResult() throws Exception {
    String parentId = MediaBrowserConstants.PARENT_ID_ERROR;

    MediaBrowser browser = createBrowser();
    LibraryResult<ImmutableList<MediaItem>> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getChildren(parentId, 1, 1, null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isNotEqualTo(RESULT_SUCCESS);
    assertThat(result.value).isNull();
  }

  @Test
  public void search() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latchForSearch = new CountDownLatch(1);
    AtomicReference<String> queryOutRef = new AtomicReference<>();
    AtomicInteger itemCountRef = new AtomicInteger();
    AtomicReference<LibraryParams> paramsRef = new AtomicReference<>();
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            queryOutRef.set(queryOut);
            itemCountRef.set(itemCount);
            paramsRef.set(params);
            latchForSearch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, listener);
    setExpectedLibraryParam(browser, testParams);

    // Request the search.
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.search(query, testParams))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latchForSearch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryOutRef.get()).isEqualTo(query);
    MediaTestUtils.assertLibraryParamsEquals(testParams, paramsRef.get());
    assertThat(itemCountRef.get()).isEqualTo(MediaBrowserConstants.SEARCH_RESULT_COUNT);

    // Get the search result.
    LibraryResult<ImmutableList<MediaItem>> searchResults =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getSearchResult(query, page, pageSize, testParams))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(searchResults.resultCode).isEqualTo(RESULT_SUCCESS);
    MediaTestUtils.assertPaginatedListHasIds(
        searchResults.value, MediaBrowserConstants.SEARCH_RESULT, page, pageSize);
  }

  @Test
  @LargeTest
  public void search_withLongList() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> queryOutRef = new AtomicReference<>();
    AtomicInteger itemCountRef = new AtomicInteger();
    AtomicReference<LibraryParams> paramsRef = new AtomicReference<>();
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            queryOutRef.set(queryOut);
            itemCountRef.set(itemCount);
            paramsRef.set(params);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, listener);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.search(query, testParams))
            .get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryOutRef.get()).isEqualTo(query);
    MediaTestUtils.assertLibraryParamsEquals(testParams, paramsRef.get());
    assertThat(itemCountRef.get()).isEqualTo(MediaBrowserConstants.LONG_LIST_COUNT);

    LibraryResult<ImmutableList<MediaItem>> searchResults =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getSearchResult(query, page, pageSize, testParams))
            .get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(searchResults.resultCode).isEqualTo(RESULT_SUCCESS);
    for (int i = 0; i < searchResults.value.size(); i++) {
      assertThat(searchResults.value.get(i).mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  @LargeTest
  public void onSearchResultChanged_searchTakesTime() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_TAKES_TIME;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> queryOutRef = new AtomicReference<>();
    AtomicInteger itemCountRef = new AtomicInteger();
    AtomicReference<LibraryParams> paramsRef = new AtomicReference<>();
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            queryOutRef.set(queryOut);
            itemCountRef.set(itemCount);
            paramsRef.set(params);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, listener);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.search(query, testParams))
            .get(LONG_TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryOutRef.get()).isEqualTo(query);
    MediaTestUtils.assertLibraryParamsEquals(testParams, paramsRef.get());
    assertThat(itemCountRef.get()).isEqualTo(MediaBrowserConstants.SEARCH_RESULT_COUNT);
  }

  @Test
  public void onSearchResultChanged_emptyResult() throws Exception {
    String query = MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> queryOutRef = new AtomicReference<>();
    AtomicInteger itemCountRef = new AtomicInteger();
    AtomicReference<LibraryParams> paramsRef = new AtomicReference<>();
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onSearchResultChanged(
              MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
            queryOutRef.set(queryOut);
            itemCountRef.set(itemCount);
            paramsRef.set(params);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, listener);
    setExpectedLibraryParam(browser, testParams);
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.search(query, testParams))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queryOutRef.get()).isEqualTo(query);
    MediaTestUtils.assertLibraryParamsEquals(testParams, paramsRef.get());
    assertThat(itemCountRef.get()).isEqualTo(0);
  }

  @Test
  public void onChildrenChanged_calledWhenSubscribed() throws Exception {
    // This test uses MediaLibrarySession.notifyChildrenChanged().
    String expectedParentId = SUBSCRIBE_PARENT_ID_1;

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> parentIdRef = new AtomicReference<>();
    AtomicInteger itemCountRef = new AtomicInteger();
    AtomicReference<LibraryParams> paramsRef = new AtomicReference<>();
    MediaBrowser.Listener browserListenerProxy =
        new MediaBrowser.Listener() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            parentIdRef.set(parentId);
            itemCountRef.set(itemCount);
            paramsRef.set(params);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, browserListenerProxy);
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe(expectedParentId, /* params= */ null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    // The MediaLibrarySession in MockMediaLibraryService is supposed to call
    // notifyChildrenChanged() in its onSubscribe() method.
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIdRef.get()).isEqualTo(expectedParentId);
    assertThat(itemCountRef.get()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void onChildrenChanged_calledWhenSubscribedAndWithDelay() throws Exception {
    String expectedParentId = SUBSCRIBE_PARENT_ID_2;

    CountDownLatch latch = new CountDownLatch(2);
    List<String> parentIds = new ArrayList<>();
    List<Integer> itemCounts = new ArrayList<>();
    MediaBrowser.Listener browserListenerProxy =
        new MediaBrowser.Listener() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            parentIds.add(parentId);
            itemCounts.add(itemCount);
            latch.countDown();
          }
        };

    MediaBrowser browser = createBrowser(null, browserListenerProxy);
    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  // Bundle to request to call onChildrenChanged() after a given delay.
                  Bundle requestNotifyChildren =
                      createNotifyChildrenChangedBundle(
                          expectedParentId,
                          /* itemCount= */ 12,
                          /* delayMs= */ 250L,
                          /* broadcast= */ false);
                  return browser.subscribe(
                      expectedParentId,
                      new LibraryParams.Builder().setExtras(requestNotifyChildren).build());
                })
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(expectedParentId, expectedParentId);
    assertThat(itemCounts).containsExactly(Integer.MAX_VALUE, 12);
  }

  @Test
  public void onChildrenChanged_notCalledWhenNotSubscribed() throws Exception {
    String mediaId1 = SUBSCRIBE_PARENT_ID_1;
    String mediaId2 = SUBSCRIBE_PARENT_ID_2;
    List<String> notifiedParentIds = new ArrayList<>();
    List<Integer> notifiedItemCounts = new ArrayList<>();
    CountDownLatch childrenChangedLatch = new CountDownLatch(4);
    CountDownLatch disconnectLatch = new CountDownLatch(2);
    MediaBrowser.Listener browserListener =
        new MediaBrowser.Listener() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser, String parentId, int itemCount, LibraryParams params) {
            notifiedParentIds.add(parentId);
            notifiedItemCounts.add(itemCount);
            childrenChangedLatch.countDown();
          }

          @Override
          public void onDisconnected(MediaController controller) {
            disconnectLatch.countDown();
          }
        };
    MediaBrowser browser1 = createBrowser(/* connectionHints= */ null, browserListener);
    MediaBrowser browser2 = createBrowser(/* connectionHints= */ null, browserListener);
    // Subscribe both browsers each to a different media IDs and request a second update after a
    // delay.
    LibraryResult<Void> subscriptionResult1 =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  Bundle requestNotifyChildren =
                      createNotifyChildrenChangedBundle(
                          mediaId1,
                          /* itemCount= */ 123,
                          /* delayMs= */ 200L,
                          /* broadcast= */ true);
                  return browser1.subscribe(
                      mediaId1,
                      new LibraryParams.Builder().setExtras(requestNotifyChildren).build());
                })
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(subscriptionResult1.resultCode).isEqualTo(RESULT_SUCCESS);
    LibraryResult<Void> result2 =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  Bundle requestNotifyChildren =
                      createNotifyChildrenChangedBundle(
                          mediaId2,
                          /* itemCount= */ 567,
                          /* delayMs= */ 200L,
                          /* broadcast= */ true);
                  return browser2.subscribe(
                      mediaId2,
                      new LibraryParams.Builder().setExtras(requestNotifyChildren).build());
                })
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result2.resultCode).isEqualTo(RESULT_SUCCESS);

    assertThat(childrenChangedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(notifiedParentIds)
        .containsExactly(
            mediaId1, // callback when subscribing browser1
            mediaId2, // callback when subscribing browser2
            mediaId1, // callback on first delayed notification
            mediaId2) // callback on second delayed notification
        .inOrder();
    assertThat(notifiedItemCounts)
        .containsExactly(Integer.MAX_VALUE, Integer.MAX_VALUE, 123, 567)
        .inOrder();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              browser1.release();
              browser2.release();
            });
    assertThat(disconnectLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  private void setExpectedLibraryParam(MediaBrowser browser, LibraryParams params)
      throws Exception {
    SessionCommand command =
        new SessionCommand(CUSTOM_ACTION_ASSERT_PARAMS, /* extras= */ Bundle.EMPTY);
    Bundle args = new Bundle();
    args.putBundle(CUSTOM_ACTION_ASSERT_PARAMS, params.toBundle());
    SessionResult result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.sendCustomCommand(command, args))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(result.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS);
  }
}

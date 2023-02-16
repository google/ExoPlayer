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

import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.text.TextUtils;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media.MediaBrowserServiceCompat.Result;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MockMediaBrowserServiceCompat.Proxy;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowserServiceCompat} with {@link MediaBrowser}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserServiceCompatCallbackWithMediaBrowserTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private Context context;
  private SessionToken token;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    token =
        new SessionToken(
            context, new ComponentName(context, LocalMockMediaBrowserServiceCompat.class));
  }

  @Test
  public void onGetRootCalledByGetLibraryRoot() throws Exception {
    String testMediaId = "testOnGetRootCalledByGetLibraryRoot";
    Bundle testExtras = new Bundle();
    testExtras.putString(testMediaId, testMediaId);
    LibraryParams testParams =
        new LibraryParams.Builder().setSuggested(true).setExtras(testExtras).build();

    Bundle testReturnedExtras = new Bundle(testExtras);
    testReturnedExtras.putBoolean(BrowserRoot.EXTRA_OFFLINE, true);
    BrowserRoot browserRoot = new BrowserRoot(testMediaId, testReturnedExtras);

    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
            assertThat(clientPackageName).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
            if (rootHints != null && rootHints.keySet().contains(testMediaId)) {
              MediaTestUtils.assertLibraryParamsEquals(testParams, rootHints);
              // This should happen because getLibraryRoot() is called with testExtras.
              latch.countDown();
              return browserRoot;
            }
            // For other random connection requests.
            return new BrowserRoot("rootId", null);
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<MediaItem> result = browser.getLibraryRoot(testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(result.value.mediaId).isEqualTo(testMediaId);

    LibraryParams returnedParams = result.params;
    assertThat(returnedParams.isRecent)
        .isEqualTo(testReturnedExtras.getBoolean(BrowserRoot.EXTRA_RECENT));
    assertThat(returnedParams.isOffline)
        .isEqualTo(testReturnedExtras.getBoolean(BrowserRoot.EXTRA_OFFLINE));
    assertThat(returnedParams.isSuggested)
        .isEqualTo(testReturnedExtras.getBoolean(BrowserRoot.EXTRA_SUGGESTED));

    // Note that TestUtils#equals() cannot be used for this because
    // MediaBrowserServiceCompat adds extra_client_version to the rootHints.
    assertThat(TestUtils.contains(returnedParams.extras, testExtras)).isTrue();
  }

  @Test
  public void onLoadItemCalledByGetItem_browsable() throws Exception {
    String testMediaId = "test_media_item";
    MediaBrowserCompat.MediaItem testItem =
        createBrowserMediaItem(testMediaId, /* browsable= */ true, /* playable= */ false);
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
            if (testMediaId.equals(itemId)) {
              result.sendResult(testItem);
              latch.countDown();
            }
          }
        });

    RemoteMediaBrowser browser =
        new RemoteMediaBrowser(
            context, token, /* waitForConnection= */ true, /* connectionHints= */ null);
    LibraryResult<MediaItem> result = browser.getItem(testMediaId);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertItemEquals(testItem, result.value);
    assertThat(result.value.mediaMetadata.isBrowsable).isTrue();
  }

  @Test
  public void onLoadItemCalledByGetItem_playable() throws Exception {
    String testMediaId = "test_media_item";
    MediaBrowserCompat.MediaItem testItem =
        createBrowserMediaItem(testMediaId, /* browsable= */ false, /* playable= */ true);
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
            if (testMediaId.equals(itemId)) {
              result.sendResult(testItem);
              latch.countDown();
            }
          }
        });

    RemoteMediaBrowser browser =
        new RemoteMediaBrowser(
            context, token, /* waitForConnection= */ true, /* connectionHints= */ null);
    LibraryResult<MediaItem> result = browser.getItem(testMediaId);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertItemEquals(testItem, result.value);
    assertThat(result.value.mediaMetadata.isPlayable).isTrue();
  }

  @Test
  public void onLoadItemCalledByGetItem_nullResult() throws Exception {
    String testMediaId = "test_media_item";
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
            assertThat(itemId).isEqualTo(testMediaId);
            result.sendResult(null);
            latch.countDown();
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<MediaItem> result = browser.getItem(testMediaId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isNotEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onLoadChildrenWithoutOptionsCalledByGetChildrenWithoutOptions() throws Exception {
    String testParentId = "test_media_parent";
    int testPage = 2;
    int testPageSize = 4;
    List<MediaBrowserCompat.MediaItem> testFullMediaItemList =
        createBrowserMediaItems((testPage + 1) * testPageSize);
    List<MediaBrowserCompat.MediaItem> testPaginatedMediaItemList =
        testFullMediaItemList.subList(
            testPage * testPageSize,
            Math.min((testPage + 1) * testPageSize, testFullMediaItemList.size()));
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result) {
            assertThat(parentId).isEqualTo(testParentId);
            result.sendResult(testFullMediaItemList);
            latch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getChildren(testParentId, testPage, testPageSize, null);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertItemsEquals(testPaginatedMediaItemList, result.value);
  }

  @Test
  public void onLoadChildrenWithOptionsCalledByGetChildrenWithoutOptions() throws Exception {
    String testParentId = "test_media_parent";
    int testPage = 2;
    int testPageSize = 4;
    List<MediaBrowserCompat.MediaItem> testMediaItemList = createBrowserMediaItems(testPageSize);
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result) {
            assertWithMessage("This isn't expected to be called").fail();
          }

          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, Bundle options) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(testPage);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(testPageSize);
            assertThat(options.keySet().size()).isEqualTo(2);
            result.sendResult(testMediaItemList);
            latch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getChildren(testParentId, testPage, testPageSize, null);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertItemsEquals(testMediaItemList, result.value);
    assertThat(result.params).isNull();
  }

  @Test
  public void onLoadChildrenWithOptionsCalledByGetChildrenWithoutOptions_nullResult()
      throws Exception {
    String testParentId = "test_media_parent";
    int testPage = 2;
    int testPageSize = 4;
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, Bundle options) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(testPage);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(testPageSize);
            result.sendResult(null);
            latch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getChildren(testParentId, testPage, testPageSize, null);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isNotEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(result.params).isNull();
  }

  @Test
  public void onLoadChildrenWithOptionsCalledByGetChildrenWithOptions() throws Exception {
    String testParentId = "test_media_parent";
    int testPage = 2;
    int testPageSize = 4;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    List<MediaBrowserCompat.MediaItem> testMediaItemList =
        createBrowserMediaItems(testPageSize / 2);
    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, Bundle options) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(testPage);
            assertThat(options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(testPageSize);
            assertThat(TestUtils.contains(options, testParams.extras)).isTrue();
            result.sendResult(testMediaItemList);
            latch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getChildren(testParentId, testPage, testPageSize, testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertItemsEquals(testMediaItemList, result.value);
    assertThat(result.params).isNull();
  }

  @Test
  public void onLoadChildrenCalledBySubscribe() throws Exception {
    String testParentId = "testOnLoadChildrenCalledBySubscribe";
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    CountDownLatch subscribeLatch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, Bundle option) {
            assertThat(parentId).isEqualTo(testParentId);
            MediaTestUtils.assertLibraryParamsEquals(testParams, option);
            result.sendResult(Collections.emptyList());
            subscribeLatch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<Void> result = browser.subscribe(testParentId, testParams);
    assertThat(subscribeLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onLoadChildrenCalledBySubscribe_failed() throws Exception {
    String testParentId = "testOnLoadChildrenCalledBySubscribe_failed";
    CountDownLatch subscribeLatch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onLoadChildren(
              String parentId, Result<List<MediaBrowserCompat.MediaItem>> result, Bundle option) {
            assertThat(parentId).isEqualTo(testParentId);
            // Cannot use Result#sendError() for sending error here. The API is specific to
            // custom action.
            result.sendResult(null);
            subscribeLatch.countDown();
          }
        });
    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<Void> result = browser.subscribe(testParentId, null);
    assertThat(subscribeLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isNotEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onSearchCalledBySearch() throws Exception {
    String testQuery = "search_query";
    int testPage = 2;
    int testPageSize = 4;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    List<MediaBrowserCompat.MediaItem> testFullSearchResult =
        createBrowserMediaItems((testPage + 1) * testPageSize + 3);

    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onSearch(
              String query, Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
            assertThat(query).isEqualTo(testQuery);
            MediaTestUtils.assertLibraryParamsEquals(testParams, extras);
            result.sendResult(testFullSearchResult);
            latch.countDown();
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<Void> result = browser.search(testQuery, testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onSearchCalledBySearch_nullResult() throws Exception {
    String testQuery = "search_query";

    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onSearch(
              String query, Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
            result.sendResult(null);
            latch.countDown();
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<Void> result = browser.search(testQuery, null);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onSearchCalledByGetSearchResult() throws Exception {
    String testQuery = "search_query";
    int testPage = 2;
    int testPageSize = 4;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onSearch(
              String query, Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
            assertThat(query).isEqualTo(testQuery);
            MediaTestUtils.assertLibraryParamsEquals(testParams, extras);
            assertThat(extras.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(testPage);
            assertThat(extras.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(testPageSize);
            result.sendResult(Collections.emptyList());
            latch.countDown();
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getSearchResult(testQuery, testPage, testPageSize, testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  @Test
  public void onSearchCalledByGetSearchResult_nullResult() throws Exception {
    String testQuery = "search_query";
    int testPage = 2;
    int testPageSize = 4;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();

    CountDownLatch latch = new CountDownLatch(1);
    MockMediaBrowserServiceCompat.setMediaBrowserServiceProxy(
        new Proxy() {
          @Override
          public void onSearch(
              String query, Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
            result.sendResult(null);
            latch.countDown();
          }
        });

    RemoteMediaBrowser browser = new RemoteMediaBrowser(context, token, true, null);
    LibraryResult<ImmutableList<MediaItem>> result =
        browser.getSearchResult(testQuery, testPage, testPageSize, testParams);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(result.resultCode).isNotEqualTo(LibraryResult.RESULT_SUCCESS);
  }

  private static MediaBrowserCompat.MediaItem createBrowserMediaItem(String mediaId) {
    return createBrowserMediaItem(mediaId, /* browsable= */ false, /* playable= */ true);
  }

  private static MediaBrowserCompat.MediaItem createBrowserMediaItem(
      String mediaId, boolean browsable, boolean playable) {
    MediaDescriptionCompat desc =
        new MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle("testTitle")
            .setSubtitle("testSubtitle")
            .setDescription("testDescription")
            .setIconUri(Uri.parse("androidx://media3-session/icon"))
            .setMediaUri(Uri.parse("androidx://media3-session/media"))
            .setExtras(TestUtils.createTestBundle())
            .build();
    int flags =
        (playable ? MediaBrowserCompat.MediaItem.FLAG_PLAYABLE : 0)
            | (browsable ? MediaBrowserCompat.MediaItem.FLAG_BROWSABLE : 0);
    return new MediaBrowserCompat.MediaItem(desc, flags);
  }

  private static List<MediaBrowserCompat.MediaItem> createBrowserMediaItems(int size) {
    List<MediaBrowserCompat.MediaItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(createBrowserMediaItem("browserItem_" + i));
    }
    return list;
  }

  private static void assertItemEquals(
      MediaBrowserCompat.MediaItem browserItem, MediaItem commonItem) {
    assertThat(commonItem.mediaId).isEqualTo(browserItem.getMediaId());
    MediaDescriptionCompat description = browserItem.getDescription();
    assertThat(commonItem.requestMetadata.mediaUri).isEqualTo(description.getMediaUri());
    MediaMetadata metadata = commonItem.mediaMetadata;
    assertThat(TextUtils.equals(metadata.title, description.getTitle())).isTrue();
    assertThat(TextUtils.equals(metadata.subtitle, description.getSubtitle())).isTrue();
    assertThat(TextUtils.equals(metadata.description, description.getDescription())).isTrue();
    assertThat(metadata.artworkUri).isEqualTo(description.getIconUri());
    assertThat(TestUtils.equals(metadata.extras, description.getExtras())).isTrue();
  }

  private static void assertItemsEquals(
      List<MediaBrowserCompat.MediaItem> browserItemList, List<MediaItem> commonItemList) {
    assertThat(commonItemList.size()).isEqualTo(browserItemList.size());
    for (int i = 0; i < browserItemList.size(); i++) {
      assertItemEquals(browserItemList.get(i), commonItemList.get(i));
    }
  }
}

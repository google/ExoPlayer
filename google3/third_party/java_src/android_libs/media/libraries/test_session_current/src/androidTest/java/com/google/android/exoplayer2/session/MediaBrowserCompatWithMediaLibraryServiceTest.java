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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_LIBRARY_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CHILDREN_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.CUSTOM_ACTION_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.GET_CHILDREN_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID_ERROR;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID_LONG_LIST;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.PARENT_ID_NO_CHILDREN;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_EXTRAS;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.ROOT_ID;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_ERROR;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_RESULT;
import static com.google.android.exoplayer2.session.vct.common.MediaBrowserConstants.SEARCH_RESULT_COUNT;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.CustomActionCallback;
import android.support.v4.media.MediaBrowserCompat.ItemCallback;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserCompat.SearchCallback;
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowserCompat} with {@link MediaLibraryService}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserCompatWithMediaLibraryServiceTest
    extends MediaBrowserCompatWithMediaSessionServiceTest {

  @Override
  ComponentName getServiceComponent() {
    return MOCK_MEDIA2_LIBRARY_SERVICE;
  }

  @Test
  public void getRoot() throws Exception {
    // The MockMediaLibraryService gives MediaBrowserConstants.ROOT_ID as root ID, and
    // MediaBrowserConstants.ROOT_EXTRAS as extras.
    handler.postAndSync(
        () -> {
          browserCompat =
              new MediaBrowserCompat(
                  context, getServiceComponent(), connectionCallback, /* rootHint= */ null);
        });
    connectAndWait();
    assertThat(browserCompat.getRoot()).isEqualTo(ROOT_ID);

    // Note: Cannot use equals() here because browser compat's extra contains server version,
    // extra binder, and extra messenger.
    assertThat(TestUtils.contains(browserCompat.getExtras(), ROOT_EXTRAS)).isTrue();
  }

  @Test
  public void getItem() throws InterruptedException {
    String mediaId = MEDIA_ID_GET_ITEM;

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            assertThat(item.getMediaId()).isEqualTo(mediaId);
            assertThat(item).isNotNull();
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getItem_nullResult() throws InterruptedException {
    String mediaId = "random_media_id";

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            assertThat(item).isNull();
            latch.countDown();
          }

          @Override
          public void onError(@NonNull String itemId) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren() throws InterruptedException {
    String testParentId = PARENT_ID;

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(children).isNotNull();
            assertThat(children.size()).isEqualTo(GET_CHILDREN_RESULT.size());

            // Compare the given results with originals.
            for (int i = 0; i < children.size(); i++) {
              assertThat(children.get(i).getMediaId()).isEqualTo(GET_CHILDREN_RESULT.get(i));
            }
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children, @NonNull Bundle option) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_withLongList() throws InterruptedException {
    String testParentId = PARENT_ID_LONG_LIST;

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(children).isNotNull();
            assertThat(children.size() < LONG_LIST_COUNT).isTrue();

            // Compare the given results with originals.
            for (int i = 0; i < children.size(); i++) {
              assertThat(children.get(i).getMediaId())
                  .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
            }
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children, @NonNull Bundle option) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_withPagination() throws InterruptedException {
    String testParentId = PARENT_ID;
    int page = 4;
    int pageSize = 10;
    Bundle extras = new Bundle();
    extras.putString(testParentId, testParentId);

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    Bundle option = new Bundle();
    option.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    option.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    browserCompat.subscribe(
        testParentId,
        option,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(
              @NonNull String parentId,
              @NonNull List<MediaItem> children,
              @NonNull Bundle options) {
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(option.getInt(MediaBrowserCompat.EXTRA_PAGE)).isEqualTo(page);
            assertThat(option.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE)).isEqualTo(pageSize);
            assertThat(children).isNotNull();

            int fromIndex = page * pageSize;
            int toIndex = Math.min((page + 1) * pageSize, CHILDREN_COUNT);

            // Compare the given results with originals.
            for (int originalIndex = fromIndex; originalIndex < toIndex; originalIndex++) {
              int relativeIndex = originalIndex - fromIndex;
              assertThat(children.get(relativeIndex).getMediaId())
                  .isEqualTo(GET_CHILDREN_RESULT.get(originalIndex));
            }
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_emptyResult() throws InterruptedException {
    String testParentId = PARENT_ID_NO_CHILDREN;

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(
              @NonNull String parentId, @NonNull List<MediaItem> children) {
            assertThat(children).isNotNull();
            assertThat(children.size()).isEqualTo(0);
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_nullResult() throws InterruptedException {
    String testParentId = PARENT_ID_ERROR;

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onError(@NonNull String parentId) {
            assertThat(parentId).isEqualTo(testParentId);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(
              @NonNull String parentId,
              @NonNull List<MediaItem> children,
              @NonNull Bundle options) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search() throws InterruptedException {
    String testQuery = SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(
              @NonNull String query, Bundle extras, @NonNull List<MediaItem> items) {
            assertThat(query).isEqualTo(testQuery);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();
            int expectedSize =
                Math.max(Math.min(pageSize, SEARCH_RESULT_COUNT - pageSize * page), 0);
            assertThat(items.size()).isEqualTo(expectedSize);

            int fromIndex = page * pageSize;
            int toIndex = Math.min((page + 1) * pageSize, SEARCH_RESULT_COUNT);

            // Compare the given results with originals.
            for (int originalIndex = fromIndex; originalIndex < toIndex; originalIndex++) {
              int relativeIndex = originalIndex - fromIndex;
              assertThat(items.get(relativeIndex).getMediaId())
                  .isEqualTo(SEARCH_RESULT.get(originalIndex));
            }
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search_withLongList() throws InterruptedException {
    String testQuery = SEARCH_QUERY_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(
              @NonNull String query, Bundle extras, @NonNull List<MediaItem> items) {
            assertThat(query).isEqualTo(testQuery);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();

            assertThat(items).isNotNull();
            assertThat(items.size() < LONG_LIST_COUNT).isTrue();
            for (int i = 0; i < items.size(); i++) {
              assertThat(items.get(i).getMediaId())
                  .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
            }
            latch.countDown();
          }
        });
    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search_emptyResult() throws InterruptedException {
    String testQuery = SEARCH_QUERY_EMPTY_RESULT;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(
              @NonNull String query, Bundle extras, @NonNull List<MediaItem> items) {
            assertThat(query).isEqualTo(testQuery);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();
            assertThat(items).isNotNull();
            assertThat(items.size()).isEqualTo(0);
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search_error() throws InterruptedException {
    String testQuery = SEARCH_QUERY_ERROR;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onError(@NonNull String query, Bundle extras) {
            assertThat(query).isEqualTo(testQuery);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();
            latch.countDown();
          }

          @Override
          public void onSearchResult(
              @NonNull String query, Bundle extras, @NonNull List<MediaItem> items) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  // TODO: Add test for onCustomCommand() in MediaLibrarySessionLegacyCallbackTest.
  @Test
  public void customAction() throws InterruptedException {
    Bundle testArgs = new Bundle();
    testArgs.putString("args_key", "args_value");

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.sendCustomAction(
        CUSTOM_ACTION,
        testArgs,
        new CustomActionCallback() {
          @Override
          public void onResult(String action, Bundle extras, Bundle resultData) {
            assertThat(action).isEqualTo(CUSTOM_ACTION);
            assertThat(TestUtils.equals(testArgs, extras)).isTrue();
            assertThat(TestUtils.equals(CUSTOM_ACTION_EXTRAS, resultData)).isTrue();
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  // TODO: Add test for onCustomCommand() in MediaLibrarySessionLegacyCallbackTest.
  @Test
  public void customAction_rejected() throws InterruptedException {
    // This action will not be allowed by the library session.
    String testAction = "random_custom_action";

    connectAndWait();
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.sendCustomAction(
        testAction,
        null,
        new CustomActionCallback() {
          @Override
          public void onResult(String action, Bundle extras, Bundle resultData) {
            latch.countDown();
          }
        });
    assertWithMessage("BrowserCompat shouldn't receive custom command")
        .that(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS))
        .isFalse();
  }
}

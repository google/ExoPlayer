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

import static androidx.media.utils.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MockMediaLibraryService.CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT;
import static androidx.media3.test.session.common.CommonConstants.METADATA_ARTWORK_URI;
import static androidx.media3.test.session.common.CommonConstants.METADATA_DESCRIPTION;
import static androidx.media3.test.session.common.CommonConstants.METADATA_EXTRA_KEY;
import static androidx.media3.test.session.common.CommonConstants.METADATA_EXTRA_VALUE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_MEDIA_URI;
import static androidx.media3.test.session.common.CommonConstants.METADATA_SUBTITLE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_TITLE;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.MediaBrowserConstants.CHILDREN_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION;
import static androidx.media3.test.session.common.MediaBrowserConstants.CUSTOM_ACTION_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.GET_CHILDREN_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.LONG_LIST_COUNT;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_BROWSABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_ITEM_WITH_METADATA;
import static androidx.media3.test.session.common.MediaBrowserConstants.MEDIA_ID_GET_PLAYABLE_ITEM;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID_NO_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_KEY;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_VALUE;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_ERROR;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT;
import static androidx.media3.test.session.common.MediaBrowserConstants.SEARCH_RESULT_COUNT;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
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
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.truth.os.BundleSubject;
import androidx.test.filters.LargeTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowserCompat} with {@link MediaLibraryService}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserCompatWithMediaLibraryServiceTest
    extends MediaBrowserCompatWithMediaSessionServiceTest {

  @Override
  ComponentName getServiceComponent() {
    return MOCK_MEDIA3_LIBRARY_SERVICE;
  }

  @Test
  public void getRoot() throws Exception {
    // The MockMediaLibraryService gives MediaBrowserConstants.ROOT_ID as root ID, and
    // MediaBrowserConstants.ROOT_EXTRAS as extras.
    connectAndWait(/* connectionHints= */ Bundle.EMPTY);

    assertThat(browserCompat.getRoot()).isEqualTo(ROOT_ID);
    assertThat(
            browserCompat
                .getExtras()
                .getInt(ROOT_EXTRAS_KEY, /* defaultValue= */ ROOT_EXTRAS_VALUE + 1))
        .isEqualTo(ROOT_EXTRAS_VALUE);

    // Note: Cannot use equals() here because browser compat's extra contains server version,
    // extra binder, and extra messenger.
    assertThat(TestUtils.contains(browserCompat.getExtras(), ROOT_EXTRAS)).isTrue();
  }

  @Test
  public void getItem_browsable() throws Exception {
    String mediaId = MEDIA_ID_GET_BROWSABLE_ITEM;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    assertThat(itemRef.get().isBrowsable()).isTrue();
    assertThat(itemRef.get().getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_playable() throws Exception {
    String mediaId = MEDIA_ID_GET_PLAYABLE_ITEM;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    assertThat(itemRef.get().isPlayable()).isTrue();
    assertThat(itemRef.get().getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_metadata() throws Exception {
    String mediaId = MEDIA_ID_GET_ITEM_WITH_METADATA;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaItem> itemRef = new AtomicReference<>();
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            itemRef.set(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(itemRef.get().getMediaId()).isEqualTo(mediaId);
    MediaDescriptionCompat description = itemRef.get().getDescription();
    assertThat(TextUtils.equals(description.getTitle(), METADATA_TITLE)).isTrue();
    assertThat(TextUtils.equals(description.getSubtitle(), METADATA_SUBTITLE)).isTrue();
    assertThat(TextUtils.equals(description.getDescription(), METADATA_DESCRIPTION)).isTrue();
    assertThat(description.getIconUri()).isEqualTo(METADATA_ARTWORK_URI);
    assertThat(description.getMediaUri()).isEqualTo(METADATA_MEDIA_URI);
    BundleSubject.assertThat(description.getExtras())
        .string(METADATA_EXTRA_KEY)
        .isEqualTo(METADATA_EXTRA_VALUE);
    assertThat(description.getIconBitmap()).isNotNull();
  }

  @Test
  public void getItem_nullResult() throws Exception {
    String mediaId = "random_media_id";

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
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
          public void onError(String itemId) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getItem_commandGetItemNotAvailable_reportsNull() throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);
    List<MediaItem> capturedMediaItems = new ArrayList<>();
    browserCompat.getItem(
        MEDIA_ID_GET_BROWSABLE_ITEM,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaItem item) {
            capturedMediaItems.add(item);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(capturedMediaItems).containsExactly((Object) null);
  }

  @Test
  public void getChildren() throws Exception {
    String testParentId = PARENT_ID;
    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    List<MediaItem> receivedChildren = new ArrayList<>();
    final String[] receivedParentId = new String[1];

    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            receivedParentId[0] = parentId;
            receivedChildren.addAll(children);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle option) {
            assertWithMessage("").fail();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedParentId[0]).isEqualTo(testParentId);
    assertThat(receivedChildren).hasSize(GET_CHILDREN_RESULT.size());
    // Compare the given results with originals.
    for (int i = 0; i < receivedChildren.size(); i++) {
      MediaItem mediaItem = receivedChildren.get(i);
      assertThat(mediaItem.getMediaId()).isEqualTo(GET_CHILDREN_RESULT.get(i));
      assertThat(
              mediaItem
                  .getDescription()
                  .getExtras()
                  .getInt(
                      EXTRAS_KEY_COMPLETION_STATUS,
                      /* defaultValue= */ EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED + 1))
          .isEqualTo(EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
      assertThat(mediaItem.getDescription().getIconBitmap()).isNotNull();
    }
  }

  @Test
  public void getChildren_withLongList() throws Exception {
    String testParentId = PARENT_ID_LONG_LIST;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
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
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle option) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_withPagination() throws Exception {
    String testParentId = PARENT_ID;
    int page = 4;
    int pageSize = 10;
    Bundle extras = new Bundle();
    extras.putString(testParentId, testParentId);

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    Bundle option = new Bundle();
    option.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    option.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    browserCompat.subscribe(
        testParentId,
        option,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
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
              assertThat(children.get(relativeIndex).getDescription().getIconBitmap()).isNotNull();
            }
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_authErrorResult() throws Exception {
    String testParentId = PARENT_ID_AUTH_EXPIRED_ERROR;
    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch errorLatch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            assertThat(parentId).isEqualTo(testParentId);
            errorLatch.countDown();
          }
        });
    assertThat(errorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(lastReportedPlaybackStateCompat.getState())
        .isEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(
            lastReportedPlaybackStateCompat
                .getExtras()
                .getString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT))
        .isEqualTo(PARENT_ID_AUTH_EXPIRED_ERROR_KEY_ERROR_RESOLUTION_ACTION_LABEL);

    CountDownLatch successLatch = new CountDownLatch(1);
    browserCompat.subscribe(
        PARENT_ID,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            assertThat(parentId).isEqualTo(PARENT_ID);
            successLatch.countDown();
          }
        });
    assertThat(successLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    // Any successful calls remove the error state,
    assertThat(lastReportedPlaybackStateCompat.getState())
        .isNotEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(
            lastReportedPlaybackStateCompat
                .getExtras()
                .getString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT))
        .isNull();
  }

  @Test
  public void getChildren_emptyResult() throws Exception {
    String testParentId = PARENT_ID_NO_CHILDREN;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children) {
            assertThat(children).isNotNull();
            assertThat(children.size()).isEqualTo(0);
            latch.countDown();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_nullResult() throws Exception {
    String testParentId = PARENT_ID_ERROR;

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.subscribe(
        testParentId,
        new SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            assertThat(parentId).isEqualTo(testParentId);
            latch.countDown();
          }

          @Override
          public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void getChildren_commandGetChildrenNotAvailable_reportsError() throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN);
    handler.postAndSync(
        () -> {
          browserCompat =
              new MediaBrowserCompat(context, getServiceComponent(), connectionCallback, rootHints);
        });
    browserCompat.connect();
    assertThat(connectionCallback.connectedLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS))
        .isTrue();
    CountDownLatch errorLatch = new CountDownLatch(1);

    browserCompat.subscribe(
        PARENT_ID,
        new MediaBrowserCompat.SubscriptionCallback() {
          @Override
          public void onError(String parentId) {
            errorLatch.countDown();
          }
        });

    assertThat(errorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search() throws Exception {
    String testQuery = SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
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
  public void search_withLongList() throws Exception {
    String testQuery = SEARCH_QUERY_LONG_LIST;
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
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
  public void search_emptyResult() throws Exception {
    String testQuery = SEARCH_QUERY_EMPTY_RESULT;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
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
  public void search_commandSearchNotAvailable_reportsError() throws Exception {
    String testQuery = SEARCH_QUERY;
    int page = 4;
    int pageSize = 10;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    testExtras.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_SEARCH);
    connectAndWait(rootHints);
    CountDownLatch latch = new CountDownLatch(1);

    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onError(String query, Bundle extras) {
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void search_error() throws Exception {
    String testQuery = SEARCH_QUERY_ERROR;
    Bundle testExtras = new Bundle();
    testExtras.putString(testQuery, testQuery);

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    browserCompat.search(
        testQuery,
        testExtras,
        new SearchCallback() {
          @Override
          public void onError(String query, Bundle extras) {
            assertThat(query).isEqualTo(testQuery);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();
            latch.countDown();
          }

          @Override
          public void onSearchResult(String query, Bundle extras, List<MediaItem> items) {
            assertWithMessage("").fail();
          }
        });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  // TODO: Add test for onCustomCommand() in MediaLibrarySessionLegacyCallbackTest.
  @Test
  public void customAction() throws Exception {
    Bundle testArgs = new Bundle();
    testArgs.putString("args_key", "args_value");

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
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
  public void customAction_rejected() throws Exception {
    // This action will not be allowed by the library session.
    String testAction = "random_custom_action";

    connectAndWait(/* connectionHints= */ Bundle.EMPTY);
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

  @Test
  public void rootBrowserHints_usedAsConnectionHints() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putString(CONNECTION_HINTS_CUSTOM_LIBRARY_ROOT, "myLibraryRoot");
    connectAndWait(connectionHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo("myLibraryRoot");
  }

  @Test
  public void rootBrowserHints_searchSupported_reportsSearchSupported() throws Exception {
    connectAndWait(/* connectionHints= */ Bundle.EMPTY);

    boolean isSearchSupported =
        browserCompat.getExtras().getBoolean(BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED);

    assertThat(isSearchSupported).isTrue();
  }

  @Test
  public void rootBrowserHints_searchNotSupported_reportsSearchNotSupported() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        MockMediaLibraryService.CONNECTION_HINTS_KEY_REMOVE_COMMAND_CODE,
        SessionCommand.COMMAND_CODE_LIBRARY_SEARCH);
    connectAndWait(connectionHints);

    boolean isSearchSupported =
        browserCompat.getExtras().getBoolean(BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED);

    assertThat(isSearchSupported).isFalse();
  }

  @Test
  public void rootBrowserHints_legacyBrowsableFlagSet_receivesRootWithBrowsableChildrenOnly()
      throws Exception {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
        MediaItem.FLAG_BROWSABLE);
    connectAndWait(rootHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo(ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY);
  }

  @Test
  public void rootBrowserHints_legacyPlayableFlagSet_receivesDefaultRoot() throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putInt(
        androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
        MediaItem.FLAG_BROWSABLE | MediaItem.FLAG_PLAYABLE);
    connectAndWait(connectionHints);

    String root = browserCompat.getRoot();

    assertThat(root).isEqualTo(ROOT_ID);
  }
}

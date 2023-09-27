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

import static androidx.media3.session.LibraryResult.RESULT_ERROR_NOT_SUPPORTED;
import static androidx.media3.session.LibraryResult.RESULT_ERROR_SESSION_SETUP_REQUIRED;
import static androidx.media3.test.session.common.MediaBrowserConstants.SUBSCRIBE_PARENT_ID_1;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaLibrarySession.Callback}.
 *
 * <p>TODO: Make this class extend MediaSessionCallbackTest. TODO: Create MediaLibrarySessionTest
 * which extends MediaSessionTest.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaLibrarySessionCallbackTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaLibrarySessionCallbackTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
  }

  @Test
  public void onSubscribeUnsubscribe() throws Exception {
    String testParentId = "testSubscribeUnsubscribeId";
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<LibraryParams> libraryParamsRef = new AtomicReference<>();
    List<ControllerInfo> subscribedControllers = new ArrayList<>();
    List<String> parentIds = new ArrayList<>();
    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<Void>> onSubscribe(
              MediaLibrarySession session,
              ControllerInfo browser,
              String parentId,
              @Nullable LibraryParams params) {
            parentIds.add(parentId);
            libraryParamsRef.set(params);
            subscribedControllers.addAll(session.getSubscribedControllers(parentId));
            latch.countDown();
            return Futures.immediateFuture(LibraryResult.ofVoid(params));
          }

          @Override
          public ListenableFuture<LibraryResult<Void>> onUnsubscribe(
              MediaLibrarySession session, ControllerInfo browser, String parentId) {
            parentIds.add(parentId);
            subscribedControllers.addAll(session.getSubscribedControllers(parentId));
            latch.countDown();
            return Futures.immediateFuture(LibraryResult.ofVoid());
          }
        };
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, sessionCallback)
                .setId("testOnSubscribe")
                .build());
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean("onSubscribeTestBrowser", true);
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), connectionHints);

    browser.subscribe(testParentId, testParams);
    browser.unsubscribe(testParentId);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(testParentId, testParentId);
    MediaTestUtils.assertLibraryParamsEquals(testParams, libraryParamsRef.get());
    assertThat(subscribedControllers).hasSize(2);
    assertThat(
            subscribedControllers
                .get(0)
                .getConnectionHints()
                .getBoolean("onSubscribeTestBrowser", /* defaultValue= */ false))
        .isTrue();
    assertThat(
            subscribedControllers
                .get(1)
                .getConnectionHints()
                .getBoolean("onSubscribeTestBrowser", /* defaultValue= */ false))
        .isTrue();
    // After unsubscribing the list of subscribed controllers is empty.
    assertThat(session.getSubscribedControllers(testParentId)).isEmpty();
  }

  @Test
  public void onSubscribe_returnsNonSuccessResult_subscribedControllerNotRegistered()
      throws Exception {
    String testParentId = "onSubscribe_returnsNoSuccessResult_subscribedControllerNotRegistered";
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    CountDownLatch latch = new CountDownLatch(1);
    List<ControllerInfo> subscribedControllers = new ArrayList<>();
    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<Void>> onSubscribe(
              MediaLibrarySession session,
              ControllerInfo browser,
              String parentId,
              @Nullable LibraryParams params) {
            latch.countDown();
            subscribedControllers.addAll(session.getSubscribedControllers(parentId));
            return Futures.immediateFuture(LibraryResult.ofError(RESULT_ERROR_NOT_SUPPORTED));
          }
        };
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, sessionCallback)
                .setId("testOnSubscribe")
                .build());
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean("onSubscribeTestBrowser", true);
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), connectionHints);

    browser.subscribe(testParentId, testParams);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    // Inside the callback the subscribed controller is available even when not returning
    // `RESULT_SUCCESS`. It will be removed after the result has been received.
    assertThat(subscribedControllers).hasSize(1);
    assertThat(
            subscribedControllers
                .get(0)
                .getConnectionHints()
                .getBoolean("onSubscribeTestBrowser", /* defaultValue= */ false))
        .isTrue();
    // After subscribing the list of subscribed controllers is empty, because the callback returns a
    // result different to `RESULT_SUCCESS`.
    assertThat(session.getSubscribedControllers(testParentId)).isEmpty();
  }

  @Test
  public void onSubscribe_onGetItemNotImplemented_errorNotSupported() throws Exception {
    String testParentId = SUBSCRIBE_PARENT_ID_1;
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, new MediaLibrarySession.Callback() {})
                .setId("testOnSubscribe")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    int resultCode = browser.subscribe(testParentId, testParams).resultCode;

    assertThat(session.getSubscribedControllers(testParentId)).isEmpty();
    assertThat(resultCode).isEqualTo(RESULT_ERROR_NOT_SUPPORTED);
    assertThat(session.getSubscribedControllers(testParentId)).isEmpty();
  }

  @Test
  public void onSubscribe_onGetItemNotSucceeded_correctErrorCodeReported() throws Exception {
    LibraryParams testParams = MediaTestUtils.createLibraryParams();
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(
                    service,
                    player,
                    new MediaLibrarySession.Callback() {
                      @Override
                      public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                          MediaLibrarySession session, ControllerInfo browser, String mediaId) {
                        return Futures.immediateFuture(
                            LibraryResult.ofError(RESULT_ERROR_SESSION_SETUP_REQUIRED));
                      }
                    })
                .setId("testOnSubscribe")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    int resultCode = browser.subscribe(SUBSCRIBE_PARENT_ID_1, testParams).resultCode;

    assertThat(resultCode).isEqualTo(RESULT_ERROR_SESSION_SETUP_REQUIRED);
    assertThat(session.getSubscribedControllers(SUBSCRIBE_PARENT_ID_1)).isEmpty();
  }

  @Test
  public void onUnsubscribe() throws Exception {
    String testParentId = "testUnsubscribeId";

    CountDownLatch latch = new CountDownLatch(1);
    List<ControllerInfo> subscribedControllers = new ArrayList<>();
    List<String> parentIds = new ArrayList<>();
    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<Void>> onUnsubscribe(
              MediaLibrarySession session, ControllerInfo browser, String parentId) {
            parentIds.add(parentId);
            subscribedControllers.addAll(session.getSubscribedControllers(parentId));
            latch.countDown();
            return Futures.immediateFuture(LibraryResult.ofVoid());
          }
        };

    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);

    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, sessionCallback)
                .setId("testOnUnsubscribe")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(
            session.getToken(), /* connectionHints= */ Bundle.EMPTY);
    browser.unsubscribe(testParentId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parentIds).containsExactly(testParentId);
    // The browser wasn't subscribed.
    assertThat(subscribedControllers).isEmpty();
  }

  @Test
  public void onGetLibraryRoot_callForRecentRootNonSystemUiPackageName_notIntercepted()
      throws Exception {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("rootMediaId")
            .setMediaMetadata(
                new MediaMetadata.Builder().setIsPlayable(false).setIsBrowsable(true).build())
            .build();
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
              MediaLibrarySession session, ControllerInfo browser, @Nullable LibraryParams params) {
            if (params != null && params.isRecent) {
              latch.countDown();
            }
            return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, params));
          }
        };
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_callForRecentRootNonSystemUiPackageName_notIntercepted")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<MediaItem> libraryRoot =
        browser.getLibraryRoot(new LibraryParams.Builder().setRecent(true).build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(libraryRoot.value).isEqualTo(mediaItem);
  }

  @Test
  public void onGetChildren_systemUiCallForRecentItemsWhenIdle_callsOnPlaybackResumption()
      throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(2);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            latch.countDown();
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    mediaItems, /* startIndex= */ 1, /* startPositionMs= */ 1000L));
          }

          @Override
          public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
              MediaLibrarySession session,
              ControllerInfo browser,
              String parentId,
              int page,
              int pageSize,
              @Nullable LibraryParams params) {
            latch.countDown();
            return Futures.immediateFuture(
                LibraryResult.ofItemList(mediaItems, /* params= */ null));
          }
        };
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_systemUiCallForRecentItems_returnsRecentItems")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<ImmutableList<MediaItem>> recentItem =
        browser.getChildren(
            "androidx.media3.session.recent.root",
            /* page= */ 0,
            /* pageSize= */ 100,
            /* params= */ null);
    // Load children of a non recent root that must not be intercepted.
    LibraryResult<ImmutableList<MediaItem>> children =
        browser.getChildren("children", /* page= */ 0, /* pageSize= */ 100, /* params= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(recentItem.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(Lists.transform(recentItem.value, (item) -> item.mediaId))
        .containsExactly("mediaItem_2");
    assertThat(children.value).isEqualTo(mediaItems);
  }

  @Test
  public void
      onGetChildren_systemUiCallForRecentItemsWhenIdleWithEmptyResumptionPlaylist_resultInvalidState()
          throws Exception {
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            latch.countDown();
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.of(), /* startIndex= */ 11, /* startPositionMs= */ 1000L));
          }
        };
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_systemUiCallForRecentItems_returnsRecentItems")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<ImmutableList<MediaItem>> recentItem =
        browser.getChildren(
            "androidx.media3.session.recent.root",
            /* page= */ 0,
            /* pageSize= */ 100,
            /* params= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(recentItem.resultCode).isEqualTo(LibraryResult.RESULT_ERROR_INVALID_STATE);
  }

  @Test
  public void
      onGetChildren_systemUiCallForRecentItemsWhenIdleStartIndexTooHigh_setToLastItemItemInList()
          throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            latch.countDown();
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    mediaItems, /* startIndex= */ 11, /* startPositionMs= */ 1000L));
          }
        };
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_systemUiCallForRecentItems_returnsRecentItems")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<ImmutableList<MediaItem>> recentItem =
        browser.getChildren(
            "androidx.media3.session.recent.root",
            /* page= */ 0,
            /* pageSize= */ 100,
            /* params= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(recentItem.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(Lists.transform(recentItem.value, (item) -> item.mediaId))
        .containsExactly("mediaItem_3");
  }

  @Test
  public void onGetChildren_systemUiCallForRecentItemsWhenIdleStartIndexNegative_setToZero()
      throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
              MediaSession mediaSession, ControllerInfo controller) {
            latch.countDown();
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    mediaItems, /* startIndex= */ -11, /* startPositionMs= */ 1000L));
          }
        };
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_systemUiCallForRecentItems_returnsRecentItems")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<ImmutableList<MediaItem>> recentItem =
        browser.getChildren(
            "androidx.media3.session.recent.root",
            /* page= */ 0,
            /* pageSize= */ 100,
            /* params= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(recentItem.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(Lists.transform(recentItem.value, (item) -> item.mediaId))
        .containsExactly("mediaItem_1");
  }

  @Test
  public void onGetChildren_systemUiCallForRecentItemsWhenNotIdle_returnsRecentItems()
      throws Exception {
    ArrayList<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    MockMediaLibraryService service = new MockMediaLibraryService();
    service.attachBaseContext(context);
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySession.Callback callback =
        new MediaLibrarySession.Callback() {
          @Override
          public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
              MediaLibrarySession session,
              ControllerInfo browser,
              String parentId,
              int page,
              int pageSize,
              @Nullable LibraryParams params) {
            latch.countDown();
            return Futures.immediateFuture(
                LibraryResult.ofItemList(mediaItems, /* params= */ null));
          }
        };
    player.playbackState = Player.STATE_READY;
    MediaLibrarySession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaLibrarySession.Builder(service, player, callback)
                .setId("onGetChildren_systemUiCallForRecentItems_returnsRecentItems")
                .build());
    RemoteMediaBrowser browser =
        controllerTestRule.createRemoteBrowser(session.getToken(), Bundle.EMPTY);

    LibraryResult<ImmutableList<MediaItem>> recentItem =
        browser.getChildren(
            "androidx.media3.session.recent.root",
            /* page= */ 0,
            /* pageSize= */ 100,
            /* params= */ null);
    // Load children of a non recent root that must not be intercepted.
    LibraryResult<ImmutableList<MediaItem>> children =
        browser.getChildren("children", /* page= */ 0, /* pageSize= */ 100, /* params= */ null);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(recentItem.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS);
    assertThat(Lists.transform(recentItem.value, (item) -> item.mediaId))
        .containsExactly("androidx.media3.session.recent.item");
    assertThat(children.value).isEqualTo(mediaItems);
  }
}

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

import static androidx.media3.session.MediaTestUtils.createMediaItem;
import static androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.METADATA_MEDIA_URI;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession.Callback}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionCallbackTest {

  private static final String TAG = "MSessionCallbackTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private MockPlayer player;
  private ListeningExecutorService executorService;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    // Intentionally use an Executor with another thread to test asynchronous workflows involving
    // background tasks.
    executorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
  }

  @After
  public void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  public void onConnect_correctControllerVersions() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger controllerVersion = new AtomicInteger();
    final AtomicInteger controllerInterfaceVersion = new AtomicInteger();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            controllerVersion.set(controller.getControllerVersion());
            controllerInterfaceVersion.set(controller.getInterfaceVersion());
            latch.countDown();
            return MediaSession.ConnectionResult.accept(
                new SessionCommands.Builder().addAllSessionCommands().build(),
                new Player.Commands.Builder().addAllCommands().build());
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnConnect_correctControllerVersions")
                .build());

    controllerTestRule.createRemoteController(session.getToken());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerVersion.get()).isEqualTo(MediaLibraryInfo.VERSION_INT);
    assertThat(controllerInterfaceVersion.get()).isEqualTo(MediaControllerStub.VERSION_INT);
  }

  @Test
  public void onPostConnect_afterConnected() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public void onPostConnect(MediaSession session, ControllerInfo controller) {
            latch.countDown();
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnPostConnect_afterConnected")
                .build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onPostConnect_afterConnectionRejected() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            return MediaSession.ConnectionResult.reject();
          }

          @Override
          public void onPostConnect(MediaSession session, ControllerInfo controller) {
            latch.countDown();
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnPostConnect_afterConnectionRejected")
                .build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void onCommandRequest() throws Exception {
    ArrayList<Integer> commands = new ArrayList<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public int onPlayerCommandRequest(
              MediaSession session, ControllerInfo controllerInfo, @Player.Command int command) {
            // TODO: Get uid of client app's and compare.
            if (!TextUtils.equals(controllerInfo.getPackageName(), SUPPORT_APP_PACKAGE_NAME)) {
              return RESULT_INFO_SKIPPED;
            }

            assertThat(controllerInfo.isTrusted()).isFalse();
            commands.add(command);
            if (command == Player.COMMAND_PREPARE) {
              return RESULT_ERROR_INVALID_STATE;
            }
            return RESULT_SUCCESS;
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnCommandRequest")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.prepare();
    Thread.sleep(NO_RESPONSE_TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(commands).hasSize(1);
    assertThat(commands.get(0)).isEqualTo(Player.COMMAND_PREPARE);

    controller.play();
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isFalse();
    assertThat(commands).hasSize(2);
    assertThat(commands.get(1)).isEqualTo(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void onCustomCommand() throws Exception {
    // TODO(jaewan): Need to revisit with the permission.
    SessionCommand testCommand =
        new SessionCommand("testCustomCommand", /* extras= */ Bundle.EMPTY);
    Bundle testArgs = new Bundle();
    testArgs.putString("args", "testOnCustomCommand");

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SessionCommand> sessionCommandRef = new AtomicReference<>();
    AtomicReference<Bundle> argsRef = new AtomicReference<>();

    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            SessionCommands sessionCommands =
                new SessionCommands.Builder().addAllPredefinedCommands().add(testCommand).build();
            return MediaSession.ConnectionResult.accept(sessionCommands, Player.Commands.EMPTY);
          }

          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaSession session,
              MediaSession.ControllerInfo controller,
              SessionCommand sessionCommand,
              Bundle args) {
            if (!TextUtils.equals(controller.getPackageName(), SUPPORT_APP_PACKAGE_NAME)) {
              return Futures.immediateFuture(new SessionResult(RESULT_INFO_SKIPPED));
            }

            sessionCommandRef.set(sessionCommand);
            argsRef.set(args);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnCustomCommand")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    SessionResult result = controller.sendCustomCommand(testCommand, testArgs);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionCommandRef.get()).isEqualTo(testCommand);
    assertThat(TestUtils.equals(testArgs, argsRef.get())).isTrue();
  }

  @Test
  public void onSetRatingWithMediaId() throws Exception {
    float ratingValue = 3.5f;
    Rating testRating = new StarRating(5, ratingValue);
    String testMediaId = "media_id";

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> mediaIdRef = new AtomicReference<>();
    AtomicReference<Rating> ratingRef = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<SessionResult> onSetRating(
              MediaSession session, ControllerInfo controller, String mediaId, Rating rating) {
            if (!TextUtils.equals(controller.getPackageName(), SUPPORT_APP_PACKAGE_NAME)) {
              return Futures.immediateFuture(new SessionResult(RESULT_INFO_SKIPPED));
            }

            mediaIdRef.set(mediaId);
            ratingRef.set(rating);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnSetRating")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    SessionResult result = controller.setRating(testMediaId, testRating);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaIdRef.get()).isEqualTo(testMediaId);
    assertThat(ratingRef.get()).isEqualTo(testRating);
  }

  @Test
  public void onSetRatingWithoutMediaId() throws Exception {
    float ratingValue = 3.5f;
    Rating testRating = new StarRating(5, ratingValue);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Rating> ratingRef = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<SessionResult> onSetRating(
              MediaSession session, ControllerInfo controller, Rating rating) {
            if (!TextUtils.equals(controller.getPackageName(), SUPPORT_APP_PACKAGE_NAME)) {
              return Futures.immediateFuture(new SessionResult(RESULT_INFO_SKIPPED));
            }
            ratingRef.set(rating);
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setCallback(callback)
                .setId("testOnSetRating")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    SessionResult result = controller.setRating(testRating);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(ratingRef.get()).isEqualTo(testRating);
  }

  @Test
  public void onAddMediaItems_withSetMediaItem() throws Exception {
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItem(mediaItem);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems).containsExactly(updateMediaItemWithLocalConfiguration(mediaItem));
  }

  @Test
  public void onAddMediaItems_withSetMediaItemWithStartPosition() throws Exception {
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItem(mediaItem, /* startPositionMs= */ 1234);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems).containsExactly(updateMediaItemWithLocalConfiguration(mediaItem));
    assertThat(player.startMediaItemIndex).isEqualTo(0);
    assertThat(player.startPositionMs).isEqualTo(1234);
  }

  @Test
  public void onAddMediaItems_withSetMediaItemWithResetPosition() throws Exception {
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItem(mediaItem, /* resetPosition= */ true);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems).containsExactly(updateMediaItemWithLocalConfiguration(mediaItem));
    assertThat(player.resetPosition).isEqualTo(true);
  }

  @Test
  public void onAddMediaItems_withSetMediaItems() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItems(mediaItems);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
  }

  @Test
  public void onAddMediaItems_withSetMediaItemsWithStartIndex() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItems(mediaItems, /* startWindowIndex= */ 1, /* startPositionMs= */ 1234);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(1234);
  }

  @Test
  public void onAddMediaItems_withSetMediaItemsWithResetPosition() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItems(mediaItems, /* resetPosition= */ true);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
    assertThat(player.resetPosition).isEqualTo(true);
  }

  @Test
  public void onAddMediaItems_withAddMediaItem() throws Exception {
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.addMediaItem(mediaItem);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems).containsExactly(updateMediaItemWithLocalConfiguration(mediaItem));
  }

  @Test
  public void onAddMediaItems_withAddMediaItemWithIndex() throws Exception {
    MediaItem existingItem = createMediaItem("existingItem");
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    controller.setMediaItem(existingItem);

    controller.addMediaItem(/* index= */ 1, mediaItem);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems)
        .containsExactly(
            updateMediaItemWithLocalConfiguration(existingItem),
            updateMediaItemWithLocalConfiguration(mediaItem));
    assertThat(player.index).isEqualTo(1);
  }

  @Test
  public void onAddMediaItems_withAddMediaItems() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.addMediaItems(mediaItems);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
  }

  @Test
  public void onAddMediaItems_withAddMediaItemsWithIndex() throws Exception {
    MediaItem existingItem = createMediaItem("existingItem");
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<List<MediaItem>> onAddMediaItems(
              MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
            requestedMediaItems.set(mediaItems);
            // Resolve MediaItems asynchronously to test correct threading logic.
            return executorService.submit(() -> updateMediaItemsWithLocalConfiguration(mediaItems));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    controller.setMediaItem(existingItem);

    controller.addMediaItems(/* index= */ 1, mediaItems);
    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(
            Iterables.concat(
                ImmutableList.of(updateMediaItemWithLocalConfiguration(existingItem)),
                updateMediaItemsWithLocalConfiguration(mediaItems)))
        .inOrder();
    assertThat(player.index).isEqualTo(1);
  }

  @Test
  public void onSetMediaItems_withSetMediaItemWithStartPosition_callsPlayerWithStartIndex()
      throws Exception {
    MediaItem mediaItem = createMediaItem("mediaId");
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            requestedMediaItems.set(mediaItems);

            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    updateMediaItemsWithLocalConfiguration(mediaItems),
                    startIndex,
                    /* startPosition = testStartPosition * 2 */ 200));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItem(mediaItem, /* startPositionMs= */ 100);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactly(mediaItem);
    assertThat(player.mediaItems).containsExactly(updateMediaItemWithLocalConfiguration(mediaItem));
    assertThat(player.startMediaItemIndex).isEqualTo(0);
    assertThat(player.startPositionMs).isEqualTo(200);
  }

  @Test
  public void onSetMediaItems_withSetMediaItemsWithStartIndex_callsPlayerWithStartIndex()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            requestedMediaItems.set(mediaItems);

            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    updateMediaItemsWithLocalConfiguration(mediaItems),
                    startIndex,
                    /* startPosition= */ 200));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItems(mediaItems, /* startWindowIndex= */ 1, /* startPositionMs= */ 100);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(200);
  }

  @Test
  public void onSetMediaItems_withIndexPositionUnset_callsPlayerWithResetPosition()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            requestedMediaItems.set(mediaItems);

            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    updateMediaItemsWithLocalConfiguration(mediaItems),
                    C.INDEX_UNSET,
                    C.TIME_UNSET));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaItems(mediaItems, /* startWindowIndex= */ 1, /* startPositionMs= */ 100);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
    assertThat(player.resetPosition).isEqualTo(true);
  }

  @Test
  public void onSetMediaItems_withStartIndexUnset_callsPlayerWithCurrentIndexAndPosition()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    AtomicReference<List<MediaItem>> requestedMediaItems = new AtomicReference<>();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
              MediaSession mediaSession,
              ControllerInfo controller,
              List<MediaItem> mediaItems,
              int startIndex,
              long startPositionMs) {
            requestedMediaItems.set(mediaItems);

            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(
                    updateMediaItemsWithLocalConfiguration(mediaItems),
                    startIndex,
                    startPositionMs));
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setCallback(callback).build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    controller.setMediaItems(mediaItems, true);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);

    // Model that player played to next item. Current media item index and position have changed
    player.currentMediaItemIndex = 1;
    player.currentPosition = 200;

    // Re-set media items with start index and position as current index and position
    controller.setMediaItems(mediaItems, C.INDEX_UNSET, /* startPosition= */ 0);
    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);

    assertThat(requestedMediaItems.get()).containsExactlyElementsIn(mediaItems).inOrder();
    assertThat(player.mediaItems)
        .containsExactlyElementsIn(updateMediaItemsWithLocalConfiguration(mediaItems))
        .inOrder();
    assertThat(player.startMediaItemIndex).isEqualTo(1);
    assertThat(player.startPositionMs).isEqualTo(200);
  }

  @Test
  public void onConnect() throws Exception {
    AtomicReference<Bundle> connectionHints = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("testOnConnect")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        // TODO: Get uid of client app's and compare.
                        if (!SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
                          return MediaSession.ConnectionResult.reject();
                        }
                        connectionHints.set(controller.getConnectionHints());
                        latch.countDown();
                        return MediaSession.Callback.super.onConnect(session, controller);
                      }
                    })
                .build());
    Bundle testConnectionHints = new Bundle();
    testConnectionHints.putString("test_key", "test_value");

    controllerTestRule.createRemoteController(
        session.getToken(), /* waitForConnection= */ false, testConnectionHints);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(testConnectionHints, connectionHints.get())).isTrue();
  }

  @Test
  public void onDisconnected() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("testOnDisconnected")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public void onDisconnected(MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            controller.getPackageName(), SUPPORT_APP_PACKAGE_NAME)) {
                          // TODO: Get uid of client app's and compare.
                          latch.countDown();
                        }
                      }
                    })
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    controller.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  private static MediaItem updateMediaItemWithLocalConfiguration(MediaItem mediaItem) {
    return mediaItem.buildUpon().setUri(METADATA_MEDIA_URI).build();
  }

  private static List<MediaItem> updateMediaItemsWithLocalConfiguration(
      List<MediaItem> mediaItems) {
    ImmutableList.Builder<MediaItem> listBuilder = ImmutableList.builder();
    for (int i = 0; i < mediaItems.size(); i++) {
      listBuilder.add(updateMediaItemWithLocalConfiguration(mediaItems.get(i)));
    }
    return listBuilder.build();
  }
}

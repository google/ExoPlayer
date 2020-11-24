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
package com.google.android.exoplayer2.ext.media2;

import static com.google.android.exoplayer2.ext.media2.TestUtils.assertPlayerResultSuccess;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.Rating;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.UriMediaItem;
import androidx.media2.session.HeartRating;
import androidx.media2.session.MediaController;
import androidx.media2.session.MediaSession;
import androidx.media2.session.SessionCommand;
import androidx.media2.session.SessionCommandGroup;
import androidx.media2.session.SessionResult;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link SessionCallbackBuilder}. */
@RunWith(AndroidJUnit4.class)
public class SessionCallbackBuilderTest {

  @Rule public final PlayerTestRule playerTestRule = new PlayerTestRule();

  private static final String MEDIA_SESSION_ID = SessionCallbackBuilderTest.class.getSimpleName();
  private static final long CONTROLLER_COMMAND_WAIT_TIME_MS = 3_000;
  private static final long PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS = 10_000;
  private static final long PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;

  private Context context;
  private Executor executor;
  private SessionPlayerConnector sessionPlayerConnector;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    executor = playerTestRule.getExecutor();
    sessionPlayerConnector = playerTestRule.getSessionPlayerConnector();
  }

  @Test
  public void constructor() throws Exception {
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector).build())) {
      assertPlayerResultSuccess(sessionPlayerConnector.setMediaItem(TestUtils.createMediaItem()));
      assertPlayerResultSuccess(sessionPlayerConnector.prepare());

      OnConnectedListener listener =
          (controller, allowedCommands) -> {
            List<Integer> disallowedCommandCodes =
                Arrays.asList(
                    SessionCommand.COMMAND_CODE_SESSION_SET_RATING, // no rating callback
                    SessionCommand.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM, // no media item provider
                    SessionCommand
                        .COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM, // no media item provider
                    SessionCommand.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM, // no media item provider
                    SessionCommand.COMMAND_CODE_PLAYER_SET_PLAYLIST, // no media item provider
                    SessionCommand.COMMAND_CODE_SESSION_REWIND, // no current media item
                    SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD // no current media item
                    );
            assertDisallowedCommands(disallowedCommandCodes, allowedCommands);
          };
      try (MediaController controller = createConnectedController(session, listener, null)) {
        assertThat(controller.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
      }
    }
  }

  @Test
  public void allowedCommand_withoutPlaylist_disallowsSkipTo() throws Exception {
    int testRewindIncrementMs = 100;
    int testFastForwardIncrementMs = 100;

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setRatingCallback(
                    (mediaSession, controller, mediaId, rating) ->
                        SessionResult.RESULT_ERROR_BAD_VALUE)
                .setRewindIncrementMs(testRewindIncrementMs)
                .setFastForwardIncrementMs(testFastForwardIncrementMs)
                .setMediaItemProvider(new SessionCallbackBuilder.MediaIdMediaItemProvider())
                .build())) {
      assertPlayerResultSuccess(sessionPlayerConnector.setMediaItem(TestUtils.createMediaItem()));
      assertPlayerResultSuccess(sessionPlayerConnector.prepare());

      CountDownLatch latch = new CountDownLatch(1);
      OnConnectedListener listener =
          (controller, allowedCommands) -> {
            List<Integer> disallowedCommandCodes =
                Arrays.asList(
                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM,
                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM,
                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM);
            assertDisallowedCommands(disallowedCommandCodes, allowedCommands);
            latch.countDown();
          };
      try (MediaController controller = createConnectedController(session, listener, null)) {
        assertThat(latch.await(CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS)).isTrue();

        assertSessionResultFailure(controller.skipToNextPlaylistItem());
        assertSessionResultFailure(controller.skipToPreviousPlaylistItem());
        assertSessionResultFailure(controller.skipToPlaylistItem(0));
      }
    }
  }

  @Test
  public void allowedCommand_whenPlaylistSet_allowsSkipTo() throws Exception {
    List<MediaItem> testPlaylist = new ArrayList<>();
    testPlaylist.add(TestUtils.createMediaItem(R.raw.video_desks));
    testPlaylist.add(TestUtils.createMediaItem(R.raw.video_not_seekable));
    int testRewindIncrementMs = 100;
    int testFastForwardIncrementMs = 100;

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setRatingCallback(
                    (mediaSession, controller, mediaId, rating) ->
                        SessionResult.RESULT_ERROR_BAD_VALUE)
                .setRewindIncrementMs(testRewindIncrementMs)
                .setFastForwardIncrementMs(testFastForwardIncrementMs)
                .setMediaItemProvider(new SessionCallbackBuilder.MediaIdMediaItemProvider())
                .build())) {

      assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(testPlaylist, null));
      assertPlayerResultSuccess(sessionPlayerConnector.prepare());

      OnConnectedListener connectedListener =
          (controller, allowedCommands) -> {
            List<Integer> allowedCommandCodes =
                Arrays.asList(
                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
                    SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO,
                    SessionCommand.COMMAND_CODE_SESSION_REWIND,
                    SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD);
            assertAllowedCommands(allowedCommandCodes, allowedCommands);

            List<Integer> disallowedCommandCodes =
                Arrays.asList(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM);
            assertDisallowedCommands(disallowedCommandCodes, allowedCommands);
          };

      CountDownLatch allowedCommandChangedLatch = new CountDownLatch(1);
      OnAllowedCommandsChangedListener allowedCommandChangedListener =
          (controller, allowedCommands) -> {
            List<Integer> allowedCommandCodes =
                Arrays.asList(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM);
            assertAllowedCommands(allowedCommandCodes, allowedCommands);

            List<Integer> disallowedCommandCodes =
                Arrays.asList(
                    SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
                    SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO,
                    SessionCommand.COMMAND_CODE_SESSION_REWIND,
                    SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD);
            assertDisallowedCommands(disallowedCommandCodes, allowedCommands);
            allowedCommandChangedLatch.countDown();
          };
      try (MediaController controller =
          createConnectedController(session, connectedListener, allowedCommandChangedListener)) {
        assertPlayerResultSuccess(sessionPlayerConnector.skipToNextPlaylistItem());

        assertThat(allowedCommandChangedLatch.await(CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS))
            .isTrue();

        // Also test whether the rewind fails as expected.
        assertSessionResultFailure(controller.rewind());
        assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(0);
        assertThat(controller.getCurrentPosition()).isEqualTo(0);
      }
    }
  }

  @Test
  public void allowedCommand_afterCurrentMediaItemPrepared_notifiesSeekToAvailable()
      throws Exception {
    List<MediaItem> testPlaylist = new ArrayList<>();
    testPlaylist.add(TestUtils.createMediaItem(R.raw.video_desks));
    UriMediaItem secondPlaylistItem = TestUtils.createMediaItem(R.raw.video_big_buck_bunny);
    testPlaylist.add(secondPlaylistItem);

    CountDownLatch readAllowedLatch = new CountDownLatch(1);
    playerTestRule.setDataSourceInstrumentation(
        dataSpec -> {
          if (dataSpec.uri.equals(secondPlaylistItem.getUri())) {
            try {
              assertThat(readAllowedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
                  .isTrue();
            } catch (Exception e) {
              assertWithMessage("Unexpected exception %s", e).fail();
            }
          }
        });

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector).build())) {

      assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(testPlaylist, null));
      assertPlayerResultSuccess(sessionPlayerConnector.prepare());

      CountDownLatch seekToAllowedForSecondMediaItem = new CountDownLatch(1);
      OnAllowedCommandsChangedListener allowedCommandsChangedListener =
          (controller, allowedCommands) -> {
            if (allowedCommands.hasCommand(SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO)
                && controller.getCurrentMediaItemIndex() == 1) {
              seekToAllowedForSecondMediaItem.countDown();
            }
          };
      try (MediaController controller =
          createConnectedController(
              session, /* onConnectedListener= */ null, allowedCommandsChangedListener)) {
        assertPlayerResultSuccess(sessionPlayerConnector.skipToNextPlaylistItem());

        readAllowedLatch.countDown();
        assertThat(
                seekToAllowedForSecondMediaItem.await(
                    CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS))
            .isTrue();
      }
    }
  }

  @Test
  public void setRatingCallback_withRatingCallback_receivesRatingCallback() throws Exception {
    String testMediaId = "testRating";
    Rating testRating = new HeartRating(true);
    CountDownLatch latch = new CountDownLatch(1);

    SessionCallbackBuilder.RatingCallback ratingCallback =
        (session, controller, mediaId, rating) -> {
          assertThat(mediaId).isEqualTo(testMediaId);
          assertThat(rating).isEqualTo(testRating);
          latch.countDown();
          return SessionResult.RESULT_SUCCESS;
        };

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setRatingCallback(ratingCallback)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        assertSessionResultSuccess(
            controller.setRating(testMediaId, testRating), CONTROLLER_COMMAND_WAIT_TIME_MS);
        assertThat(latch.await(0, MILLISECONDS)).isTrue();
      }
    }
  }

  @Test
  public void setCustomCommandProvider_withCustomCommandProvider_receivesCustomCommand()
      throws Exception {
    SessionCommand testCommand = new SessionCommand("exo.ext.media2.COMMAND", null);
    CountDownLatch latch = new CountDownLatch(1);

    SessionCallbackBuilder.CustomCommandProvider provider =
        new SessionCallbackBuilder.CustomCommandProvider() {
          @Override
          public SessionResult onCustomCommand(
              MediaSession session,
              MediaSession.ControllerInfo controllerInfo,
              SessionCommand customCommand,
              @Nullable Bundle args) {
            assertThat(customCommand.getCustomAction()).isEqualTo(testCommand.getCustomAction());
            assertThat(args).isNull();
            latch.countDown();
            return new SessionResult(SessionResult.RESULT_SUCCESS, null);
          }

          @Override
          public SessionCommandGroup getCustomCommands(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            return new SessionCommandGroup.Builder().addCommand(testCommand).build();
          }
        };

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setCustomCommandProvider(provider)
                .build())) {
      OnAllowedCommandsChangedListener listener =
          (controller, allowedCommands) -> {
            boolean foundCustomCommand = false;
            for (SessionCommand command : allowedCommands.getCommands()) {
              if (TextUtils.equals(testCommand.getCustomAction(), command.getCustomAction())) {
                foundCustomCommand = true;
                break;
              }
            }
            assertThat(foundCustomCommand).isTrue();
          };
      try (MediaController controller = createConnectedController(session, null, listener)) {
        assertSessionResultSuccess(
            controller.sendCustomCommand(testCommand, null), CONTROLLER_COMMAND_WAIT_TIME_MS);
        assertThat(latch.await(0, MILLISECONDS)).isTrue();
      }
    }
  }

  @LargeTest
  @Test
  public void setRewindIncrementMs_withPositiveRewindIncrement_rewinds() throws Exception {
    int testResId = R.raw.video_big_buck_bunny;
    int testDuration = 10_000;
    int tolerance = 100;
    int testSeekPosition = 2_000;
    int testRewindIncrementMs = 500;

    TestUtils.loadResource(testResId, sessionPlayerConnector);

    // seekTo() sometimes takes couple of seconds. Disable default timeout behavior.
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setRewindIncrementMs(testRewindIncrementMs)
                .setSeekTimeoutMs(0)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        // Prepare first to ensure that seek() works.
        assertSessionResultSuccess(
            controller.prepare(), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);

        assertThat((float) sessionPlayerConnector.getDuration())
            .isWithin(tolerance)
            .of(testDuration);
        assertSessionResultSuccess(
            controller.seekTo(testSeekPosition), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);
        assertThat((float) sessionPlayerConnector.getCurrentPosition())
            .isWithin(tolerance)
            .of(testSeekPosition);

        // Test rewind
        assertSessionResultSuccess(
            controller.rewind(), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);
        assertThat((float) sessionPlayerConnector.getCurrentPosition())
            .isWithin(tolerance)
            .of(testSeekPosition - testRewindIncrementMs);
      }
    }
  }

  @LargeTest
  @Test
  public void setFastForwardIncrementMs_withPositiveFastForwardIncrement_fastsForward()
      throws Exception {
    int testResId = R.raw.video_big_buck_bunny;
    int testDuration = 10_000;
    int tolerance = 100;
    int testSeekPosition = 2_000;
    int testFastForwardIncrementMs = 300;

    TestUtils.loadResource(testResId, sessionPlayerConnector);

    // seekTo() sometimes takes couple of seconds. Disable default timeout behavior.
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setFastForwardIncrementMs(testFastForwardIncrementMs)
                .setSeekTimeoutMs(0)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        // Prepare first to ensure that seek() works.
        assertSessionResultSuccess(
            controller.prepare(), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);

        assertThat((float) sessionPlayerConnector.getDuration())
            .isWithin(tolerance)
            .of(testDuration);
        assertSessionResultSuccess(
            controller.seekTo(testSeekPosition), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);
        assertThat((float) sessionPlayerConnector.getCurrentPosition())
            .isWithin(tolerance)
            .of(testSeekPosition);

        // Test fast-forward
        assertSessionResultSuccess(
            controller.fastForward(), PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);
        assertThat((float) sessionPlayerConnector.getCurrentPosition())
            .isWithin(tolerance)
            .of(testSeekPosition + testFastForwardIncrementMs);
      }
    }
  }

  @Test
  public void setMediaItemProvider_withMediaItemProvider_receivesOnCreateMediaItem()
      throws Exception {
    Uri testMediaUri = RawResourceDataSource.buildRawResourceUri(R.raw.audio);

    CountDownLatch providerLatch = new CountDownLatch(1);
    SessionCallbackBuilder.MediaIdMediaItemProvider mediaIdMediaItemProvider =
        new SessionCallbackBuilder.MediaIdMediaItemProvider();
    SessionCallbackBuilder.MediaItemProvider provider =
        (session, controllerInfo, mediaId) -> {
          assertThat(mediaId).isEqualTo(testMediaUri.toString());
          providerLatch.countDown();
          return mediaIdMediaItemProvider.onCreateMediaItem(session, controllerInfo, mediaId);
        };

    CountDownLatch currentMediaItemChangedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
            MediaMetadata metadata = item.getMetadata();
            assertThat(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
                .isEqualTo(testMediaUri.toString());
            currentMediaItemChangedLatch.countDown();
          }
        });

    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setMediaItemProvider(provider)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        assertSessionResultSuccess(
            controller.setMediaItem(testMediaUri.toString()),
            PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS);
        assertThat(providerLatch.await(0, MILLISECONDS)).isTrue();
        assertThat(
                currentMediaItemChangedLatch.await(CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS))
            .isTrue();
      }
    }
  }

  @Test
  public void setSkipCallback_withSkipBackward_receivesOnSkipBackward() throws Exception {
    CountDownLatch skipBackwardCalledLatch = new CountDownLatch(1);
    SessionCallbackBuilder.SkipCallback skipCallback =
        new SessionCallbackBuilder.SkipCallback() {
          @Override
          public int onSkipBackward(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            skipBackwardCalledLatch.countDown();
            return SessionResult.RESULT_SUCCESS;
          }

          @Override
          public int onSkipForward(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
          }
        };
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setSkipCallback(skipCallback)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        assertSessionResultSuccess(controller.skipBackward(), CONTROLLER_COMMAND_WAIT_TIME_MS);
        assertThat(skipBackwardCalledLatch.await(0, MILLISECONDS)).isTrue();
      }
    }
  }

  @Test
  public void setSkipCallback_withSkipForward_receivesOnSkipForward() throws Exception {
    CountDownLatch skipForwardCalledLatch = new CountDownLatch(1);
    SessionCallbackBuilder.SkipCallback skipCallback =
        new SessionCallbackBuilder.SkipCallback() {
          @Override
          public int onSkipBackward(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
          }

          @Override
          public int onSkipForward(
              MediaSession session, MediaSession.ControllerInfo controllerInfo) {
            skipForwardCalledLatch.countDown();
            return SessionResult.RESULT_SUCCESS;
          }
        };
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setSkipCallback(skipCallback)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        assertSessionResultSuccess(controller.skipForward(), CONTROLLER_COMMAND_WAIT_TIME_MS);
        assertThat(skipForwardCalledLatch.await(0, MILLISECONDS)).isTrue();
      }
    }
  }

  @Test
  public void setPostConnectCallback_afterConnect_receivesOnPostConnect() throws Exception {
    CountDownLatch postConnectLatch = new CountDownLatch(1);
    SessionCallbackBuilder.PostConnectCallback postConnectCallback =
        (session, controllerInfo) -> postConnectLatch.countDown();
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setPostConnectCallback(postConnectCallback)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {
        assertThat(postConnectLatch.await(CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS)).isTrue();
      }
    }
  }

  @Test
  public void setDisconnectedCallback_afterDisconnect_receivesOnDisconnected() throws Exception {
    CountDownLatch disconnectedLatch = new CountDownLatch(1);
    SessionCallbackBuilder.DisconnectedCallback disconnectCallback =
        (session, controllerInfo) -> disconnectedLatch.countDown();
    try (MediaSession session =
        createMediaSession(
            sessionPlayerConnector,
            new SessionCallbackBuilder(context, sessionPlayerConnector)
                .setDisconnectedCallback(disconnectCallback)
                .build())) {
      try (MediaController controller = createConnectedController(session)) {}
      assertThat(disconnectedLatch.await(CONTROLLER_COMMAND_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    }
  }

  private MediaSession createMediaSession(
      SessionPlayer sessionPlayer, MediaSession.SessionCallback callback) {
    return new MediaSession.Builder(context, sessionPlayer)
        .setSessionCallback(executor, callback)
        .setId(MEDIA_SESSION_ID)
        .build();
  }

  private MediaController createConnectedController(MediaSession session) throws Exception {
    return createConnectedController(session, null, null);
  }

  private MediaController createConnectedController(
      MediaSession session,
      OnConnectedListener onConnectedListener,
      OnAllowedCommandsChangedListener onAllowedCommandsChangedListener)
      throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          public void onAllowedCommandsChanged(
              MediaController controller, SessionCommandGroup commands) {
            if (onAllowedCommandsChangedListener != null) {
              onAllowedCommandsChangedListener.onAllowedCommandsChanged(controller, commands);
            }
          }

          @Override
          public void onConnected(MediaController controller, SessionCommandGroup allowedCommands) {
            if (onConnectedListener != null) {
              onConnectedListener.onConnected(controller, allowedCommands);
            }
            latch.countDown();
          }
        };
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionToken(session.getToken())
            .setControllerCallback(ContextCompat.getMainExecutor(context), callback)
            .build();
    latch.await();
    return controller;
  }

  private static void assertSessionResultSuccess(Future<SessionResult> future) throws Exception {
    assertSessionResultSuccess(future, CONTROLLER_COMMAND_WAIT_TIME_MS);
  }

  private static void assertSessionResultSuccess(Future<SessionResult> future, long timeoutMs)
      throws Exception {
    SessionResult result = future.get(timeoutMs, MILLISECONDS);
    assertThat(result.getResultCode()).isEqualTo(SessionResult.RESULT_SUCCESS);
  }

  private static void assertSessionResultFailure(Future<SessionResult> future) throws Exception {
    SessionResult result = future.get(PLAYER_STATE_CHANGE_OVER_SESSION_WAIT_TIME_MS, MILLISECONDS);
    assertThat(result.getResultCode()).isNotEqualTo(SessionResult.RESULT_SUCCESS);
  }

  private static void assertAllowedCommands(
      List<Integer> expectedAllowedCommandsCode, SessionCommandGroup allowedCommands) {
    for (int commandCode : expectedAllowedCommandsCode) {
      assertWithMessage("Command should be allowed, code=" + commandCode)
          .that(allowedCommands.hasCommand(commandCode))
          .isTrue();
    }
  }

  private static void assertDisallowedCommands(
      List<Integer> expectedDisallowedCommandsCode, SessionCommandGroup allowedCommands) {
    for (int commandCode : expectedDisallowedCommandsCode) {
      assertWithMessage("Command shouldn't be allowed, code=" + commandCode)
          .that(allowedCommands.hasCommand(commandCode))
          .isFalse();
    }
  }

  private interface OnAllowedCommandsChangedListener {
    void onAllowedCommandsChanged(MediaController controller, SessionCommandGroup allowedCommands);
  }

  private interface OnConnectedListener {
    void onConnected(MediaController controller, SessionCommandGroup allowedCommands);
  }
}

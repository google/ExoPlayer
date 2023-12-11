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

import static androidx.media.MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.session.MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.R;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for key event handling of {@link MediaSession}. In order to get the media key events, the
 * player state is set to 'Playing' before every test method.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionKeyEventTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionKeyEventTest");

  // Intentionally member variable to prevent GC while playback is running.
  // Should be only used on the sHandler.
  private MediaPlayer mediaPlayer;

  private AudioManager audioManager;
  private TestHandler handler;
  private MediaSession session;
  private MockPlayer player;
  private TestSessionCallback sessionCallback;
  private CallerCollectorPlayer callerCollectorPlayer;

  @Before
  public void setUp() throws Exception {
    if (Util.SDK_INT < 21) {
      return;
    }
    Context context = ApplicationProvider.getApplicationContext();
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    handler = threadTestRule.getHandler();
    player =
        new MockPlayer.Builder().setMediaItems(1).setApplicationLooper(handler.getLooper()).build();
    sessionCallback = new TestSessionCallback();
    callerCollectorPlayer = new CallerCollectorPlayer(player);
    session =
        new MediaSession.Builder(context, callerCollectorPlayer)
            .setCallback(sessionCallback)
            .build();

    // Here's the requirement for an app to receive media key events via MediaSession.
    // - SDK < 26: Player should be playing for receiving key events
    // - SDK >= 26: Play a media item in the same process of the session for receiving key events.
    if (Util.SDK_INT < 26) {
      handler.postAndSync(
          () -> {
            player.notifyPlayWhenReadyChanged(
                /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
            player.notifyPlaybackStateChanged(Player.STATE_READY);
          });
    } else {
      CountDownLatch latch = new CountDownLatch(1);
      handler.postAndSync(
          () -> {
            // Pick the shortest media to finish within the timeout.
            mediaPlayer = MediaPlayer.create(context, R.raw.camera_click);
            mediaPlayer.setOnCompletionListener(
                player -> {
                  if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                    latch.countDown();
                  }
                });
            mediaPlayer.start();
          });
      assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    }
  }

  @After
  public void tearDown() throws Exception {
    if (Util.SDK_INT < 21) {
      return;
    }
    handler.postAndSync(
        () -> {
          if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
          }
        });
    session.release();
  }

  @Test
  public void playKeyEvent() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void pauseKeyEvent() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void nextKeyEvent() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
  }

  @Test
  public void previousKeyEvent() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
  }

  @Test
  public void
      fastForwardKeyEvent_mediaNotificationControllerConnected_callFromNotificationController()
          throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    MediaController controller = connectMediaNotificationController();
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, /* doubleTap= */ false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    assertThat(callerCollectorPlayer.callers).hasSize(1);
    assertThat(callerCollectorPlayer.callers.get(0).getControllerVersion())
        .isNotEqualTo(LEGACY_CONTROLLER_VERSION);
    assertThat(callerCollectorPlayer.callers.get(0).getPackageName())
        .isEqualTo("androidx.media3.test.session");
    assertThat(callerCollectorPlayer.callers.get(0).getConnectionHints().size()).isEqualTo(1);
    assertThat(
            callerCollectorPlayer
                .callers
                .get(0)
                .getConnectionHints()
                .getBoolean(
                    MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG,
                    /* defaultValue= */ false))
        .isTrue();
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void
      fastForwardKeyEvent_mediaNotificationControllerNotConnected_callFromLegacyFallbackController()
          throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    List<ControllerInfo> controllers = callerCollectorPlayer.callers;
    assertThat(controllers).hasSize(1);
    assertThat(controllers.get(0).getControllerVersion()).isEqualTo(LEGACY_CONTROLLER_VERSION);
    assertThat(controllers.get(0).getConnectionHints().size()).isEqualTo(0);
    assertThat(controllers.get(0).getPackageName())
        .isEqualTo(getExpectedControllerPackageName(controllers.get(0)));
  }

  @Test
  public void rewindKeyEvent_mediaNotificationControllerConnected_callFromNotificationController()
      throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    MediaController controller = connectMediaNotificationController();

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_BACK, TIMEOUT_MS);
    List<ControllerInfo> controllers = callerCollectorPlayer.callers;
    assertThat(controllers).hasSize(1);
    assertThat(controllers.get(0).getPackageName()).isEqualTo("androidx.media3.test.session");
    assertThat(controllers.get(0).getControllerVersion()).isNotEqualTo(LEGACY_CONTROLLER_VERSION);
    assertThat(controllers.get(0).getConnectionHints().size()).isEqualTo(1);
    assertThat(
            controllers
                .get(0)
                .getConnectionHints()
                .getBoolean(
                    MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG,
                    /* defaultValue= */ false))
        .isTrue();
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void
      rewindKeyEvent_mediaNotificationControllerNotConnected_callFromLegacyFallbackController()
          throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_BACK, TIMEOUT_MS);
    List<ControllerInfo> controllers = callerCollectorPlayer.callers;
    assertThat(controllers).hasSize(1);
    assertThat(controllers.get(0).getControllerVersion()).isEqualTo(LEGACY_CONTROLLER_VERSION);
    assertThat(controllers.get(0).getConnectionHints().size()).isEqualTo(0);
    assertThat(controllers.get(0).getPackageName())
        .isEqualTo(getExpectedControllerPackageName(controllers.get(0)));
  }

  @Test
  public void stopKeyEvent() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP, false);

    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_paused_play() throws Exception {
    // We don't receive media key events when we are not playing on API < 26, so we can't test this
    // case as it's not supported.
    assumeTrue(Util.SDK_INT >= 26);

    handler.postAndSync(
        () -> {
          player.playbackState = Player.STATE_READY;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_fromIdle_prepareAndPlay() throws Exception {
    // We don't receive media key events when we are not playing on API < 26, so we can't test this
    // case as it's not supported.
    assumeTrue(Util.SDK_INT >= 26);

    handler.postAndSync(
        () -> {
          player.playbackState = Player.STATE_IDLE;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_playWhenReadyAndEnded_seekAndPlay() throws Exception {
    // We don't receive media key events when we are not playing on API < 26, so we can't test this
    // case as it's not supported.
    assumeTrue(Util.SDK_INT >= 26);

    handler.postAndSync(
        () -> {
          player.playWhenReady = true;
          player.playbackState = STATE_ENDED;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_playing_pause() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    handler.postAndSync(
        () -> {
          player.playWhenReady = true;
          player.playbackState = Player.STATE_READY;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_doubleTapOnPlayPause_seekNext() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // TODO: b/199064299 - Lower minSdk to 19.
    handler.postAndSync(
        () -> {
          player.playWhenReady = true;
          player.playbackState = Player.STATE_READY;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, /* doubleTap= */ true);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
  }

  private MediaController connectMediaNotificationController() throws Exception {
    return threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              Bundle connectionHints = new Bundle();
              connectionHints.putBoolean(
                  MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, /* value= */ true);
              return new MediaController.Builder(
                      ApplicationProvider.getApplicationContext(), session.getToken())
                  .setConnectionHints(connectionHints)
                  .buildAsync()
                  .get();
            });
  }

  private void dispatchMediaKeyEvent(int keyCode, boolean doubleTap) {
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    if (doubleTap) {
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
  }

  private static String getExpectedControllerPackageName(ControllerInfo controllerInfo) {
    if (controllerInfo.getControllerVersion() != ControllerInfo.LEGACY_CONTROLLER_VERSION) {
      return SUPPORT_APP_PACKAGE_NAME;
    }
    // Legacy controllers
    if (Util.SDK_INT < 21 || Util.SDK_INT >= 28) {
      // Above API 28: package of the app using AudioManager.
      // Below 21: package of the owner of the session. Note: This is specific to this test setup
      // where `ApplicationProvider.getContext().packageName == SUPPORT_APP_PACKAGE_NAME`.
      return SUPPORT_APP_PACKAGE_NAME;
    } else if (Util.SDK_INT >= 24) {
      // API 24 - 27: KeyEvent from system service has the package name "android".
      return "android";
    } else {
      // API 21 - 23: Fallback set by MediaSessionCompat#getCurrentControllerInfo
      return LEGACY_CONTROLLER;
    }
  }

  private static class TestSessionCallback implements MediaSession.Callback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (session.isMediaNotificationController(controller)
          || getExpectedControllerPackageName(controller).equals(controller.getPackageName())) {
        return MediaSession.Callback.super.onConnect(session, controller);
      }
      return MediaSession.ConnectionResult.reject();
    }
  }

  private class CallerCollectorPlayer extends ForwardingPlayer {
    private final List<ControllerInfo> callers;

    public CallerCollectorPlayer(Player player) {
      super(player);
      callers = new ArrayList<>();
    }

    @Override
    public void seekForward() {
      callers.add(session.getControllerForCurrentRequest());
      super.seekForward();
    }

    @Override
    public void seekBack() {
      callers.add(session.getControllerForCurrentRequest());
      super.seekBack();
    }
  }
}

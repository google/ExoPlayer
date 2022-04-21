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
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.KeyEvent;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.R;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for key event handling of {@link MediaSession}. In order to get the media key events, the
 * player state is set to 'Playing' before every test method.
 */
// TODO(b/199064299): Down minSdk to 19 (AudioManager#dispatchMediaKeyEvent() requires API 19)
@SdkSuppress(minSdkVersion = 21)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionKeyEventTest {

  private static String expectedControllerPackageName;

  static {
    if (Util.SDK_INT >= 28 || Util.SDK_INT < 21) {
      expectedControllerPackageName = SUPPORT_APP_PACKAGE_NAME;
    } else if (Util.SDK_INT >= 24) {
      // KeyEvent from system service has the package name "android".
      expectedControllerPackageName = "android";
    } else {
      // In API 21+, MediaSessionCompat#getCurrentControllerInfo always returns fake info.
      expectedControllerPackageName = LEGACY_CONTROLLER;
    }
  }

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

  @Before
  public void setUp() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    handler = threadTestRule.getHandler();
    player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();

    sessionCallback = new TestSessionCallback();
    session = new MediaSession.Builder(context, player).setSessionCallback(sessionCallback).build();

    // Here's the requirement for an app to receive media key events via MediaSession.
    // - SDK < 26: Player should be playing for receiving key events
    // - SDK >= 26: Play a media item in the same process of the session for receiving key events.
    handler.postAndSync(() -> player.notifyIsPlayingChanged(/* isPlaying= */ true));
    if (Util.SDK_INT >= 26) {
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
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void pauseKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void nextKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
  }

  @Test
  public void previousKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
  }

  @Test
  public void stopKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP, false);

    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_play() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_pause() throws Exception {
    handler.postAndSync(
        () -> {
          player.playWhenReady = true;
        });

    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);

    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void playPauseKeyEvent_doubleTapIsTranslatedToSkipToNext() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isFalse();
    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PAUSE)).isFalse();
  }

  private void dispatchMediaKeyEvent(int keyCode, boolean doubleTap) {
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    if (doubleTap) {
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
  }

  private static class TestSessionCallback implements MediaSession.SessionCallback {

    @Override
    public MediaSession.ConnectionResult onConnect(
        MediaSession session, ControllerInfo controller) {
      if (expectedControllerPackageName.equals(controller.getPackageName())) {
        return MediaSession.SessionCallback.super.onConnect(session, controller);
      }
      return MediaSession.ConnectionResult.reject();
    }
  }
}

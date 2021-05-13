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

import static androidx.media.MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.R;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
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
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT) // For AudioManager#dispatchMediaKeyEvent()
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionKeyEventTest {

  private static String expectedControllerPackageName;

  static {
    if (Build.VERSION.SDK_INT >= 28 || Build.VERSION.SDK_INT < 21) {
      expectedControllerPackageName = SUPPORT_APP_PACKAGE_NAME;
    } else if (Build.VERSION.SDK_INT >= 24) {
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
    player =
        new MockPlayer.Builder().setLatchCount(1).setApplicationLooper(handler.getLooper()).build();

    sessionCallback = new TestSessionCallback();
    session = new MediaSession.Builder(context, player).setSessionCallback(sessionCallback).build();

    // Here's the requirement for an app to receive media key events via MediaSession.
    // - SDK < 26: Player should be playing for receiving key events
    // - SDK >= 26: Play a media item in the same process of the session for receiving key events.
    handler.postAndSync(() -> player.notifyIsPlayingChanged(/* isPlaying= */ true));
    if (Build.VERSION.SDK_INT >= 26) {
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
  public void cleanUp() throws Exception {
    handler.postAndSync(
        () -> {
          if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
          }
        });
    session.release();
  }

  private void dispatchMediaKeyEvent(int keyCode, boolean doubleTap) {
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    if (doubleTap) {
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
      audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
  }

  @Test
  public void playKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.playCalled).isTrue();
  }

  @Test
  public void pauseKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.pauseCalled).isTrue();
  }

  @Test
  public void nextKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.nextCalled).isTrue();
  }

  @Test
  public void previousKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.previousCalled).isTrue();
  }

  @Test
  public void stopKeyEvent() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.stopCalled).isTrue();
  }

  @Test
  public void playPauseKeyEvent_play() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.playCalled).isTrue();
  }

  @Test
  public void playPauseKeyEvent_pause() throws Exception {
    handler.postAndSync(
        () -> {
          player.playWhenReady = true;
        });
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.pauseCalled).isTrue();
  }

  @Test
  public void playPauseKeyEvent_doubleTapIsTranslatedToSkipToNext() throws Exception {
    dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.nextCalled).isTrue();
    assertThat(player.playCalled).isFalse();
    assertThat(player.pauseCalled).isFalse();
  }

  private static class TestSessionCallback extends MediaSession.SessionCallback {
    @Nullable
    @Override
    public MediaSession.ConnectResult onConnect(MediaSession session, ControllerInfo controller) {
      if (expectedControllerPackageName.equals(controller.getPackageName())) {
        return super.onConnect(session, controller);
      }
      return null;
    }
  }
}

/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.HandlerThread;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import com.google.android.exoplayer2.Player.State;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController} with framework MediaSession, which exists since Android-L. */
@RunWith(AndroidJUnit4.class)
@LargeTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.LOLLIPOP) // For framework MediaSession
public class MediaControllerWithFrameworkMediaSessionTest {
  private static final String TAG = "MediaControllerWithFrameworkMediaSessionTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private Context context;
  private TestHandler handler;
  private MediaSession fwkSession;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();

    HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    TestHandler handler = new TestHandler(handlerThread.getLooper());
    this.handler = handler;

    fwkSession = new android.media.session.MediaSession(context, TAG);
    fwkSession.setActive(true);
    fwkSession.setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    fwkSession.setCallback(new android.media.session.MediaSession.Callback() {}, handler);
  }

  @After
  public void cleanUp() {
    if (fwkSession != null) {
      fwkSession.release();
      fwkSession = null;
    }
    if (handler != null) {
      if (Build.VERSION.SDK_INT >= 18) {
        handler.getLooper().quitSafely();
      } else {
        handler.getLooper().quit();
      }
      handler = null;
    }
  }

  @Test
  public void onConnected_calledAfterCreated() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          public void onConnected(MediaController controller) {
            connectedLatch.countDown();
          }
        };
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionCompatToken(MediaSessionCompat.Token.fromToken(fwkSession.getSessionToken()))
            .setControllerCallback(callback)
            .setApplicationLooper(handler.getLooper())
            .build();
    try {
      assertThat(connectedLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();
    } finally {
      handler.postAndSync(controller::release);
    }
  }

  @Test
  public void onPlaybackStateChanged_isNotifiedByFwkSessionChanges() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    CountDownLatch playbackStateChangedLatch = new CountDownLatch(1);
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          public void onConnected(MediaController controller) {
            connectedLatch.countDown();
          }
        };
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionCompatToken(MediaSessionCompat.Token.fromToken(fwkSession.getSessionToken()))
            .setControllerCallback(callback)
            .setApplicationLooper(handler.getLooper())
            .build();
    SessionPlayer.PlayerCallback playerCallback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(@State int state) {
            playbackStateRef.set(state);
            playWhenReadyRef.set(controller.getPlayWhenReady());
            playbackStateChangedLatch.countDown();
          }
        };
    try {
      controller.addListener(playerCallback);
      assertThat(connectedLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();
      fwkSession.setPlaybackState(
          new PlaybackState.Builder()
              .setState(PlaybackState.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1.0f)
              .build());
      assertThat(playbackStateChangedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      assertThat(playbackStateRef.get()).isEqualTo(STATE_READY);
      assertThat(playWhenReadyRef.get()).isTrue();
    } finally {
      handler.postAndSync(controller::release);
    }
  }
}

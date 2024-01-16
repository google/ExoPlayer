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
package androidx.media3.session;

import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.HandlerThread;
import androidx.media3.common.Player;
import androidx.media3.common.Player.State;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController} with framework MediaSession, which exists since Android-L. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerWithFrameworkMediaSessionTest {

  private static final String TAG = "MCFMediaSessionTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private Context context;
  private TestHandler handler;

  @Before
  public void setUp() {
    if (Util.SDK_INT < 21) {
      return;
    }
    context = ApplicationProvider.getApplicationContext();

    HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    TestHandler handler = new TestHandler(handlerThread.getLooper());
    this.handler = handler;
  }

  @After
  public void cleanUp() {
    if (handler != null) {
      handler.getLooper().quitSafely();
      handler = null;
    }
  }

  @SuppressWarnings("UnnecessarilyFullyQualified") // Intentionally fully qualified for fwk session.
  @Test
  public void createController() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // For framework MediaSession.
    MediaSession fwkSession = new android.media.session.MediaSession(context, TAG);
    fwkSession.setActive(true);
    fwkSession.setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    fwkSession.setCallback(new android.media.session.MediaSession.Callback() {}, handler);
    SessionToken token =
        SessionToken.createSessionToken(context, fwkSession.getSessionToken())
            .get(TIMEOUT_MS, MILLISECONDS);
    MediaController controller =
        new MediaController.Builder(context, token)
            .setApplicationLooper(handler.getLooper())
            .buildAsync()
            .get(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
    handler.postAndSync(controller::release);
    fwkSession.release();
  }

  @SuppressWarnings("UnnecessarilyFullyQualified") // Intentionally fully qualified for fwk session.
  @Test
  public void onPlaybackStateChanged_isNotifiedByFwkSessionChanges() throws Exception {
    Assume.assumeTrue(Util.SDK_INT >= 21); // For framework MediaSession.
    MediaSession fwkSession = new android.media.session.MediaSession(context, TAG);
    fwkSession.setActive(true);
    fwkSession.setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    fwkSession.setCallback(new android.media.session.MediaSession.Callback() {}, handler);
    CountDownLatch playbackStateChangedLatch = new CountDownLatch(1);
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    SessionToken token =
        SessionToken.createSessionToken(context, fwkSession.getSessionToken())
            .get(TIMEOUT_MS, MILLISECONDS);
    MediaController controller =
        new MediaController.Builder(context, token)
            .setApplicationLooper(handler.getLooper())
            .buildAsync()
            .get(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@State int playbackState) {
            playbackStateRef.set(playbackState);
            playWhenReadyRef.set(controller.getPlayWhenReady());
            playbackStateChangedLatch.countDown();
          }
        };
    controller.addListener(listener);
    fwkSession.setPlaybackState(
        new PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, /* position= */ 0, /* playbackSpeed= */ 1.0f)
            .build());
    try {
      assertThat(playbackStateChangedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      assertThat(playbackStateRef.get()).isEqualTo(STATE_READY);
      assertThat(playWhenReadyRef.get()).isTrue();
    } finally {
      handler.postAndSync(controller::release);
      fwkSession.release();
    }
  }
}

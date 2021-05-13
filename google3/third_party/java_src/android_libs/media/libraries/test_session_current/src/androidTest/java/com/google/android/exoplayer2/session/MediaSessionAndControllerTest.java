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

import static com.google.android.exoplayer2.Player.STATE_IDLE;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession} and {@link MediaController} in the same process. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionAndControllerTest {

  private Context context;
  private TestHandler handler;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();

    HandlerThread handlerThread = new HandlerThread("MediaSessionAndControllerTest");
    handlerThread.start();
    handler = new TestHandler(handlerThread.getLooper());
  }

  @After
  public void cleanUp() {
    if (Build.VERSION.SDK_INT >= 18) {
      handler.getLooper().quitSafely();
    } else {
      handler.getLooper().quit();
    }
  }

  /** Test potential deadlock for calls between controller and session. */
  @Test
  public void deadlock() throws Exception {
    HandlerThread testThread = new HandlerThread("deadlock");
    testThread.start();
    TestHandler testHandler = new TestHandler(testThread.getLooper());
    AtomicReference<MediaSession> sessionRef = new AtomicReference<>();
    AtomicReference<MediaController> controllerRef = new AtomicReference<>();
    try {
      MockPlayer player =
          new MockPlayer.Builder().setApplicationLooper(testThread.getLooper()).build();
      handler.postAndSync(
          () ->
              sessionRef.set(new MediaSession.Builder(context, player).setId("deadlock").build()));
      controllerRef.set(createController(sessionRef.get().getToken(), testThread.getLooper()));
      // This may hang if deadlock happens.
      testHandler.postAndSync(
          () -> {
            int state = STATE_IDLE;
            MediaController controller = controllerRef.get();
            for (int i = 0; i < 100; i++) {
              // triggers call from session to controller.
              player.notifyPlaybackStateChanged(state);
              // triggers call from controller to session.
              controller.play();

              // Repeat above
              player.notifyPlaybackStateChanged(state);
              controller.pause();
              player.notifyPlaybackStateChanged(state);
              controller.seekTo(0);
              player.notifyPlaybackStateChanged(state);
              controller.next();
              player.notifyPlaybackStateChanged(state);
              controller.previous();
            }
          },
          LONG_TIMEOUT_MS);
    } finally {
      testHandler.postAndSync(
          () -> {
            if (controllerRef.get() != null) {
              controllerRef.get().release();
              controllerRef.set(null);
            }
          });

      handler.postAndSync(
          () -> {
            // Clean up here because sessionHandler will be removed afterwards.
            if (sessionRef.get() != null) {
              sessionRef.get().release();
            }
          });

      if (Build.VERSION.SDK_INT >= 18) {
        testThread.quitSafely();
      } else {
        testThread.quit();
      }
    }
  }

  private MediaController createController(
      @NonNull SessionToken token, @NonNull Looper applicationLooper) throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    AtomicReference<MediaController> controller = new AtomicReference<>();
    handler.postAndSync(
        () -> {
          // Create controller on the test handler, for changing MediaBrowserCompat's Handler
          // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
          // and commands wouldn't be run if tests codes waits on the test handler.
          MediaController.Builder builder =
              new MediaController.Builder(context)
                  .setSessionToken(token)
                  .setApplicationLooper(applicationLooper)
                  .setControllerCallback(
                      new MediaController.ControllerCallback() {
                        @Override
                        public void onConnected(MediaController controller) {
                          connectedLatch.countDown();
                        }
                      });
          controller.set(builder.build());
        });
    assertThat(connectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    return controller.get();
  }
}

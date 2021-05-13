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

import static com.google.android.exoplayer2.Player.STATE_IDLE;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaSessionManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.session.MediaSession.SessionCallback;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import com.google.android.exoplayer2.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionTest {

  private static final String TAG = "MediaSessionTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private TestHandler handler;
  private MediaSession session;
  private MockPlayer player;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    player =
        new MockPlayer.Builder().setLatchCount(1).setApplicationLooper(handler.getLooper()).build();

    session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId(TAG)
                .setSessionCallback(
                    new MediaSession.SessionCallback() {
                      @Nullable
                      @Override
                      public MediaSession.ConnectResult onConnect(
                          MediaSession session, MediaSession.ControllerInfo controller) {
                        if (TextUtils.equals(
                            context.getPackageName(), controller.getPackageName())) {
                          return super.onConnect(session, controller);
                        }
                        return null;
                      }
                    })
                .build());
  }

  @Test
  public void builder() {
    MediaSession.Builder builder;
    try {
      builder = new MediaSession.Builder(context, null);
      assertWithMessage("null player shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }
    try {
      builder = new MediaSession.Builder(context, player);
      builder.setId(null);
      assertWithMessage("null id shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }
    try {
      builder = new MediaSession.Builder(context, player);
      builder.setExtras(null);
      assertWithMessage("null extras shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }
    // Empty string as ID is allowed.
    new MediaSession.Builder(context, player).setId("").build().release();
  }

  @Test
  public void getPlayer() throws Exception {
    assertThat(session.getPlayer()).isEqualTo(player);
  }

  @Test
  public void setPlayer() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(this.player.getApplicationLooper()).build();
    session.setPlayer(player);
    assertThat(session.getPlayer()).isEqualTo(player);
  }

  @Test
  public void setPlayer_withSamePlayerInstance() throws Exception {
    session.setPlayer(player);
    assertThat(session.getPlayer()).isEqualTo(player);
  }

  @Test
  public void setPlayer_withDifferentLooper_throwsIAE() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(Looper.getMainLooper()).build();
    try {
      session.setPlayer(player);
      assertWithMessage("IAE is expected").fail();
    } catch (IllegalArgumentException unused) {
      // expected
    }
  }

  /** Test potential deadlock for calls between controller and session. */
  @Test
  @LargeTest
  public void deadlock() throws Exception {
    handler.postAndSync(
        () -> {
          session.release();
          session = null;
        });

    HandlerThread testThread = new HandlerThread("deadlock");
    testThread.start();
    TestHandler testHandler = new TestHandler(testThread.getLooper());
    try {
      MockPlayer player =
          new MockPlayer.Builder().setApplicationLooper(testThread.getLooper()).build();
      handler.postAndSync(
          () -> {
            session = new MediaSession.Builder(context, player).setId("testDeadlock").build();
          });
      RemoteMediaController controller =
          controllerTestRule.createRemoteController(session.getToken());
      // This may hang if deadlock happens.
      testHandler.postAndSync(
          () -> {
            int state = STATE_IDLE;
            for (int i = 0; i < 100; i++) {
              Log.d(TAG, "testDeadlock for-loop started: index=" + i);
              long startTime = SystemClock.elapsedRealtime();

              // triggers call from session to controller.
              player.notifyPlaybackStateChanged(state);
              long endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "1) Time spent on API call(ms): " + (endTime - startTime));

              // triggers call from controller to session.
              startTime = endTime;
              controller.play();
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "2) Time spent on API call(ms): " + (endTime - startTime));

              // Repeat above
              startTime = endTime;
              player.notifyPlaybackStateChanged(state);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "3) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              controller.pause();
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "4) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              player.notifyPlaybackStateChanged(state);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "5) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              controller.seekTo(0);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "6) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              player.notifyPlaybackStateChanged(state);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "7) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              controller.next();
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "8) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              player.notifyPlaybackStateChanged(state);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "9) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              controller.previous();
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "10) Time spent on API call(ms): " + (endTime - startTime));
            }
          },
          LONG_TIMEOUT_MS);
    } finally {
      if (session != null) {
        handler.postAndSync(
            () -> {
              // Clean up here because sessionHandler will be removed afterwards.
              session.release();
              session = null;
            });
      }

      if (Build.VERSION.SDK_INT >= 18) {
        testThread.quitSafely();
      } else {
        testThread.quit();
      }
    }
  }

  @Test
  public void creatingTwoSessionWithSameId() {
    String sessionId = "testSessionId";
    MediaSession session =
        new MediaSession.Builder(
                context, new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build())
            .setId(sessionId)
            .build();

    MediaSession.Builder builderWithSameId =
        new MediaSession.Builder(
            context, new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build());
    try {
      builderWithSameId.setId(sessionId).build();
      assertWithMessage(
              "Creating a new session with the same ID in a process should not be allowed")
          .fail();
    } catch (IllegalStateException e) {
      // expected. pass-through
    }

    session.release();
    // Creating a new session with ID of the closed session is okay.
    MediaSession sessionWithSameId = builderWithSameId.build();
    sessionWithSameId.release();
  }

  @Test
  public void sendCustomCommand_onConnect() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SessionCommand testCommand = new SessionCommand("test", null);
    SessionCallback testSessionCallback =
        new SessionCallback() {
          @Nullable
          @Override
          public MediaSession.ConnectResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            Future<SessionResult> result = session.sendCustomCommand(controller, testCommand, null);
            try {
              // The controller is not connected yet.
              assertThat(result.get(TIMEOUT_MS, MILLISECONDS).resultCode)
                  .isEqualTo(SessionResult.RESULT_ERROR_SESSION_DISCONNECTED);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
              assertWithMessage("Fail to get result of the returned future.").fail();
            }
            return super.onConnect(session, controller);
          }

          @Override
          public void onPostConnect(
              @NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
            Future<SessionResult> result = session.sendCustomCommand(controller, testCommand, null);
            try {
              // The controller is connected but doesn't implement onCustomCommand.
              assertThat(result.get(TIMEOUT_MS, MILLISECONDS).resultCode)
                  .isEqualTo(SessionResult.RESULT_ERROR_NOT_SUPPORTED);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
              assertWithMessage("Fail to get result of the returned future.").fail();
            }
            latch.countDown();
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(testSessionCallback)
                .build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  /** Test {@link MediaSession#getSessionCompatToken()}. */
  @Test
  public void getSessionCompatToken_returnsCompatibleWithMediaControllerCompat() throws Exception {
    String expectedControllerCompatPackageName =
        (21 <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < 24)
            ? MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER
            : context.getPackageName();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("getSessionCompatToken_returnsCompatibleWithMediaControllerCompat")
                .setSessionCallback(
                    new SessionCallback() {
                      @Nullable
                      @Override
                      public MediaSession.ConnectResult onConnect(
                          MediaSession session, MediaSession.ControllerInfo controller) {
                        if (TextUtils.equals(
                            expectedControllerCompatPackageName, controller.getPackageName())) {
                          return super.onConnect(session, controller);
                        }
                        return null;
                      }
                    })
                .build());
    MediaSessionCompat.Token token = session.getSessionCompatToken();
    MediaControllerCompat controllerCompat = new MediaControllerCompat(context, token);
    CountDownLatch sessionReadyLatch = new CountDownLatch(1);
    controllerCompat.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionReady() {
            sessionReadyLatch.countDown();
          }
        },
        handler);
    assertThat(sessionReadyLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    long testSeekPositionMs = 1234;
    controllerCompat.getTransportControls().seekTo(testSeekPositionMs);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.seekToCalled).isTrue();
    assertThat(player.seekPositionMs).isEqualTo(testSeekPositionMs);
  }
}

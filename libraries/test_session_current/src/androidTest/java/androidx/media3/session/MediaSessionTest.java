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

import static android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_MEDIA_REWIND;
import static android.view.KeyEvent.KEYCODE_MEDIA_STOP;
import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.view.KeyEvent;
import androidx.media.MediaSessionManager;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
  private MediaController controller;
  private MockPlayer player;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId(TAG)
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            context.getPackageName(), controller.getPackageName())) {
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }
                        return MediaSession.ConnectionResult.reject();
                      }
                    })
                .build());

    controller =
        new MediaController.Builder(context, session.getToken())
            .setListener(new MediaController.Listener() {})
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .buildAsync()
            .get(TIMEOUT_MS, MILLISECONDS);
  }

  @After
  public void tearDown() throws Exception {
    if ((controller != null)) {
      threadTestRule.getHandler().postAndSync(() -> controller.release());
    }
    if (session != null) {
      session.release();
    }
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
      builder = new MediaSession.Builder(context, controller);
      assertWithMessage("MediaController shouldn't be allowed").fail();
    } catch (IllegalArgumentException e) {
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
    sessionTestRule.ensureReleaseAfterTest(
        new MediaSession.Builder(context, player).setId("").build());
  }

  @Test
  public void builderSetSessionActivity_activityIntent_accepted() {
    PendingIntent pendingIntent =
        PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(),
            /* requestCode= */ 0,
            new Intent("action"),
            PendingIntent.FLAG_IMMUTABLE);

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(getApplicationContext(), new MockPlayer.Builder().build())
                .setId("sessionActivity")
                .setSessionActivity(pendingIntent)
                .build());

    assertThat(session.getSessionActivity()).isEqualTo(pendingIntent);
  }

  @Test
  public void setSessionActivity_activityIntent_accepted() {
    PendingIntent pendingIntent =
        PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(),
            /* requestCode= */ 0,
            new Intent("action"),
            PendingIntent.FLAG_IMMUTABLE);

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(getApplicationContext(), new MockPlayer.Builder().build())
                .setId("sessionActivity")
                .build());
    session.setSessionActivity(pendingIntent);

    assertThat(session.getSessionActivity()).isEqualTo(pendingIntent);
  }

  @Test
  public void builderSetSessionActivity_nonActivityIntent_throwsIllegalArgumentException() {
    Assume.assumeTrue(Util.SDK_INT >= 31);
    PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
            ApplicationProvider.getApplicationContext(),
            /* requestCode= */ 0,
            new Intent("action"),
            PendingIntent.FLAG_IMMUTABLE);

    MediaSession.Builder builder =
        new MediaSession.Builder(getApplicationContext(), new MockPlayer.Builder().build())
            .setId("sessionActivity");

    Assert.assertThrows(
        IllegalArgumentException.class, () -> builder.setSessionActivity(pendingIntent));
  }

  @Test
  public void setSessionActivity_nonActivityIntent_throwsIllegalArgumentException() {
    Assume.assumeTrue(Util.SDK_INT >= 31);
    PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
            ApplicationProvider.getApplicationContext(),
            /* requestCode= */ 0,
            new Intent("action"),
            PendingIntent.FLAG_IMMUTABLE);

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(getApplicationContext(), new MockPlayer.Builder().build())
                .setId("sessionActivity")
                .build());

    Assert.assertThrows(
        IllegalArgumentException.class, () -> session.setSessionActivity(pendingIntent));
  }

  @Test
  public void getPlayer() throws Exception {
    assertThat(handler.postAndSync(session::getPlayer)).isEqualTo(player);
  }

  @Test
  public void setPlayer_withNewPlayer_changesPlayer() throws Exception {
    MockPlayer player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    handler.postAndSync(() -> session.setPlayer(player));
    assertThat(handler.postAndSync(session::getPlayer)).isEqualTo(player);
  }

  @Test
  public void setPlayer_withTheSamePlayer_doesNothing() throws Exception {
    handler.postAndSync(() -> session.setPlayer(player));
    assertThat(handler.postAndSync(session::getPlayer)).isEqualTo(player);
  }

  @Test
  public void setPlayer_withDifferentLooper_throwsIAE() throws Exception {
    MockPlayer player =
        new MockPlayer.Builder().setApplicationLooper(Looper.getMainLooper()).build();
    assertThrows(
        IllegalArgumentException.class, () -> handler.postAndSync(() -> session.setPlayer(player)));
  }

  @Test
  public void setPlayer_withMediaController_throwsIAE() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> handler.postAndSync(() -> session.setPlayer(controller)));
  }

  @Test
  public void setPlayer_fromDifferentLooper_throwsISE() throws Exception {
    MockPlayer player = new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
    assertThrows(IllegalStateException.class, () -> session.setPlayer(player));
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
              controller.seekToNextMediaItem();
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "8) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              player.notifyPlaybackStateChanged(state);
              endTime = SystemClock.elapsedRealtime();
              Log.d(TAG, "9) Time spent on API call(ms): " + (endTime - startTime));

              startTime = endTime;
              controller.seekToPreviousMediaItem();
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

      if (Util.SDK_INT >= 18) {
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
    SessionCommand testCommand = new SessionCommand("test", /* extras= */ Bundle.EMPTY);
    MediaSession.Callback testSessionCallback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            Future<SessionResult> result =
                session.sendCustomCommand(controller, testCommand, /* args= */ Bundle.EMPTY);
            try {
              // The controller is not connected yet.
              assertThat(result.get(TIMEOUT_MS, MILLISECONDS).resultCode)
                  .isEqualTo(SessionResult.RESULT_ERROR_SESSION_DISCONNECTED);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
              assertWithMessage("Fail to get result of the returned future.").fail();
            }
            return MediaSession.Callback.super.onConnect(session, controller);
          }

          @Override
          public void onPostConnect(MediaSession session, ControllerInfo controller) {
            Future<SessionResult> result =
                session.sendCustomCommand(controller, testCommand, /* args= */ Bundle.EMPTY);
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
            new MediaSession.Builder(context, player).setCallback(testSessionCallback).build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  /** Test {@link MediaSession#getSessionCompatToken()}. */
  @Test
  public void getSessionCompatToken_returnsCompatibleWithMediaControllerCompat() throws Exception {
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("getSessionCompatToken_returnsCompatibleWithMediaControllerCompat")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            getControllerCallerPackageName(controller),
                            controller.getPackageName())) {
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }
                        return MediaSession.ConnectionResult.reject();
                      }
                    })
                .build());
    Object token = session.getSessionCompatToken();
    assertThat(token).isInstanceOf(MediaSessionCompat.Token.class);
    MediaControllerCompat controllerCompat =
        new MediaControllerCompat(context, (MediaSessionCompat.Token) token);
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

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO, TIMEOUT_MS);
    assertThat(player.seekPositionMs).isEqualTo(testSeekPositionMs);
  }

  @Test
  public void getControllerVersion() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    AtomicInteger controllerVersionRef = new AtomicInteger();
    MediaSession.Callback sessionCallback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            controllerVersionRef.set(controller.getControllerVersion());
            connectedLatch.countDown();
            return MediaSession.Callback.super.onConnect(session, controller);
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("getControllerVersion")
                .setCallback(sessionCallback)
                .build());
    controllerTestRule.createRemoteController(session.getToken());

    assertThat(connectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    // TODO(b/199226670): The expected version should vary if the test runs with the previous
    //  version of remote controller.
    assertThat(controllerVersionRef.get()).isEqualTo(MediaLibraryInfo.VERSION_INT);
  }

  @Test
  public void setPeriodicPositionUpdateEnabled_periodicUpdatesEnabled_bufferedPositionMsUpdated()
      throws Exception {
    player.playWhenReady = true;
    player.playbackState = Player.STATE_READY;
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setPeriodicPositionUpdateEnabled(true)
                .setId(
                    "setPeriodicPositionUpdateEnabled_periodicUpdatesEnabled_bufferedPositionMsUpdated")
                .build());
    threadTestRule.getHandler().postAndSync(() -> session.setSessionPositionUpdateDelayMs(10L));
    MediaController controller =
        new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
            .buildAsync()
            .get();
    List<Long> bufferedPositionsMs = new ArrayList<>();
    TestHandler testHandler = new TestHandler(controller.getApplicationLooper());

    for (long bufferedPositionMs = 0; bufferedPositionMs < 5000; bufferedPositionMs += 1000) {
      player.bufferedPosition = bufferedPositionMs;
      Thread.sleep(50L);
      bufferedPositionsMs.add(testHandler.postAndSync(controller::getBufferedPosition));
    }

    assertThat(bufferedPositionsMs).containsExactly(0L, 1000L, 2000L, 3000L, 4000L).inOrder();
  }

  @Test
  public void setPeriodicPositionUpdateEnabled_periodicUpdatesDisabled_bufferedPositionMsUnchanged()
      throws Exception {
    player.playWhenReady = true;
    player.playbackState = Player.STATE_READY;
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setPeriodicPositionUpdateEnabled(false)
                .setId(
                    "setPeriodicPositionUpdateEnabled_periodicUpdatesDisabled_bufferedPositionMsUnchanged")
                .build());
    threadTestRule.getHandler().postAndSync(() -> session.setSessionPositionUpdateDelayMs(10L));
    MediaController controller =
        new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
            .buildAsync()
            .get();
    List<Long> bufferedPositionsMs = new ArrayList<>();
    TestHandler testHandler = new TestHandler(controller.getApplicationLooper());

    for (long bufferedPositionMs = 0; bufferedPositionMs < 5000; bufferedPositionMs += 1000) {
      player.bufferedPosition = bufferedPositionMs;
      Thread.sleep(50L);
      bufferedPositionsMs.add(testHandler.postAndSync(controller::getBufferedPosition));
    }

    assertThat(bufferedPositionsMs).containsExactly(0L, 0L, 0L, 0L, 0L).inOrder();
  }

  @Test
  public void onMediaButtonEvent_allSupportedKeys_notificationControllerConnected_dispatchesEvent()
      throws Exception {
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(player, session);
    session.set(
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("onMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            context.getPackageName(), controller.getPackageName())) {
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }
                        return MediaSession.ConnectionResult.reject();
                      }
                    })
                .build()));
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(
            ApplicationProvider.getApplicationContext(), session.get().getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              MediaSessionImpl impl = session.get().getImpl();
              ControllerInfo controllerInfo = createMediaButtonCaller();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PLAY)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PAUSE)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_FAST_FORWARD)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_REWIND)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_NEXT)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PREVIOUS)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_STOP)))
                  .isTrue();
            });

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
    assertThat(callerCollectorPlayer.callingControllers).hasSize(7);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callingControllers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isTrue();
    }
  }

  @Test
  public void
      onMediaButtonEvent_allSupportedKeys_notificationControllerNotConnected_dispatchesEventThroughFrameworkFallback()
          throws Exception {
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CallerCollectorPlayer callerCollectorPlayer = new CallerCollectorPlayer(player, session);
    session.set(
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, callerCollectorPlayer)
                .setId("onMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            getControllerCallerPackageName(controller),
                            controller.getPackageName())) {
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }
                        return MediaSession.ConnectionResult.reject();
                      }
                    })
                .build()));
    MediaSessionImpl impl = session.get().getImpl();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              ControllerInfo controllerInfo = createMediaButtonCaller();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PLAY)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PAUSE)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_FAST_FORWARD)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_REWIND)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_NEXT)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_PREVIOUS)))
                  .isTrue();
              assertThat(
                      impl.onMediaButtonEvent(
                          controllerInfo, getMediaButtonIntent(KEYCODE_MEDIA_STOP)))
                  .isTrue();
            });

    // Fallback through the framework session when media notification controller in disabled.
    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_BACK, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
    assertThat(callerCollectorPlayer.callingControllers).hasSize(7);
    for (ControllerInfo controllerInfo : callerCollectorPlayer.callingControllers) {
      assertThat(session.get().isMediaNotificationController(controllerInfo)).isFalse();
      assertThat(controllerInfo.getControllerVersion())
          .isEqualTo(ControllerInfo.LEGACY_CONTROLLER_VERSION);
      assertThat(controllerInfo.getPackageName())
          .isEqualTo(getControllerCallerPackageName(controllerInfo));
    }
  }

  @Test
  public void
      onMediaButtonEvent_appOverridesCallback_notificationControllerNotConnected_callsWhatAppCalls()
          throws Exception {
    List<ControllerInfo> controllers = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("onMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public MediaSession.ConnectionResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        if (TextUtils.equals(
                            getControllerCallerPackageName(controller),
                            controller.getPackageName())) {
                          return MediaSession.Callback.super.onConnect(session, controller);
                        }
                        return MediaSession.ConnectionResult.reject();
                      }

                      @Override
                      public boolean onMediaButtonEvent(
                          MediaSession session, ControllerInfo controllerInfo, Intent intent) {
                        session.getPlayer().seekToNext();
                        controllers.add(controllerInfo);
                        latch.countDown();
                        return true;
                      }
                    })
                .build());
    MediaSessionImpl impl = session.getImpl();

    ControllerInfo controllerInfo = createMediaButtonCaller();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY_PAUSE);
              assertThat(impl.onMediaButtonEvent(controllerInfo, intent)).isTrue();
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
    assertThat(controllers).hasSize(1);
    assertThat(session.isMediaNotificationController(controllers.get(0))).isFalse();
  }

  @Test
  public void
      onMediaButtonEvent_appOverridesCallback_notificationControllerConnected_callsWhatAppCalls()
          throws Exception {
    List<ControllerInfo> controllers = new ArrayList<>();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("onMediaButtonEvent")
                .setCallback(
                    new MediaSession.Callback() {
                      @Override
                      public boolean onMediaButtonEvent(
                          MediaSession session, ControllerInfo controllerInfo, Intent intent) {
                        if (DefaultActionFactory.getKeyEvent(intent).getKeyCode()
                            == KEYCODE_MEDIA_PLAY) {
                          player.seekForward();
                          controllers.add(controllerInfo);
                          return true;
                        }
                        return MediaSession.Callback.super.onMediaButtonEvent(
                            session, controllerInfo, intent);
                      }
                    })
                .build());
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();

    boolean isEventHandled =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    session
                        .getImpl()
                        .onMediaButtonEvent(
                            session.getMediaNotificationControllerInfo(),
                            getMediaButtonIntent(KEYCODE_MEDIA_PLAY)));

    assertThat(isEventHandled).isTrue();
    // App changed default behaviour
    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
    assertThat(controllers).hasSize(1);
    assertThat(session.isMediaNotificationController(controllers.get(0))).isTrue();
  }

  @Test
  public void onMediaButtonEvent_noKeyEvent_returnsFalse() {
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.removeExtra(Intent.EXTRA_KEY_EVENT);

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_noKeyEvent_mediaNotificationControllerConnected_returnsFalse()
      throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.removeExtra(Intent.EXTRA_KEY_EVENT);

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_invalidKeyEvent_returnsFalse() {
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.removeExtra(Intent.EXTRA_KEY_EVENT);
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_MEDIA_PAUSE));

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_invalidKeyEvent_mediaNotificationControllerConnected_returnsFalse()
      throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.removeExtra(Intent.EXTRA_KEY_EVENT);
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_MEDIA_PAUSE));

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_invalidAction_returnsFalse() {
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.setAction("notAMediaButtonAction");

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_invalidAction_mediaNotificationControllerConnected_returnsFalse()
      throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.setAction("notAMediaButtonAction");

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void onMediaButtonEvent_invalidComponent_returnsFalse() {
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.setComponent(new ComponentName("a.package", "a.class"));

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  @Test
  public void
      onMediaButtonEvent_invalidComponent_mediaNotificationControllerConnected_returnsFalse()
          throws Exception {
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(ApplicationProvider.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();
    Intent intent = getMediaButtonIntent(KEYCODE_MEDIA_PLAY);
    intent.setComponent(new ComponentName("a.package", "a.class"));

    boolean isEventHandled =
        session.getImpl().onMediaButtonEvent(createMediaButtonCaller(), intent);

    assertThat(isEventHandled).isFalse();
  }

  private static Intent getMediaButtonIntent(int keyCode) {
    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    intent.setComponent(
        new ComponentName(ApplicationProvider.getApplicationContext(), Object.class));
    intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    return intent;
  }

  /**
   * Returns the expected {@link MediaSessionManager.RemoteUserInfo#getPackageName()} of a
   * controller hosted in the test companion app.
   *
   * <p>Before API 21 and after API 23 the package name is {@link Context#getPackageName()} of the
   * {@link ApplicationProvider#getApplicationContext() application under test}.
   *
   * <p>The early implementations (API 21 - 23), the platform MediaSession doesn't report the caller
   * package name. Instead the package of the RemoteUserInfo is set for all external controllers to
   * the same {@code MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER} (see
   * MediaSessionCompat.MediaSessionCallbackApi21.setCurrentControllerInfo()).
   *
   * <p>Calling this method should only be required to test legacy behaviour.
   */
  private static String getControllerCallerPackageName(ControllerInfo controllerInfo) {
    return (Util.SDK_INT < 21
            || Util.SDK_INT > 23
            || controllerInfo.getControllerVersion() != ControllerInfo.LEGACY_CONTROLLER_VERSION)
        ? ApplicationProvider.getApplicationContext().getPackageName()
        : MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
  }

  private static ControllerInfo createMediaButtonCaller() {
    return new ControllerInfo(
        new MediaSessionManager.RemoteUserInfo(
            "RANDOM_MEDIA_BUTTON_CALLER_PACKAGE",
            MediaSessionManager.RemoteUserInfo.UNKNOWN_PID,
            MediaSessionManager.RemoteUserInfo.UNKNOWN_UID),
        MediaLibraryInfo.VERSION_INT,
        MediaControllerStub.VERSION_INT,
        /* trusted= */ false,
        /* cb= */ null,
        /* connectionHints= */ Bundle.EMPTY);
  }

  private static class CallerCollectorPlayer extends ForwardingPlayer {
    private final List<ControllerInfo> callingControllers;
    private final AtomicReference<MediaSession> session;

    public CallerCollectorPlayer(Player player, AtomicReference<MediaSession> mediaSession) {
      super(player);
      this.session = mediaSession;
      callingControllers = new ArrayList<>();
    }

    @Override
    public void play() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.play();
    }

    @Override
    public void pause() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.pause();
    }

    @Override
    public void seekBack() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.seekBack();
    }

    @Override
    public void seekForward() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.seekForward();
    }

    @Override
    public void seekToNext() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.seekToNext();
    }

    @Override
    public void seekToPrevious() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.seekToPrevious();
    }

    @Override
    public void stop() {
      callingControllers.add(session.get().getControllerForCurrentRequest());
      super.stop();
    }
  }
}

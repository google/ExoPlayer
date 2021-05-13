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

import static com.google.android.exoplayer2.session.SessionResult.RESULT_ERROR_INVALID_STATE;
import static com.google.android.exoplayer2.session.SessionResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Rating;
import com.google.android.exoplayer2.StarRating;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession.SessionCallback}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionCallbackTest {
  private static final String TAG = "MediaSessionCallbackTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    player =
        new MockPlayer.Builder()
            .setLatchCount(1)
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
  }

  @Test
  public void onPostConnect_afterConnected() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.SessionCallback callback =
        new MediaSession.SessionCallback() {
          @Override
          public void onPostConnect(
              @NonNull MediaSession session, @NonNull ControllerInfo controller) {
            latch.countDown();
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnPostConnect_afterConnected")
                .build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onPostConnect_afterConnectionRejected() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.SessionCallback callback =
        new MediaSession.SessionCallback() {
          @Nullable
          @Override
          public MediaSession.ConnectResult onConnect(
              MediaSession session, ControllerInfo controller) {
            return null;
          }

          @Override
          public void onPostConnect(
              @NonNull MediaSession session, @NonNull ControllerInfo controller) {
            latch.countDown();
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnPostConnect_afterConnectionRejected")
                .build());
    controllerTestRule.createRemoteController(session.getToken());
    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void onCommandRequest() throws Exception {
    MockOnCommandCallback callback = new MockOnCommandCallback();
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnCommandRequest")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.prepare();
    assertThat(player.countDownLatch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    assertThat(player.prepareCalled).isFalse();
    assertThat(callback.commands).hasSize(1);
    assertThat(callback.commands.get(0)).isEqualTo(Player.COMMAND_PREPARE_STOP);

    controller.play();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.playCalled).isTrue();
    assertThat(player.prepareCalled).isFalse();
    assertThat(callback.commands).hasSize(2);
    assertThat(callback.commands.get(1)).isEqualTo(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void onCustomCommand() throws Exception {
    // TODO(jaewan): Need to revisit with the permission.
    SessionCommand testCommand = new SessionCommand("testCustomCommand", null);
    Bundle testArgs = new Bundle();
    testArgs.putString("args", "testOnCustomCommand");

    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.SessionCallback callback =
        new MediaSession.SessionCallback() {
          @Nullable
          @Override
          public MediaSession.ConnectResult onConnect(
              MediaSession session, ControllerInfo controller) {
            SessionCommands commands =
                new SessionCommands.Builder()
                    .addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_1)
                    .add(testCommand)
                    .build();
            return new MediaSession.ConnectResult(commands, Player.Commands.EMPTY);
          }

          @Override
          @NonNull
          public ListenableFuture<SessionResult> onCustomCommand(
              @NonNull MediaSession session,
              @NonNull MediaSession.ControllerInfo controller,
              @NonNull SessionCommand sessionCommand,
              Bundle args) {
            assertThat(controller.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
            assertThat(sessionCommand).isEqualTo(testCommand);
            assertThat(TestUtils.equals(testArgs, args)).isTrue();
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnCustomCommand")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    SessionResult result = controller.sendCustomCommand(testCommand, testArgs);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onSetMediaUri() throws Exception {
    Uri testUri = Uri.parse("foo://boo");
    Bundle testExtras = TestUtils.createTestBundle();
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.SessionCallback callback =
        new MediaSession.SessionCallback() {
          @Override
          public int onSetMediaUri(
              @NonNull MediaSession session,
              @NonNull ControllerInfo controller,
              @NonNull Uri uri,
              @Nullable Bundle extras) {
            assertThat(controller.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
            assertThat(uri).isEqualTo(testUri);
            assertThat(TestUtils.equals(testExtras, extras)).isTrue();
            latch.countDown();
            return RESULT_SUCCESS;
          }
        };
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnSetMediaUri")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());

    controller.setMediaUri(testUri, testExtras);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onSetRating() throws Exception {
    float ratingValue = 3.5f;
    Rating testRating = new StarRating(5, ratingValue);
    String testMediaId = "media_id";

    CountDownLatch latch = new CountDownLatch(1);
    MediaSession.SessionCallback callback =
        new MediaSession.SessionCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onSetRating(
              @NonNull MediaSession session,
              @NonNull ControllerInfo controller,
              @NonNull String mediaId,
              @NonNull Rating rating) {
            assertThat(controller.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
            assertThat(mediaId).isEqualTo(testMediaId);
            assertThat(rating).isEqualTo(testRating);
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setSessionCallback(callback)
                .setId("testOnSetRating")
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    SessionResult result = controller.setRating(testMediaId, testRating);
    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onConnect() throws Exception {
    AtomicReference<Bundle> connectionHints = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player)
                .setId("testOnConnect")
                .setSessionCallback(
                    new MediaSession.SessionCallback() {
                      @Nullable
                      @Override
                      public MediaSession.ConnectResult onConnect(
                          MediaSession session, ControllerInfo controller) {
                        // TODO: Get uid of client app's and compare.
                        if (!SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
                          return null;
                        }
                        connectionHints.set(controller.getConnectionHints());
                        latch.countDown();
                        return super.onConnect(session, controller);
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
                .setSessionCallback(
                    new MediaSession.SessionCallback() {
                      @Override
                      public void onDisconnected(
                          @NonNull MediaSession session, @NonNull ControllerInfo controller) {
                        assertThat(controller.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
                        // TODO: Get uid of client app's and compare.
                        latch.countDown();
                      }
                    })
                .build());
    RemoteMediaController controller =
        controllerTestRule.createRemoteController(session.getToken());
    controller.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  static class MockOnCommandCallback extends MediaSession.SessionCallback {
    public final ArrayList<Integer> commands = new ArrayList<>();

    @Override
    public int onPlayerCommandRequest(
        @NonNull MediaSession session,
        @NonNull ControllerInfo controllerInfo,
        @Player.Command int command) {
      // TODO: Get uid of client app's and compare.
      assertThat(controllerInfo.getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
      assertThat(controllerInfo.isTrusted()).isFalse();
      commands.add(command);
      if (command == Player.COMMAND_PREPARE_STOP) {
        return RESULT_ERROR_INVALID_STATE;
      }
      return RESULT_SUCCESS;
    }
  }
}

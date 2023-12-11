/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.session.MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS;
import static androidx.media3.session.RemoteMediaControllerCompat.QUEUE_IS_NULL;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSession} with {@link MediaControllerCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionWithMediaControllerCompatTest {

  private static final String TAG = "MediaSessionWithMCCTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final MediaSessionTestRule sessionTestRule = new MediaSessionTestRule();

  @Rule
  public final RemoteControllerTestRule remoteControllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
  }

  @Test
  public void getControllerVersion() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    AtomicInteger controllerVersionRef = new AtomicInteger();
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            controllerVersionRef.set(controller.getControllerVersion());
            connectedLatch.countDown();
            return MediaSession.Callback.super.onConnect(session, controller);
          }
        };

    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).setCallback(callback).build());
    RemoteMediaControllerCompat controllerCompat =
        remoteControllerTestRule.createRemoteControllerCompat(
            session.getSessionCompat().getSessionToken());
    // Invoke any command for session to recognize the controller compat.
    controllerCompat.getTransportControls().prepare();

    assertThat(connectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerVersionRef.get()).isLessThan(1_000_000);
  }

  @Test
  public void notificationController_commandGetTimelineNotAvailable_queueIsNull() throws Exception {
    Player.Commands playerCommands =
        DEFAULT_PLAYER_COMMANDS.buildUpon().remove(Player.COMMAND_GET_TIMELINE).build();
    CountDownLatch connectedLatch = new CountDownLatch(1);
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            if (session.isMediaNotificationController(controller)) {
              return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                  .setAvailablePlayerCommands(playerCommands)
                  .build();
            }
            connectedLatch.countDown();
            return MediaSession.Callback.super.onConnect(session, controller);
          }
        };
    player.timeline =
        new PlaylistTimeline(
            ImmutableList.of(
                new MediaItem.Builder().setMediaId("id1").setUri("https://example.com/1").build(),
                new MediaItem.Builder().setMediaId("id2").setUri("https://example.com/2").build()));
    MediaSession session =
        sessionTestRule.ensureReleaseAfterTest(
            new MediaSession.Builder(context, player).setId(TAG).setCallback(callback).build());
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    new MediaController.Builder(context.getApplicationContext(), session.getToken())
        .setConnectionHints(connectionHints)
        .buildAsync()
        .get();

    RemoteMediaControllerCompat controllerCompat =
        remoteControllerTestRule.createRemoteControllerCompat(
            session.getSessionCompat().getSessionToken());
    controllerCompat.transportControls.play();

    assertThat(connectedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getQueueSize()).isEqualTo(QUEUE_IS_NULL);

    session.setAvailableCommands(
        session.getMediaNotificationControllerInfo(),
        SessionCommands.EMPTY,
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_GET_TIMELINE).build());
    RemoteMediaControllerCompat controllerCompat2 =
        remoteControllerTestRule.createRemoteControllerCompat(
            session.getSessionCompat().getSessionToken());
    controllerCompat2.transportControls.pause();

    assertThat(controllerCompat.getQueueSize()).isEqualTo(2);
    assertThat(controllerCompat2.getQueueSize()).isEqualTo(2);
  }
}

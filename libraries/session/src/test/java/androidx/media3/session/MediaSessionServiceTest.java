/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.StatusBarNotification;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class MediaSessionServiceTest {

  private Context context;
  private NotificationManager notificationManager;
  private ServiceController<TestService> serviceController;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    serviceController = Robolectric.buildService(TestService.class);
  }

  @After
  public void tearDown() {
    serviceController.destroy();
  }

  @Test
  public void service_multipleSessionsOnMainThread_createsNotificationForEachSession() {
    ExoPlayer player1 = new TestExoPlayerBuilder(context).build();
    ExoPlayer player2 = new TestExoPlayerBuilder(context).build();
    MediaSession session1 = new MediaSession.Builder(context, player1).setId("1").build();
    MediaSession session2 = new MediaSession.Builder(context, player2).setId("2").build();
    TestService service = serviceController.create().get();
    service.setMediaNotificationProvider(
        new DefaultMediaNotificationProvider(
            service,
            session -> 2000 + Integer.parseInt(session.getId()),
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID));

    service.addSession(session1);
    service.addSession(session2);
    // Start the players so that we also create notifications for them.
    player1.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
    player1.prepare();
    player1.play();
    player2.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
    player2.prepare();
    player2.play();
    ShadowLooper.idleMainLooper();

    assertThat(getStatusBarNotification(2001)).isNotNull();
    assertThat(getStatusBarNotification(2002)).isNotNull();

    session1.release();
    session2.release();
    player1.release();
    player2.release();
  }

  @Test
  public void service_multipleSessionsOnDifferentThreads_createsNotificationForEachSession()
      throws Exception {
    HandlerThread thread1 = new HandlerThread("player1");
    HandlerThread thread2 = new HandlerThread("player2");
    thread1.start();
    thread2.start();
    ExoPlayer player1 = new TestExoPlayerBuilder(context).setLooper(thread1.getLooper()).build();
    ExoPlayer player2 = new TestExoPlayerBuilder(context).setLooper(thread2.getLooper()).build();
    MediaSession session1 = new MediaSession.Builder(context, player1).setId("1").build();
    MediaSession session2 = new MediaSession.Builder(context, player2).setId("2").build();
    TestService service = serviceController.create().get();
    service.setMediaNotificationProvider(
        new DefaultMediaNotificationProvider(
            service,
            session -> 2000 + Integer.parseInt(session.getId()),
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID));

    service.addSession(session1);
    service.addSession(session2);
    // Start the players so that we also create notifications for them.
    new Handler(thread1.getLooper())
        .post(
            () -> {
              player1.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
              player1.prepare();
              player1.play();
            });
    new Handler(thread2.getLooper())
        .post(
            () -> {
              player2.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
              player2.prepare();
              player2.play();
            });
    runMainLooperUntil(() -> notificationManager.getActiveNotifications().length == 2);

    assertThat(getStatusBarNotification(2001)).isNotNull();
    assertThat(getStatusBarNotification(2002)).isNotNull();

    session1.release();
    session2.release();
    new Handler(thread1.getLooper()).post(player1::release);
    new Handler(thread2.getLooper()).post(player2::release);
    thread1.quit();
    thread2.quit();
  }

  @Test
  public void mediaNotificationController_setCustomLayout_correctNotificationActions()
      throws TimeoutException {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    CommandButton button1 =
        new CommandButton.Builder()
            .setDisplayName("customAction1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    CommandButton button2 =
        new CommandButton.Builder()
            .setDisplayName("customAction2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaSession session =
        new MediaSession.Builder(context, player)
            .setCustomLayout(ImmutableList.of(button1, button2))
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, MediaSession.ControllerInfo controller) {
                    if (session.isMediaNotificationController(controller)) {
                      return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                          .setAvailableSessionCommands(
                              MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                  .buildUpon()
                                  .add(command1)
                                  .add(command2)
                                  .build())
                          .build();
                    }
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).build();
                  }
                })
            .build();
    TestService service = serviceController.create().get();
    service.setMediaNotificationProvider(
        new DefaultMediaNotificationProvider(
            service,
            mediaSession -> 2000,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID));
    service.addSession(session);
    // Play media to create a notification.
    player.setMediaItems(
        ImmutableList.of(
            MediaItem.fromUri("asset:///media/mp4/sample.mp4"),
            MediaItem.fromUri("asset:///media/mp4/sample.mp4")));
    player.prepare();
    player.play();
    runMainLooperUntil(() -> notificationManager.getActiveNotifications().length == 1);

    StatusBarNotification mediaNotification = getStatusBarNotification(2000);

    assertThat(mediaNotification.getNotification().actions).hasLength(5);
    assertThat(mediaNotification.getNotification().actions[0].title.toString())
        .isEqualTo("Seek to previous item");
    assertThat(mediaNotification.getNotification().actions[1].title.toString()).isEqualTo("Pause");
    assertThat(mediaNotification.getNotification().actions[2].title.toString())
        .isEqualTo("Seek to next item");
    assertThat(mediaNotification.getNotification().actions[3].title.toString())
        .isEqualTo("customAction1");
    assertThat(mediaNotification.getNotification().actions[4].title.toString())
        .isEqualTo("customAction2");

    player.pause();
    session.setCustomLayout(
        session.getMediaNotificationControllerInfo(), ImmutableList.of(button2));
    ShadowLooper.idleMainLooper();
    mediaNotification = getStatusBarNotification(2000);

    assertThat(mediaNotification.getNotification().actions).hasLength(4);
    assertThat(mediaNotification.getNotification().actions[0].title.toString())
        .isEqualTo("Seek to previous item");
    assertThat(mediaNotification.getNotification().actions[1].title.toString()).isEqualTo("Play");
    assertThat(mediaNotification.getNotification().actions[2].title.toString())
        .isEqualTo("Seek to next item");
    assertThat(mediaNotification.getNotification().actions[3].title.toString())
        .isEqualTo("customAction2");
    session.release();
    player.release();
  }

  @Test
  public void mediaNotificationController_setAvailableCommands_correctNotificationActions()
      throws TimeoutException {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    CommandButton button1 =
        new CommandButton.Builder()
            .setDisplayName("customAction1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    CommandButton button2 =
        new CommandButton.Builder()
            .setDisplayName("customAction2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    Context context = ApplicationProvider.getApplicationContext();
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaSession session =
        new MediaSession.Builder(context, player)
            .setId("1")
            .setCustomLayout(ImmutableList.of(button1, button2))
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, MediaSession.ControllerInfo controller) {
                    if (session.isMediaNotificationController(controller)) {
                      return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                          .setAvailableSessionCommands(
                              MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                  .buildUpon()
                                  .add(command2)
                                  .build())
                          .build();
                    }
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).build();
                  }
                })
            .build();
    TestService service = serviceController.create().get();
    service.setMediaNotificationProvider(
        new DefaultMediaNotificationProvider(
            service,
            mediaSession -> 2000,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
            DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID));
    service.addSession(session);
    // Start the players so that we also create notifications for them.
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample.mp4"));
    player.prepare();
    player.play();
    runMainLooperUntil(() -> notificationManager.getActiveNotifications().length == 1);

    StatusBarNotification mediaNotification = getStatusBarNotification(2000);

    assertThat(mediaNotification.getNotification().actions[0].title.toString())
        .isEqualTo("Seek to previous item");
    assertThat(mediaNotification.getNotification().actions[1].title.toString()).isEqualTo("Pause");
    assertThat(mediaNotification.getNotification().actions[2].title.toString())
        .isEqualTo("customAction2");

    player.pause();
    session.setAvailableCommands(
        session.getMediaNotificationControllerInfo(),
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .add(command1)
            .add(command2)
            .build(),
        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS);
    ShadowLooper.idleMainLooper();
    mediaNotification = getStatusBarNotification(2000);

    assertThat(mediaNotification.getNotification().actions).hasLength(4);
    assertThat(mediaNotification.getNotification().actions[0].title.toString())
        .isEqualTo("Seek to previous item");
    assertThat(mediaNotification.getNotification().actions[1].title.toString()).isEqualTo("Play");
    assertThat(mediaNotification.getNotification().actions[2].title.toString())
        .isEqualTo("customAction1");
    assertThat(mediaNotification.getNotification().actions[3].title.toString())
        .isEqualTo("customAction2");

    session.release();
    player.release();
  }

  @Nullable
  private StatusBarNotification getStatusBarNotification(int notificationId) {
    for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
      if (notification.getId() == notificationId) {
        return notification;
      }
    }
    return null;
  }

  private static final class TestService extends MediaSessionService {
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return null; // No need to support binding or pending intents for this test.
    }
  }
}

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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.StatusBarNotification;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class MediaSessionServiceTest {

  private static final int TIMEOUT_MS = 500;

  private Context context;
  private NotificationManager notificationManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  @Test
  public void service_multipleSessionsOnMainThread_createsNotificationForEachSession() {
    ExoPlayer player1 = new TestExoPlayerBuilder(context).build();
    ExoPlayer player2 = new TestExoPlayerBuilder(context).build();
    MediaSession session1 = new MediaSession.Builder(context, player1).setId("1").build();
    MediaSession session2 = new MediaSession.Builder(context, player2).setId("2").build();
    ServiceController<TestService> serviceController = Robolectric.buildService(TestService.class);
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
    serviceController.destroy();
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
    ServiceController<TestService> serviceController = Robolectric.buildService(TestService.class);
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
    serviceController.destroy();
  }

  @Test
  public void mediaNotificationController_setCustomLayout_correctNotificationActions()
      throws TimeoutException {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    SessionCommand command3 = new SessionCommand("command3", Bundle.EMPTY);
    SessionCommand command4 = new SessionCommand("command4", Bundle.EMPTY);
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("customAction1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("customAction2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    CommandButton button3 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("customAction3")
            .setEnabled(false)
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command3)
            .build();
    CommandButton button4 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("customAction4")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command4)
            .build();
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaSession session =
        new MediaSession.Builder(context, player)
            .setCustomLayout(ImmutableList.of(button1, button2, button3, button4))
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
                                  .add(command3)
                                  .build())
                          .build();
                    }
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).build();
                  }
                })
            .build();
    ServiceController<TestService> serviceController = Robolectric.buildService(TestService.class);
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
    serviceController.destroy();
  }

  @Test
  public void mediaNotificationController_setAvailableCommands_correctNotificationActions()
      throws TimeoutException {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("customAction1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
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
    ServiceController<TestService> serviceController = Robolectric.buildService(TestService.class);
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
    serviceController.destroy();
  }

  @Test
  public void onStartCommand_mediaButtonEvent_pausedByMediaNotificationController()
      throws InterruptedException {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    AtomicReference<MediaSession> session = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    ForwardingPlayer forwardingPlayer =
        new ForwardingPlayer(player) {
          @Override
          public void pause() {
            super.pause();
            if (session
                .get()
                .isMediaNotificationController(session.get().getControllerForCurrentRequest())) {
              latch.countDown();
            }
          }
        };
    session.set(new MediaSession.Builder(context, forwardingPlayer).build());
    Intent playIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    playIntent.setData(session.get().getUri());
    playIntent.putExtra(
        Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
    ServiceController<TestService> serviceController =
        Robolectric.buildService(TestService.class, playIntent);
    TestService service = serviceController.create().get();
    service.addSession(session.get());
    player.setMediaItems(ImmutableList.of(MediaItem.fromUri("asset:///media/mp4/sample.mp4")));
    player.play();
    player.prepare();

    serviceController.startCommand(/* flags= */ 0, /* startId= */ 0);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.getPlayWhenReady()).isFalse();
    session.get().release();
    player.release();
    serviceController.destroy();
  }

  @Test
  public void onStartCommand_playbackResumption_calledByMediaNotificationController()
      throws InterruptedException, ExecutionException, TimeoutException {
    Intent playIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    playIntent.putExtra(
        Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
    ServiceController<TestServiceWithPlaybackResumption> serviceController =
        Robolectric.buildService(TestServiceWithPlaybackResumption.class, playIntent);
    TestServiceWithPlaybackResumption service = serviceController.create().get();
    service.setMediaItems(
        ImmutableList.of(
            new MediaItem.Builder()
                .setMediaId("media-id-0")
                .setUri("asset:///media/mp4/sample.mp4")
                .build()));
    MediaController controller =
        new MediaController.Builder(context, service.session.getToken())
            .buildAsync()
            .get(TIMEOUT_MS, MILLISECONDS);
    CountDownLatch latch = new CountDownLatch(1);
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)
                && player.getMediaItemCount() == 1
                && player.getCurrentMediaItem().mediaId.equals("media-id-0")
                && events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                && player.getPlayWhenReady()
                && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                && player.getPlaybackState() == Player.STATE_BUFFERING) {
              latch.countDown();
            }
          }
        });

    serviceController.startCommand(/* flags= */ 0, /* startId= */ 0);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(service.callers).hasSize(1);
    assertThat(service.session.isMediaNotificationController(service.callers.get(0))).isTrue();
    controller.release();
    serviceController.destroy();
  }

  @Test
  public void onStartCommand_customCommands_deliveredByMediaNotificationController()
      throws InterruptedException {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    AtomicReference<MediaSession> sessionRef = new AtomicReference<>();
    SessionCommand expectedCustomCommand = new SessionCommand("enable_shuffle", Bundle.EMPTY);
    CountDownLatch latch = new CountDownLatch(1);
    sessionRef.set(
        new MediaSession.Builder(context, player)
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, MediaSession.ControllerInfo controller) {
                    if (session.getUri().equals(sessionRef.get().getUri())
                        && session.isMediaNotificationController(controller)) {
                      return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                          .setAvailableSessionCommands(
                              new SessionCommands.Builder().add(expectedCustomCommand).build())
                          .build();
                    } else {
                      return MediaSession.ConnectionResult.reject();
                    }
                  }

                  @Override
                  public ListenableFuture<SessionResult> onCustomCommand(
                      MediaSession session,
                      MediaSession.ControllerInfo controller,
                      SessionCommand customCommand,
                      Bundle args) {
                    if (session.getUri().equals(sessionRef.get().getUri())
                        && session.isMediaNotificationController(controller)
                        && customCommand.equals(expectedCustomCommand)
                        && customCommand
                            .customExtras
                            .getString("expectedKey", /* defaultValue= */ "")
                            .equals("expectedValue")
                        && args.isEmpty()) {
                      latch.countDown();
                    }
                    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                  }
                })
            .build());
    MediaSession session = sessionRef.get();
    Intent customCommandIntent = new Intent("androidx.media3.session.CUSTOM_NOTIFICATION_ACTION");
    customCommandIntent.setData(session.getUri());
    customCommandIntent.putExtra(
        "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION", "enable_shuffle");
    Bundle extras = new Bundle();
    extras.putString("expectedKey", "expectedValue");
    customCommandIntent.putExtra(
        "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION_EXTRAS", extras);
    ServiceController<TestService> serviceController =
        Robolectric.buildService(TestService.class, customCommandIntent);
    TestService service = serviceController.create().get();
    service.addSession(session);

    serviceController.startCommand(/* flags= */ 0, /* startId= */ 0);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    session.release();
    player.release();
    serviceController.destroy();
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

  private static final class TestServiceWithPlaybackResumption extends MediaSessionService {

    private final List<MediaSession.ControllerInfo> callers;

    private ImmutableList<MediaItem> mediaItems;
    @Nullable private MediaSession session;

    public TestServiceWithPlaybackResumption() {
      callers = new ArrayList<>();
      mediaItems = ImmutableList.of();
    }

    public void setMediaItems(List<MediaItem> mediaItems) {
      this.mediaItems = ImmutableList.copyOf(mediaItems);
    }

    @Override
    public void onCreate() {
      super.onCreate();
      Context context = ApplicationProvider.getApplicationContext();
      ExoPlayer player = new TestExoPlayerBuilder(context).build();
      ForwardingPlayer forwardingPlayer =
          new ForwardingPlayer(player) {
            @Override
            public void play() {
              callers.add(session.getControllerForCurrentRequest());
              super.play();
            }
          };
      session =
          new MediaSession.Builder(context, forwardingPlayer)
              .setCallback(
                  new MediaSession.Callback() {
                    @Override
                    public ListenableFuture<MediaSession.MediaItemsWithStartPosition>
                        onPlaybackResumption(
                            MediaSession mediaSession, MediaSession.ControllerInfo controller) {
                      // Automatic playback resumption is expected to be called only from the media
                      // notification controller. So we call it here only if the callback is
                      // actually called from the media notification controller (or a fake of it).
                      if (mediaSession.isMediaNotificationController(controller)) {
                        return Futures.immediateFuture(
                            new MediaSession.MediaItemsWithStartPosition(
                                mediaItems,
                                /* startIndex= */ 0,
                                /* startPositionMs= */ C.TIME_UNSET));
                      }
                      return Futures.immediateFailedFuture(new UnsupportedOperationException());
                    }
                  })
              .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return session;
    }

    @Override
    public void onDestroy() {
      if (session != null) {
        session.getPlayer().stop();
        session.getPlayer().clearMediaItems();
        session.getPlayer().release();
        session.release();
        callers.clear();
        session = null;
      }
      super.onDestroy();
    }
  }
}

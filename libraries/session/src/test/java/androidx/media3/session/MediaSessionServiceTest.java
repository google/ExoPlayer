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
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.stream;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.StatusBarNotification;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class MediaSessionServiceTest {

  @Test
  public void service_multipleSessionsOnMainThread_createsNotificationForEachSession() {
    Context context = ApplicationProvider.getApplicationContext();
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

    NotificationManager notificationService =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    assertThat(
            stream(notificationService.getActiveNotifications()).map(StatusBarNotification::getId))
        .containsExactly(2001, 2002);

    serviceController.destroy();
    session1.release();
    session2.release();
    player1.release();
    player2.release();
  }

  @Test
  public void service_multipleSessionsOnDifferentThreads_createsNotificationForEachSession()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
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
    NotificationManager notificationService =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

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
    runMainLooperUntil(() -> notificationService.getActiveNotifications().length == 2);

    assertThat(
            stream(notificationService.getActiveNotifications()).map(StatusBarNotification::getId))
        .containsExactly(2001, 2002);

    serviceController.destroy();
    session1.release();
    session2.release();
    new Handler(thread1.getLooper()).post(player1::release);
    new Handler(thread2.getLooper()).post(player2::release);
    thread1.quit();
    thread2.quit();
  }

  private static final class TestService extends MediaSessionService {
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return null; // No need to support binding or pending intents for this test.
    }
  }
}

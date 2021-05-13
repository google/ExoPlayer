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

import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_SESSION_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.session.MediaLibraryService.MediaLibrarySession.MediaLibrarySessionCallback;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.R;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Manual test of {@link MediaSessionService} for showing/removing notification when the playback is
 * started/ended.
 *
 * <p>This test is a manual test, which means the one who runs this test should keep looking at the
 * device and check whether the notification is shown/removed.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionServiceNotificationTest {
  private static final long NOTIFICATION_SHOW_TIME_MS = 15000;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionServiceNotificationTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  @Ignore("Comment out this line and manually run the test.")
  public void notification() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySessionCallback sessionCallback =
        new MediaLibrarySessionCallback() {
          @Nullable
          @Override
          public MediaSession.ConnectResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
              player =
                  new MockPlayer.Builder()
                      .setApplicationLooper(Looper.myLooper())
                      .setChangePlayerStateWithTransportControl(true)
                      .build();
              session.setPlayer(player);
              latch.countDown();
            }
            return super.onConnect(session, controller);
          }
        };
    TestServiceRegistry.getInstance().setSessionCallback(sessionCallback);

    // Create a controller to start the service.
    controllerTestRule.createRemoteController(
        new SessionToken(context, MOCK_MEDIA2_SESSION_SERVICE),
        /* waitForConnection= */ true,
        /* connectionHints= */ null);
    assertThat(latch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();

    TestHandler handler = new TestHandler(player.getApplicationLooper());
    handler.postAndSync(
        () -> {
          // Set current media item.
          String mediaId = "testMediaId";
          String title = "Test Song Name";
          Bitmap albumArt =
              BitmapFactory.decodeResource(context.getResources(), R.drawable.big_buck_bunny);
          // TODO(b/180293668): Set artist, album art, browsable type, playable.
          MediaMetadata metadata = new MediaMetadata.Builder().setTitle(title).build();
          player.currentMediaItem =
              new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build();

          // Notification should be shown. Clicking play/pause button will change the player state.
          // When playing, the notification will not be removed by swiping horizontally.
          // When paused, the notification can be swiped away.
          player.notifyPlaybackStateChanged(STATE_READY);
        });
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);
  }

  @Test
  @Ignore("Comment out this line and manually run the test.")
  public void notificationUpdatedWhenCurrentMediaItemChanged() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaLibrarySessionCallback sessionCallback =
        new MediaLibrarySessionCallback() {
          @Nullable
          @Override
          public MediaSession.ConnectResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
              session.setPlayer(player);
              latch.countDown();
            }
            return super.onConnect(session, controller);
          }
        };
    TestServiceRegistry.getInstance().setSessionCallback(sessionCallback);

    // Create a controller to start the service.
    controllerTestRule.createRemoteController(
        new SessionToken(context, MOCK_MEDIA2_SESSION_SERVICE),
        /* waitForConnection= */ true,
        /* connectionHints= */ null);

    // Set current media item.
    Bitmap albumArt =
        BitmapFactory.decodeResource(context.getResources(), R.drawable.big_buck_bunny);
    // TODO(b/180293668): Set artist, album art, browsable type, playable.
    MediaMetadata metadata = new MediaMetadata.Builder().setTitle("Test Song Name").build();
    player.currentMediaItem =
        new MediaItem.Builder().setMediaId("testMediaId").setMediaMetadata(metadata).build();

    player.notifyPlaybackStateChanged(STATE_READY);
    // At this point, the notification should be shown.
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);

    // Set a new media item. (current media item is changed)
    // TODO(b/180293668): Set artist, album art, browsable type, playable.
    MediaMetadata newMetadata = new MediaMetadata.Builder().setTitle("New Song Name").build();

    MediaItem newItem =
        new MediaItem.Builder().setMediaId("New media ID").setMediaMetadata(newMetadata).build();
    player.currentMediaItem = newItem;

    // Calling this should update the notification with the new metadata.
    player.notifyMediaItemTransition(newItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);
  }
}

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

import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;

import android.content.ComponentName;
import android.content.Context;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.R;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.io.IOException;
import java.io.InputStream;
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

  private static final long NOTIFICATION_SHOW_TIME_MS = 15_000;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionServiceNotificationTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private MockPlayer player;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();

    MediaLibrarySession.Callback sessionCallback =
        new MediaLibrarySession.Callback() {
          @Override
          public MediaSession.ConnectionResult onConnect(
              MediaSession session, ControllerInfo controller) {
            if (player == null) {
              player =
                  new MockPlayer.Builder().setChangePlayerStateWithTransportControl(true).build();
              session.setPlayer(player);
            }
            if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
              return MediaLibrarySession.Callback.super.onConnect(session, controller);
            } else {
              return MediaSession.ConnectionResult.reject();
            }
          }
        };
    TestServiceRegistry.getInstance().setSessionCallback(sessionCallback);

    controllerTestRule.createRemoteController(
        new SessionToken(context, new ComponentName(context, LocalMockMediaSessionService.class)),
        /* waitForConnection= */ true,
        /* connectionHints= */ null);
  }

  @Test
  @Ignore("Comment out this line and manually run the test.")
  public void notification() throws Exception {
    TestHandler handler = new TestHandler(player.getApplicationLooper());
    handler.postAndSync(
        () -> {
          player.mediaMetadata = createTestMediaMetadata();
          player.notifyMediaMetadataChanged();
        });
    // Notification should be shown.
    // Clicking play/pause button will change the player state.
    //   When playing, the notification will not be removed by swiping horizontally.
    //   When paused, the notification can be swiped away.
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);
  }

  @Test
  @Ignore("Comment out this line and manually run the test.")
  public void notificationUpdatedWhenMediaMetadataChanged() throws Exception {
    TestHandler handler = new TestHandler(player.getApplicationLooper());
    handler.postAndSync(
        () -> {
          player.mediaMetadata = createTestMediaMetadata();
          player.notifyMediaMetadataChanged();
        });
    // At this point, the notification should be shown.
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);
    // Update media metadata.
    handler.postAndSync(
        () -> {
          player.mediaMetadata = createAnotherTestMediaMetadata();
          player.notifyMediaMetadataChanged();
        });
    // Notification should be updated.
    Thread.sleep(NOTIFICATION_SHOW_TIME_MS);
  }

  private MediaMetadata createTestMediaMetadata() throws IOException {
    byte[] artworkData;
    try (InputStream stream = context.getResources().openRawResource(R.drawable.big_buck_bunny)) {
      artworkData = Util.toByteArray(stream);
    }
    return new MediaMetadata.Builder()
        .setTitle("Test Song Name")
        .setArtist("Test Artist Name")
        .setArtworkData(artworkData)
        .setIsPlayable(true)
        .build();
  }

  private MediaMetadata createAnotherTestMediaMetadata() {
    return new MediaMetadata.Builder()
        .setTitle("New Song Name")
        .setArtist("New Artist Name")
        .setIsPlayable(true)
        .build();
  }
}

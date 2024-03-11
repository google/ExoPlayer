/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowPendingIntent;

/** Tests for {@link DefaultActionFactory}. */
@RunWith(AndroidJUnit4.class)
public class DefaultActionFactoryTest {

  private Player player;
  private MediaSession mediaSession;

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    player = new TestExoPlayerBuilder(context).build();
    mediaSession = new MediaSession.Builder(context, player).build();
  }

  @After
  public void tearDown() {
    mediaSession.release();
    player.release();
  }

  @Test
  public void createMediaPendingIntent_intentIsMediaAction() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    PendingIntent pendingIntent =
        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_FORWARD);

    ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);
    assertThat(actionFactory.isMediaAction(shadowPendingIntent.getSavedIntent())).isTrue();
    assertThat(shadowPendingIntent.getSavedIntent().getData()).isEqualTo(mediaSession.getUri());
  }

  @Test
  public void createMediaPendingIntent_commandPlayPauseWhenNotPlayWhenReady_isForegroundService() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    PendingIntent pendingIntent =
        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE);

    ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);
    assertThat(shadowPendingIntent.isForegroundService()).isTrue();
  }

  @Test
  public void createMediaPendingIntent_commandPlayPauseWhenPlayWhenReady_notAForegroundService() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    player.play();
    PendingIntent pendingIntent =
        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE);

    ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);
    assertThat(actionFactory.isMediaAction(shadowPendingIntent.getSavedIntent())).isTrue();
    assertThat(shadowPendingIntent.isForegroundService()).isFalse();
  }

  @Test
  public void isMediaAction_withNonMediaIntent_returnsFalse() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    Intent intent = new Intent("invalid_action");

    assertThat(actionFactory.isMediaAction(intent)).isFalse();
  }

  @Test
  public void isCustomAction_withNonCustomActionIntent_returnsFalse() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));

    Intent intent = new Intent("invalid_action");

    assertThat(actionFactory.isCustomAction(intent)).isFalse();
  }

  @Test
  public void createCustomActionFromCustomCommandButton() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    Bundle commandBundle = new Bundle();
    commandBundle.putString("command-key", "command-value");
    Bundle buttonBundle = new Bundle();
    buttonBundle.putString("button-key", "button-value");
    CommandButton customSessionCommand =
        new CommandButton.Builder(CommandButton.ICON_PAUSE)
            .setSessionCommand(new SessionCommand("a", commandBundle))
            .setExtras(buttonBundle)
            .setDisplayName("name")
            .build();

    NotificationCompat.Action notificationAction =
        actionFactory.createCustomActionFromCustomCommandButton(mediaSession, customSessionCommand);

    ShadowPendingIntent shadowPendingIntent = shadowOf(notificationAction.actionIntent);
    assertThat(shadowPendingIntent.getSavedIntent().getData()).isEqualTo(mediaSession.getUri());
    assertThat(String.valueOf(notificationAction.title)).isEqualTo("name");
    assertThat(notificationAction.getIconCompat().getResId())
        .isEqualTo(R.drawable.media3_icon_pause);
    assertThat(notificationAction.getExtras().size()).isEqualTo(0);
    assertThat(notificationAction.getActionIntent()).isNotNull();
  }

  @Test
  public void
      createCustomActionFromCustomCommandButton_notACustomAction_throwsIllegalArgumentException() {
    DefaultActionFactory actionFactory =
        new DefaultActionFactory(Robolectric.setupService(TestService.class));
    CommandButton customSessionCommand =
        new CommandButton.Builder(CommandButton.ICON_PAUSE)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName("name")
            .build();

    Assert.assertThrows(
        IllegalArgumentException.class,
        () ->
            actionFactory.createCustomActionFromCustomCommandButton(
                mediaSession, customSessionCommand));
  }

  /** A test service for unit tests. */
  public static final class TestService extends MediaLibraryService {
    @Nullable
    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
      return null;
    }
  }
}

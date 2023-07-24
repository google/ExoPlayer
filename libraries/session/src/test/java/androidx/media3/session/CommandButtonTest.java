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

import static androidx.media3.session.CommandButton.CREATOR;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Bundle;
import androidx.media3.common.Player;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CommandButton}. */
@RunWith(AndroidJUnit4.class)
public class CommandButtonTest {

  @Test
  public void isEnabled_playerCommandAvailableOrUnavailableInPlayerCommands_isEnabledCorrectly() {
    CommandButton button =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build();
    Player.Commands availablePlayerCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).build();

    assertThat(CommandButton.isEnabled(button, SessionCommands.EMPTY, Player.Commands.EMPTY))
        .isFalse();
    assertThat(CommandButton.isEnabled(button, SessionCommands.EMPTY, availablePlayerCommands))
        .isTrue();
  }

  @Test
  public void isEnabled_playerCommandAvailableOrUnavailableInSessionCommands_isEnabledCorrectly() {
    CommandButton button =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build();
    SessionCommands availableSessionCommands =
        SessionCommands.EMPTY.buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).build();

    assertThat(CommandButton.isEnabled(button, SessionCommands.EMPTY, Player.Commands.EMPTY))
        .isFalse();
    assertThat(CommandButton.isEnabled(button, availableSessionCommands, Player.Commands.EMPTY))
        .isTrue();
  }

  @Test
  public void isEnabled_sessionCommandAvailableOrUnavailable_isEnabledCorrectly() {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    CommandButton button =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    SessionCommands availableSessionCommands =
        SessionCommands.EMPTY.buildUpon().add(command1).build();

    assertThat(CommandButton.isEnabled(button, SessionCommands.EMPTY, Player.Commands.EMPTY))
        .isFalse();
    assertThat(CommandButton.isEnabled(button, availableSessionCommands, Player.Commands.EMPTY))
        .isTrue();
  }

  @Test
  public void getEnabledCommandButtons() {
    CommandButton button1 =
        new CommandButton.Builder()
            .setDisplayName("button1")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    CommandButton button2 =
        new CommandButton.Builder()
            .setDisplayName("button2")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    SessionCommands availableSessionCommands =
        SessionCommands.EMPTY.buildUpon().add(command2).build();
    Player.Commands availablePlayerCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_SEEK_TO_PREVIOUS).build();

    assertThat(
            CommandButton.getEnabledCommandButtons(
                ImmutableList.of(button1, button2), SessionCommands.EMPTY, Player.Commands.EMPTY))
        .containsExactly(button1, button2);
    assertThat(
            CommandButton.getEnabledCommandButtons(
                ImmutableList.of(button1, button2),
                availableSessionCommands,
                availablePlayerCommands))
        .containsExactly(button1.copyWithIsEnabled(true), button2.copyWithIsEnabled(true));
  }

  @Test
  public void equals() {
    assertThat(
            new CommandButton.Builder()
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build())
        .isEqualTo(
            new CommandButton.Builder()
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build());
  }

  @Test
  public void equals_minimalDifference_notEqual() {
    CommandButton button =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build();

    assertThat(button).isEqualTo(CREATOR.fromBundle(button.toBundle()));
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder()
                .setDisplayName("button2")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder()
                .setIconResId(R.drawable.media3_notification_play)
                .setDisplayName("button")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder()
                .setEnabled(true)
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand(Player.COMMAND_PLAY_PAUSE))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .build());
  }

  @Test
  public void equals_differenceInExtras_ignored() {
    CommandButton.Builder builder =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setIconResId(R.drawable.media3_notification_small_icon);
    CommandButton button1 = builder.build();
    Bundle extras2 = new Bundle();
    extras2.putInt("something", 0);
    Bundle extras3 = new Bundle();
    extras3.putInt("something", 1);
    extras3.putInt("something2", 2);

    assertThat(button1).isEqualTo(builder.setExtras(extras2).build());
    assertThat(builder.setExtras(extras2).build()).isEqualTo(builder.setExtras(extras3).build());
  }

  @Test
  public void equals_differencesInSessionCommand_notEqual() {
    assertThat(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand(Player.COMMAND_PLAY_PAUSE))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build())
        .isNotEqualTo(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand(Player.COMMAND_SEEK_BACK))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build());
    assertThat(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand(Player.COMMAND_PLAY_PAUSE))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build())
        .isNotEqualTo(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build());
    assertThat(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build())
        .isNotEqualTo(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand("customAction2", Bundle.EMPTY))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build());
  }

  @Test
  public void equals_differenceInSessionCommandExtras_ignored() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    assertThat(
            new CommandButton.Builder()
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build())
        .isEqualTo(
            new CommandButton.Builder()
                .setExtras(extras)
                .setSessionCommand(new SessionCommand("customAction", extras))
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_play)
                .build());
  }

  @Test
  public void hashCode_equalButtons_sameHashcode() {
    assertThat(
            new CommandButton.Builder()
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode())
        .isEqualTo(
            new CommandButton.Builder()
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode());
    assertThat(
            new CommandButton.Builder()
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode())
        .isNotEqualTo(
            new CommandButton.Builder()
                .setDisplayName("button")
                .setIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode());
  }

  @Test
  public void build_withoutSessionOrPlayerCommandSet_throwsIllegalStateException() {
    CommandButton.Builder builder =
        new CommandButton.Builder()
            .setDisplayName("button")
            .setIconResId(R.drawable.media3_notification_small_icon);
    assertThrows(IllegalStateException.class, builder::build);
  }
}

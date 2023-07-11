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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.List;

/**
 * A button for a {@link SessionCommand} or {@link Player.Command} that can be displayed by
 * controllers.
 *
 * @see MediaSession#setCustomLayout(MediaSession.ControllerInfo, List)
 * @see MediaController.Listener#onCustomLayoutChanged(MediaController, List)
 */
public final class CommandButton implements Bundleable {

  /** A builder for {@link CommandButton}. */
  public static final class Builder {

    @Nullable private SessionCommand sessionCommand;
    private @Player.Command int playerCommand;
    @DrawableRes private int iconResId;
    private CharSequence displayName;
    private Bundle extras;
    private boolean enabled;

    /** Creates a builder. */
    public Builder() {
      displayName = "";
      extras = Bundle.EMPTY;
      playerCommand = Player.COMMAND_INVALID;
    }

    /**
     * Sets the {@link SessionCommand} that will be sent to the session when the button is clicked.
     * Cannot set this if player command is already set via {@link #setPlayerCommand(int)}.
     *
     * @param sessionCommand The session command.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setSessionCommand(SessionCommand sessionCommand) {
      checkNotNull(sessionCommand, "sessionCommand should not be null.");
      checkArgument(
          playerCommand == Player.COMMAND_INVALID,
          "playerCommands is already set. Only one of sessionCommand and playerCommand should be"
              + " set.");
      this.sessionCommand = sessionCommand;
      return this;
    }

    /**
     * Sets the {@link Player.Command} that would be sent to the session when the button is clicked.
     * Cannot set this if session command is already set via {@link
     * #setSessionCommand(SessionCommand)}.
     *
     * @param playerCommand The player command.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setPlayerCommand(@Player.Command int playerCommand) {
      checkArgument(
          sessionCommand == null,
          "sessionCommand is already set. Only one of sessionCommand and playerCommand should be"
              + " set.");
      this.playerCommand = playerCommand;
      return this;
    }

    /**
     * Sets the resource id of a bitmap (e.g. PNG) icon of this button.
     *
     * <p>Non-bitmap (e.g. VectorDrawable) may cause unexpected behavior in a {@link
     * MediaController} app, so please avoid using it especially for the older platforms ({@code
     * SDK_INT < 21}).
     *
     * @param resId The resource id of an icon.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setIconResId(@DrawableRes int resId) {
      iconResId = resId;
      return this;
    }

    /**
     * Sets a display name of this button.
     *
     * @param displayName The display name.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setDisplayName(CharSequence displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * Sets whether the button is enabled.
     *
     * @param enabled Whether the button is enabled.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Sets an extra {@link Bundle} of this button.
     *
     * @param extras The extra {@link Bundle}.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder setExtras(Bundle extras) {
      this.extras = new Bundle(extras);
      return this;
    }

    /** Builds a {@link CommandButton}. */
    public CommandButton build() {
      return new CommandButton(
          sessionCommand, playerCommand, iconResId, displayName, extras, enabled);
    }
  }

  /** The session command of the button. Can be {@code null} if the button is a placeholder. */
  @Nullable public final SessionCommand sessionCommand;

  /**
   * The {@link Player.Command} command of the button. Can be {@link Player#COMMAND_INVALID} if the
   * button is a placeholder.
   */
  public final @Player.Command int playerCommand;

  /**
   * The icon resource id of the button. Can be {@code 0} if the command is predefined and a custom
   * icon isn't needed.
   */
  @DrawableRes public final int iconResId;

  /**
   * The display name of the button. Can be empty if the command is predefined and a custom name
   * isn't needed.
   */
  public final CharSequence displayName;

  /**
   * The extra {@link Bundle} of the button. It's private information between session and
   * controller.
   */
  @UnstableApi public final Bundle extras;

  /** Whether it's enabled. */
  public final boolean isEnabled;

  private CommandButton(
      @Nullable SessionCommand sessionCommand,
      @Player.Command int playerCommand,
      @DrawableRes int iconResId,
      CharSequence displayName,
      Bundle extras,
      boolean enabled) {
    this.sessionCommand = sessionCommand;
    this.playerCommand = playerCommand;
    this.iconResId = iconResId;
    this.displayName = displayName;
    this.extras = new Bundle(extras);
    this.isEnabled = enabled;
  }

  /** Returns a copy with the new {@link #isEnabled} flag. */
  @CheckReturnValue
  /* package */ CommandButton copyWithIsEnabled(boolean isEnabled) {
    // Because this method is supposed to be used by the library only, this method has been chosen
    // over the conventional `buildUpon` approach. This aims for keeping this separate from the
    // public Builder-API used by apps.
    if (this.isEnabled == isEnabled) {
      return this;
    }
    return new CommandButton(
        sessionCommand, playerCommand, iconResId, displayName, new Bundle(extras), isEnabled);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CommandButton)) {
      return false;
    }
    CommandButton button = (CommandButton) obj;
    return Objects.equal(sessionCommand, button.sessionCommand)
        && playerCommand == button.playerCommand
        && iconResId == button.iconResId
        && TextUtils.equals(displayName, button.displayName)
        && isEnabled == button.isEnabled;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sessionCommand, playerCommand, iconResId, displayName, isEnabled);
  }

  // Bundleable implementation.

  private static final String FIELD_SESSION_COMMAND = Util.intToStringMaxRadix(0);
  private static final String FIELD_PLAYER_COMMAND = Util.intToStringMaxRadix(1);
  private static final String FIELD_ICON_RES_ID = Util.intToStringMaxRadix(2);
  private static final String FIELD_DISPLAY_NAME = Util.intToStringMaxRadix(3);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(4);
  private static final String FIELD_ENABLED = Util.intToStringMaxRadix(5);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (sessionCommand != null) {
      bundle.putBundle(FIELD_SESSION_COMMAND, sessionCommand.toBundle());
    }
    bundle.putInt(FIELD_PLAYER_COMMAND, playerCommand);
    bundle.putInt(FIELD_ICON_RES_ID, iconResId);
    bundle.putCharSequence(FIELD_DISPLAY_NAME, displayName);
    bundle.putBundle(FIELD_EXTRAS, extras);
    bundle.putBoolean(FIELD_ENABLED, isEnabled);
    return bundle;
  }

  /** Object that can restore {@link CommandButton} from a {@link Bundle}. */
  @UnstableApi public static final Creator<CommandButton> CREATOR = CommandButton::fromBundle;

  private static CommandButton fromBundle(Bundle bundle) {
    @Nullable Bundle sessionCommandBundle = bundle.getBundle(FIELD_SESSION_COMMAND);
    @Nullable
    SessionCommand sessionCommand =
        sessionCommandBundle == null
            ? null
            : SessionCommand.CREATOR.fromBundle(sessionCommandBundle);
    @Player.Command
    int playerCommand =
        bundle.getInt(FIELD_PLAYER_COMMAND, /* defaultValue= */ Player.COMMAND_INVALID);
    int iconResId = bundle.getInt(FIELD_ICON_RES_ID, /* defaultValue= */ 0);
    CharSequence displayName = bundle.getCharSequence(FIELD_DISPLAY_NAME, /* defaultValue= */ "");
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    boolean enabled = bundle.getBoolean(FIELD_ENABLED, /* defaultValue= */ false);
    Builder builder = new Builder();
    if (sessionCommand != null) {
      builder.setSessionCommand(sessionCommand);
    }
    if (playerCommand != Player.COMMAND_INVALID) {
      builder.setPlayerCommand(playerCommand);
    }
    return builder
        .setIconResId(iconResId)
        .setDisplayName(displayName)
        .setExtras(extras == null ? Bundle.EMPTY : extras)
        .setEnabled(enabled)
        .build();
  }
}

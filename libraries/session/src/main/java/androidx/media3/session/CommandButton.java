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
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.Player;
import androidx.media3.common.util.BundleableUtil;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * A button for a {@link SessionCommand} or {@link Player.Command} that can be displayed by
 * controllers.
 *
 * @see MediaSession#setCustomLayout(MediaSession.ControllerInfo, List)
 * @see MediaController.Listener#onSetCustomLayout(MediaController, List)
 */
public final class CommandButton implements Bundleable {

  /** A builder for {@link CommandButton}. */
  public static final class Builder {

    @Nullable private SessionCommand sessionCommand;
    @Player.Command private int playerCommand;
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
  @Player.Command public final int playerCommand;

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

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_SESSION_COMMAND,
    FIELD_PLAYER_COMMAND,
    FIELD_ICON_RES_ID,
    FIELD_DISPLAY_NAME,
    FIELD_EXTRAS,
    FIELD_ENABLED
  })
  private @interface FieldNumber {}

  private static final int FIELD_SESSION_COMMAND = 0;
  private static final int FIELD_PLAYER_COMMAND = 1;
  private static final int FIELD_ICON_RES_ID = 2;
  private static final int FIELD_DISPLAY_NAME = 3;
  private static final int FIELD_EXTRAS = 4;
  private static final int FIELD_ENABLED = 5;

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putBundle(
        keyForField(FIELD_SESSION_COMMAND), BundleableUtil.toNullableBundle(sessionCommand));
    bundle.putInt(keyForField(FIELD_PLAYER_COMMAND), playerCommand);
    bundle.putInt(keyForField(FIELD_ICON_RES_ID), iconResId);
    bundle.putCharSequence(keyForField(FIELD_DISPLAY_NAME), displayName);
    bundle.putBundle(keyForField(FIELD_EXTRAS), extras);
    bundle.putBoolean(keyForField(FIELD_ENABLED), isEnabled);
    return bundle;
  }

  /** Object that can restore {@link CommandButton} from a {@link Bundle}. */
  @UnstableApi public static final Creator<CommandButton> CREATOR = CommandButton::fromBundle;

  private static CommandButton fromBundle(Bundle bundle) {
    @Nullable
    SessionCommand sessionCommand =
        BundleableUtil.fromNullableBundle(
            SessionCommand.CREATOR, bundle.getBundle(keyForField(FIELD_SESSION_COMMAND)));
    @Player.Command
    int playerCommand =
        bundle.getInt(
            keyForField(FIELD_PLAYER_COMMAND), /* defaultValue= */ Player.COMMAND_INVALID);
    int iconResId = bundle.getInt(keyForField(FIELD_ICON_RES_ID), /* defaultValue= */ 0);
    CharSequence displayName =
        bundle.getCharSequence(keyForField(FIELD_DISPLAY_NAME), /* defaultValue= */ "");
    @Nullable Bundle extras = bundle.getBundle(keyForField(FIELD_EXTRAS));
    boolean enabled = bundle.getBoolean(keyForField(FIELD_ENABLED), /* defaultValue= */ false);
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

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

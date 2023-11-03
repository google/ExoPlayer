/*
 * Copyright 2019 The Android Open Source Project
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
import static androidx.media3.session.SessionCommand.COMMAND_CODE_CUSTOM;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media3.common.Bundleable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.SessionCommand.CommandCode;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A set of {@link SessionCommand session commands}. */
public final class SessionCommands implements Bundleable {

  private static final String TAG = "SessionCommands";

  /** A builder for {@link SessionCommands}. */
  public static final class Builder {

    private final Set<SessionCommand> commands;

    /** Creates a new builder. */
    public Builder() {
      commands = new HashSet<>();
    }

    /** Creates a new builder from another {@link SessionCommands}. */
    private Builder(SessionCommands sessionCommands) {
      this.commands = new HashSet<>(checkNotNull(sessionCommands).commands);
    }

    /**
     * Adds a command.
     *
     * @param command A command to add.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder add(SessionCommand command) {
      commands.add(checkNotNull(command));
      return this;
    }

    /**
     * Adds a command with command code. Command code must not be {@link
     * SessionCommand#COMMAND_CODE_CUSTOM}.
     *
     * @param commandCode A command code to build command and add.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder add(@CommandCode int commandCode) {
      checkArgument(commandCode != COMMAND_CODE_CUSTOM);
      commands.add(new SessionCommand(commandCode));
      return this;
    }

    /**
     * Adds all of the commands in the specified collection.
     *
     * @param commands collection containing elements to be added to this set
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder addSessionCommands(Collection<SessionCommand> commands) {
      this.commands.addAll(commands);
      return this;
    }

    /**
     * Removes a command which matches a given {@link SessionCommand command}.
     *
     * @param command A command to find.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder remove(SessionCommand command) {
      commands.remove(checkNotNull(command));
      return this;
    }

    /**
     * Removes a command which matches a given {@code command code}. Command code must not be {@link
     * SessionCommand#COMMAND_CODE_CUSTOM}.
     *
     * @param commandCode A command code to find.
     * @return This builder for chaining.
     */
    @CanIgnoreReturnValue
    public Builder remove(@CommandCode int commandCode) {
      checkArgument(commandCode != COMMAND_CODE_CUSTOM);
      for (SessionCommand command : commands) {
        if (command.commandCode == commandCode) {
          commands.remove(command);
          break;
        }
      }
      return this;
    }

    /**
     * Adds all session commands.
     *
     * @return This builder for chaining.
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllSessionCommands() {
      addCommandCodes(SessionCommand.SESSION_COMMANDS);
      return this;
    }

    /**
     * Adds all library commands.
     *
     * @return This builder for chaining.
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllLibraryCommands() {
      addCommandCodes(SessionCommand.LIBRARY_COMMANDS);
      return this;
    }

    /**
     * Adds all predefined commands.
     *
     * @return This builder for chaining.
     */
    /* package */ @CanIgnoreReturnValue
    Builder addAllPredefinedCommands() {
      addAllSessionCommands();
      addAllLibraryCommands();
      return this;
    }

    private void addCommandCodes(List<@CommandCode Integer> commandCodes) {
      for (int i = 0; i < commandCodes.size(); i++) {
        add(new SessionCommand(commandCodes.get(i)));
      }
    }

    /** Builds a {@link SessionCommands}. */
    public SessionCommands build() {
      return new SessionCommands(commands);
    }
  }

  /** An empty set of session commands. */
  public static final SessionCommands EMPTY = new Builder().build();

  /** All session commands. */
  public final ImmutableSet<SessionCommand> commands;

  /**
   * Creates a new set of session commands.
   *
   * @param sessionCommands The collection of session commands to copy.
   */
  private SessionCommands(Collection<SessionCommand> sessionCommands) {
    this.commands = ImmutableSet.copyOf(sessionCommands);
  }

  /**
   * Returns whether a command that matches given {@code command} exists.
   *
   * @param command A command to find.
   * @return Whether the command exists.
   */
  public boolean contains(SessionCommand command) {
    return commands.contains(checkNotNull(command));
  }

  /**
   * Returns whether a command that matches given {@code commandCode} exists.
   *
   * @param commandCode A {@link SessionCommand.CommandCode} command code to find. Shouldn't be
   *     {@link SessionCommand#COMMAND_CODE_CUSTOM}.
   * @return Whether the command exists.
   */
  public boolean contains(@CommandCode int commandCode) {
    checkArgument(commandCode != COMMAND_CODE_CUSTOM, "Use contains(Command) for custom command");
    return containsCommandCode(commands, commandCode);
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SessionCommands)) {
      return false;
    }

    SessionCommands that = (SessionCommands) obj;
    return commands.equals(that.commands);
  }

  @Override
  public int hashCode() {
    return ObjectsCompat.hash(commands);
  }

  private static boolean containsCommandCode(
      Collection<SessionCommand> commands, @CommandCode int commandCode) {
    for (SessionCommand command : commands) {
      if (command.commandCode == commandCode) {
        return true;
      }
    }
    return false;
  }

  // Bundleable implementation.

  private static final String FIELD_SESSION_COMMANDS = Util.intToStringMaxRadix(0);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    ArrayList<Bundle> sessionCommandBundleList = new ArrayList<>();
    for (SessionCommand command : commands) {
      sessionCommandBundleList.add(command.toBundle());
    }
    bundle.putParcelableArrayList(FIELD_SESSION_COMMANDS, sessionCommandBundleList);
    return bundle;
  }

  /**
   * Object that can restore {@link SessionCommands} from a {@link Bundle}.
   *
   * @deprecated Use {@link #fromBundle} instead.
   */
  @UnstableApi
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<SessionCommands> CREATOR = SessionCommands::fromBundle;

  /** Restores a {@code SessionCommands} from a {@link Bundle}. */
  @UnstableApi
  public static SessionCommands fromBundle(Bundle bundle) {
    @Nullable
    ArrayList<Bundle> sessionCommandBundleList =
        bundle.getParcelableArrayList(FIELD_SESSION_COMMANDS);
    if (sessionCommandBundleList == null) {
      Log.w(TAG, "Missing commands. Creating an empty SessionCommands");
      return SessionCommands.EMPTY;
    }

    Builder builder = new Builder();
    for (int i = 0; i < sessionCommandBundleList.size(); i++) {
      builder.add(SessionCommand.fromBundle(sessionCommandBundleList.get(i)));
    }
    return builder.build();
  }
  ;
}

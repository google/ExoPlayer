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
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.Bundleable;
import androidx.media3.common.Rating;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A command that a {@link MediaController} can send to a {@link MediaSession}.
 *
 * <p>If {@link #commandCode} isn't {@link #COMMAND_CODE_CUSTOM}, it's a predefined command. If
 * {@link #commandCode} is {@link #COMMAND_CODE_CUSTOM}, it's a custom command and {@link
 * #customAction} must not be {@code null}.
 */
public final class SessionCommand implements Bundleable {

  /** Command codes of session commands. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    COMMAND_CODE_CUSTOM,
    COMMAND_CODE_SESSION_SET_RATING,
    COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
    COMMAND_CODE_LIBRARY_SUBSCRIBE,
    COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
    COMMAND_CODE_LIBRARY_GET_CHILDREN,
    COMMAND_CODE_LIBRARY_GET_ITEM,
    COMMAND_CODE_LIBRARY_SEARCH,
    COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT
  })
  public @interface CommandCode {}

  /**
   * Command code for the custom command which can be defined by string action in the {@link
   * SessionCommand}.
   */
  public static final int COMMAND_CODE_CUSTOM = 0;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Session commands (i.e. commands to MediaSession.Callback)
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Command code for {@link MediaController#setRating(String, Rating)}. */
  public static final int COMMAND_CODE_SESSION_SET_RATING = 40010;

  /* package */ static final ImmutableList<Integer> SESSION_COMMANDS =
      ImmutableList.of(COMMAND_CODE_SESSION_SET_RATING);

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Library commands (i.e. commands to MediaLibraryService.MediaLibrarySession.Callback)
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Command code for {@link MediaBrowser#getLibraryRoot(LibraryParams)}. */
  public static final int COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT = 50000;

  /** Command code for {@link MediaBrowser#subscribe(String, LibraryParams)}. */
  public static final int COMMAND_CODE_LIBRARY_SUBSCRIBE = 50001;

  /** Command code for {@link MediaBrowser#unsubscribe(String)}. */
  public static final int COMMAND_CODE_LIBRARY_UNSUBSCRIBE = 50002;

  /** Command code for {@link MediaBrowser#getChildren(String, int, int, LibraryParams)}. */
  public static final int COMMAND_CODE_LIBRARY_GET_CHILDREN = 50003;

  /** Command code for {@link MediaBrowser#getItem(String)}. */
  public static final int COMMAND_CODE_LIBRARY_GET_ITEM = 50004;

  /** Command code for {@link MediaBrowser#search(String, LibraryParams)}. */
  public static final int COMMAND_CODE_LIBRARY_SEARCH = 50005;

  /** Command code for {@link MediaBrowser#getSearchResult(String, int, int, LibraryParams)}. */
  public static final int COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT = 50006;

  /* package */ static final ImmutableList<Integer> LIBRARY_COMMANDS =
      ImmutableList.of(
          COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
          COMMAND_CODE_LIBRARY_SUBSCRIBE,
          COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
          COMMAND_CODE_LIBRARY_GET_CHILDREN,
          COMMAND_CODE_LIBRARY_GET_ITEM,
          COMMAND_CODE_LIBRARY_SEARCH,
          COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT);

  /**
   * The command code of a predefined command. It will be {@link #COMMAND_CODE_CUSTOM} for a custom
   * command.
   */
  public final @CommandCode int commandCode;

  /** The action of a custom command. It will be an empty string for a predefined command. */
  public final String customAction;

  /**
   * The extra bundle of a custom command. It will be {@link Bundle#EMPTY} for a predefined command.
   *
   * <p>Interoperability: This value is not used when the command is sent to a legacy {@link
   * android.support.v4.media.session.MediaSessionCompat} or {@link
   * android.support.v4.media.session.MediaControllerCompat}.
   */
  public final Bundle customExtras;

  /**
   * Creates a predefined command.
   *
   * @param commandCode A command code for a predefined command.
   */
  public SessionCommand(@CommandCode int commandCode) {
    checkArgument(
        commandCode != COMMAND_CODE_CUSTOM, "commandCode shouldn't be COMMAND_CODE_CUSTOM");
    this.commandCode = commandCode;
    customAction = "";
    customExtras = Bundle.EMPTY;
  }

  /**
   * Creates a custom command.
   *
   * @param action The action of this custom command.
   * @param extras An extra bundle for this custom command. This value is not used when the command
   *     is sent to a legacy {@link android.support.v4.media.session.MediaSessionCompat} or {@link
   *     android.support.v4.media.session.MediaControllerCompat}.
   */
  public SessionCommand(String action, Bundle extras) {
    commandCode = COMMAND_CODE_CUSTOM;
    customAction = checkNotNull(action);
    customExtras = new Bundle(checkNotNull(extras));
  }

  /** Checks the given session command for equality while ignoring extras. */
  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof SessionCommand)) {
      return false;
    }
    SessionCommand other = (SessionCommand) obj;
    return commandCode == other.commandCode && TextUtils.equals(customAction, other.customAction);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(customAction, commandCode);
  }

  // Bundleable implementation.
  private static final String FIELD_COMMAND_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_CUSTOM_ACTION = Util.intToStringMaxRadix(1);
  private static final String FIELD_CUSTOM_EXTRAS = Util.intToStringMaxRadix(2);

  @UnstableApi
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_COMMAND_CODE, commandCode);
    bundle.putString(FIELD_CUSTOM_ACTION, customAction);
    bundle.putBundle(FIELD_CUSTOM_EXTRAS, customExtras);
    return bundle;
  }

  /**
   * Object that can restore a {@link SessionCommand} from a {@link Bundle}.
   *
   * @deprecated Use {@link #fromBundle} instead.
   */
  @UnstableApi
  @Deprecated
  @SuppressWarnings("deprecation") // Deprecated instance of deprecated class
  public static final Creator<SessionCommand> CREATOR = SessionCommand::fromBundle;

  /** Restores a {@code SessionCommand} from a {@link Bundle}. */
  @UnstableApi
  public static SessionCommand fromBundle(Bundle bundle) {
    int commandCode = bundle.getInt(FIELD_COMMAND_CODE, /* defaultValue= */ COMMAND_CODE_CUSTOM);
    if (commandCode != COMMAND_CODE_CUSTOM) {
      return new SessionCommand(commandCode);
    } else {
      String customAction = checkNotNull(bundle.getString(FIELD_CUSTOM_ACTION));
      @Nullable Bundle customExtras = bundle.getBundle(FIELD_CUSTOM_EXTRAS);
      return new SessionCommand(customAction, customExtras == null ? Bundle.EMPTY : customExtras);
    }
  }
  ;
}

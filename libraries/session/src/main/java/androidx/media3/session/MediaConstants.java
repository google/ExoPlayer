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

import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.session.MediaLibraryService.LibraryParams;

/** Constants that can be shared between media session and controller. */
public final class MediaConstants {

  /**
   * Bundle key to indicate a preference that a region of space for the skip to next control should
   * always be blocked out in the UI, even when the seek to next standard action is not supported.
   *
   * <p>This may be used when the session temporarily disallows {@link
   * androidx.media3.common.Player#COMMAND_SEEK_TO_NEXT} by design.
   *
   * @see MediaSession#setSessionExtras(Bundle)
   * @see MediaSessionCompat#setExtras(Bundle)
   * @see MediaController.Listener#onExtrasChanged(MediaController, Bundle)
   * @see MediaControllerCompat.Callback#onExtrasChanged(Bundle)
   * @see androidx.media3.common.Player#COMMAND_SEEK_TO_NEXT
   * @see androidx.media3.common.Player#COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
   */
  public static final String EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT =
      "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT";

  /**
   * Bundle key to indicate a preference that a region of space for the skip to previous control
   * should always be blocked out in the UI, even when the seek to previous standard action is not
   * supported.
   *
   * <p>This may be used when the session temporarily disallows {@link
   * androidx.media3.common.Player#COMMAND_SEEK_TO_PREVIOUS} by design.
   *
   * @see MediaSession#setSessionExtras(Bundle)
   * @see MediaSessionCompat#setExtras(Bundle)
   * @see MediaController.Listener#onExtrasChanged(MediaController, Bundle)
   * @see MediaControllerCompat.Callback#onExtrasChanged(Bundle)
   * @see androidx.media3.common.Player#COMMAND_SEEK_TO_PREVIOUS
   * @see androidx.media3.common.Player#COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
   */
  public static final String EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV =
      "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS";

  /**
   * The extras key for the localized error resolution string.
   *
   * <p>Use this key to populate the extras bundle of the {@link LibraryParams} when {@link
   * LibraryResult#ofError(int, LibraryParams) creating a LibraryResult} for an unsuccessful service
   * call.
   *
   * @see
   *     androidx.media.utils.MediaConstants#PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL
   */
  public static final String EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT =
      "android.media.extras.ERROR_RESOLUTION_ACTION_LABEL";
  /**
   * The extras key for the error resolution intent.
   *
   * <p>Use this key to populate the extras bundle of the {@link LibraryParams} when {@link
   * LibraryResult#ofError(int, LibraryParams) creating a LibraryResult} for an unsuccessful service
   * call.
   *
   * @see
   *     androidx.media.utils.MediaConstants#PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT
   */
  public static final String EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT =
      "android.media.extras.ERROR_RESOLUTION_ACTION_INTENT";

  /**
   * The legacy error code for expired authentication.
   *
   * <p>Use this error code to indicate an expired authentication when {@link
   * LibraryResult#ofError(int, LibraryParams) creating a LibraryResult} for an unsuccessful service
   * call.
   *
   * @see PlaybackStateCompat#ERROR_CODE_AUTHENTICATION_EXPIRED
   */
  public static final int ERROR_CODE_AUTHENTICATION_EXPIRED_COMPAT = 3;

  /* package */ static final String SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED =
      "androidx.media3.session.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED";
  /* package */ static final String SESSION_COMMAND_REQUEST_SESSION3_TOKEN =
      "androidx.media3.session.SESSION_COMMAND_REQUEST_SESSION3_TOKEN";

  /* package */ static final String ARGUMENT_CAPTIONING_ENABLED =
      "androidx.media3.session.ARGUMENT_CAPTIONING_ENABLED";

  private MediaConstants() {}
}

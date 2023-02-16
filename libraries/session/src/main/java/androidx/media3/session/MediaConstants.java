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

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;

/** Constants that can be shared between media session and controller. */
public final class MediaConstants {

  /**
   * {@link Bundle} key used for the error code for expired authentication.
   *
   * <p>Use this error code to indicate an expired authentication when {@linkplain
   * LibraryResult#ofError(int, LibraryParams) creating a library result} for an unsuccessful
   * service call.
   *
   * @see PlaybackStateCompat#ERROR_CODE_AUTHENTICATION_EXPIRED
   */
  public static final int ERROR_CODE_AUTHENTICATION_EXPIRED_COMPAT = 3;

  /**
   * {@link Bundle} key used for the value of {@code Player.getPlaybackParameters().speed}.
   *
   * <p>Use this key in the extras bundle of the legacy {@link PlaybackStateCompat}.
   */
  @UnstableApi public static final String EXTRAS_KEY_PLAYBACK_SPEED_COMPAT = "EXO_SPEED";

  /**
   * {@link Bundle} key used for the media id of the media being played.
   *
   * <p>Use this key in the extras bundle of the legacy {@link PlaybackStateCompat}.
   */
  @UnstableApi
  public static final String EXTRAS_KEY_MEDIA_ID_COMPAT =
      androidx.media.utils.MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID;

  /**
   * {@link Bundle} key used for a localized error resolution string.
   *
   * <p>Use this key to populate the extras bundle of the {@link LibraryParams} when {@linkplain
   * LibraryResult#ofError(int, LibraryParams) creating a library result} for an unsuccessful
   * service call.
   */
  public static final String EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT =
      androidx.media.utils.MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL;

  /**
   * {@link Bundle} key used for an error resolution intent.
   *
   * <p>Use this key to populate the extras bundle of the {@link LibraryParams} when {@linkplain
   * LibraryResult#ofError(int, LibraryParams) creating a library result} for an unsuccessful
   * service call.
   */
  public static final String EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT =
      androidx.media.utils.MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT;

  /**
   * {@link Bundle} key used to store a {@link PendingIntent}. When launched, the {@link
   * PendingIntent} should allow users to resolve the current playback state error.
   *
   * <p>Applications must also set the error message and {@link
   * #EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT} for cases in which the intent cannot be auto
   * launched.
   *
   * <p>Use this key to populate the extras bundle of the {@link LibraryParams} when {@linkplain
   * LibraryResult#ofError(int, LibraryParams) creating a library result} for an unsuccessful
   * service call. Must be inserted {@linkplain Bundle#putParcelable(String, Parcelable) into the
   * bundle as a parcelable}.
   *
   * <p>TYPE: {@link PendingIntent}.
   */
  @UnstableApi
  public static final String EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT_COMPAT =
      androidx.media.utils.MediaConstants
          .PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT;

  /**
   * {@link Bundle} key to indicate a preference that a region of space for the skip to next control
   * should always be blocked out in the UI, even when the seek to next standard action is not
   * supported.
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
      androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT;

  /**
   * {@link Bundle} key to indicate a preference that a region of space for the skip to previous
   * control should always be blocked out in the UI, even when the seek to previous standard action
   * is not supported.
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
      androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV;

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate the playback completion
   * status of the corresponding {@link MediaItem}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
   * @see #EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
   * @see #EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
   */
  @UnstableApi
  public static final String EXTRAS_KEY_COMPLETION_STATUS =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate that the corresponding
   * {@link MediaItem} has not been played by the user.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_COMPLETION_STATUS
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate that the corresponding
   * {@link MediaItem} has been partially played by the user.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_COMPLETION_STATUS
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED =
      androidx.media.utils.MediaConstants
          .DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate that the corresponding
   * {@link MediaItem} has been fully played by the user.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_COMPLETION_STATUS
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED;

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate an amount of completion
   * progress for the corresponding {@link MediaItem}. This extra augments {@link
   * #EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED the partially played status} by indicating how
   * much has been played by the user.
   *
   * <p>TYPE: double, a value between 0.0 and 1.0, inclusive. 0.0 indicates no completion progress
   * (item is not started) and 1.0 indicates full completion progress (item is fully played). Values
   * in between indicate partial progress (for example, 0.75 indicates the item is 75% complete).
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_COMPLETION_PERCENTAGE =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE;

  /**
   * {@link Bundle} key used to indicate a preference about how playable instances of {@link
   * MediaItem} are presented.
   *
   * <p>If exposed through {@link LibraryParams#extras} of the {@link LibraryResult} returned by
   * {@link MediaBrowser#getLibraryRoot}, the preference applies to all playable items within the
   * browse tree.
   *
   * <p>If exposed through {@link MediaMetadata#extras} of a {@linkplain MediaMetadata#isBrowsable
   * browsable media item}, the preference applies to only the immediate playable children. It takes
   * precedence over preferences received with {@link MediaBrowser#getLibraryRoot}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaBrowser#getLibraryRoot(LibraryParams)
   * @see LibraryResult#params
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   */
  @UnstableApi
  public static final String EXTRAS_KEY_CONTENT_STYLE_PLAYABLE =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE;

  /**
   * {@link Bundle} key used to indicate a preference about how browsable instances of {@link
   * MediaItem} are presented.
   *
   * <p>If exposed through {@link LibraryParams#extras} of the {@link LibraryResult} returned by
   * {@link MediaBrowser#getLibraryRoot}, the preference applies to all browsable items within the
   * browse tree.
   *
   * <p>If exposed through {@link MediaMetadata#extras} of a {@linkplain MediaMetadata#isBrowsable
   * browsable media item}, the preference applies to only the immediate browsable children. It
   * takes precedence over preferences received with {@link
   * MediaBrowser#getLibraryRoot(LibraryParams)}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaBrowser#getLibraryRoot(LibraryParams)
   * @see LibraryResult#params
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
   */
  @UnstableApi
  public static final String EXTRAS_KEY_CONTENT_STYLE_BROWSABLE =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE;

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate a preference about how the
   * corresponding {@link MediaItem} is presented.
   *
   * <p>This preference takes precedence over those expressed by {@link
   * #EXTRAS_KEY_CONTENT_STYLE_PLAYABLE} and {@link #EXTRAS_KEY_CONTENT_STYLE_BROWSABLE}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
   * @see #EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
   */
  @UnstableApi
  public static final String EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate a preference that certain
   * instances of {@link MediaItem} should be presented as list items.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   * @see #EXTRAS_KEY_CONTENT_STYLE_PLAYABLE
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate a preference that certain
   * instances of {@link MediaItem} should be presented as grid items.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   * @see #EXTRAS_KEY_CONTENT_STYLE_PLAYABLE
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate a preference that
   * browsable instances of {@link MediaItem} should be presented as "category" list items. This
   * means the items provide icons that render well when they do <strong>not</strong> fill all of
   * the available area.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM;

  /**
   * {@link Bundle} value used in {@link MediaMetadata#extras} to indicate a preference that
   * browsable instances of {@link MediaItem} should be presented as "category" grid items. This
   * means the items provide icons that render well when they do <strong>not</strong> fill all of
   * the available area.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   * @see #EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   */
  @UnstableApi
  public static final int EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM;

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate that certain instances of
   * {@link MediaItem} are related as a group, with a title that is specified through the bundle
   * value. Items that are children of the same browsable node and have the same title are members
   * of the same group. An app may present a group's items as a contiguous block and display the
   * title alongside the group.
   *
   * <p>TYPE: String. Should be human readable and localized.
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE =
      androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE;

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate that the corresponding
   * {@link MediaItem} has explicit content (that is, user discretion is advised when viewing or
   * listening to this content).
   *
   * <p>TYPE: long (to enable, use value {@link #EXTRAS_VALUE_ATTRIBUTE_PRESENT})
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   */
  @UnstableApi public static final String EXTRAS_KEY_IS_EXPLICIT = "android.media.IS_EXPLICIT";

  /**
   * {@link Bundle} key used in {@link MediaMetadata#extras} to indicate that the corresponding
   * {@link MediaItem} is an advertisement.
   *
   * <p>TYPE: long (to enable, use value {@link #EXTRAS_VALUE_ATTRIBUTE_PRESENT})
   *
   * @see MediaMetadata.Builder#setExtras(Bundle)
   * @see MediaMetadata#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_IS_ADVERTISEMENT =
      androidx.media.utils.MediaConstants.METADATA_KEY_IS_ADVERTISEMENT;

  /**
   * {@link Bundle} value used to indicate the presence of an attribute described by its
   * corresponding key.
   */
  @UnstableApi public static final long EXTRAS_VALUE_ATTRIBUTE_PRESENT = 1L;

  /**
   * {@link Bundle} key used in {@link LibraryParams#extras} passed to {@link
   * MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession, MediaSession.ControllerInfo,
   * LibraryParams)} to indicate the maximum number of children of the root node that can be
   * supported by the {@link MediaBrowser}. Excess root children may be omitted or made less
   * discoverable.
   *
   * <p>TYPE: int
   *
   * @see MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   *     MediaSession.ControllerInfo, LibraryParams)
   * @see MediaBrowser#getLibraryRoot(LibraryParams)
   * @see LibraryParams#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_ROOT_CHILDREN_LIMIT =
      androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT;

  /**
   * {@link Bundle} key used in {@link LibraryParams#extras} passed to {@link
   * MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession, MediaSession.ControllerInfo,
   * LibraryParams)} to indicate whether only browsable media items are supported as children of the
   * root node by the {@link MediaBrowser}. If true, root children that are not browsable may be
   * omitted or made less discoverable.
   *
   * <p>TYPE: boolean.
   *
   * @see MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   *     MediaSession.ControllerInfo, LibraryParams)
   * @see MediaBrowser#getLibraryRoot(LibraryParams)
   * @see LibraryParams#extras
   */
  @UnstableApi
  public static final String EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY =
      "androidx.media3.session.LibraryParams.Extras.KEY_ROOT_CHILDREN_BROWSABLE_ONLY";

  /**
   * {@link Bundle} key used in {@link LibraryParams#extras} passed by the {@link MediaBrowser} as
   * root hints to {@link MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   * MediaSession.ControllerInfo, LibraryParams)} to indicate the recommended size, in pixels, for
   * media art bitmaps. Much smaller images may not render well, and much larger images may cause
   * inefficient resource consumption.
   *
   * @see MediaBrowser#getLibraryRoot(LibraryParams)
   * @see MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   *     MediaSession.ControllerInfo, LibraryParams)
   * @see LibraryParams#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS =
      androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_MEDIA_ART_SIZE_PIXELS;

  /**
   * {@link Bundle} key used to indicate that the media app that provides the service supports
   * showing a settings page.
   *
   * <p>Use this key to populate the {@link LibraryParams#extras} of the {@link LibraryResult}
   * returned by {@link MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   * MediaSession.ControllerInfo, LibraryParams)}. Use this key with {@link
   * Bundle#putParcelable(String, Parcelable)} to put a {@link PendingIntent} that is created using
   * {@code CarPendingIntent#getCarApp()}.
   *
   * <p>The {@link Intent} carried by the pending intent needs to have the component name set to a
   * <a href="http://developer.android.com/training/cars/apps#create-carappservice">Car App Library
   * service</a> that needs to exist in the same application package as the media browser service.
   *
   * <p>TYPE: {@link PendingIntent}.
   *
   * @see MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibrarySession,
   *     MediaSession.ControllerInfo, LibraryParams)
   * @see LibraryParams#extras
   */
  @UnstableApi
  public static final String EXTRAS_KEY_APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT =
      androidx.media.utils.MediaConstants
          .BROWSER_SERVICE_EXTRAS_KEY_APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT;

  /**
   * {@link Bundle} key used to indicate the {@link MediaMetadata.MediaType} in the legacy {@link
   * MediaDescriptionCompat} as a long {@link MediaDescriptionCompat#getExtras() extra} and as a
   * long value in {@link android.support.v4.media.MediaMetadataCompat}.
   */
  @UnstableApi
  public static final String EXTRAS_KEY_MEDIA_TYPE_COMPAT =
      "androidx.media3.session.EXTRAS_KEY_MEDIA_TYPE_COMPAT";

  /* package */ static final String SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED =
      "androidx.media3.session.SESSION_COMMAND_ON_CAPTIONING_ENABLED_CHANGED";
  /* package */ static final String SESSION_COMMAND_REQUEST_SESSION3_TOKEN =
      "androidx.media3.session.SESSION_COMMAND_REQUEST_SESSION3_TOKEN";

  /* package */ static final String ARGUMENT_CAPTIONING_ENABLED =
      "androidx.media3.session.ARGUMENT_CAPTIONING_ENABLED";

  private MediaConstants() {}
}

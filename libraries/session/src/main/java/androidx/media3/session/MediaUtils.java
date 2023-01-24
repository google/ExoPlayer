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

import static android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;
import static androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS;
import static androidx.media3.common.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_GET_AUDIO_ATTRIBUTES;
import static androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_GET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_GET_MEDIA_ITEMS_METADATA;
import static androidx.media3.common.Player.COMMAND_GET_TIMELINE;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.common.util.Util.constrainValue;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.CustomAction;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PercentageRating;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Command;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.ThumbRating;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.PlayerInfo.BundlingExclusions;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/* package */ final class MediaUtils {

  public static final String TAG = "MediaUtils";
  public static final int TRANSACTION_SIZE_LIMIT_IN_BYTES = 256 * 1024; // 256KB
  /** Constant to identify whether two calculated positions are considered as same */
  public static final long POSITION_DIFF_TOLERANCE_MS = 100;

  // Stub BrowserRoot for accepting any connection here.
  public static final BrowserRoot defaultBrowserRoot =
      new BrowserRoot(MediaLibraryService.SERVICE_INTERFACE, null);

  /** Returns whether two {@link PlaybackStateCompat} have equal error. */
  public static boolean areEqualError(
      @Nullable PlaybackStateCompat a, @Nullable PlaybackStateCompat b) {
    boolean aHasError = a != null && a.getState() == PlaybackStateCompat.STATE_ERROR;
    boolean bHasError = b != null && b.getState() == PlaybackStateCompat.STATE_ERROR;
    if (aHasError && bHasError) {
      return castNonNull(a).getErrorCode() == castNonNull(b).getErrorCode()
          && TextUtils.equals(castNonNull(a).getErrorMessage(), castNonNull(b).getErrorMessage());
    }
    return aHasError == bHasError;
  }

  /** Converts {@link PlaybackStateCompat} to {@link PlaybackException}. */
  @Nullable
  public static PlaybackException convertToPlaybackException(
      @Nullable PlaybackStateCompat playbackStateCompat) {
    if (playbackStateCompat == null
        || playbackStateCompat.getState() != PlaybackStateCompat.STATE_ERROR) {
      return null;
    }
    StringBuilder stringBuilder = new StringBuilder();
    if (!TextUtils.isEmpty(playbackStateCompat.getErrorMessage())) {
      stringBuilder.append(playbackStateCompat.getErrorMessage().toString()).append(", ");
    }
    stringBuilder.append("code=").append(playbackStateCompat.getErrorCode());
    String errorMessage = stringBuilder.toString();
    return new PlaybackException(
        errorMessage, /* cause= */ null, PlaybackException.ERROR_CODE_REMOTE_ERROR);
  }

  public static MediaBrowserCompat.MediaItem convertToBrowserItem(
      MediaItem item, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat description = convertToMediaDescriptionCompat(item, artworkBitmap);
    MediaMetadata metadata = item.mediaMetadata;
    int flags = 0;
    if (metadata.isBrowsable != null && metadata.isBrowsable) {
      flags |= MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
    }
    if (metadata.isPlayable != null && metadata.isPlayable) {
      flags |= MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
    }
    return new MediaBrowserCompat.MediaItem(description, flags);
  }

  /** Converts a {@link MediaBrowserCompat.MediaItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(MediaBrowserCompat.MediaItem item) {
    return convertToMediaItem(item.getDescription(), item.isBrowsable(), item.isPlayable());
  }

  /** Converts a {@link QueueItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(QueueItem item) {
    return convertToMediaItem(item.getDescription());
  }

  /** Converts a {@link QueueItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(MediaDescriptionCompat description) {
    checkNotNull(description);
    return convertToMediaItem(description, /* browsable= */ false, /* playable= */ true);
  }

  /** Converts a {@link MediaMetadataCompat} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(
      MediaMetadataCompat metadataCompat, @RatingCompat.Style int ratingType) {
    return convertToMediaItem(
        metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
        metadataCompat,
        ratingType);
  }

  /** Converts a {@code mediaId} and {@link MediaMetadataCompat} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(
      @Nullable String mediaId,
      MediaMetadataCompat metadataCompat,
      @RatingCompat.Style int ratingType) {
    MediaItem.Builder builder = new MediaItem.Builder();
    if (mediaId != null) {
      builder.setMediaId(mediaId);
    }
    @Nullable
    String mediaUriString = metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI);
    if (mediaUriString != null) {
      builder.setRequestMetadata(
          new MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(mediaUriString)).build());
    }
    builder.setMediaMetadata(convertToMediaMetadata(metadataCompat, ratingType));
    return builder.build();
  }

  private static MediaItem convertToMediaItem(
      MediaDescriptionCompat descriptionCompat, boolean browsable, boolean playable) {
    @Nullable String mediaId = descriptionCompat.getMediaId();
    return new MediaItem.Builder()
        .setMediaId(mediaId == null ? MediaItem.DEFAULT_MEDIA_ID : mediaId)
        .setRequestMetadata(
            new MediaItem.RequestMetadata.Builder()
                .setMediaUri(descriptionCompat.getMediaUri())
                .build())
        .setMediaMetadata(
            convertToMediaMetadata(
                descriptionCompat, RatingCompat.RATING_NONE, browsable, playable))
        .build();
  }

  /** Converts a list of {@link MediaBrowserCompat.MediaItem} to a list of {@link MediaItem}. */
  public static ImmutableList<MediaItem> convertBrowserItemListToMediaItemList(
      List<MediaBrowserCompat.MediaItem> items) {
    ImmutableList.Builder<MediaItem> builder = new ImmutableList.Builder<>();
    for (int i = 0; i < items.size(); i++) {
      builder.add(convertToMediaItem(items.get(i)));
    }
    return builder.build();
  }

  /** Converts a {@link Timeline} to a list of {@link MediaItem MediaItems}. */
  public static List<MediaItem> convertToMediaItemList(Timeline timeline) {
    List<MediaItem> mediaItems = new ArrayList<>();
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      mediaItems.add(timeline.getWindow(i, window).mediaItem);
    }
    return mediaItems;
  }

  /**
   * Converts a {@link MediaItem} to a {@link QueueItem}. The index of the item in the playlist
   * would be used as the queue ID to match the behavior of {@link MediaController}.
   */
  public static QueueItem convertToQueueItem(
      MediaItem item, int mediaItemIndex, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat description = convertToMediaDescriptionCompat(item, artworkBitmap);
    long id = convertToQueueItemId(mediaItemIndex);
    return new QueueItem(description, id);
  }

  /** Converts the index of a {@link MediaItem} in a playlist into id of {@link QueueItem}. */
  public static long convertToQueueItemId(int mediaItemIndex) {
    if (mediaItemIndex == C.INDEX_UNSET) {
      return QueueItem.UNKNOWN_ID;
    }
    return mediaItemIndex;
  }

  public static Window convertToWindow(MediaItem mediaItem, int periodIndex) {
    Window window = new Window();
    window.set(
        /* uid= */ 0,
        mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ 0,
        /* windowStartTimeMs= */ 0,
        /* elapsedRealtimeEpochOffsetMs= */ 0,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* liveConfiguration= */ null,
        /* defaultPositionUs= */ 0,
        /* durationUs= */ C.TIME_UNSET,
        /* firstPeriodIndex= */ periodIndex,
        /* lastPeriodIndex= */ periodIndex,
        /* positionInFirstPeriodUs= */ 0);
    return window;
  }

  public static Period convertToPeriod(int windowIndex) {
    Period period = new Period();
    period.set(
        /* id= */ null,
        /* uid= */ null,
        windowIndex,
        /* durationUs= */ C.TIME_UNSET,
        /* positionInWindowUs= */ 0,
        /* adPlaybackState= */ AdPlaybackState.NONE,
        /* isPlaceholder= */ true);
    return period;
  }

  /**
   * Returns a list which consists of first {@code N} items of the given list with the same order.
   * {@code N} is determined as the maximum number of items whose total parcelled size is less than
   * {@code sizeLimitInBytes}.
   */
  public static <T extends Parcelable> List<T> truncateListBySize(
      List<T> list, int sizeLimitInBytes) {
    List<T> result = new ArrayList<>();
    Parcel parcel = Parcel.obtain();
    try {
      for (int i = 0; i < list.size(); i++) {
        // Calculate the size.
        T item = list.get(i);
        parcel.writeParcelable(item, 0);
        if (parcel.dataSize() < sizeLimitInBytes) {
          result.add(item);
        } else {
          break;
        }
      }
    } finally {
      parcel.recycle();
    }
    return result;
  }

  /** Converts a {@link MediaItem} to a {@link MediaDescriptionCompat} */
  public static MediaDescriptionCompat convertToMediaDescriptionCompat(
      MediaItem item, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat.Builder builder =
        new MediaDescriptionCompat.Builder()
            .setMediaId(item.mediaId.equals(MediaItem.DEFAULT_MEDIA_ID) ? null : item.mediaId);
    MediaMetadata metadata = item.mediaMetadata;
    if (artworkBitmap != null) {
      builder.setIconBitmap(artworkBitmap);
    }
    @Nullable Bundle extras = metadata.extras;
    boolean hasFolderType =
        metadata.folderType != null && metadata.folderType != MediaMetadata.FOLDER_TYPE_NONE;
    boolean hasMediaType = metadata.mediaType != null;
    if (hasFolderType || hasMediaType) {
      if (extras == null) {
        extras = new Bundle();
      } else {
        extras = new Bundle(extras);
      }
      if (hasFolderType) {
        extras.putLong(
            MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE,
            convertToExtraBtFolderType(checkNotNull(metadata.folderType)));
      }
      if (hasMediaType) {
        extras.putLong(
            MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT, checkNotNull(metadata.mediaType));
      }
    }
    return builder
        .setTitle(metadata.title)
        // The BT AVRPC service expects the subtitle of the media description to be the artist
        // (see https://github.com/androidx/media/issues/148).
        .setSubtitle(metadata.artist != null ? metadata.artist : metadata.subtitle)
        .setDescription(metadata.description)
        .setIconUri(metadata.artworkUri)
        .setMediaUri(item.requestMetadata.mediaUri)
        .setExtras(extras)
        .build();
  }

  /** Creates {@link MediaMetadata} from the {@link CharSequence queue title}. */
  public static MediaMetadata convertToMediaMetadata(@Nullable CharSequence queueTitle) {
    if (queueTitle == null) {
      return MediaMetadata.EMPTY;
    }
    return new MediaMetadata.Builder().setTitle(queueTitle).build();
  }

  public static MediaMetadata convertToMediaMetadata(
      @Nullable MediaDescriptionCompat descriptionCompat, @RatingCompat.Style int ratingType) {
    return convertToMediaMetadata(
        descriptionCompat, ratingType, /* browsable= */ false, /* playable= */ true);
  }

  private static MediaMetadata convertToMediaMetadata(
      @Nullable MediaDescriptionCompat descriptionCompat,
      @RatingCompat.Style int ratingType,
      boolean browsable,
      boolean playable) {
    if (descriptionCompat == null) {
      return MediaMetadata.EMPTY;
    }

    MediaMetadata.Builder builder = new MediaMetadata.Builder();

    builder
        .setTitle(descriptionCompat.getTitle())
        .setSubtitle(descriptionCompat.getSubtitle())
        .setDescription(descriptionCompat.getDescription())
        .setArtworkUri(descriptionCompat.getIconUri())
        .setUserRating(convertToRating(RatingCompat.newUnratedRating(ratingType)));

    @Nullable Bitmap iconBitmap = descriptionCompat.getIconBitmap();
    if (iconBitmap != null) {
      @Nullable byte[] artworkData = null;
      try {
        artworkData = convertToByteArray(iconBitmap);
      } catch (IOException e) {
        Log.w(TAG, "Failed to convert iconBitmap to artworkData", e);
      }
      builder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
    }

    @Nullable Bundle compatExtras = descriptionCompat.getExtras();
    @Nullable Bundle extras = compatExtras == null ? null : new Bundle(compatExtras);

    if (extras != null && extras.containsKey(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE)) {
      builder.setFolderType(
          convertToFolderType(extras.getLong(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE)));
      extras.remove(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE);
    }
    builder.setIsBrowsable(browsable);

    if (extras != null && extras.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT)) {
      builder.setMediaType((int) extras.getLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT));
      extras.remove(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT);
    }
    if (extras != null && !extras.isEmpty()) {
      builder.setExtras(extras);
    }

    builder.setIsPlayable(playable);

    return builder.build();
  }

  /** Creates {@link MediaMetadata} from the {@link MediaMetadataCompat} and rating type. */
  public static MediaMetadata convertToMediaMetadata(
      @Nullable MediaMetadataCompat metadataCompat, @RatingCompat.Style int ratingType) {
    if (metadataCompat == null) {
      return MediaMetadata.EMPTY;
    }

    MediaMetadata.Builder builder = new MediaMetadata.Builder();

    builder
        .setTitle(
            getFirstText(
                metadataCompat,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadataCompat.METADATA_KEY_TITLE))
        .setSubtitle(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
        .setDescription(
            metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
        .setArtist(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
        .setAlbumTitle(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ALBUM))
        .setAlbumArtist(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
        .setOverallRating(
            convertToRating(metadataCompat.getRating(MediaMetadataCompat.METADATA_KEY_RATING)));

    @Nullable
    Rating userRating =
        convertToRating(metadataCompat.getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING));
    if (userRating != null) {
      builder.setUserRating(userRating);
    } else {
      builder.setUserRating(convertToRating(RatingCompat.newUnratedRating(ratingType)));
    }

    if (metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_YEAR)) {
      long year = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_YEAR);
      builder.setRecordingYear((int) year);
    }

    @Nullable
    String artworkUriString =
        getFirstString(
            metadataCompat,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
    if (artworkUriString != null) {
      builder.setArtworkUri(Uri.parse(artworkUriString));
    }

    @Nullable
    Bitmap artworkBitmap =
        getFirstBitmap(
            metadataCompat,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
    if (artworkBitmap != null) {
      try {
        byte[] artworkData = convertToByteArray(artworkBitmap);
        builder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
      } catch (IOException e) {
        Log.w(TAG, "Failed to convert artworkBitmap to artworkData", e);
      }
    }

    boolean isBrowsable =
        metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE);
    builder.setIsBrowsable(isBrowsable);
    if (isBrowsable) {
      builder.setFolderType(
          convertToFolderType(
              metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE)));
    }

    if (metadataCompat.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT)) {
      builder.setMediaType(
          (int) metadataCompat.getLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT));
    }

    builder.setIsPlayable(true);

    return builder.build();
  }

  @Nullable
  private static Bitmap getFirstBitmap(MediaMetadataCompat mediaMetadataCompat, String... keys) {
    for (String key : keys) {
      if (mediaMetadataCompat.containsKey(key)) {
        return mediaMetadataCompat.getBitmap(key);
      }
    }
    return null;
  }

  @Nullable
  private static String getFirstString(MediaMetadataCompat mediaMetadataCompat, String... keys) {
    for (String key : keys) {
      if (mediaMetadataCompat.containsKey(key)) {
        return mediaMetadataCompat.getString(key);
      }
    }
    return null;
  }

  @Nullable
  private static CharSequence getFirstText(
      MediaMetadataCompat mediaMetadataCompat, String... keys) {
    for (String key : keys) {
      if (mediaMetadataCompat.containsKey(key)) {
        return mediaMetadataCompat.getText(key);
      }
    }
    return null;
  }

  /**
   * Converts a {@link MediaMetadata} to a {@link MediaMetadataCompat}.
   *
   * @param metadata The {@link MediaMetadata} instance to convert.
   * @param mediaId The corresponding media ID.
   * @param mediaUri The corresponding media URI, or null if unknown.
   * @param durationMs The duration of the media, in milliseconds or {@link C#TIME_UNSET}, if no
   *     duration should be included.
   * @return An instance of the legacy {@link MediaMetadataCompat}.
   */
  public static MediaMetadataCompat convertToMediaMetadataCompat(
      MediaMetadata metadata,
      String mediaId,
      @Nullable Uri mediaUri,
      long durationMs,
      @Nullable Bitmap artworkBitmap) {
    MediaMetadataCompat.Builder builder =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId);

    if (metadata.title != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title);
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, metadata.title);
    }

    if (metadata.subtitle != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, metadata.subtitle);
    }

    if (metadata.description != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, metadata.description);
    }

    if (metadata.artist != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.artist);
    }

    if (metadata.albumTitle != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.albumTitle);
    }

    if (metadata.albumArtist != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, metadata.albumArtist);
    }

    if (metadata.recordingYear != null) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, metadata.recordingYear);
    }

    if (mediaUri != null) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, mediaUri.toString());
    }

    if (metadata.artworkUri != null) {
      builder.putString(
          MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, metadata.artworkUri.toString());
      builder.putString(
          MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, metadata.artworkUri.toString());
    }

    if (artworkBitmap != null) {
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap);
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
    }

    if (metadata.folderType != null && metadata.folderType != MediaMetadata.FOLDER_TYPE_NONE) {
      builder.putLong(
          MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE,
          convertToExtraBtFolderType(metadata.folderType));
    }

    if (durationMs != C.TIME_UNSET) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
    }

    @Nullable RatingCompat userRatingCompat = convertToRatingCompat(metadata.userRating);
    if (userRatingCompat != null) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING, userRatingCompat);
    }

    @Nullable RatingCompat overallRatingCompat = convertToRatingCompat(metadata.overallRating);
    if (overallRatingCompat != null) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_RATING, overallRatingCompat);
    }

    if (metadata.mediaType != null) {
      builder.putLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT, metadata.mediaType);
    }

    return builder.build();
  }

  @MediaMetadata.FolderType
  private static int convertToFolderType(long extraBtFolderType) {
    if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_MIXED) {
      return MediaMetadata.FOLDER_TYPE_MIXED;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_TITLES) {
      return MediaMetadata.FOLDER_TYPE_TITLES;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_ALBUMS) {
      return MediaMetadata.FOLDER_TYPE_ALBUMS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_ARTISTS) {
      return MediaMetadata.FOLDER_TYPE_ARTISTS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_GENRES) {
      return MediaMetadata.FOLDER_TYPE_GENRES;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS) {
      return MediaMetadata.FOLDER_TYPE_PLAYLISTS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_YEARS) {
      return MediaMetadata.FOLDER_TYPE_YEARS;
    } else {
      return MediaMetadata.FOLDER_TYPE_MIXED;
    }
  }

  private static long convertToExtraBtFolderType(@MediaMetadata.FolderType int folderType) {
    switch (folderType) {
      case MediaMetadata.FOLDER_TYPE_MIXED:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_MIXED;
      case MediaMetadata.FOLDER_TYPE_TITLES:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_TITLES;
      case MediaMetadata.FOLDER_TYPE_ALBUMS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_ALBUMS;
      case MediaMetadata.FOLDER_TYPE_ARTISTS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_ARTISTS;
      case MediaMetadata.FOLDER_TYPE_GENRES:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_GENRES;
      case MediaMetadata.FOLDER_TYPE_PLAYLISTS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS;
      case MediaMetadata.FOLDER_TYPE_YEARS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_YEARS;
      case MediaMetadata.FOLDER_TYPE_NONE:
      default:
        throw new IllegalArgumentException("Unrecognized FolderType: " + folderType);
    }
  }

  /**
   * Creates a {@link Rating} from the {@link RatingCompat}.
   *
   * @param ratingCompat A {@link RatingCompat} object.
   * @return The newly created {@link Rating} object.
   */
  @Nullable
  public static Rating convertToRating(@Nullable RatingCompat ratingCompat) {
    if (ratingCompat == null) {
      return null;
    }
    switch (ratingCompat.getRatingStyle()) {
      case RatingCompat.RATING_3_STARS:
        return ratingCompat.isRated()
            ? new StarRating(3, ratingCompat.getStarRating())
            : new StarRating(3);
      case RatingCompat.RATING_4_STARS:
        return ratingCompat.isRated()
            ? new StarRating(4, ratingCompat.getStarRating())
            : new StarRating(4);
      case RatingCompat.RATING_5_STARS:
        return ratingCompat.isRated()
            ? new StarRating(5, ratingCompat.getStarRating())
            : new StarRating(5);
      case RatingCompat.RATING_HEART:
        return ratingCompat.isRated()
            ? new HeartRating(ratingCompat.hasHeart())
            : new HeartRating();
      case RatingCompat.RATING_THUMB_UP_DOWN:
        return ratingCompat.isRated()
            ? new ThumbRating(ratingCompat.isThumbUp())
            : new ThumbRating();
      case RatingCompat.RATING_PERCENTAGE:
        return ratingCompat.isRated()
            ? new PercentageRating(ratingCompat.getPercentRating())
            : new PercentageRating();
      case RatingCompat.RATING_NONE:
      default:
        return null;
    }
  }

  /**
   * Creates a {@link RatingCompat} from the {@link Rating}.
   *
   * @param rating A {@link Rating} object.
   * @return The newly created {@link RatingCompat} object.
   */
  @SuppressLint("WrongConstant") // for @StarStyle
  @Nullable
  public static RatingCompat convertToRatingCompat(@Nullable Rating rating) {
    if (rating == null) {
      return null;
    }
    int ratingCompatStyle = getRatingCompatStyle(rating);
    if (!rating.isRated()) {
      return RatingCompat.newUnratedRating(ratingCompatStyle);
    }

    switch (ratingCompatStyle) {
      case RatingCompat.RATING_3_STARS:
      case RatingCompat.RATING_4_STARS:
      case RatingCompat.RATING_5_STARS:
        return RatingCompat.newStarRating(ratingCompatStyle, ((StarRating) rating).getStarRating());
      case RatingCompat.RATING_HEART:
        return RatingCompat.newHeartRating(((HeartRating) rating).isHeart());
      case RatingCompat.RATING_THUMB_UP_DOWN:
        return RatingCompat.newThumbRating(((ThumbRating) rating).isThumbsUp());
      case RatingCompat.RATING_PERCENTAGE:
        return RatingCompat.newPercentageRating(((PercentageRating) rating).getPercent());
      case RatingCompat.RATING_NONE:
      default:
        return null;
    }
  }

  /** Converts {@link Player}' states to state of {@link PlaybackStateCompat}. */
  @PlaybackStateCompat.State
  public static int convertToPlaybackStateCompatState(
      @Nullable PlaybackException playerError,
      @Player.State int playbackState,
      boolean playWhenReady) {
    if (playerError != null) {
      return PlaybackStateCompat.STATE_ERROR;
    }
    switch (playbackState) {
      case Player.STATE_IDLE:
        return PlaybackStateCompat.STATE_NONE;
      case Player.STATE_READY:
        return playWhenReady ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_STOPPED;
      case Player.STATE_BUFFERING:
        return playWhenReady
            ? PlaybackStateCompat.STATE_BUFFERING
            : PlaybackStateCompat.STATE_PAUSED;
      default:
        throw new IllegalArgumentException("Unrecognized State: " + playbackState);
    }
  }

  /** Converts a {@link PlaybackStateCompat} to {@link PlaybackParameters}. */
  public static PlaybackParameters convertToPlaybackParameters(
      @Nullable PlaybackStateCompat playbackStateCompat) {
    return playbackStateCompat == null
        ? PlaybackParameters.DEFAULT
        : new PlaybackParameters(playbackStateCompat.getPlaybackSpeed());
  }

  /** Converts a {@link PlaybackStateCompat} to {@link Player}'s play when ready. */
  public static boolean convertToPlayWhenReady(@Nullable PlaybackStateCompat playbackState) {
    if (playbackState == null) {
      return false;
    }
    switch (playbackState.getState()) {
      case PlaybackStateCompat.STATE_BUFFERING:
      case PlaybackStateCompat.STATE_FAST_FORWARDING:
      case PlaybackStateCompat.STATE_PLAYING:
      case PlaybackStateCompat.STATE_REWINDING:
      case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
      case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
      case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
        return true;
      case PlaybackStateCompat.STATE_CONNECTING:
      case PlaybackStateCompat.STATE_ERROR:
      case PlaybackStateCompat.STATE_NONE:
      case PlaybackStateCompat.STATE_PAUSED:
      case PlaybackStateCompat.STATE_STOPPED:
        return false;
    }
    return false;
  }

  /** Converts a {@link PlaybackStateCompat} to {@link Player.State} */
  @Player.State
  public static int convertToPlaybackState(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat currentMediaMetadata,
      long timeDiffMs) {
    if (playbackStateCompat == null) {
      return Player.STATE_IDLE;
    }
    switch (playbackStateCompat.getState()) {
      case PlaybackStateCompat.STATE_CONNECTING:
      case PlaybackStateCompat.STATE_ERROR:
      case PlaybackStateCompat.STATE_NONE:
      case PlaybackStateCompat.STATE_STOPPED:
        return Player.STATE_IDLE;
      case PlaybackStateCompat.STATE_BUFFERING:
      case PlaybackStateCompat.STATE_FAST_FORWARDING:
      case PlaybackStateCompat.STATE_REWINDING:
      case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
      case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
      case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
        return Player.STATE_BUFFERING;
      case PlaybackStateCompat.STATE_PLAYING:
        return Player.STATE_READY;
      case PlaybackStateCompat.STATE_PAUSED:
        long duration = convertToDurationMs(currentMediaMetadata);
        if (duration == C.TIME_UNSET) {
          return Player.STATE_READY;
        }
        long currentPosition =
            convertToCurrentPositionMs(playbackStateCompat, currentMediaMetadata, timeDiffMs);
        return (currentPosition < duration) ? Player.STATE_READY : Player.STATE_ENDED;
      default:
        throw new IllegalStateException(
            "Unrecognized PlaybackStateCompat: " + playbackStateCompat.getState());
    }
  }

  /** Converts a {@link PlaybackStateCompat} to isPlaying, defined by {@link Player#isPlaying()}. */
  public static boolean convertToIsPlaying(@Nullable PlaybackStateCompat playbackStateCompat) {
    if (playbackStateCompat == null) {
      return false;
    }
    return playbackStateCompat.getState() == PlaybackStateCompat.STATE_PLAYING;
  }

  /** Converts a {@link PlaybackStateCompat} to isPlaying, defined by {@link Player#isPlaying()}. */
  public static boolean convertToIsPlayingAd(@Nullable MediaMetadataCompat metadataCompat) {
    if (metadataCompat == null) {
      return false;
    }
    return metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT) != 0;
  }

  /** Gets the current position. {@code 0} will be returned if unknown. */
  public static long convertToCurrentPositionMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    if (playbackStateCompat == null) {
      return 0;
    }
    long positionMs =
        playbackStateCompat.getState() == PlaybackStateCompat.STATE_PLAYING
            ? getCurrentPosition(playbackStateCompat, timeDiffMs)
            : playbackStateCompat.getPosition();
    long durationMs = convertToDurationMs(metadataCompat);
    return durationMs == C.TIME_UNSET
        ? max(0, positionMs)
        : constrainValue(positionMs, /* min= */ 0, durationMs);
  }

  @SuppressWarnings("nullness:argument") // PlaybackStateCompat#getCurrentPosition can take null.
  private static long getCurrentPosition(PlaybackStateCompat playbackStateCompat, long timeDiffMs) {
    return playbackStateCompat.getCurrentPosition(timeDiffMs == C.TIME_UNSET ? null : timeDiffMs);
  }

  /** Gets the duration. {@link C#TIME_UNSET} will be returned if unknown. */
  public static long convertToDurationMs(@Nullable MediaMetadataCompat metadataCompat) {
    if (metadataCompat == null
        || !metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
      return C.TIME_UNSET;
    }
    long legacyDurationMs = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
    return legacyDurationMs <= 0 ? C.TIME_UNSET : legacyDurationMs;
  }

  /** Gets the buffered position. {@code 0} will be returned if unknown. */
  public static long convertToBufferedPositionMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    long legacyBufferedPositionMs =
        (playbackStateCompat == null) ? 0 : playbackStateCompat.getBufferedPosition();
    long currentPositionMs =
        convertToCurrentPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    long durationMs = convertToDurationMs(metadataCompat);
    return (durationMs == C.TIME_UNSET)
        ? max(currentPositionMs, legacyBufferedPositionMs)
        : constrainValue(legacyBufferedPositionMs, currentPositionMs, durationMs);
  }

  /** Gets the total buffered duration. {@code 0} will be returned if unknown. */
  public static long convertToTotalBufferedDurationMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    long bufferedPositionMs =
        convertToBufferedPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    long currentPositionMs =
        convertToCurrentPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    return bufferedPositionMs - currentPositionMs;
  }

  /** Gets the buffered percentage. {@code 0} will be returned if unknown. */
  public static int convertToBufferedPercentage(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat mediaMetadataCompat,
      long timeDiffMs) {
    long bufferedPositionMs =
        MediaUtils.convertToBufferedPositionMs(
            playbackStateCompat, mediaMetadataCompat, timeDiffMs);
    long durationMs = MediaUtils.convertToDurationMs(mediaMetadataCompat);
    return calculateBufferedPercentage(bufferedPositionMs, durationMs);
  }

  public static @RatingCompat.Style int getRatingCompatStyle(@Nullable Rating rating) {
    if (rating instanceof HeartRating) {
      return RatingCompat.RATING_HEART;
    } else if (rating instanceof ThumbRating) {
      return RatingCompat.RATING_THUMB_UP_DOWN;
    } else if (rating instanceof StarRating) {
      switch (((StarRating) rating).getMaxStars()) {
        case 3:
          return RatingCompat.RATING_3_STARS;
        case 4:
          return RatingCompat.RATING_4_STARS;
        case 5:
          return RatingCompat.RATING_5_STARS;
      }
    } else if (rating instanceof PercentageRating) {
      return RatingCompat.RATING_PERCENTAGE;
    }
    return RatingCompat.RATING_NONE;
  }

  /** Converts {@link PlaybackStateCompat.RepeatMode} to {@link Player.RepeatMode}. */
  @Player.RepeatMode
  public static int convertToRepeatMode(
      @PlaybackStateCompat.RepeatMode int playbackStateCompatRepeatMode) {
    switch (playbackStateCompatRepeatMode) {
      case PlaybackStateCompat.REPEAT_MODE_INVALID:
      case PlaybackStateCompat.REPEAT_MODE_NONE:
        return Player.REPEAT_MODE_OFF;
      case PlaybackStateCompat.REPEAT_MODE_ONE:
        return Player.REPEAT_MODE_ONE;
      case PlaybackStateCompat.REPEAT_MODE_ALL:
      case PlaybackStateCompat.REPEAT_MODE_GROUP:
        return Player.REPEAT_MODE_ALL;
      default:
        throw new IllegalArgumentException(
            "Unrecognized PlaybackStateCompat.RepeatMode: " + playbackStateCompatRepeatMode);
    }
  }

  /** Converts {@link Player.RepeatMode} to {@link PlaybackStateCompat.RepeatMode} */
  @PlaybackStateCompat.RepeatMode
  public static int convertToPlaybackStateCompatRepeatMode(@Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return PlaybackStateCompat.REPEAT_MODE_NONE;
      case Player.REPEAT_MODE_ONE:
        return PlaybackStateCompat.REPEAT_MODE_ONE;
      case Player.REPEAT_MODE_ALL:
        return PlaybackStateCompat.REPEAT_MODE_ALL;
      default:
        throw new IllegalArgumentException("Unrecognized RepeatMode: " + repeatMode);
    }
  }

  /** Converts {@link PlaybackStateCompat.ShuffleMode} to shuffle mode enabled. */
  public static boolean convertToShuffleModeEnabled(
      @PlaybackStateCompat.ShuffleMode int playbackStateCompatShuffleMode) {
    switch (playbackStateCompatShuffleMode) {
      case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
      case PlaybackStateCompat.SHUFFLE_MODE_NONE:
        return false;
      case PlaybackStateCompat.SHUFFLE_MODE_ALL:
      case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
        return true;
      default:
        throw new IllegalArgumentException(
            "Unrecognized ShuffleMode: " + playbackStateCompatShuffleMode);
    }
  }

  /** Converts shuffle mode enabled to {@link PlaybackStateCompat.ShuffleMode} */
  @PlaybackStateCompat.ShuffleMode
  public static int convertToPlaybackStateCompatShuffleMode(boolean shuffleModeEnabled) {
    return shuffleModeEnabled
        ? PlaybackStateCompat.SHUFFLE_MODE_ALL
        : PlaybackStateCompat.SHUFFLE_MODE_NONE;
  }

  /** Converts the rootHints, option, and extra to the {@link LibraryParams}. */
  @Nullable
  public static LibraryParams convertToLibraryParams(
      Context context, @Nullable Bundle legacyBundle) {
    if (legacyBundle == null) {
      return null;
    }
    try {
      legacyBundle.setClassLoader(context.getClassLoader());
      int supportedChildrenFlags =
          legacyBundle.getInt(
              BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ -1);
      if (supportedChildrenFlags >= 0) {
        legacyBundle.remove(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS);
        legacyBundle.putBoolean(
            EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY,
            supportedChildrenFlags == MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
      }
      return new LibraryParams.Builder()
          .setExtras(legacyBundle)
          .setRecent(legacyBundle.getBoolean(BrowserRoot.EXTRA_RECENT))
          .setOffline(legacyBundle.getBoolean(BrowserRoot.EXTRA_OFFLINE))
          .setSuggested(legacyBundle.getBoolean(BrowserRoot.EXTRA_SUGGESTED))
          .build();
    } catch (Exception e) {
      // Failure when unpacking the legacy bundle.
      return new LibraryParams.Builder().setExtras(legacyBundle).build();
    }
  }

  /** Converts {@link LibraryParams} to the root hints. */
  @Nullable
  public static Bundle convertToRootHints(@Nullable LibraryParams params) {
    if (params == null) {
      return null;
    }
    Bundle rootHints = new Bundle(params.extras);
    if (params.extras.containsKey(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)) {
      boolean browsableChildrenSupported =
          params.extras.getBoolean(
              EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, /* defaultValue= */ false);
      rootHints.remove(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY);
      rootHints.putInt(
          BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
          browsableChildrenSupported
              ? MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
              : MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                  | MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }
    rootHints.putBoolean(BrowserRoot.EXTRA_RECENT, params.isRecent);
    rootHints.putBoolean(BrowserRoot.EXTRA_OFFLINE, params.isOffline);
    rootHints.putBoolean(BrowserRoot.EXTRA_SUGGESTED, params.isSuggested);
    return rootHints;
  }

  /** Returns a new list that only contains non-null elements of the original list. */
  public static <T> List<T> removeNullElements(List<@NullableType T> list) {
    List<T> newList = new ArrayList<>();
    for (@Nullable T item : list) {
      if (item != null) {
        newList.add(item);
      }
    }
    return newList;
  }

  /**
   * Converts {@link PlaybackStateCompat}, {@link
   * MediaControllerCompat.PlaybackInfo#getVolumeControl() volume control type}, {@link
   * MediaControllerCompat#getFlags() session flags} and {@link MediaControllerCompat#isSessionReady
   * whether the session is ready} to {@link Player.Commands}.
   *
   * @param playbackStateCompat The {@link PlaybackStateCompat}.
   * @param volumeControlType The {@link MediaControllerCompat.PlaybackInfo#getVolumeControl()
   *     volume control type}.
   * @param sessionFlags The session flags.
   * @param isSessionReady Whether the session compat is ready.
   * @return The converted player commands.
   */
  public static Player.Commands convertToPlayerCommands(
      @Nullable PlaybackStateCompat playbackStateCompat,
      int volumeControlType,
      long sessionFlags,
      boolean isSessionReady) {
    Commands.Builder playerCommandsBuilder = new Commands.Builder();
    long actions = playbackStateCompat == null ? 0 : playbackStateCompat.getActions();
    if ((hasAction(actions, PlaybackStateCompat.ACTION_PLAY)
            && hasAction(actions, PlaybackStateCompat.ACTION_PAUSE))
        || hasAction(actions, PlaybackStateCompat.ACTION_PLAY_PAUSE)) {
      playerCommandsBuilder.add(COMMAND_PLAY_PAUSE);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE)) {
      playerCommandsBuilder.add(COMMAND_PREPARE);
    }
    if ((hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID))
        || (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH))
        || (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_URI))) {
      // Require both PREPARE and PLAY actions as we have no logic to handle having just one action.
      playerCommandsBuilder.addAll(COMMAND_SET_MEDIA_ITEM, COMMAND_PREPARE);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_REWIND)) {
      playerCommandsBuilder.add(COMMAND_SEEK_BACK);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_FAST_FORWARD)) {
      playerCommandsBuilder.add(COMMAND_SEEK_FORWARD);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SEEK_TO)) {
      playerCommandsBuilder.addAll(
          COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
      playerCommandsBuilder.addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
      playerCommandsBuilder.addAll(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)) {
      playerCommandsBuilder.add(COMMAND_SET_SPEED_AND_PITCH);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_STOP)) {
      playerCommandsBuilder.add(COMMAND_STOP);
    }
    if (volumeControlType == VolumeProviderCompat.VOLUME_CONTROL_RELATIVE) {
      playerCommandsBuilder.add(COMMAND_ADJUST_DEVICE_VOLUME);
    } else if (volumeControlType == VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE) {
      playerCommandsBuilder.addAll(COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_DEVICE_VOLUME);
    }
    playerCommandsBuilder.addAll(
        COMMAND_GET_DEVICE_VOLUME,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_MEDIA_ITEMS_METADATA,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_AUDIO_ATTRIBUTES);
    if ((sessionFlags & FLAG_HANDLES_QUEUE_COMMANDS) != 0) {
      playerCommandsBuilder.add(COMMAND_CHANGE_MEDIA_ITEMS);
      if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
        playerCommandsBuilder.add(Player.COMMAND_SEEK_TO_MEDIA_ITEM);
      }
    }
    if (isSessionReady) {
      if (hasAction(actions, PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
        playerCommandsBuilder.add(COMMAND_SET_REPEAT_MODE);
      }
      if (hasAction(actions, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
        playerCommandsBuilder.add(COMMAND_SET_SHUFFLE_MODE);
      }
    }
    return playerCommandsBuilder.build();
  }

  /**
   * Checks if the set of actions contains the specified action.
   *
   * @param actions A bit set of actions.
   * @param action The action to check.
   * @return Whether the action is contained in the set.
   */
  private static boolean hasAction(long actions, @PlaybackStateCompat.Actions long action) {
    return (actions & action) != 0;
  }

  /**
   * Converts {@link PlaybackStateCompat} to {@link SessionCommands}.
   *
   * <p>This ignores {@link PlaybackStateCompat#getActions() actions} in the {@link
   * PlaybackStateCompat} to workaround media apps' issues that they don't set playback state
   * correctly.
   *
   * @param state playback state
   * @param isSessionReady Whether the session compat is ready.
   * @return the converted session commands
   */
  public static SessionCommands convertToSessionCommands(
      @Nullable PlaybackStateCompat state, boolean isSessionReady) {
    SessionCommands.Builder sessionCommandsBuilder = new SessionCommands.Builder();
    sessionCommandsBuilder.addAllSessionCommands();
    if (!isSessionReady) {
      // Disables rating function when session isn't ready because of the
      // MediaController#setRating(RatingCompat, Bundle) and MediaController#getRatingType().
      sessionCommandsBuilder.remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING);
    }

    if (state != null && state.getCustomActions() != null) {
      for (CustomAction customAction : state.getCustomActions()) {
        String action = customAction.getAction();
        @Nullable Bundle extras = customAction.getExtras();
        sessionCommandsBuilder.add(
            new SessionCommand(action, extras == null ? Bundle.EMPTY : extras));
      }
    }
    return sessionCommandsBuilder.build();
  }

  /**
   * Converts {@link CustomAction} in the {@link PlaybackStateCompat} to the custom layout which is
   * the list of the {@link CommandButton}.
   *
   * @param state playback state
   * @return custom layout. Always non-null.
   */
  public static ImmutableList<CommandButton> convertToCustomLayout(
      @Nullable PlaybackStateCompat state) {
    if (state == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<CommandButton> layout = new ImmutableList.Builder<>();
    for (CustomAction customAction : state.getCustomActions()) {
      String action = customAction.getAction();
      @Nullable Bundle extras = customAction.getExtras();
      CommandButton button =
          new CommandButton.Builder()
              .setSessionCommand(new SessionCommand(action, extras == null ? Bundle.EMPTY : extras))
              .setDisplayName(customAction.getName())
              .setEnabled(true)
              .setIconResId(customAction.getIcon())
              .build();
      layout.add(button);
    }
    return layout.build();
  }

  /** Converts {@link AudioAttributesCompat} into {@link AudioAttributes}. */
  /*
   * @AudioAttributesCompat.AttributeUsage and @C.AudioUsage both use the same constant values,
   * defined by AudioAttributes in the platform.
   */
  @SuppressLint("WrongConstant")
  public static AudioAttributes convertToAudioAttributes(
      @Nullable AudioAttributesCompat audioAttributesCompat) {
    if (audioAttributesCompat == null) {
      return AudioAttributes.DEFAULT;
    }
    return new AudioAttributes.Builder()
        .setContentType(audioAttributesCompat.getContentType())
        .setFlags(audioAttributesCompat.getFlags())
        .setUsage(audioAttributesCompat.getUsage())
        .build();
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to {@link AudioAttributes}. */
  public static AudioAttributes convertToAudioAttributes(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return AudioAttributes.DEFAULT;
    }
    return MediaUtils.convertToAudioAttributes(playbackInfoCompat.getAudioAttributes());
  }

  /** Converts {@link AudioAttributes} into {@link AudioAttributesCompat}. */
  public static AudioAttributesCompat convertToAudioAttributesCompat(
      AudioAttributes audioAttributes) {
    return new AudioAttributesCompat.Builder()
        .setContentType(audioAttributes.contentType)
        .setFlags(audioAttributes.flags)
        .setUsage(audioAttributes.usage)
        .build();
  }

  /**
   * Gets the legacy stream type from {@link AudioAttributes}.
   *
   * @param audioAttributes audio attributes
   * @return int legacy stream type from {@link AudioManager}
   */
  public static int getLegacyStreamType(AudioAttributes audioAttributes) {
    int legacyStreamType =
        MediaUtils.convertToAudioAttributesCompat(audioAttributes).getLegacyStreamType();
    if (legacyStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
      // Usually, AudioAttributesCompat#getLegacyStreamType() does not return
      // USE_DEFAULT_STREAM_TYPE unless the developer sets it with
      // AudioAttributesCompat.Builder#setLegacyStreamType().
      // But for safety, let's convert USE_DEFAULT_STREAM_TYPE to STREAM_MUSIC here.
      return AudioManager.STREAM_MUSIC;
    }
    return legacyStreamType;
  }

  public static <T> T getFutureResult(Future<T> future, long timeoutMs)
      throws ExecutionException, TimeoutException {
    long initialTimeMs = SystemClock.elapsedRealtime();
    long remainingTimeMs = timeoutMs;
    boolean interrupted = false;
    try {
      while (true) {
        try {
          return future.get(remainingTimeMs, MILLISECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
          long elapsedTimeMs = SystemClock.elapsedRealtime() - initialTimeMs;
          if (elapsedTimeMs >= timeoutMs) {
            throw new TimeoutException();
          }
          remainingTimeMs = timeoutMs - elapsedTimeMs;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to {@link DeviceInfo}. */
  public static DeviceInfo convertToDeviceInfo(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return DeviceInfo.UNKNOWN;
    }
    return new DeviceInfo(
        playbackInfoCompat.getPlaybackType()
                == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE
            ? DeviceInfo.PLAYBACK_TYPE_REMOTE
            : DeviceInfo.PLAYBACK_TYPE_LOCAL,
        /* minVolume= */ 0,
        playbackInfoCompat.getMaxVolume());
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to device volume. */
  public static int convertToDeviceVolume(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return 0;
    }
    return playbackInfoCompat.getCurrentVolume();
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to device muted. */
  public static boolean convertToIsDeviceMuted(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return false;
    }
    return playbackInfoCompat.getCurrentVolume() == 0;
  }

  public static Commands createPlayerCommandsWith(@Command int command) {
    return new Commands.Builder().add(command).build();
  }

  public static Commands createPlayerCommandsWithout(@Command int command) {
    return new Commands.Builder().addAllCommands().remove(command).build();
  }

  /**
   * Returns the intersection of {@link Player.Command commands} from the given two {@link
   * Commands}.
   */
  public static Commands intersect(@Nullable Commands commands1, @Nullable Commands commands2) {
    if (commands1 == null || commands2 == null) {
      return Commands.EMPTY;
    }
    Commands.Builder intersectCommandsBuilder = new Commands.Builder();
    for (int i = 0; i < commands1.size(); i++) {
      if (commands2.contains(commands1.get(i))) {
        intersectCommandsBuilder.add(commands1.get(i));
      }
    }
    return intersectCommandsBuilder.build();
  }

  /**
   * Merges the excluded fields into the {@code newPlayerInfo} by taking the values of the {@code
   * previousPlayerInfo} and taking into account the passed available commands.
   *
   * @param oldPlayerInfo The old {@link PlayerInfo}.
   * @param oldBundlingExclusions The bundling exlusions in the old {@link PlayerInfo}.
   * @param newPlayerInfo The new {@link PlayerInfo}.
   * @param newBundlingExclusions The bundling exlusions in the new {@link PlayerInfo}.
   * @param availablePlayerCommands The available commands to take into account when merging.
   * @return A pair with the resulting {@link PlayerInfo} and {@link BundlingExclusions}.
   */
  public static Pair<PlayerInfo, BundlingExclusions> mergePlayerInfo(
      PlayerInfo oldPlayerInfo,
      BundlingExclusions oldBundlingExclusions,
      PlayerInfo newPlayerInfo,
      BundlingExclusions newBundlingExclusions,
      Commands availablePlayerCommands) {
    PlayerInfo mergedPlayerInfo = newPlayerInfo;
    BundlingExclusions mergedBundlingExclusions = newBundlingExclusions;
    if (newBundlingExclusions.isTimelineExcluded
        && availablePlayerCommands.contains(Player.COMMAND_GET_TIMELINE)
        && !oldBundlingExclusions.isTimelineExcluded) {
      // Use the previous timeline if it is excluded in the most recent update.
      mergedPlayerInfo = mergedPlayerInfo.copyWithTimeline(oldPlayerInfo.timeline);
      mergedBundlingExclusions =
          new BundlingExclusions(
              /* isTimelineExcluded= */ false, mergedBundlingExclusions.areCurrentTracksExcluded);
    }
    if (newBundlingExclusions.areCurrentTracksExcluded
        && availablePlayerCommands.contains(Player.COMMAND_GET_TRACKS)
        && !oldBundlingExclusions.areCurrentTracksExcluded) {
      // Use the previous tracks if it is excluded in the most recent update.
      mergedPlayerInfo = mergedPlayerInfo.copyWithCurrentTracks(oldPlayerInfo.currentTracks);
      mergedBundlingExclusions =
          new BundlingExclusions(
              mergedBundlingExclusions.isTimelineExcluded, /* areCurrentTracksExcluded= */ false);
    }
    return new Pair<>(mergedPlayerInfo, mergedBundlingExclusions);
  }

  private static byte[] convertToByteArray(Bitmap bitmap) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      bitmap.compress(Bitmap.CompressFormat.PNG, /* ignored */ 0, stream);
      return stream.toByteArray();
    }
  }

  public static int[] generateUnshuffledIndices(int n) {
    int[] indices = new int[n];
    for (int i = 0; i < n; i++) {
      indices[i] = i;
    }
    return indices;
  }

  public static int calculateBufferedPercentage(long bufferedPositionMs, long durationMs) {
    return bufferedPositionMs == C.TIME_UNSET || durationMs == C.TIME_UNSET
        ? 0
        : durationMs == 0
            ? 100
            : Util.constrainValue((int) ((bufferedPositionMs * 100) / durationMs), 0, 100);
  }

  public static void setMediaItemsWithStartIndexAndPosition(
      PlayerWrapper player, MediaSession.MediaItemsWithStartPosition mediaItemsWithStartPosition) {
    if (mediaItemsWithStartPosition.startIndex == C.INDEX_UNSET) {
      if (player.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
        player.setMediaItems(mediaItemsWithStartPosition.mediaItems, /* resetPosition= */ true);
      } else if (!mediaItemsWithStartPosition.mediaItems.isEmpty()) {
        player.setMediaItem(
            mediaItemsWithStartPosition.mediaItems.get(0), /* resetPosition= */ true);
      }
    } else {
      if (player.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
        player.setMediaItems(
            mediaItemsWithStartPosition.mediaItems,
            mediaItemsWithStartPosition.startIndex,
            mediaItemsWithStartPosition.startPositionMs);
      } else if (!mediaItemsWithStartPosition.mediaItems.isEmpty()) {
        player.setMediaItem(
            mediaItemsWithStartPosition.mediaItems.get(0),
            mediaItemsWithStartPosition.startPositionMs);
      }
    }
  }

  private MediaUtils() {}
}

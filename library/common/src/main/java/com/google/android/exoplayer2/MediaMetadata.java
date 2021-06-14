/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

/**
 * Metadata of a {@link MediaItem}, playlist, or a combination of multiple sources of {@link
 * Metadata}.
 */
public final class MediaMetadata implements Bundleable {

  /** A builder for {@link MediaMetadata} instances. */
  public static final class Builder {

    @Nullable private CharSequence title;
    @Nullable private CharSequence artist;
    @Nullable private CharSequence albumTitle;
    @Nullable private CharSequence albumArtist;
    @Nullable private CharSequence displayTitle;
    @Nullable private CharSequence subtitle;
    @Nullable private CharSequence description;
    @Nullable private Uri mediaUri;
    @Nullable private Rating userRating;
    @Nullable private Rating overallRating;
    @Nullable private byte[] artworkData;
    @Nullable private Uri artworkUri;
    @Nullable private Integer trackNumber;
    @Nullable private Integer totalTrackCount;
    @Nullable @FolderType private Integer folderType;
    @Nullable private Boolean isPlayable;
    @Nullable private Integer year;
    @Nullable private Bundle extras;

    public Builder() {}

    private Builder(MediaMetadata mediaMetadata) {
      this.title = mediaMetadata.title;
      this.artist = mediaMetadata.artist;
      this.albumTitle = mediaMetadata.albumTitle;
      this.albumArtist = mediaMetadata.albumArtist;
      this.displayTitle = mediaMetadata.displayTitle;
      this.subtitle = mediaMetadata.subtitle;
      this.description = mediaMetadata.description;
      this.mediaUri = mediaMetadata.mediaUri;
      this.userRating = mediaMetadata.userRating;
      this.overallRating = mediaMetadata.overallRating;
      this.artworkData = mediaMetadata.artworkData;
      this.artworkUri = mediaMetadata.artworkUri;
      this.trackNumber = mediaMetadata.trackNumber;
      this.totalTrackCount = mediaMetadata.totalTrackCount;
      this.folderType = mediaMetadata.folderType;
      this.isPlayable = mediaMetadata.isPlayable;
      this.year = mediaMetadata.year;
      this.extras = mediaMetadata.extras;
    }

    /** Sets the title. */
    public Builder setTitle(@Nullable CharSequence title) {
      this.title = title;
      return this;
    }

    /** Sets the artist. */
    public Builder setArtist(@Nullable CharSequence artist) {
      this.artist = artist;
      return this;
    }

    /** Sets the album title. */
    public Builder setAlbumTitle(@Nullable CharSequence albumTitle) {
      this.albumTitle = albumTitle;
      return this;
    }

    /** Sets the album artist. */
    public Builder setAlbumArtist(@Nullable CharSequence albumArtist) {
      this.albumArtist = albumArtist;
      return this;
    }

    /** Sets the display title. */
    public Builder setDisplayTitle(@Nullable CharSequence displayTitle) {
      this.displayTitle = displayTitle;
      return this;
    }

    /**
     * Sets the subtitle.
     *
     * <p>This is the secondary title of the media, unrelated to closed captions.
     */
    public Builder setSubtitle(@Nullable CharSequence subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    /** Sets the description. */
    public Builder setDescription(@Nullable CharSequence description) {
      this.description = description;
      return this;
    }

    /** Sets the media {@link Uri}. */
    public Builder setMediaUri(@Nullable Uri mediaUri) {
      this.mediaUri = mediaUri;
      return this;
    }

    /** Sets the user {@link Rating}. */
    public Builder setUserRating(@Nullable Rating userRating) {
      this.userRating = userRating;
      return this;
    }

    /** Sets the overall {@link Rating}. */
    public Builder setOverallRating(@Nullable Rating overallRating) {
      this.overallRating = overallRating;
      return this;
    }

    /** Sets the artwork data as a compressed byte array. */
    public Builder setArtworkData(@Nullable byte[] artworkData) {
      this.artworkData = artworkData == null ? null : artworkData.clone();
      return this;
    }

    /** Sets the artwork {@link Uri}. */
    public Builder setArtworkUri(@Nullable Uri artworkUri) {
      this.artworkUri = artworkUri;
      return this;
    }

    /** Sets the track number. */
    public Builder setTrackNumber(@Nullable Integer trackNumber) {
      this.trackNumber = trackNumber;
      return this;
    }

    /** Sets the total number of tracks. */
    public Builder setTotalTrackCount(@Nullable Integer totalTrackCount) {
      this.totalTrackCount = totalTrackCount;
      return this;
    }

    /** Sets the {@link FolderType}. */
    public Builder setFolderType(@Nullable @FolderType Integer folderType) {
      this.folderType = folderType;
      return this;
    }

    /** Sets whether the media is playable. */
    public Builder setIsPlayable(@Nullable Boolean isPlayable) {
      this.isPlayable = isPlayable;
      return this;
    }

    /** Sets the year. */
    public Builder setYear(@Nullable Integer year) {
      this.year = year;
      return this;
    }

    /** Sets the extras {@link Bundle}. */
    public Builder setExtras(@Nullable Bundle extras) {
      this.extras = extras;
      return this;
    }

    /**
     * Sets all fields supported by the {@link Metadata.Entry entries} within the {@link Metadata}.
     *
     * <p>Fields are only set if the {@link Metadata.Entry} has an implementation for {@link
     * Metadata.Entry#populateMediaMetadata(Builder)}.
     *
     * <p>In the event that multiple {@link Metadata.Entry} objects within the {@link Metadata}
     * relate to the same {@link MediaMetadata} field, then the last one will be used.
     */
    public Builder populateFromMetadata(Metadata metadata) {
      for (int i = 0; i < metadata.length(); i++) {
        Metadata.Entry entry = metadata.get(i);
        entry.populateMediaMetadata(this);
      }
      return this;
    }

    /**
     * Sets all fields supported by the {@link Metadata.Entry entries} within the list of {@link
     * Metadata}.
     *
     * <p>Fields are only set if the {@link Metadata.Entry} has an implementation for {@link
     * Metadata.Entry#populateMediaMetadata(Builder)}.
     *
     * <p>In the event that multiple {@link Metadata.Entry} objects within any of the {@link
     * Metadata} relate to the same {@link MediaMetadata} field, then the last one will be used.
     */
    public Builder populateFromMetadata(List<Metadata> metadataList) {
      for (int i = 0; i < metadataList.size(); i++) {
        Metadata metadata = metadataList.get(i);
        for (int j = 0; j < metadata.length(); j++) {
          Metadata.Entry entry = metadata.get(j);
          entry.populateMediaMetadata(this);
        }
      }
      return this;
    }

    /** Returns a new {@link MediaMetadata} instance with the current builder values. */
    public MediaMetadata build() {
      return new MediaMetadata(/* builder= */ this);
    }
  }

  /**
   * The folder type of the media item.
   *
   * <p>This can be used as the type of a browsable bluetooth folder (see section 6.10.2.2 of the <a
   * href="https://www.bluetooth.com/specifications/specs/a-v-remote-control-profile-1-6-2/">Bluetooth
   * AVRCP 1.6.2</a>).
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FOLDER_TYPE_MIXED,
    FOLDER_TYPE_TITLES,
    FOLDER_TYPE_ALBUMS,
    FOLDER_TYPE_ARTISTS,
    FOLDER_TYPE_GENRES,
    FOLDER_TYPE_PLAYLISTS,
    FOLDER_TYPE_YEARS
  })
  public @interface FolderType {}

  /** Type for a folder containing media of mixed types. */
  public static final int FOLDER_TYPE_MIXED = 0;
  /** Type for a folder containing only playable media. */
  public static final int FOLDER_TYPE_TITLES = 1;
  /** Type for a folder containing media categorized by album. */
  public static final int FOLDER_TYPE_ALBUMS = 2;
  /** Type for a folder containing media categorized by artist. */
  public static final int FOLDER_TYPE_ARTISTS = 3;
  /** Type for a folder containing media categorized by genre. */
  public static final int FOLDER_TYPE_GENRES = 4;
  /** Type for a folder containing a playlist. */
  public static final int FOLDER_TYPE_PLAYLISTS = 5;
  /** Type for a folder containing media categorized by year. */
  public static final int FOLDER_TYPE_YEARS = 6;

  /** Empty {@link MediaMetadata}. */
  public static final MediaMetadata EMPTY = new MediaMetadata.Builder().build();

  /** Optional title. */
  @Nullable public final CharSequence title;
  /** Optional artist. */
  @Nullable public final CharSequence artist;
  /** Optional album title. */
  @Nullable public final CharSequence albumTitle;
  /** Optional album artist. */
  @Nullable public final CharSequence albumArtist;
  /** Optional display title. */
  @Nullable public final CharSequence displayTitle;
  /**
   * Optional subtitle.
   *
   * <p>This is the secondary title of the media, unrelated to closed captions.
   */
  @Nullable public final CharSequence subtitle;
  /** Optional description. */
  @Nullable public final CharSequence description;
  /** Optional media {@link Uri}. */
  @Nullable public final Uri mediaUri;
  /** Optional user {@link Rating}. */
  @Nullable public final Rating userRating;
  /** Optional overall {@link Rating}. */
  @Nullable public final Rating overallRating;
  /** Optional artwork data as a compressed byte array. */
  @Nullable public final byte[] artworkData;
  /** Optional artwork {@link Uri}. */
  @Nullable public final Uri artworkUri;
  /** Optional track number. */
  @Nullable public final Integer trackNumber;
  /** Optional total number of tracks. */
  @Nullable public final Integer totalTrackCount;
  /** Optional {@link FolderType}. */
  @Nullable @FolderType public final Integer folderType;
  /** Optional boolean for media playability. */
  @Nullable public final Boolean isPlayable;
  /** Optional year. */
  @Nullable public final Integer year;
  /**
   * Optional extras {@link Bundle}.
   *
   * <p>Given the complexities of checking the equality of two {@link Bundle}s, this is not
   * considered in the {@link #equals(Object)} or {@link #hashCode()}.
   */
  @Nullable public final Bundle extras;

  private MediaMetadata(Builder builder) {
    this.title = builder.title;
    this.artist = builder.artist;
    this.albumTitle = builder.albumTitle;
    this.albumArtist = builder.albumArtist;
    this.displayTitle = builder.displayTitle;
    this.subtitle = builder.subtitle;
    this.description = builder.description;
    this.mediaUri = builder.mediaUri;
    this.userRating = builder.userRating;
    this.overallRating = builder.overallRating;
    this.artworkData = builder.artworkData;
    this.artworkUri = builder.artworkUri;
    this.trackNumber = builder.trackNumber;
    this.totalTrackCount = builder.totalTrackCount;
    this.folderType = builder.folderType;
    this.isPlayable = builder.isPlayable;
    this.year = builder.year;
    this.extras = builder.extras;
  }

  /** Returns a new {@link Builder} instance with the current {@link MediaMetadata} fields. */
  public Builder buildUpon() {
    return new Builder(/* mediaMetadata= */ this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MediaMetadata that = (MediaMetadata) obj;
    return Util.areEqual(title, that.title)
        && Util.areEqual(artist, that.artist)
        && Util.areEqual(albumTitle, that.albumTitle)
        && Util.areEqual(albumArtist, that.albumArtist)
        && Util.areEqual(displayTitle, that.displayTitle)
        && Util.areEqual(subtitle, that.subtitle)
        && Util.areEqual(description, that.description)
        && Util.areEqual(mediaUri, that.mediaUri)
        && Util.areEqual(userRating, that.userRating)
        && Util.areEqual(overallRating, that.overallRating)
        && Arrays.equals(artworkData, that.artworkData)
        && Util.areEqual(artworkUri, that.artworkUri)
        && Util.areEqual(trackNumber, that.trackNumber)
        && Util.areEqual(totalTrackCount, that.totalTrackCount)
        && Util.areEqual(folderType, that.folderType)
        && Util.areEqual(isPlayable, that.isPlayable)
        && Util.areEqual(year, that.year);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        title,
        artist,
        albumTitle,
        albumArtist,
        displayTitle,
        subtitle,
        description,
        mediaUri,
        userRating,
        overallRating,
        Arrays.hashCode(artworkData),
        artworkUri,
        trackNumber,
        totalTrackCount,
        folderType,
        isPlayable,
        year);
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_TITLE,
    FIELD_ARTIST,
    FIELD_ALBUM_TITLE,
    FIELD_ALBUM_ARTIST,
    FIELD_DISPLAY_TITLE,
    FIELD_SUBTITLE,
    FIELD_DESCRIPTION,
    FIELD_MEDIA_URI,
    FIELD_USER_RATING,
    FIELD_OVERALL_RATING,
    FIELD_ARTWORK_DATA,
    FIELD_ARTWORK_URI,
    FIELD_TRACK_NUMBER,
    FIELD_TOTAL_TRACK_COUNT,
    FIELD_FOLDER_TYPE,
    FIELD_IS_PLAYABLE,
    FIELD_YEAR,
    FIELD_EXTRAS
  })
  private @interface FieldNumber {}

  private static final int FIELD_TITLE = 0;
  private static final int FIELD_ARTIST = 1;
  private static final int FIELD_ALBUM_TITLE = 2;
  private static final int FIELD_ALBUM_ARTIST = 3;
  private static final int FIELD_DISPLAY_TITLE = 4;
  private static final int FIELD_SUBTITLE = 5;
  private static final int FIELD_DESCRIPTION = 6;
  private static final int FIELD_MEDIA_URI = 7;
  private static final int FIELD_USER_RATING = 8;
  private static final int FIELD_OVERALL_RATING = 9;
  private static final int FIELD_ARTWORK_DATA = 10;
  private static final int FIELD_ARTWORK_URI = 11;
  private static final int FIELD_TRACK_NUMBER = 12;
  private static final int FIELD_TOTAL_TRACK_COUNT = 13;
  private static final int FIELD_FOLDER_TYPE = 14;
  private static final int FIELD_IS_PLAYABLE = 15;
  private static final int FIELD_YEAR = 16;
  private static final int FIELD_EXTRAS = 1000;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putCharSequence(keyForField(FIELD_TITLE), title);
    bundle.putCharSequence(keyForField(FIELD_ARTIST), artist);
    bundle.putCharSequence(keyForField(FIELD_ALBUM_TITLE), albumTitle);
    bundle.putCharSequence(keyForField(FIELD_ALBUM_ARTIST), albumArtist);
    bundle.putCharSequence(keyForField(FIELD_DISPLAY_TITLE), displayTitle);
    bundle.putCharSequence(keyForField(FIELD_SUBTITLE), subtitle);
    bundle.putCharSequence(keyForField(FIELD_DESCRIPTION), description);
    bundle.putParcelable(keyForField(FIELD_MEDIA_URI), mediaUri);
    bundle.putByteArray(keyForField(FIELD_ARTWORK_DATA), artworkData);
    bundle.putParcelable(keyForField(FIELD_ARTWORK_URI), artworkUri);

    if (userRating != null) {
      bundle.putBundle(keyForField(FIELD_USER_RATING), userRating.toBundle());
    }
    if (overallRating != null) {
      bundle.putBundle(keyForField(FIELD_OVERALL_RATING), overallRating.toBundle());
    }
    if (trackNumber != null) {
      bundle.putInt(keyForField(FIELD_TRACK_NUMBER), trackNumber);
    }
    if (totalTrackCount != null) {
      bundle.putInt(keyForField(FIELD_TOTAL_TRACK_COUNT), totalTrackCount);
    }
    if (folderType != null) {
      bundle.putInt(keyForField(FIELD_FOLDER_TYPE), folderType);
    }
    if (isPlayable != null) {
      bundle.putBoolean(keyForField(FIELD_IS_PLAYABLE), isPlayable);
    }
    if (year != null) {
      bundle.putInt(keyForField(FIELD_YEAR), year);
    }
    if (extras != null) {
      bundle.putBundle(keyForField(FIELD_EXTRAS), extras);
    }
    return bundle;
  }

  /** Object that can restore {@link MediaMetadata} from a {@link Bundle}. */
  public static final Creator<MediaMetadata> CREATOR = MediaMetadata::fromBundle;

  private static MediaMetadata fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    builder
        .setTitle(bundle.getCharSequence(keyForField(FIELD_TITLE)))
        .setArtist(bundle.getCharSequence(keyForField(FIELD_ARTIST)))
        .setAlbumTitle(bundle.getCharSequence(keyForField(FIELD_ALBUM_TITLE)))
        .setAlbumArtist(bundle.getCharSequence(keyForField(FIELD_ALBUM_ARTIST)))
        .setDisplayTitle(bundle.getCharSequence(keyForField(FIELD_DISPLAY_TITLE)))
        .setSubtitle(bundle.getCharSequence(keyForField(FIELD_SUBTITLE)))
        .setDescription(bundle.getCharSequence(keyForField(FIELD_DESCRIPTION)))
        .setMediaUri(bundle.getParcelable(keyForField(FIELD_MEDIA_URI)))
        .setArtworkData(bundle.getByteArray(keyForField(FIELD_ARTWORK_DATA)))
        .setArtworkUri(bundle.getParcelable(keyForField(FIELD_ARTWORK_URI)))
        .setExtras(bundle.getBundle(keyForField(FIELD_EXTRAS)));

    if (bundle.containsKey(keyForField(FIELD_USER_RATING))) {
      @Nullable Bundle fieldBundle = bundle.getBundle(keyForField(FIELD_USER_RATING));
      if (fieldBundle != null) {
        builder.setUserRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(keyForField(FIELD_OVERALL_RATING))) {
      @Nullable Bundle fieldBundle = bundle.getBundle(keyForField(FIELD_OVERALL_RATING));
      if (fieldBundle != null) {
        builder.setOverallRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(keyForField(FIELD_TRACK_NUMBER))) {
      builder.setTrackNumber(bundle.getInt(keyForField(FIELD_TRACK_NUMBER)));
    }
    if (bundle.containsKey(keyForField(FIELD_TOTAL_TRACK_COUNT))) {
      builder.setTotalTrackCount(bundle.getInt(keyForField(FIELD_TOTAL_TRACK_COUNT)));
    }
    if (bundle.containsKey(keyForField(FIELD_FOLDER_TYPE))) {
      builder.setFolderType(bundle.getInt(keyForField(FIELD_FOLDER_TYPE)));
    }
    if (bundle.containsKey(keyForField(FIELD_IS_PLAYABLE))) {
      builder.setIsPlayable(bundle.getBoolean(keyForField(FIELD_IS_PLAYABLE)));
    }
    if (bundle.containsKey(keyForField(FIELD_YEAR))) {
      builder.setYear(bundle.getInt(keyForField(FIELD_YEAR)));
    }

    return builder.build();
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

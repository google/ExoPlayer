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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
    @Nullable @PictureType private Integer artworkDataType;
    @Nullable private Uri artworkUri;
    @Nullable private Integer trackNumber;
    @Nullable private Integer totalTrackCount;
    @Nullable @FolderType private Integer folderType;
    @Nullable private Boolean isPlayable;
    @Nullable private Integer recordingYear;
    @Nullable private Integer recordingMonth;
    @Nullable private Integer recordingDay;
    @Nullable private Integer releaseYear;
    @Nullable private Integer releaseMonth;
    @Nullable private Integer releaseDay;
    @Nullable private CharSequence writer;
    @Nullable private CharSequence composer;
    @Nullable private CharSequence conductor;
    @Nullable private Integer discNumber;
    @Nullable private Integer totalDiscCount;
    @Nullable private CharSequence genre;
    @Nullable private CharSequence compilation;
    @Nullable private CharSequence station;
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
      this.artworkDataType = mediaMetadata.artworkDataType;
      this.artworkUri = mediaMetadata.artworkUri;
      this.trackNumber = mediaMetadata.trackNumber;
      this.totalTrackCount = mediaMetadata.totalTrackCount;
      this.folderType = mediaMetadata.folderType;
      this.isPlayable = mediaMetadata.isPlayable;
      this.recordingYear = mediaMetadata.recordingYear;
      this.recordingMonth = mediaMetadata.recordingMonth;
      this.recordingDay = mediaMetadata.recordingDay;
      this.releaseYear = mediaMetadata.releaseYear;
      this.releaseMonth = mediaMetadata.releaseMonth;
      this.releaseDay = mediaMetadata.releaseDay;
      this.writer = mediaMetadata.writer;
      this.composer = mediaMetadata.composer;
      this.conductor = mediaMetadata.conductor;
      this.discNumber = mediaMetadata.discNumber;
      this.totalDiscCount = mediaMetadata.totalDiscCount;
      this.genre = mediaMetadata.genre;
      this.compilation = mediaMetadata.compilation;
      this.station = mediaMetadata.station;
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

    /**
     * @deprecated Use {@link #setArtworkData(byte[] data, Integer pictureType)} or {@link
     *     #maybeSetArtworkData(byte[] data, int pictureType)}, providing a {@link PictureType}.
     */
    @Deprecated
    public Builder setArtworkData(@Nullable byte[] artworkData) {
      return setArtworkData(artworkData, /* artworkDataType= */ null);
    }

    /**
     * Sets the artwork data as a compressed byte array with an associated {@link PictureType
     * artworkDataType}.
     */
    public Builder setArtworkData(
        @Nullable byte[] artworkData, @Nullable @PictureType Integer artworkDataType) {
      this.artworkData = artworkData == null ? null : artworkData.clone();
      this.artworkDataType = artworkDataType;
      return this;
    }

    /**
     * Sets the artwork data as a compressed byte array in the event that the associated {@link
     * PictureType} is {@link #PICTURE_TYPE_FRONT_COVER}, the existing {@link PictureType} is not
     * {@link #PICTURE_TYPE_FRONT_COVER}, or the current artworkData is not set.
     *
     * <p>Use {@link #setArtworkData(byte[], Integer)} to set the artwork data without checking the
     * {@link PictureType}.
     */
    public Builder maybeSetArtworkData(byte[] artworkData, @PictureType int artworkDataType) {
      if (this.artworkData == null
          || Util.areEqual(artworkDataType, PICTURE_TYPE_FRONT_COVER)
          || !Util.areEqual(this.artworkDataType, PICTURE_TYPE_FRONT_COVER)) {
        this.artworkData = artworkData.clone();
        this.artworkDataType = artworkDataType;
      }
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

    /** @deprecated Use {@link #setRecordingYear(Integer)} instead. */
    @Deprecated
    public Builder setYear(@Nullable Integer year) {
      return setRecordingYear(year);
    }

    /** Sets the year of the recording date. */
    public Builder setRecordingYear(@Nullable Integer recordingYear) {
      this.recordingYear = recordingYear;
      return this;
    }

    /**
     * Sets the month of the recording date.
     *
     * <p>Value should be between 1 and 12.
     */
    public Builder setRecordingMonth(
        @Nullable @IntRange(from = 1, to = 12) Integer recordingMonth) {
      this.recordingMonth = recordingMonth;
      return this;
    }

    /**
     * Sets the day of the recording date.
     *
     * <p>Value should be between 1 and 31.
     */
    public Builder setRecordingDay(@Nullable @IntRange(from = 1, to = 31) Integer recordingDay) {
      this.recordingDay = recordingDay;
      return this;
    }

    /** Sets the year of the release date. */
    public Builder setReleaseYear(@Nullable Integer releaseYear) {
      this.releaseYear = releaseYear;
      return this;
    }

    /**
     * Sets the month of the release date.
     *
     * <p>Value should be between 1 and 12.
     */
    public Builder setReleaseMonth(@Nullable @IntRange(from = 1, to = 12) Integer releaseMonth) {
      this.releaseMonth = releaseMonth;
      return this;
    }

    /**
     * Sets the day of the release date.
     *
     * <p>Value should be between 1 and 31.
     */
    public Builder setReleaseDay(@Nullable @IntRange(from = 1, to = 31) Integer releaseDay) {
      this.releaseDay = releaseDay;
      return this;
    }

    /** Sets the writer. */
    public Builder setWriter(@Nullable CharSequence writer) {
      this.writer = writer;
      return this;
    }

    /** Sets the composer. */
    public Builder setComposer(@Nullable CharSequence composer) {
      this.composer = composer;
      return this;
    }

    /** Sets the conductor. */
    public Builder setConductor(@Nullable CharSequence conductor) {
      this.conductor = conductor;
      return this;
    }

    /** Sets the disc number. */
    public Builder setDiscNumber(@Nullable Integer discNumber) {
      this.discNumber = discNumber;
      return this;
    }

    /** Sets the total number of discs. */
    public Builder setTotalDiscCount(@Nullable Integer totalDiscCount) {
      this.totalDiscCount = totalDiscCount;
      return this;
    }

    /** Sets the genre. */
    public Builder setGenre(@Nullable CharSequence genre) {
      this.genre = genre;
      return this;
    }

    /** Sets the compilation. */
    public Builder setCompilation(@Nullable CharSequence compilation) {
      this.compilation = compilation;
      return this;
    }

    /** Sets the name of the station streaming the media. */
    public Builder setStation(@Nullable CharSequence station) {
      this.station = station;
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

    /** Populates all the fields from {@code mediaMetadata}, provided they are non-null. */
    public Builder populate(@Nullable MediaMetadata mediaMetadata) {
      if (mediaMetadata == null) {
        return this;
      }
      if (mediaMetadata.title != null) {
        setTitle(mediaMetadata.title);
      }
      if (mediaMetadata.artist != null) {
        setArtist(mediaMetadata.artist);
      }
      if (mediaMetadata.albumTitle != null) {
        setAlbumTitle(mediaMetadata.albumTitle);
      }
      if (mediaMetadata.albumArtist != null) {
        setAlbumArtist(mediaMetadata.albumArtist);
      }
      if (mediaMetadata.displayTitle != null) {
        setDisplayTitle(mediaMetadata.displayTitle);
      }
      if (mediaMetadata.subtitle != null) {
        setSubtitle(mediaMetadata.subtitle);
      }
      if (mediaMetadata.description != null) {
        setDescription(mediaMetadata.description);
      }
      if (mediaMetadata.mediaUri != null) {
        setMediaUri(mediaMetadata.mediaUri);
      }
      if (mediaMetadata.userRating != null) {
        setUserRating(mediaMetadata.userRating);
      }
      if (mediaMetadata.overallRating != null) {
        setOverallRating(mediaMetadata.overallRating);
      }
      if (mediaMetadata.artworkData != null) {
        setArtworkData(mediaMetadata.artworkData, mediaMetadata.artworkDataType);
      }
      if (mediaMetadata.artworkUri != null) {
        setArtworkUri(mediaMetadata.artworkUri);
      }
      if (mediaMetadata.trackNumber != null) {
        setTrackNumber(mediaMetadata.trackNumber);
      }
      if (mediaMetadata.totalTrackCount != null) {
        setTotalTrackCount(mediaMetadata.totalTrackCount);
      }
      if (mediaMetadata.folderType != null) {
        setFolderType(mediaMetadata.folderType);
      }
      if (mediaMetadata.isPlayable != null) {
        setIsPlayable(mediaMetadata.isPlayable);
      }
      if (mediaMetadata.year != null) {
        setRecordingYear(mediaMetadata.year);
      }
      if (mediaMetadata.recordingYear != null) {
        setRecordingYear(mediaMetadata.recordingYear);
      }
      if (mediaMetadata.recordingMonth != null) {
        setRecordingMonth(mediaMetadata.recordingMonth);
      }
      if (mediaMetadata.recordingDay != null) {
        setRecordingDay(mediaMetadata.recordingDay);
      }
      if (mediaMetadata.releaseYear != null) {
        setReleaseYear(mediaMetadata.releaseYear);
      }
      if (mediaMetadata.releaseMonth != null) {
        setReleaseMonth(mediaMetadata.releaseMonth);
      }
      if (mediaMetadata.releaseDay != null) {
        setReleaseDay(mediaMetadata.releaseDay);
      }
      if (mediaMetadata.writer != null) {
        setWriter(mediaMetadata.writer);
      }
      if (mediaMetadata.composer != null) {
        setComposer(mediaMetadata.composer);
      }
      if (mediaMetadata.conductor != null) {
        setConductor(mediaMetadata.conductor);
      }
      if (mediaMetadata.discNumber != null) {
        setDiscNumber(mediaMetadata.discNumber);
      }
      if (mediaMetadata.totalDiscCount != null) {
        setTotalDiscCount(mediaMetadata.totalDiscCount);
      }
      if (mediaMetadata.genre != null) {
        setGenre(mediaMetadata.genre);
      }
      if (mediaMetadata.compilation != null) {
        setCompilation(mediaMetadata.compilation);
      }
      if (mediaMetadata.station != null) {
        setStation(mediaMetadata.station);
      }
      if (mediaMetadata.extras != null) {
        setExtras(mediaMetadata.extras);
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
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    FOLDER_TYPE_NONE,
    FOLDER_TYPE_MIXED,
    FOLDER_TYPE_TITLES,
    FOLDER_TYPE_ALBUMS,
    FOLDER_TYPE_ARTISTS,
    FOLDER_TYPE_GENRES,
    FOLDER_TYPE_PLAYLISTS,
    FOLDER_TYPE_YEARS
  })
  public @interface FolderType {}

  /** Type for an item that is not a folder. */
  public static final int FOLDER_TYPE_NONE = -1;
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

  /**
   * The picture type of the artwork.
   *
   * <p>Values sourced from the ID3 v2.4 specification (See section 4.14 of
   * https://id3.org/id3v2.4.0-frames).
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    PICTURE_TYPE_OTHER,
    PICTURE_TYPE_FILE_ICON,
    PICTURE_TYPE_FILE_ICON_OTHER,
    PICTURE_TYPE_FRONT_COVER,
    PICTURE_TYPE_BACK_COVER,
    PICTURE_TYPE_LEAFLET_PAGE,
    PICTURE_TYPE_MEDIA,
    PICTURE_TYPE_LEAD_ARTIST_PERFORMER,
    PICTURE_TYPE_ARTIST_PERFORMER,
    PICTURE_TYPE_CONDUCTOR,
    PICTURE_TYPE_BAND_ORCHESTRA,
    PICTURE_TYPE_COMPOSER,
    PICTURE_TYPE_LYRICIST,
    PICTURE_TYPE_RECORDING_LOCATION,
    PICTURE_TYPE_DURING_RECORDING,
    PICTURE_TYPE_DURING_PERFORMANCE,
    PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE,
    PICTURE_TYPE_A_BRIGHT_COLORED_FISH,
    PICTURE_TYPE_ILLUSTRATION,
    PICTURE_TYPE_BAND_ARTIST_LOGO,
    PICTURE_TYPE_PUBLISHER_STUDIO_LOGO
  })
  public @interface PictureType {}

  public static final int PICTURE_TYPE_OTHER = 0x00;
  public static final int PICTURE_TYPE_FILE_ICON = 0x01;
  public static final int PICTURE_TYPE_FILE_ICON_OTHER = 0x02;
  public static final int PICTURE_TYPE_FRONT_COVER = 0x03;
  public static final int PICTURE_TYPE_BACK_COVER = 0x04;
  public static final int PICTURE_TYPE_LEAFLET_PAGE = 0x05;
  public static final int PICTURE_TYPE_MEDIA = 0x06;
  public static final int PICTURE_TYPE_LEAD_ARTIST_PERFORMER = 0x07;
  public static final int PICTURE_TYPE_ARTIST_PERFORMER = 0x08;
  public static final int PICTURE_TYPE_CONDUCTOR = 0x09;
  public static final int PICTURE_TYPE_BAND_ORCHESTRA = 0x0A;
  public static final int PICTURE_TYPE_COMPOSER = 0x0B;
  public static final int PICTURE_TYPE_LYRICIST = 0x0C;
  public static final int PICTURE_TYPE_RECORDING_LOCATION = 0x0D;
  public static final int PICTURE_TYPE_DURING_RECORDING = 0x0E;
  public static final int PICTURE_TYPE_DURING_PERFORMANCE = 0x0F;
  public static final int PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE = 0x10;
  public static final int PICTURE_TYPE_A_BRIGHT_COLORED_FISH = 0x11;
  public static final int PICTURE_TYPE_ILLUSTRATION = 0x12;
  public static final int PICTURE_TYPE_BAND_ARTIST_LOGO = 0x13;
  public static final int PICTURE_TYPE_PUBLISHER_STUDIO_LOGO = 0x14;

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
  /** Optional {@link PictureType} of the artwork data. */
  @Nullable public final @PictureType Integer artworkDataType;
  /** Optional artwork {@link Uri}. */
  @Nullable public final Uri artworkUri;
  /** Optional track number. */
  @Nullable public final Integer trackNumber;
  /** Optional total number of tracks. */
  @Nullable public final Integer totalTrackCount;
  /** Optional {@link FolderType}. */
  @Nullable public final @FolderType Integer folderType;
  /** Optional boolean for media playability. */
  @Nullable public final Boolean isPlayable;
  /** @deprecated Use {@link #recordingYear} instead. */
  @Deprecated @Nullable public final Integer year;
  /** Optional year of the recording date. */
  @Nullable public final Integer recordingYear;
  /**
   * Optional month of the recording date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer recordingMonth;
  /**
   * Optional day of the recording date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer recordingDay;

  /** Optional year of the release date. */
  @Nullable public final Integer releaseYear;
  /**
   * Optional month of the release date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer releaseMonth;
  /**
   * Optional day of the release date.
   *
   * <p>Note that there is no guarantee that the month and day are a valid combination.
   */
  @Nullable public final Integer releaseDay;
  /** Optional writer. */
  @Nullable public final CharSequence writer;
  /** Optional composer. */
  @Nullable public final CharSequence composer;
  /** Optional conductor. */
  @Nullable public final CharSequence conductor;
  /** Optional disc number. */
  @Nullable public final Integer discNumber;
  /** Optional total number of discs. */
  @Nullable public final Integer totalDiscCount;
  /** Optional genre. */
  @Nullable public final CharSequence genre;
  /** Optional compilation. */
  @Nullable public final CharSequence compilation;
  /** Optional name of the station streaming the media. */
  @Nullable public final CharSequence station;

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
    this.artworkDataType = builder.artworkDataType;
    this.artworkUri = builder.artworkUri;
    this.trackNumber = builder.trackNumber;
    this.totalTrackCount = builder.totalTrackCount;
    this.folderType = builder.folderType;
    this.isPlayable = builder.isPlayable;
    this.year = builder.recordingYear;
    this.recordingYear = builder.recordingYear;
    this.recordingMonth = builder.recordingMonth;
    this.recordingDay = builder.recordingDay;
    this.releaseYear = builder.releaseYear;
    this.releaseMonth = builder.releaseMonth;
    this.releaseDay = builder.releaseDay;
    this.writer = builder.writer;
    this.composer = builder.composer;
    this.conductor = builder.conductor;
    this.discNumber = builder.discNumber;
    this.totalDiscCount = builder.totalDiscCount;
    this.genre = builder.genre;
    this.compilation = builder.compilation;
    this.station = builder.station;
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
        && Util.areEqual(artworkDataType, that.artworkDataType)
        && Util.areEqual(artworkUri, that.artworkUri)
        && Util.areEqual(trackNumber, that.trackNumber)
        && Util.areEqual(totalTrackCount, that.totalTrackCount)
        && Util.areEqual(folderType, that.folderType)
        && Util.areEqual(isPlayable, that.isPlayable)
        && Util.areEqual(recordingYear, that.recordingYear)
        && Util.areEqual(recordingMonth, that.recordingMonth)
        && Util.areEqual(recordingDay, that.recordingDay)
        && Util.areEqual(releaseYear, that.releaseYear)
        && Util.areEqual(releaseMonth, that.releaseMonth)
        && Util.areEqual(releaseDay, that.releaseDay)
        && Util.areEqual(writer, that.writer)
        && Util.areEqual(composer, that.composer)
        && Util.areEqual(conductor, that.conductor)
        && Util.areEqual(discNumber, that.discNumber)
        && Util.areEqual(totalDiscCount, that.totalDiscCount)
        && Util.areEqual(genre, that.genre)
        && Util.areEqual(compilation, that.compilation)
        && Util.areEqual(station, that.station);
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
        artworkDataType,
        artworkUri,
        trackNumber,
        totalTrackCount,
        folderType,
        isPlayable,
        recordingYear,
        recordingMonth,
        recordingDay,
        releaseYear,
        releaseMonth,
        releaseDay,
        writer,
        composer,
        conductor,
        discNumber,
        totalDiscCount,
        genre,
        compilation,
        station);
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
    FIELD_ARTWORK_DATA_TYPE,
    FIELD_ARTWORK_URI,
    FIELD_TRACK_NUMBER,
    FIELD_TOTAL_TRACK_COUNT,
    FIELD_FOLDER_TYPE,
    FIELD_IS_PLAYABLE,
    FIELD_RECORDING_YEAR,
    FIELD_RECORDING_MONTH,
    FIELD_RECORDING_DAY,
    FIELD_RELEASE_YEAR,
    FIELD_RELEASE_MONTH,
    FIELD_RELEASE_DAY,
    FIELD_WRITER,
    FIELD_COMPOSER,
    FIELD_CONDUCTOR,
    FIELD_DISC_NUMBER,
    FIELD_TOTAL_DISC_COUNT,
    FIELD_GENRE,
    FIELD_COMPILATION,
    FIELD_STATION,
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
  private static final int FIELD_RECORDING_YEAR = 16;
  private static final int FIELD_RECORDING_MONTH = 17;
  private static final int FIELD_RECORDING_DAY = 18;
  private static final int FIELD_RELEASE_YEAR = 19;
  private static final int FIELD_RELEASE_MONTH = 20;
  private static final int FIELD_RELEASE_DAY = 21;
  private static final int FIELD_WRITER = 22;
  private static final int FIELD_COMPOSER = 23;
  private static final int FIELD_CONDUCTOR = 24;
  private static final int FIELD_DISC_NUMBER = 25;
  private static final int FIELD_TOTAL_DISC_COUNT = 26;
  private static final int FIELD_GENRE = 27;
  private static final int FIELD_COMPILATION = 28;
  private static final int FIELD_ARTWORK_DATA_TYPE = 29;
  private static final int FIELD_STATION = 30;
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
    bundle.putCharSequence(keyForField(FIELD_WRITER), writer);
    bundle.putCharSequence(keyForField(FIELD_COMPOSER), composer);
    bundle.putCharSequence(keyForField(FIELD_CONDUCTOR), conductor);
    bundle.putCharSequence(keyForField(FIELD_GENRE), genre);
    bundle.putCharSequence(keyForField(FIELD_COMPILATION), compilation);
    bundle.putCharSequence(keyForField(FIELD_STATION), station);

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
    if (recordingYear != null) {
      bundle.putInt(keyForField(FIELD_RECORDING_YEAR), recordingYear);
    }
    if (recordingMonth != null) {
      bundle.putInt(keyForField(FIELD_RECORDING_MONTH), recordingMonth);
    }
    if (recordingDay != null) {
      bundle.putInt(keyForField(FIELD_RECORDING_DAY), recordingDay);
    }
    if (releaseYear != null) {
      bundle.putInt(keyForField(FIELD_RELEASE_YEAR), releaseYear);
    }
    if (releaseMonth != null) {
      bundle.putInt(keyForField(FIELD_RELEASE_MONTH), releaseMonth);
    }
    if (releaseDay != null) {
      bundle.putInt(keyForField(FIELD_RELEASE_DAY), releaseDay);
    }
    if (discNumber != null) {
      bundle.putInt(keyForField(FIELD_DISC_NUMBER), discNumber);
    }
    if (totalDiscCount != null) {
      bundle.putInt(keyForField(FIELD_TOTAL_DISC_COUNT), totalDiscCount);
    }
    if (artworkDataType != null) {
      bundle.putInt(keyForField(FIELD_ARTWORK_DATA_TYPE), artworkDataType);
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
        .setArtworkData(
            bundle.getByteArray(keyForField(FIELD_ARTWORK_DATA)),
            bundle.containsKey(keyForField(FIELD_ARTWORK_DATA_TYPE))
                ? bundle.getInt(keyForField(FIELD_ARTWORK_DATA_TYPE))
                : null)
        .setArtworkUri(bundle.getParcelable(keyForField(FIELD_ARTWORK_URI)))
        .setWriter(bundle.getCharSequence(keyForField(FIELD_WRITER)))
        .setComposer(bundle.getCharSequence(keyForField(FIELD_COMPOSER)))
        .setConductor(bundle.getCharSequence(keyForField(FIELD_CONDUCTOR)))
        .setGenre(bundle.getCharSequence(keyForField(FIELD_GENRE)))
        .setCompilation(bundle.getCharSequence(keyForField(FIELD_COMPILATION)))
        .setStation(bundle.getCharSequence(keyForField(FIELD_STATION)))
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
    if (bundle.containsKey(keyForField(FIELD_RECORDING_YEAR))) {
      builder.setRecordingYear(bundle.getInt(keyForField(FIELD_RECORDING_YEAR)));
    }
    if (bundle.containsKey(keyForField(FIELD_RECORDING_MONTH))) {
      builder.setRecordingMonth(bundle.getInt(keyForField(FIELD_RECORDING_MONTH)));
    }
    if (bundle.containsKey(keyForField(FIELD_RECORDING_DAY))) {
      builder.setRecordingDay(bundle.getInt(keyForField(FIELD_RECORDING_DAY)));
    }
    if (bundle.containsKey(keyForField(FIELD_RELEASE_YEAR))) {
      builder.setReleaseYear(bundle.getInt(keyForField(FIELD_RELEASE_YEAR)));
    }
    if (bundle.containsKey(keyForField(FIELD_RELEASE_MONTH))) {
      builder.setReleaseMonth(bundle.getInt(keyForField(FIELD_RELEASE_MONTH)));
    }
    if (bundle.containsKey(keyForField(FIELD_RELEASE_DAY))) {
      builder.setReleaseDay(bundle.getInt(keyForField(FIELD_RELEASE_DAY)));
    }
    if (bundle.containsKey(keyForField(FIELD_DISC_NUMBER))) {
      builder.setDiscNumber(bundle.getInt(keyForField(FIELD_DISC_NUMBER)));
    }
    if (bundle.containsKey(keyForField(FIELD_TOTAL_DISC_COUNT))) {
      builder.setTotalDiscCount(bundle.getInt(keyForField(FIELD_TOTAL_DISC_COUNT)));
    }

    return builder.build();
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

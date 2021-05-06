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
import java.util.List;

/**
 * Metadata of a {@link MediaItem}, playlist, or a combination of multiple sources of {@link
 * Metadata}.
 */
public final class MediaMetadata implements Bundleable {

  /** A builder for {@link MediaMetadata} instances. */
  public static final class Builder {

    @Nullable private CharSequence trackTitle;
    @Nullable private CharSequence trackArtist;
    @Nullable private CharSequence albumTitle;
    @Nullable private CharSequence albumArtist;
    @Nullable private CharSequence displayTitle;
    @Nullable private CharSequence subtitle;
    @Nullable private CharSequence description;
    @Nullable private Uri mediaUri;
    @Nullable private Rating userRating;
    @Nullable private Rating overallRating;

    public Builder() {}

    private Builder(MediaMetadata mediaMetadata) {
      this.trackTitle = mediaMetadata.trackTitle;
      this.trackArtist = mediaMetadata.trackArtist;
      this.albumTitle = mediaMetadata.albumTitle;
      this.albumArtist = mediaMetadata.albumArtist;
      this.displayTitle = mediaMetadata.displayTitle;
      this.subtitle = mediaMetadata.subtitle;
      this.mediaUri = mediaMetadata.mediaUri;
      this.userRating = mediaMetadata.userRating;
      this.overallRating = mediaMetadata.overallRating;
    }

    /** @deprecated Use {@link #setTrackTitle(CharSequence)} instead. */
    @Deprecated
    public Builder setTitle(@Nullable String title) {
      this.trackTitle = title;
      return this;
    }

    /** Sets the optional track title. */
    public Builder setTrackTitle(@Nullable CharSequence trackTitle) {
      this.trackTitle = trackTitle;
      return this;
    }

    public Builder setTrackArtist(@Nullable CharSequence trackArtist) {
      this.trackArtist = trackArtist;
      return this;
    }

    public Builder setAlbumTitle(@Nullable CharSequence albumTitle) {
      this.albumTitle = albumTitle;
      return this;
    }

    public Builder setAlbumArtist(@Nullable CharSequence albumArtist) {
      this.albumArtist = albumArtist;
      return this;
    }

    public Builder setDisplayTitle(@Nullable CharSequence displayTitle) {
      this.displayTitle = displayTitle;
      return this;
    }

    public Builder setSubtitle(@Nullable CharSequence subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    public Builder setDescription(@Nullable CharSequence description) {
      this.description = description;
      return this;
    }

    public Builder setMediaUri(@Nullable Uri mediaUri) {
      this.mediaUri = mediaUri;
      return this;
    }

    public Builder setUserRating(@Nullable Rating userRating) {
      this.userRating = userRating;
      return this;
    }

    public Builder setOverallRating(@Nullable Rating overallRating) {
      this.overallRating = overallRating;
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

  /** Empty {@link MediaMetadata}. */
  public static final MediaMetadata EMPTY = new MediaMetadata.Builder().build();

  /** @deprecated Use {@link #trackTitle} instead. */
  @Deprecated @Nullable public final String title;

  @Nullable public final CharSequence trackTitle;
  @Nullable public final CharSequence trackArtist;
  @Nullable public final CharSequence albumTitle;
  @Nullable public final CharSequence albumArtist;
  @Nullable public final CharSequence displayTitle;
  @Nullable public final CharSequence subtitle;
  @Nullable public final CharSequence description;
  @Nullable public final Uri mediaUri;
  @Nullable public final Rating userRating;
  @Nullable public final Rating overallRating;

  private MediaMetadata(Builder builder) {
    this.title = builder.trackTitle != null ? builder.trackTitle.toString() : null;
    this.trackTitle = builder.trackTitle;
    this.trackArtist = builder.trackArtist;
    this.albumTitle = builder.albumTitle;
    this.albumArtist = builder.albumArtist;
    this.displayTitle = builder.displayTitle;
    this.subtitle = builder.subtitle;
    this.description = builder.description;
    this.mediaUri = builder.mediaUri;
    this.userRating = builder.userRating;
    this.overallRating = builder.overallRating;
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
    return Util.areEqual(trackTitle, that.trackTitle)
        && Util.areEqual(trackArtist, that.trackArtist)
        && Util.areEqual(albumTitle, that.albumTitle)
        && Util.areEqual(albumArtist, that.albumArtist)
        && Util.areEqual(displayTitle, that.displayTitle)
        && Util.areEqual(subtitle, that.subtitle)
        && Util.areEqual(description, that.description)
        && Util.areEqual(mediaUri, that.mediaUri)
        && Util.areEqual(userRating, that.userRating)
        && Util.areEqual(overallRating, that.overallRating);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        trackTitle,
        trackArtist,
        albumTitle,
        albumArtist,
        displayTitle,
        subtitle,
        description,
        mediaUri,
        userRating,
        overallRating);
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_TRACK_TITLE,
    FIELD_TRACK_ARTIST,
    FIELD_ALBUM_TITLE,
    FIELD_ALBUM_ARTIST,
    FIELD_DISPLAY_TITLE,
    FIELD_SUBTITLE,
    FIELD_DESCRIPTION,
    FIELD_MEDIA_URI,
    FIELD_USER_RATING,
    FIELD_OVERALL_RATING,
  })
  private @interface FieldNumber {}

  private static final int FIELD_TRACK_TITLE = 0;
  private static final int FIELD_TRACK_ARTIST = 1;
  private static final int FIELD_ALBUM_TITLE = 2;
  private static final int FIELD_ALBUM_ARTIST = 3;
  private static final int FIELD_DISPLAY_TITLE = 4;
  private static final int FIELD_SUBTITLE = 5;
  private static final int FIELD_DESCRIPTION = 6;
  private static final int FIELD_MEDIA_URI = 7;
  private static final int FIELD_USER_RATING = 8;
  private static final int FIELD_OVERALL_RATING = 9;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putCharSequence(keyForField(FIELD_TRACK_TITLE), trackTitle);
    bundle.putCharSequence(keyForField(FIELD_TRACK_ARTIST), trackArtist);
    bundle.putCharSequence(keyForField(FIELD_ALBUM_TITLE), albumTitle);
    bundle.putCharSequence(keyForField(FIELD_ALBUM_ARTIST), albumArtist);
    bundle.putCharSequence(keyForField(FIELD_DISPLAY_TITLE), displayTitle);
    bundle.putCharSequence(keyForField(FIELD_SUBTITLE), subtitle);
    bundle.putCharSequence(keyForField(FIELD_DESCRIPTION), description);
    bundle.putParcelable(keyForField(FIELD_MEDIA_URI), mediaUri);

    if (userRating != null) {
      bundle.putBundle(keyForField(FIELD_USER_RATING), userRating.toBundle());
    }
    if (overallRating != null) {
      bundle.putBundle(keyForField(FIELD_OVERALL_RATING), overallRating.toBundle());
    }

    return bundle;
  }

  /** Object that can restore {@link MediaMetadata} from a {@link Bundle}. */
  public static final Creator<MediaMetadata> CREATOR = MediaMetadata::fromBundle;

  private static MediaMetadata fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    builder
        .setTrackTitle(bundle.getCharSequence(keyForField(FIELD_TRACK_TITLE)))
        .setTrackArtist(bundle.getCharSequence(keyForField(FIELD_TRACK_ARTIST)))
        .setAlbumTitle(bundle.getCharSequence(keyForField(FIELD_ALBUM_TITLE)))
        .setAlbumArtist(bundle.getCharSequence(keyForField(FIELD_ALBUM_ARTIST)))
        .setDisplayTitle(bundle.getCharSequence(keyForField(FIELD_DISPLAY_TITLE)))
        .setSubtitle(bundle.getCharSequence(keyForField(FIELD_SUBTITLE)))
        .setDescription(bundle.getCharSequence(keyForField(FIELD_DESCRIPTION)))
        .setMediaUri(bundle.getParcelable(keyForField(FIELD_MEDIA_URI)));

    if (bundle.containsKey(keyForField(FIELD_USER_RATING))) {
      @Nullable Bundle fieldBundle = bundle.getBundle(keyForField(FIELD_USER_RATING));
      if (fieldBundle != null) {
        builder.setUserRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }
    if (bundle.containsKey(keyForField(FIELD_OVERALL_RATING))) {
      @Nullable Bundle fieldBundle = bundle.getBundle(keyForField(FIELD_OVERALL_RATING));
      if (fieldBundle != null) {
        builder.setUserRating(Rating.CREATOR.fromBundle(fieldBundle));
      }
    }

    return builder.build();
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.BundleableUtil.fromBundleNullableList;
import static com.google.android.exoplayer2.util.BundleableUtil.toBundleArrayList;
import static java.util.Collections.max;
import static java.util.Collections.min;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Forces the selection of the specified tracks in {@link TrackGroup TrackGroups}.
 *
 * <p>Each {@link TrackSelectionOverride override} only affects the selection of tracks of that
 * {@link C.TrackType type}. For example overriding the selection of an {@link C#TRACK_TYPE_AUDIO
 * audio} {@link TrackGroup} will not affect the selection of {@link C#TRACK_TYPE_VIDEO video} or
 * {@link C#TRACK_TYPE_TEXT text} tracks.
 *
 * <p>If multiple {@link TrackGroup TrackGroups} of the same {@link C.TrackType} are overridden,
 * which tracks will be selected depend on the player capabilities. For example, by default {@code
 * ExoPlayer} doesn't support selecting more than one {@link TrackGroup} per {@link C.TrackType}.
 *
 * <p>Overrides of {@link TrackGroup} that are not currently available are ignored. For example,
 * when the player transitions to the next {@link MediaItem} in a playlist, any overrides of the
 * previous {@link MediaItem} are ignored.
 *
 * @see TrackSelectionParameters#trackSelectionOverrides
 */
public final class TrackSelectionOverrides implements Bundleable {

  /** Builder for {@link TrackSelectionOverrides}. */
  public static final class Builder {
    // Cannot use ImmutableMap.Builder as it doesn't support removing entries.
    private final HashMap<TrackGroup, TrackSelectionOverride> overrides;

    /** Creates an builder with no {@link TrackSelectionOverride}. */
    public Builder() {
      overrides = new HashMap<>();
    }

    private Builder(Map<TrackGroup, TrackSelectionOverride> overrides) {
      this.overrides = new HashMap<>(overrides);
    }

    /** Adds an override for the provided {@link TrackGroup}. */
    public Builder addOverride(TrackSelectionOverride override) {
      overrides.put(override.trackGroup, override);
      return this;
    }

    /** Removes the override associated with the provided {@link TrackGroup} if present. */
    public Builder clearOverride(TrackGroup trackGroup) {
      overrides.remove(trackGroup);
      return this;
    }

    /** Set the override for the type of the provided {@link TrackGroup}. */
    public Builder setOverrideForType(TrackSelectionOverride override) {
      clearOverridesOfType(override.getTrackType());
      overrides.put(override.trackGroup, override);
      return this;
    }

    /**
     * Remove any override associated with {@link TrackGroup TrackGroups} of type {@code trackType}.
     */
    public Builder clearOverridesOfType(@C.TrackType int trackType) {
      for (Iterator<TrackSelectionOverride> it = overrides.values().iterator(); it.hasNext(); ) {
        TrackSelectionOverride trackSelectionOverride = it.next();
        if (trackSelectionOverride.getTrackType() == trackType) {
          it.remove();
        }
      }
      return this;
    }

    /** Returns a new {@link TrackSelectionOverrides} instance with the current builder values. */
    public TrackSelectionOverrides build() {
      return new TrackSelectionOverrides(overrides);
    }
  }

  /**
   * Forces the selection of {@link #trackIndices} for a {@link TrackGroup}.
   *
   * <p>If multiple tracks in {@link #trackGroup} are overridden, as many as possible will be
   * selected depending on the player capabilities.
   *
   * <p>If {@link #trackIndices} is empty, no tracks from {@link #trackGroup} will be played. This
   * is similar to {@link TrackSelectionParameters#disabledTrackTypes}, except it will only affect
   * the playback of the associated {@link TrackGroup}. For example, if the only {@link
   * C#TRACK_TYPE_VIDEO} {@link TrackGroup} is associated with no tracks, no video will play until
   * the next video starts.
   */
  public static final class TrackSelectionOverride implements Bundleable {

    /** The {@link TrackGroup} whose {@link #trackIndices} are forced to be selected. */
    public final TrackGroup trackGroup;
    /** The indices of tracks in a {@link TrackGroup} to be selected. */
    public final ImmutableList<Integer> trackIndices;

    /** Constructs an instance to force all tracks in {@code trackGroup} to be selected. */
    public TrackSelectionOverride(TrackGroup trackGroup) {
      this.trackGroup = trackGroup;
      ImmutableList.Builder<Integer> builder = new ImmutableList.Builder<>();
      for (int i = 0; i < trackGroup.length; i++) {
        builder.add(i);
      }
      this.trackIndices = builder.build();
    }

    /**
     * Constructs an instance to force {@code trackIndices} in {@code trackGroup} to be selected.
     *
     * @param trackGroup The {@link TrackGroup} for which to override the track selection.
     * @param trackIndices The indices of the tracks in the {@link TrackGroup} to select.
     */
    public TrackSelectionOverride(TrackGroup trackGroup, List<Integer> trackIndices) {
      if (!trackIndices.isEmpty()) {
        if (min(trackIndices) < 0 || max(trackIndices) >= trackGroup.length) {
          throw new IndexOutOfBoundsException();
        }
      }
      this.trackGroup = trackGroup;
      this.trackIndices = ImmutableList.copyOf(trackIndices);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TrackSelectionOverride that = (TrackSelectionOverride) obj;
      return trackGroup.equals(that.trackGroup) && trackIndices.equals(that.trackIndices);
    }

    @Override
    public int hashCode() {
      return trackGroup.hashCode() + 31 * trackIndices.hashCode();
    }

    /** Returns the {@link C.TrackType} of the overriden track group. */
    public @C.TrackType int getTrackType() {
      return MimeTypes.getTrackType(trackGroup.getFormat(0).sampleMimeType);
    }

    // Bundleable implementation

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_TRACK_GROUP,
      FIELD_TRACKS,
    })
    private @interface FieldNumber {}

    private static final int FIELD_TRACK_GROUP = 0;
    private static final int FIELD_TRACKS = 1;

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBundle(keyForField(FIELD_TRACK_GROUP), trackGroup.toBundle());
      bundle.putIntArray(keyForField(FIELD_TRACKS), Ints.toArray(trackIndices));
      return bundle;
    }

    /** Object that can restore {@code TrackSelectionOverride} from a {@link Bundle}. */
    public static final Creator<TrackSelectionOverride> CREATOR =
        bundle -> {
          @Nullable Bundle trackGroupBundle = bundle.getBundle(keyForField(FIELD_TRACK_GROUP));
          checkNotNull(trackGroupBundle); // Mandatory as there are no reasonable defaults.
          TrackGroup trackGroup = TrackGroup.CREATOR.fromBundle(trackGroupBundle);
          @Nullable int[] tracks = bundle.getIntArray(keyForField(FIELD_TRACKS));
          if (tracks == null) {
            return new TrackSelectionOverride(trackGroup);
          }
          return new TrackSelectionOverride(trackGroup, Ints.asList(tracks));
        };

    private static String keyForField(@FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
  }

  /** Empty {@code TrackSelectionOverrides}, where no track selection is overridden. */
  public static final TrackSelectionOverrides EMPTY =
      new TrackSelectionOverrides(ImmutableMap.of());

  private final ImmutableMap<TrackGroup, TrackSelectionOverride> overrides;

  private TrackSelectionOverrides(Map<TrackGroup, TrackSelectionOverride> overrides) {
    this.overrides = ImmutableMap.copyOf(overrides);
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(overrides);
  }

  /** Returns a list of the {@link TrackSelectionOverride overrides}. */
  public ImmutableList<TrackSelectionOverride> asList() {
    return ImmutableList.copyOf(overrides.values());
  }

  /**
   * Returns the {@link TrackSelectionOverride} of the provided {@link TrackGroup} or {@code null}
   * if there is none.
   */
  @Nullable
  public TrackSelectionOverride getOverride(TrackGroup trackGroup) {
    return overrides.get(trackGroup);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionOverrides that = (TrackSelectionOverrides) obj;
    return overrides.equals(that.overrides);
  }

  @Override
  public int hashCode() {
    return overrides.hashCode();
  }

  // Bundleable implementation

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_OVERRIDES,
  })
  private @interface FieldNumber {}

  private static final int FIELD_OVERRIDES = 0;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        keyForField(FIELD_OVERRIDES), toBundleArrayList(overrides.values()));
    return bundle;
  }

  /** Object that can restore {@code TrackSelectionOverrides} from a {@link Bundle}. */
  public static final Creator<TrackSelectionOverrides> CREATOR =
      bundle -> {
        List<TrackSelectionOverride> trackSelectionOverrides =
            fromBundleNullableList(
                TrackSelectionOverride.CREATOR,
                bundle.getParcelableArrayList(keyForField(FIELD_OVERRIDES)),
                ImmutableList.of());
        ImmutableMap.Builder<TrackGroup, TrackSelectionOverride> builder =
            new ImmutableMap.Builder<>();
        for (int i = 0; i < trackSelectionOverrides.size(); i++) {
          TrackSelectionOverride trackSelectionOverride = trackSelectionOverrides.get(i);
          builder.put(trackSelectionOverride.trackGroup, trackSelectionOverride);
        }
        return new TrackSelectionOverrides(builder.build());
      };

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.BundleableUtil.fromBundleNullableList;
import static com.google.android.exoplayer2.util.BundleableUtil.fromNullableBundle;
import static com.google.android.exoplayer2.util.BundleableUtil.toBundleArrayList;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

/** Information about groups of tracks. */
public final class TracksInfo implements Bundleable {
  /**
   * Information about a single group of tracks, including the underlying {@link TrackGroup}, the
   * {@link C.TrackType type} of tracks it contains, and the level to which each track is supported
   * by the player.
   */
  public static final class TrackGroupInfo implements Bundleable {
    private final TrackGroup trackGroup;
    private final @C.FormatSupport int[] trackSupport;
    private final @C.TrackType int trackType;
    private final boolean[] trackSelected;

    /**
     * Constructs a TrackGroupInfo.
     *
     * @param trackGroup The {@link TrackGroup} described.
     * @param trackSupport The {@link C.FormatSupport} of each track in the {@code trackGroup}.
     * @param trackType The {@link C.TrackType} of the tracks in the {@code trackGroup}.
     * @param tracksSelected Whether a track is selected for each track in {@code trackGroup}.
     */
    public TrackGroupInfo(
        TrackGroup trackGroup,
        @C.FormatSupport int[] trackSupport,
        @C.TrackType int trackType,
        boolean[] tracksSelected) {
      int length = trackGroup.length;
      checkArgument(length == trackSupport.length && length == tracksSelected.length);
      this.trackGroup = trackGroup;
      this.trackSupport = trackSupport.clone();
      this.trackType = trackType;
      this.trackSelected = tracksSelected.clone();
    }

    /** Returns the {@link TrackGroup} described by this {@code TrackGroupInfo}. */
    public TrackGroup getTrackGroup() {
      return trackGroup;
    }

    /**
     * Returns the level of support for a specified track.
     *
     * @param trackIndex The index of the track in the {@link TrackGroup}.
     * @return The {@link C.FormatSupport} of the track.
     */
    public @C.FormatSupport int getTrackSupport(int trackIndex) {
      return trackSupport[trackIndex];
    }

    /**
     * Returns whether a specified track is supported for playback, without exceeding the advertised
     * capabilities of the device. Equivalent to {@code isTrackSupported(trackIndex, false)}.
     *
     * @param trackIndex The index of the track in the {@link TrackGroup}.
     * @return True if the track's format can be played, false otherwise.
     */
    public boolean isTrackSupported(int trackIndex) {
      return isTrackSupported(trackIndex, /* allowExceedsCapabilities= */ false);
    }

    /**
     * Returns whether a specified track is supported for playback.
     *
     * @param trackIndex The index of the track in the {@link TrackGroup}.
     * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
     *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
     *     capabilities of the device. For example, a video track for which there's a corresponding
     *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
     *     Such tracks may be playable in some cases.
     * @return True if the track's format can be played, false otherwise.
     */
    public boolean isTrackSupported(int trackIndex, boolean allowExceedsCapabilities) {
      return trackSupport[trackIndex] == C.FORMAT_HANDLED
          || (allowExceedsCapabilities
              && trackSupport[trackIndex] == C.FORMAT_EXCEEDS_CAPABILITIES);
    }

    /** Returns whether at least one track in the group is selected for playback. */
    public boolean isSelected() {
      return Booleans.contains(trackSelected, true);
    }

    /**
     * Returns whether at least one track in the group is supported for playback, without exceeding
     * the advertised capabilities of the device. Equivalent to {@code isSupported(false)}.
     */
    public boolean isSupported() {
      return isSupported(/* allowExceedsCapabilities= */ false);
    }

    /**
     * Returns whether at least one track in the group is supported for playback.
     *
     * @param allowExceedsCapabilities Whether to consider a track as supported if it has a
     *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
     *     capabilities of the device. For example, a video track for which there's a corresponding
     *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
     *     Such tracks may be playable in some cases.
     */
    public boolean isSupported(boolean allowExceedsCapabilities) {
      for (int i = 0; i < trackSupport.length; i++) {
        if (isTrackSupported(i, allowExceedsCapabilities)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns whether a specified track is selected for playback.
     *
     * <p>Note that multiple tracks in the group may be selected. This is common in adaptive
     * streaming, where tracks of different qualities are selected and the player switches between
     * them during playback (e.g., based on the available network bandwidth).
     *
     * <p>This class doesn't provide a way to determine which of the selected tracks is currently
     * playing, however some player implementations have ways of getting such information. For
     * example, ExoPlayer provides this information via {@code ExoTrackSelection.getSelectedFormat}.
     *
     * @param trackIndex The index of the track in the {@link TrackGroup}.
     * @return True if the track is selected, false otherwise.
     */
    public boolean isTrackSelected(int trackIndex) {
      return trackSelected[trackIndex];
    }

    /** Returns the {@link C.TrackType} of the group. */
    public @C.TrackType int getTrackType() {
      return trackType;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      TrackGroupInfo that = (TrackGroupInfo) other;
      return trackType == that.trackType
          && trackGroup.equals(that.trackGroup)
          && Arrays.equals(trackSupport, that.trackSupport)
          && Arrays.equals(trackSelected, that.trackSelected);
    }

    @Override
    public int hashCode() {
      int result = trackGroup.hashCode();
      result = 31 * result + Arrays.hashCode(trackSupport);
      result = 31 * result + trackType;
      result = 31 * result + Arrays.hashCode(trackSelected);
      return result;
    }

    // Bundleable implementation.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({
      FIELD_TRACK_GROUP,
      FIELD_TRACK_SUPPORT,
      FIELD_TRACK_TYPE,
      FIELD_TRACK_SELECTED,
    })
    private @interface FieldNumber {}

    private static final int FIELD_TRACK_GROUP = 0;
    private static final int FIELD_TRACK_SUPPORT = 1;
    private static final int FIELD_TRACK_TYPE = 2;
    private static final int FIELD_TRACK_SELECTED = 3;

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBundle(keyForField(FIELD_TRACK_GROUP), trackGroup.toBundle());
      bundle.putIntArray(keyForField(FIELD_TRACK_SUPPORT), trackSupport);
      bundle.putInt(keyForField(FIELD_TRACK_TYPE), trackType);
      bundle.putBooleanArray(keyForField(FIELD_TRACK_SELECTED), trackSelected);
      return bundle;
    }

    /** Object that can restores a {@code TracksInfo} from a {@link Bundle}. */
    public static final Creator<TrackGroupInfo> CREATOR =
        bundle -> {
          TrackGroup trackGroup =
              fromNullableBundle(
                  TrackGroup.CREATOR, bundle.getBundle(keyForField(FIELD_TRACK_GROUP)));
          checkNotNull(trackGroup); // Can't create a trackGroup info without a trackGroup
          final @C.FormatSupport int[] trackSupport =
              MoreObjects.firstNonNull(
                  bundle.getIntArray(keyForField(FIELD_TRACK_SUPPORT)), new int[trackGroup.length]);
          @C.TrackType
          int trackType = bundle.getInt(keyForField(FIELD_TRACK_TYPE), C.TRACK_TYPE_UNKNOWN);
          boolean[] selected =
              MoreObjects.firstNonNull(
                  bundle.getBooleanArray(keyForField(FIELD_TRACK_SELECTED)),
                  new boolean[trackGroup.length]);
          return new TrackGroupInfo(trackGroup, trackSupport, trackType, selected);
        };

    private static String keyForField(@FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
  }

  private final ImmutableList<TrackGroupInfo> trackGroupInfos;

  /** An {@code TrackInfo} that contains no tracks. */
  public static final TracksInfo EMPTY = new TracksInfo(ImmutableList.of());

  /**
   * Constructs an instance.
   *
   * @param trackGroupInfos The {@link TrackGroupInfo TrackGroupInfos} describing the groups of
   *     tracks.
   */
  public TracksInfo(List<TrackGroupInfo> trackGroupInfos) {
    this.trackGroupInfos = ImmutableList.copyOf(trackGroupInfos);
  }

  /** Returns the {@link TrackGroupInfo TrackGroupInfos} describing the groups of tracks. */
  public ImmutableList<TrackGroupInfo> getTrackGroupInfos() {
    return trackGroupInfos;
  }

  /**
   * Returns true if at least one track of type {@code trackType} is {@link
   * TrackGroupInfo#isTrackSupported(int) supported} or if there are no tracks of this type.
   */
  public boolean isTypeSupportedOrEmpty(@C.TrackType int trackType) {
    return isTypeSupportedOrEmpty(trackType, /* allowExceedsCapabilities= */ false);
  }

  /**
   * Returns true if at least one track of type {@code trackType} is {@link
   * TrackGroupInfo#isTrackSupported(int, boolean) supported} or if there are no tracks of this
   * type.
   *
   * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
   *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
   *     capabilities of the device. For example, a video track for which there's a corresponding
   *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
   *     Such tracks may be playable in some cases.
   */
  public boolean isTypeSupportedOrEmpty(
      @C.TrackType int trackType, boolean allowExceedsCapabilities) {
    boolean supported = true;
    for (int i = 0; i < trackGroupInfos.size(); i++) {
      if (trackGroupInfos.get(i).trackType == trackType) {
        if (trackGroupInfos.get(i).isSupported(allowExceedsCapabilities)) {
          return true;
        } else {
          supported = false;
        }
      }
    }
    return supported;
  }

  /** Returns true if at least one track of the type {@code trackType} is selected for playback. */
  public boolean isTypeSelected(@C.TrackType int trackType) {
    for (int i = 0; i < trackGroupInfos.size(); i++) {
      TrackGroupInfo trackGroupInfo = trackGroupInfos.get(i);
      if (trackGroupInfo.isSelected() && trackGroupInfo.getTrackType() == trackType) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    TracksInfo that = (TracksInfo) other;
    return trackGroupInfos.equals(that.trackGroupInfos);
  }

  @Override
  public int hashCode() {
    return trackGroupInfos.hashCode();
  }
  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FIELD_TRACK_GROUP_INFOS,
  })
  private @interface FieldNumber {}

  private static final int FIELD_TRACK_GROUP_INFOS = 0;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        keyForField(FIELD_TRACK_GROUP_INFOS), toBundleArrayList(trackGroupInfos));
    return bundle;
  }

  /** Object that can restore a {@code TracksInfo} from a {@link Bundle}. */
  public static final Creator<TracksInfo> CREATOR =
      bundle -> {
        List<TrackGroupInfo> trackGroupInfos =
            fromBundleNullableList(
                TrackGroupInfo.CREATOR,
                bundle.getParcelableArrayList(keyForField(FIELD_TRACK_GROUP_INFOS)),
                /* defaultValue= */ ImmutableList.of());
        return new TracksInfo(trackGroupInfos);
      };

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.Assertions;

import java.util.Arrays;

/**
 * A track selection, consisting of a {@link TrackGroup} and a selected subset of the tracks within
 * it. The selected tracks are exposed in order of decreasing bandwidth.
 */
public final class TrackSelection {

  /**
   * The selected {@link TrackGroup}.
   */
  public final TrackGroup group;
  /**
   * The number of selected tracks within the {@link TrackGroup}. Always greater than zero.
   */
  public final int length;

  private final int[] tracks;
  private final Format[] formats;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   */
  public TrackSelection(TrackGroup group, int... tracks) {
    Assertions.checkState(tracks.length > 0);
    this.group = Assertions.checkNotNull(group);
    this.length = tracks.length;
    // Set the formats, sorted in order of decreasing bandwidth.
    formats = new Format[length];
    for (int i = 0; i < tracks.length; i++) {
      formats[i] = group.getFormat(tracks[i]);
    }
    Arrays.sort(formats, new DecreasingBandwidthComparator());
    // Set the format indices in the same order.
    this.tracks = new int[length];
    for (int i = 0; i < length; i++) {
      this.tracks[i] = group.indexOf(formats[i]);
    }
  }

  /**
   * Gets the format of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The format of the selected track.
   */
  public Format getFormat(int index) {
    return formats[index];
  }

  /**
   * Gets a copy of the formats of the selected tracks.
   *
   * @return The track formats.
   */
  public Format[] getFormats() {
    return formats.clone();
  }

  /**
   * Gets the index in the selection of the track with the specified format.
   *
   * @param format The format.
   * @return The index in the selection, or -1 if the track with the specified format is not part of
   *     the selection.
   */
  public int indexOf(Format format) {
    for (int i = 0; i < length; i++) {
      if (formats[i] == format) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Gets the index in the track group of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The index of the selected track.
   */
  public int getTrack(int index) {
    return tracks[index];
  }

  /**
   * Gets a copy of the selected tracks in the track group.
   *
   * @return The track indices.
   */
  public int[] getTracks() {
    return tracks.clone();
  }

  /**
   * Gets the index in the selection of the track with the specified index in the track group.
   *
   * @param trackIndex The index in the track group.
   * @return The index in the selection, or -1 if the track with the specified index is not part of
   *     the selection.
   */
  public int indexOf(int trackIndex) {
    for (int i = 0; i < length; i++) {
      if (tracks[i] == trackIndex) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = 31 * System.identityHashCode(group) + Arrays.hashCode(tracks);
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelection other = (TrackSelection) obj;
    return group == other.group && Arrays.equals(tracks, other.tracks);
  }

}

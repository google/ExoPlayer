/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

/** Sample table for a track in an MP4 file. */
/* package */ final class TrackSampleTable {

  /** Sample index when no sample is available. */
  public static final int NO_SAMPLE = -1;

  /** Number of samples. */
  public final int sampleCount;
  /** Sample offsets in bytes. */
  public final long[] offsets;
  /** Sample sizes in bytes. */
  public final int[] sizes;
  /** Sample timestamps in microseconds. */
  public final long[] timestampsUs;
  /** Sample flags. */
  public final int[] flags;

  TrackSampleTable(
      long[] offsets, int[] sizes, long[] timestampsUs, int[] flags) {
    Assertions.checkArgument(sizes.length == timestampsUs.length);
    Assertions.checkArgument(offsets.length == timestampsUs.length);
    Assertions.checkArgument(flags.length == timestampsUs.length);

    this.offsets = offsets;
    this.sizes = sizes;
    this.timestampsUs = timestampsUs;
    this.flags = flags;
    sampleCount = offsets.length;
  }

  /**
   * Returns the sample index of the closest synchronization sample at or before the given
   * timestamp, if one is available.
   *
   * @param timeUs Timestamp adjacent to which to find a synchronization sample.
   * @return Index of the synchronization sample, or {@link #NO_SAMPLE} if none.
   */
  public int getIndexOfEarlierOrEqualSynchronizationSample(long timeUs) {
    int startIndex = Util.binarySearchFloor(timestampsUs, timeUs, true, false);
    for (int i = startIndex; i >= 0; i--) {
      if (timestampsUs[i] <= timeUs && (flags[i] & C.SAMPLE_FLAG_SYNC) != 0) {
        return i;
      }
    }
    return NO_SAMPLE;
  }

  /**
   * Returns the sample index of the closest synchronization sample at or after the given timestamp,
   * if one is available.
   *
   * @param timeUs Timestamp adjacent to which to find a synchronization sample.
   * @return index Index of the synchronization sample, or {@link #NO_SAMPLE} if none.
   */
  public int getIndexOfLaterOrEqualSynchronizationSample(long timeUs) {
    int startIndex = Util.binarySearchCeil(timestampsUs, timeUs, true, false);
    for (int i = startIndex; i < timestampsUs.length; i++) {
      if (timestampsUs[i] >= timeUs && (flags[i] & C.SAMPLE_FLAG_SYNC) != 0) {
        return i;
      }
    }
    return NO_SAMPLE;
  }

}

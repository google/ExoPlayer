/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import java.util.List;

/** A {@link TrackBitrateEstimator} which derives estimates from a window of time. */
public final class WindowedTrackBitrateEstimator implements TrackBitrateEstimator {

  private final long maxFutureDurationUs;
  private final long maxPastDurationUs;
  private final boolean useFormatBitrateAsLowerBound;

  /**
   * @param maxFutureDurationUs Maximum duration of future chunks to be included in average bitrate
   *     values, in microseconds.
   * @param maxPastDurationUs Maximum duration of past chunks to be included in average bitrate
   *     values, in microseconds.
   * @param useFormatBitrateAsLowerBound Whether to return the estimated bitrate only if it's higher
   *     than the bitrate of the track's format.
   */
  public WindowedTrackBitrateEstimator(
      long maxFutureDurationUs, long maxPastDurationUs, boolean useFormatBitrateAsLowerBound) {
    this.maxFutureDurationUs = maxFutureDurationUs;
    this.maxPastDurationUs = maxPastDurationUs;
    this.useFormatBitrateAsLowerBound = useFormatBitrateAsLowerBound;
  }

  @Override
  public int[] getBitrates(
      Format[] formats,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] iterators,
      @Nullable int[] bitrates) {
    if (maxFutureDurationUs > 0 || maxPastDurationUs > 0) {
      return TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
          formats,
          queue,
          maxPastDurationUs,
          iterators,
          maxFutureDurationUs,
          useFormatBitrateAsLowerBound,
          bitrates);
    }
    return TrackSelectionUtil.getFormatBitrates(formats, bitrates);
  }
}

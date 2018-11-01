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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.util.Assertions;

/** Track selection related utility methods. */
public final class TrackSelectionUtil {

  private TrackSelectionUtil() {}

  /**
   * Returns average bitrate for chunks in bits per second. Chunks are included in average until
   * {@code maxDurationMs} or the first unknown length chunk.
   *
   * @param iterator Iterator for media chunk sequences.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate, in
   *     microseconds.
   * @return Average bitrate for chunks in bits per second, or {@link Format#NO_VALUE} if there are
   *     no chunks or the first chunk length is unknown.
   */
  public static int getAverageBitrate(MediaChunkIterator iterator, long maxDurationUs) {
    long totalDurationUs = 0;
    long totalLength = 0;
    while (iterator.next()) {
      long chunkLength = iterator.getDataSpec().length;
      if (chunkLength == C.LENGTH_UNSET) {
        break;
      }
      long chunkDurationUs = iterator.getChunkEndTimeUs() - iterator.getChunkStartTimeUs();
      if (totalDurationUs + chunkDurationUs >= maxDurationUs) {
        totalLength += chunkLength * (maxDurationUs - totalDurationUs) / chunkDurationUs;
        totalDurationUs = maxDurationUs;
        break;
      }
      totalDurationUs += chunkDurationUs;
      totalLength += chunkLength;
    }
    return totalDurationUs == 0
        ? Format.NO_VALUE
        : (int) (totalLength * C.BITS_PER_BYTE * C.MICROS_PER_SECOND / totalDurationUs);
  }

  /**
   * Returns average bitrate values for a set of tracks whose upcoming media chunk iterators and
   * formats are given. If an average bitrate can't be calculated, an estimation is calculated using
   * average bitrate of another track and the ratio of the bitrate values defined in the formats of
   * the two tracks.
   *
   * @param iterators An array of {@link MediaChunkIterator}s providing information about the
   *     sequence of upcoming media chunks for each track.
   * @param formats The track formats.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate values, in
   *     microseconds.
   * @return Average bitrate values for the tracks. If for a track, an average bitrate or an
   *     estimation can't be calculated, {@link Format#NO_VALUE} is set.
   * @see #getAverageBitrate(MediaChunkIterator, long)
   */
  public static int[] getAverageBitrates(
      MediaChunkIterator[] iterators, Format[] formats, long maxDurationUs) {
    int trackCount = iterators.length;
    Assertions.checkArgument(trackCount == formats.length);
    if (trackCount == 0) {
      return new int[0];
    }

    int[] bitrates = new int[trackCount];
    int[] formatBitrates = new int[trackCount];
    float[] bitrateRatios = new float[trackCount];
    boolean needEstimateBitrate = false;
    boolean canEstimateBitrate = false;
    for (int i = 0; i < trackCount; i++) {
      int bitrate = getAverageBitrate(iterators[i], maxDurationUs);
      if (bitrate != Format.NO_VALUE) {
        int formatBitrate = formats[i].bitrate;
        formatBitrates[i] = formatBitrate;
        if (formatBitrate != Format.NO_VALUE) {
          bitrateRatios[i] = ((float) bitrate) / formatBitrate;
          canEstimateBitrate = true;
        }
      } else {
        needEstimateBitrate = true;
        formatBitrates[i] = Format.NO_VALUE;
      }
      bitrates[i] = bitrate;
    }

    if (needEstimateBitrate && canEstimateBitrate) {
      for (int i = 0; i < trackCount; i++) {
        if (bitrates[i] == Format.NO_VALUE) {
          int formatBitrate = formats[i].bitrate;
          if (formatBitrate != Format.NO_VALUE) {
            int closestFormat = findClosestBitrateFormat(formatBitrate, formatBitrates);
            bitrates[i] = (int) (bitrateRatios[closestFormat] * formatBitrate);
          }
        }
      }
    }
    return bitrates;
  }

  private static int findClosestBitrateFormat(int formatBitrate, int[] formatBitrates) {
    int closestDistance = Integer.MAX_VALUE;
    int closestFormat = C.INDEX_UNSET;
    for (int j = 0; j < formatBitrates.length; j++) {
      if (formatBitrates[j] != Format.NO_VALUE) {
        int distance = Math.abs(formatBitrates[j] - formatBitrate);
        if (distance < closestDistance) {
          closestFormat = j;
        }
      }
    }
    return closestFormat;
  }
}

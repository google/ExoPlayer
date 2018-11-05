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
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.MediaChunkListIterator;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Arrays;
import java.util.List;

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
   * Returns bitrate values for a set of tracks whose upcoming media chunk iterators and formats are
   * given.
   *
   * <p>If an average bitrate can't be calculated, an estimation is calculated using average bitrate
   * of another track and the ratio of the bitrate values defined in the formats of the two tracks.
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
  public static int[] getBitratesUsingFutureInfo(
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
      estimateBitrates(bitrates, formats, formatBitrates, bitrateRatios);
    }
    return bitrates;
  }

  /**
   * Returns bitrate values for a set of tracks whose formats are given, using the given queue of
   * already buffered {@link MediaChunk} instances.
   *
   * @param queue The queue of already buffered {@link MediaChunk} instances. Must not be modified.
   * @param formats The track formats.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate values, in
   *     microseconds.
   * @return Bitrate values for the tracks. If for a track, a bitrate value can't be calculated,
   *     {@link Format#NO_VALUE} is set.
   * @see #getBitratesUsingFutureInfo(MediaChunkIterator[], Format[], long)
   */
  public static int[] getBitratesUsingPastInfo(
      List<? extends MediaChunk> queue, Format[] formats, long maxDurationUs) {
    int[] bitrates = new int[formats.length];
    Arrays.fill(bitrates, Format.NO_VALUE);
    int queueAverageBitrate = getAverageQueueBitrate(queue, maxDurationUs);
    if (queueAverageBitrate == Format.NO_VALUE) {
      return bitrates;
    }
    int queueFormatBitrate = queue.get(queue.size() - 1).trackFormat.bitrate;
    if (queueFormatBitrate != Format.NO_VALUE) {
      float queueBitrateRatio = ((float) queueAverageBitrate) / queueFormatBitrate;
      estimateBitrates(
          bitrates, formats, new int[] {queueFormatBitrate}, new float[] {queueBitrateRatio});
    }
    return bitrates;
  }

  private static int getAverageQueueBitrate(List<? extends MediaChunk> queue, long maxDurationUs) {
    if (queue.isEmpty()) {
      return Format.NO_VALUE;
    }
    MediaChunkListIterator iterator =
        new MediaChunkListIterator(getSingleFormatSubQueue(queue), /* reverseOrder= */ true);
    return getAverageBitrate(iterator, maxDurationUs);
  }

  private static List<? extends MediaChunk> getSingleFormatSubQueue(
      List<? extends MediaChunk> queue) {
    Format queueFormat = queue.get(queue.size() - 1).trackFormat;
    int queueSize = queue.size();
    for (int i = queueSize - 2; i >= 0; i--) {
      if (!queue.get(i).trackFormat.equals(queueFormat)) {
        return queue.subList(i + 1, queueSize);
      }
    }
    return queue;
  }

  private static void estimateBitrates(
      int[] bitrates, Format[] formats, int[] formatBitrates, float[] bitrateRatios) {
    for (int i = 0; i < bitrates.length; i++) {
      if (bitrates[i] == Format.NO_VALUE) {
        int formatBitrate = formats[i].bitrate;
        if (formatBitrate != Format.NO_VALUE) {
          int closestFormat = getClosestBitrateIndex(formatBitrate, formatBitrates);
          bitrates[i] = (int) (bitrateRatios[closestFormat] * formatBitrate);
        }
      }
    }
  }

  private static int getClosestBitrateIndex(int formatBitrate, int[] formatBitrates) {
    int closestDistance = Integer.MAX_VALUE;
    int closestFormat = C.INDEX_UNSET;
    for (int j = 0; j < formatBitrates.length; j++) {
      if (formatBitrates[j] != Format.NO_VALUE) {
        int distance = Math.abs(formatBitrates[j] - formatBitrate);
        if (distance < closestDistance) {
          closestDistance = distance;
          closestFormat = j;
        }
      }
    }
    return closestFormat;
  }
}

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
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;

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
   * @return Average bitrate for chunks in bits per second, or {@link C#LENGTH_UNSET} if there are
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
        ? C.LENGTH_UNSET
        : (int) (totalLength * C.BITS_PER_BYTE * C.MICROS_PER_SECOND / totalDurationUs);
  }
}

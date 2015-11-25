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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;

/**
 * Scales and adjusts MPEG-2 TS presentation timestamps, taking into account an initial offset and
 * timestamp rollover.
 */
public final class PtsTimestampAdjuster {

  /**
   * The value one greater than the largest representable (33 bit) presentation timestamp.
   */
  private static final long MAX_PTS_PLUS_ONE = 0x200000000L;

  private final long firstSampleTimestampUs;

  private long timestampOffsetUs;
  private long lastPts;

  /**
   * @param firstSampleTimestampUs The desired result of the first call to
   *     {@link #adjustTimestamp(long)}.
   */
  public PtsTimestampAdjuster(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    lastPts = Long.MIN_VALUE;
  }

  /**
   * Resets the instance to its initial state.
   */
  public void reset() {
    lastPts = Long.MIN_VALUE;
  }

  /**
   * Scales and adjusts an MPEG-2 TS presentation timestamp.
   *
   * @param pts The unscaled MPEG-2 TS presentation timestamp.
   * @return The adjusted timestamp in microseconds.
   */
  public long adjustTimestamp(long pts) {
    if (lastPts != Long.MIN_VALUE) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastPts.
      long closestWrapCount = (lastPts + (MAX_PTS_PLUS_ONE / 2)) / MAX_PTS_PLUS_ONE;
      long ptsWrapBelow = pts + (MAX_PTS_PLUS_ONE * (closestWrapCount - 1));
      long ptsWrapAbove = pts + (MAX_PTS_PLUS_ONE * closestWrapCount);
      pts = Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
          ? ptsWrapBelow : ptsWrapAbove;
    }
    // Calculate the corresponding timestamp.
    long timeUs = (pts * C.MICROS_PER_SECOND) / 90000;
    // If we haven't done the initial timestamp adjustment, do it now.
    if (lastPts == Long.MIN_VALUE) {
      timestampOffsetUs = firstSampleTimestampUs - timeUs;
    }
    // Record the adjusted PTS to adjust for wraparound next time.
    lastPts = pts;
    return timeUs + timestampOffsetUs;
  }

}

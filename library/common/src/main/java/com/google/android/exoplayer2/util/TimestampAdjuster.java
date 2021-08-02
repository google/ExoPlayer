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
package com.google.android.exoplayer2.util;

import androidx.annotation.GuardedBy;
import com.google.android.exoplayer2.C;

/**
 * Offsets timestamps according to an initial sample timestamp offset. MPEG-2 TS timestamps scaling
 * and adjustment is supported, taking into account timestamp rollover.
 */
public final class TimestampAdjuster {

  /**
   * A special {@code firstSampleTimestampUs} value indicating that presentation timestamps should
   * not be offset.
   */
  public static final long DO_NOT_OFFSET = Long.MAX_VALUE;

  /**
   * The value one greater than the largest representable (33 bit) MPEG-2 TS 90 kHz clock
   * presentation timestamp.
   */
  private static final long MAX_PTS_PLUS_ONE = 0x200000000L;

  @GuardedBy("this")
  private boolean sharedInitializationStarted;

  @GuardedBy("this")
  private long firstSampleTimestampUs;

  @GuardedBy("this")
  private long timestampOffsetUs;

  @GuardedBy("this")
  private long lastUnadjustedTimestampUs;

  /**
   * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp in
   *     microseconds, or {@link #DO_NOT_OFFSET} if timestamps should not be offset.
   */
  public TimestampAdjuster(long firstSampleTimestampUs) {
    reset(firstSampleTimestampUs);
  }

  /**
   * For shared timestamp adjusters, performs necessary initialization actions for a caller.
   *
   * <ul>
   *   <li>If the adjuster does not yet have a target {@link #getFirstSampleTimestampUs first sample
   *       timestamp} and if {@code canInitialize} is {@code true}, then initialization is started
   *       by setting the target first sample timestamp to {@code firstSampleTimestampUs}. The call
   *       returns, allowing the caller to proceed. Initialization completes when a caller adjusts
   *       the first timestamp.
   *   <li>If {@code canInitialize} is {@code true} and the adjuster already has a target {@link
   *       #getFirstSampleTimestampUs first sample timestamp}, then the call returns to allow the
   *       caller to proceed only if {@code firstSampleTimestampUs} is equal to the target. This
   *       ensures a caller that's previously started initialization can continue to proceed. It
   *       also allows other callers with the same {@code firstSampleTimestampUs} to proceed, since
   *       in this case it doesn't matter which caller adjusts the first timestamp to complete
   *       initialization.
   *   <li>If {@code canInitialize} is {@code false} or if {@code firstSampleTimestampUs} differs
   *       from the target {@link #getFirstSampleTimestampUs first sample timestamp}, then the call
   *       blocks until initialization completes. If initialization has already been completed the
   *       call returns immediately.
   * </ul>
   *
   * @param canInitialize Whether the caller is able to initialize the adjuster, if needed.
   * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp in
   *     microseconds. Only used if {@code canInitialize} is {@code true}.
   * @throws InterruptedException If the thread is interrupted whilst blocked waiting for
   *     initialization to complete.
   */
  public synchronized void sharedInitializeOrWait(
      boolean canInitialize, long firstSampleTimestampUs) throws InterruptedException {
    if (canInitialize && !sharedInitializationStarted) {
      reset(firstSampleTimestampUs);
      sharedInitializationStarted = true;
    }
    if (!canInitialize || this.firstSampleTimestampUs != firstSampleTimestampUs) {
      while (timestampOffsetUs == C.TIME_UNSET) {
        wait();
      }
    }
  }

  /**
   * Returns the value of the first adjusted sample timestamp in microseconds, or {@link
   * #DO_NOT_OFFSET} if timestamps will not be offset.
   */
  public synchronized long getFirstSampleTimestampUs() {
    return firstSampleTimestampUs;
  }

  /**
   * Returns the last value obtained from {@link #adjustSampleTimestamp}. If {@link
   * #adjustSampleTimestamp} has not been called, returns the result of calling {@link
   * #getFirstSampleTimestampUs()} unless that value is {@link #DO_NOT_OFFSET}, in which case {@link
   * C#TIME_UNSET} is returned.
   */
  public synchronized long getLastAdjustedTimestampUs() {
    return lastUnadjustedTimestampUs != C.TIME_UNSET
        ? lastUnadjustedTimestampUs + timestampOffsetUs
        : firstSampleTimestampUs != DO_NOT_OFFSET ? firstSampleTimestampUs : C.TIME_UNSET;
  }

  /**
   * Returns the offset between the input of {@link #adjustSampleTimestamp(long)} and its output, or
   * {@link C#TIME_UNSET} if the offset has not yet been determined.
   */
  public synchronized long getTimestampOffsetUs() {
    return timestampOffsetUs;
  }

  /**
   * Resets the instance.
   *
   * @param firstSampleTimestampUs The desired value of the first adjusted sample timestamp after
   *     this reset, in microseconds, or {@link #DO_NOT_OFFSET} if timestamps should not be offset.
   */
  public synchronized void reset(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    timestampOffsetUs = firstSampleTimestampUs == DO_NOT_OFFSET ? 0 : C.TIME_UNSET;
    lastUnadjustedTimestampUs = C.TIME_UNSET;
    sharedInitializationStarted = false;
  }

  /**
   * Scales and offsets an MPEG-2 TS presentation timestamp considering wraparound.
   *
   * @param pts90Khz A 90 kHz clock MPEG-2 TS presentation timestamp.
   * @return The adjusted timestamp in microseconds.
   */
  public synchronized long adjustTsTimestamp(long pts90Khz) {
    if (pts90Khz == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    if (lastUnadjustedTimestampUs != C.TIME_UNSET) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastSampleTimestampUs.
      long lastPts = usToNonWrappedPts(lastUnadjustedTimestampUs);
      long closestWrapCount = (lastPts + (MAX_PTS_PLUS_ONE / 2)) / MAX_PTS_PLUS_ONE;
      long ptsWrapBelow = pts90Khz + (MAX_PTS_PLUS_ONE * (closestWrapCount - 1));
      long ptsWrapAbove = pts90Khz + (MAX_PTS_PLUS_ONE * closestWrapCount);
      pts90Khz =
          Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
              ? ptsWrapBelow
              : ptsWrapAbove;
    }
    return adjustSampleTimestamp(ptsToUs(pts90Khz));
  }

  /**
   * Offsets a timestamp in microseconds.
   *
   * @param timeUs The timestamp to adjust in microseconds.
   * @return The adjusted timestamp in microseconds.
   */
  public synchronized long adjustSampleTimestamp(long timeUs) {
    if (timeUs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    if (timestampOffsetUs == C.TIME_UNSET) {
      timestampOffsetUs = firstSampleTimestampUs - timeUs;
      // Notify threads waiting for the timestamp offset to be determined.
      notifyAll();
    }
    lastUnadjustedTimestampUs = timeUs;
    return timeUs + timestampOffsetUs;
  }

  /**
   * Converts a 90 kHz clock timestamp to a timestamp in microseconds.
   *
   * @param pts A 90 kHz clock timestamp.
   * @return The corresponding value in microseconds.
   */
  public static long ptsToUs(long pts) {
    return (pts * C.MICROS_PER_SECOND) / 90000;
  }

  /**
   * Converts a timestamp in microseconds to a 90 kHz clock timestamp, performing wraparound to keep
   * the result within 33-bits.
   *
   * @param us A value in microseconds.
   * @return The corresponding value as a 90 kHz clock timestamp, wrapped to 33 bits.
   */
  public static long usToWrappedPts(long us) {
    return usToNonWrappedPts(us) % MAX_PTS_PLUS_ONE;
  }

  /**
   * Converts a timestamp in microseconds to a 90 kHz clock timestamp.
   *
   * <p>Does not perform any wraparound. To get a 90 kHz timestamp suitable for use with MPEG-TS,
   * use {@link #usToWrappedPts(long)}.
   *
   * @param us A value in microseconds.
   * @return The corresponding value as a 90 kHz clock timestamp.
   */
  public static long usToNonWrappedPts(long us) {
    return (us * 90000) / C.MICROS_PER_SECOND;
  }
}

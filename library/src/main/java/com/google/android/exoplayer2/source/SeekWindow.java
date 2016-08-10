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
package com.google.android.exoplayer2.source;

/**
 * A window of times the player can seek to.
 */
public final class SeekWindow {

  public static final SeekWindow UNSEEKABLE = createWindowFromZero(0);

  /**
   * Creates a new {@link SeekWindow} containing times from zero up to {@code durationUs} in the
   * first period.
   *
   * @param durationUs The duration of the window, in microseconds.
   */
  public static SeekWindow createWindowFromZero(long durationUs) {
    return createWindow(0, 0, 0, durationUs);
  }

  /**
   * Creates a new {@link SeekWindow} representing the specified time range.
   *
   * @param startPeriodIndex The index of the period containing the start of the window.
   * @param startTimeUs The start time of the window in microseconds, relative to the start of the
   *     specified start period.
   * @param endPeriodIndex The index of the period containing the end of the window.
   * @param endTimeUs = The end time of the window in microseconds, relative to the start of the
   *     specified end period.
   */
  public static SeekWindow createWindow(int startPeriodIndex, long startTimeUs,
      int endPeriodIndex, long endTimeUs) {
    return new SeekWindow(startPeriodIndex, startTimeUs / 1000, endPeriodIndex, endTimeUs / 1000);
  }

  /**
   * The period index at the start of the window.
   */
  public final int startPeriodIndex;
  /**
   * The time at the start of the window relative to the start of the period at
   * {@link #startPeriodIndex}, in milliseconds.
   */
  public final long startTimeMs;
  /**
   * The period index at the end of the window.
   */
  public final int endPeriodIndex;
  /**
   * The time at the end of the window relative to the start of the period at
   * {@link #endPeriodIndex}, in milliseconds.
   */
  public final long endTimeMs;

  private SeekWindow(int startPeriodIndex, long startTimeMs, int endPeriodIndex, long endTimeMs) {
    this.startPeriodIndex = startPeriodIndex;
    this.startTimeMs = startTimeMs;
    this.endPeriodIndex = endPeriodIndex;
    this.endTimeMs = endTimeMs;
  }

  /**
   * Returns a new seek window that is offset by the specified number of periods.
   *
   * @param periodCount The number of periods to add to {@link #startPeriodIndex} and
   *     {@link #endPeriodIndex} when constructing the copy.
   * @return A new seek window that is offset by the specified number of periods.
   */
  public SeekWindow copyOffsetByPeriodCount(int periodCount) {
    return new SeekWindow(startPeriodIndex + periodCount, startTimeMs, endPeriodIndex + periodCount,
        endTimeMs);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + startPeriodIndex;
    result = 31 * result + (int) startTimeMs;
    result = 31 * result + endPeriodIndex;
    result = 31 * result + (int) endTimeMs;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SeekWindow other = (SeekWindow) obj;
    return other.startPeriodIndex == startPeriodIndex
        && other.startTimeMs == startTimeMs
        && other.endPeriodIndex == endPeriodIndex
        && other.endTimeMs == endTimeMs;
  }

  @Override
  public String toString() {
    return "SeekWindow[" + startPeriodIndex + ", " + startTimeMs + ", " + endPeriodIndex + ", "
        + endTimeMs + "]";
  }

}

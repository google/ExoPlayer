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
package com.google.android.exoplayer2;

/**
 * A window of available media. Instances are immutable.
 */
public final class Window {

  /**
   * Creates a new {@link Window} consisting of a single period starting at time zero and with the
   * specified duration. The default initial position is the start of the window.
   *
   * @param durationUs The duration of the window, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether this seek window may change when the timeline is updated.
   */
  public static Window createWindowFromZero(long durationUs, boolean isSeekable,
      boolean isDynamic) {
    return createWindow(0, 0, 0, durationUs, durationUs, isSeekable, isDynamic, 0, 0);
  }

  /**
   * Creates a new {@link Window} representing the specified time range. The default initial
   * position is the start of the window.
   *
   * @param startPeriodIndex The index of the period containing the start of the window.
   * @param startTimeUs The start time of the window in microseconds, relative to the start of the
   *     specified start period.
   * @param endPeriodIndex The index of the period containing the end of the window.
   * @param endTimeUs The end time of the window in microseconds, relative to the start of the
   *     specified end period.
   * @param durationUs The duration of the window in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether this seek window may change when the timeline is updated.
   */
  public static Window createWindow(int startPeriodIndex, long startTimeUs,
      int endPeriodIndex, long endTimeUs, long durationUs, boolean isSeekable, boolean isDynamic) {
    return createWindow(startPeriodIndex, startTimeUs, endPeriodIndex, endTimeUs, durationUs,
        isSeekable, isDynamic, startPeriodIndex, startTimeUs);
  }

  /**
   * Creates a new {@link Window} representing the specified time range.
   *
   * @param startPeriodIndex The index of the period containing the start of the window.
   * @param startTimeUs The start time of the window in microseconds, relative to the start of the
   *     specified start period.
   * @param endPeriodIndex The index of the period containing the end of the window.
   * @param endTimeUs The end time of the window in microseconds, relative to the start of the
   *     specified end period.
   * @param durationUs The duration of the window in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether this seek window may change when the timeline is updated.
   * @param defaultInitialPeriodIndex The index of the period containing the default position from
   *     which playback should start.
   * @param defaultInitialTimeUs The time of the default position from which playback should start
   *     in microseconds, relative to the start of the period that contains it.
   */
  public static Window createWindow(int startPeriodIndex, long startTimeUs,
      int endPeriodIndex, long endTimeUs, long durationUs, boolean isSeekable, boolean isDynamic,
      int defaultInitialPeriodIndex, long defaultInitialTimeUs) {
    return new Window(startPeriodIndex, startTimeUs / 1000, endPeriodIndex,
        endTimeUs == C.UNSET_TIME_US ? ExoPlayer.UNKNOWN_TIME : (endTimeUs / 1000),
        durationUs == C.UNSET_TIME_US ? ExoPlayer.UNKNOWN_TIME : (durationUs / 1000),
        isSeekable, isDynamic, defaultInitialPeriodIndex, defaultInitialTimeUs / 1000);
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
  /**
   * The duration of the window in milliseconds, or {@link C#UNSET_TIME_US} if unknown.
   */
  public final long durationMs;
  /**
   * Whether it's possible to seek within the window.
   */
  public final boolean isSeekable;
  /**
   * Whether this seek window may change when the timeline is updated.
   */
  public final boolean isDynamic;
  /**
   * The period index of the default position from which playback should start.
   */
  public final int defaultInitialPeriodIndex;
  /**
   * The time of the default position relative to the start of the period at
   * {@link #defaultInitialPeriodIndex}, in milliseconds.
   */
  public final long defaultInitialTimeMs;

  private Window(int startPeriodIndex, long startTimeMs, int endPeriodIndex, long endTimeMs,
      long durationMs, boolean isSeekable, boolean isDynamic, int defaultInitialPeriodIndex,
      long defaultInitialTimeMs) {
    this.startPeriodIndex = startPeriodIndex;
    this.startTimeMs = startTimeMs;
    this.endPeriodIndex = endPeriodIndex;
    this.endTimeMs = endTimeMs;
    this.durationMs = durationMs;
    this.isSeekable = isSeekable;
    this.isDynamic = isDynamic;
    this.defaultInitialPeriodIndex = defaultInitialPeriodIndex;
    this.defaultInitialTimeMs = defaultInitialTimeMs;
  }

  /**
   * Returns a new window that is offset by the specified number of periods.
   *
   * @param periodCount The number of periods to add to {@link #startPeriodIndex} and
   *     {@link #endPeriodIndex} when constructing the copy.
   * @return A new window that is offset by the specified number of periods.
   */
  public Window copyOffsetByPeriodCount(int periodCount) {
    return new Window(startPeriodIndex + periodCount, startTimeMs, endPeriodIndex + periodCount,
        endTimeMs, durationMs, isSeekable, isDynamic, defaultInitialPeriodIndex + periodCount,
        defaultInitialTimeMs);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + startPeriodIndex;
    result = 31 * result + (int) startTimeMs;
    result = 31 * result + endPeriodIndex;
    result = 31 * result + (int) endTimeMs;
    result = 31 * result + (isSeekable ? 1 : 2);
    result = 31 * result + (isDynamic ? 1 : 2);
    result = 31 * result + defaultInitialPeriodIndex;
    result = 31 * result + (int) defaultInitialTimeMs;
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
    Window other = (Window) obj;
    return other.startPeriodIndex == startPeriodIndex
        && other.startTimeMs == startTimeMs
        && other.endPeriodIndex == endPeriodIndex
        && other.endTimeMs == endTimeMs
        && other.durationMs == durationMs
        && other.isSeekable == isSeekable
        && other.isDynamic == isDynamic
        && other.defaultInitialPeriodIndex == defaultInitialPeriodIndex
        && other.defaultInitialTimeMs == defaultInitialTimeMs;
  }

  @Override
  public String toString() {
    return "Window[" + startPeriodIndex + ", " + startTimeMs + ", " + endPeriodIndex + ", "
        + endTimeMs + ", " + isDynamic + "]";
  }

}

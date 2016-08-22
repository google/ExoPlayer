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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaTimeline;
import com.google.android.exoplayer2.MediaWindow;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link MediaTimeline} consisting of a single period and static window.
 */
public final class SinglePeriodMediaTimeline implements MediaTimeline {

  private static final Object ID = new Object();

  private final long offsetInFirstPeriodUs;
  private final MediaWindow window;

  /**
   * Creates a timeline with one period of known duration and a window extending from zero to its
   * duration.
   *
   * @param durationUs The duration of the period, in microseconds.
   * @param isSeekable Whether seeking is supported within the period.
   */
  public SinglePeriodMediaTimeline(long durationUs, boolean isSeekable) {
    this(0, MediaWindow.createWindowFromZero(durationUs, isSeekable, false /* isDynamic */));
  }

  /**
   * Creates a timeline with one period of known duration and a window extending from zero to its
   * duration.
   *
   * @param offsetInFirstPeriodUs The offset of the start of the window in the period.
   * @param window The available window within the period.
   */
  public SinglePeriodMediaTimeline(long offsetInFirstPeriodUs, MediaWindow window) {
    this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
    this.window = window;
  }

  @Override
  public long getAbsoluteStartTime() {
    return 0;
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public long getPeriodDurationMs(int periodIndex) {
    return C.usToMs(getPeriodDurationUs(periodIndex));
  }

  @Override
  public long getPeriodDurationUs(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, 1);
    return window.durationUs == C.TIME_UNSET ? C.TIME_UNSET
        : (offsetInFirstPeriodUs + window.durationUs);
  }

  @Override
  public Object getPeriodId(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, 1);
    return ID;
  }

  @Override
  public MediaWindow getPeriodWindow(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, 1);
    return window;
  }

  @Override
  public int getPeriodWindowIndex(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, 1);
    return 0;
  }

  @Override
  public int getIndexOfPeriod(Object id) {
    return ID.equals(id) ? 0 : C.INDEX_UNSET;
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  @Override
  public MediaWindow getWindow(int windowIndex) {
    Assertions.checkIndex(windowIndex, 0, 1);
    return window;
  }

  @Override
  public int getWindowFirstPeriodIndex(int windowIndex) {
    Assertions.checkIndex(windowIndex, 0, 1);
    return 0;
  }

  @Override
  public int getWindowLastPeriodIndex(int windowIndex) {
    Assertions.checkIndex(windowIndex, 0, 1);
    return 0;
  }

  @Override
  public long getWindowOffsetInFirstPeriodUs(int windowIndex) {
    Assertions.checkIndex(windowIndex, 0, 1);
    return offsetInFirstPeriodUs;
  }

}

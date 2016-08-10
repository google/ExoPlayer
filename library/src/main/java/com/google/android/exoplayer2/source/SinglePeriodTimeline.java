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
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link Timeline} consisting of a single period and seek window.
 */
public final class SinglePeriodTimeline implements Timeline {

  /**
   * Returns a new timeline with one period of unknown duration and an empty seek window.
   *
   * @param id The identifier for the period.
   * @return A new timeline with one period of unknown duration.
   */
  public static Timeline createNonFinalTimeline(Object id) {
    return new SinglePeriodTimeline(id, false, C.UNSET_TIME_US, SeekWindow.UNSEEKABLE);
  }

  /**
   * Returns a new timeline with one period of unknown duration and the specified seek window.
   *
   * @param id The identifier for the period.
   * @param seekWindow The seek window.
   * @return A new timeline with one period of unknown duration.
   */
  public static Timeline createNonFinalTimeline(Object id, SeekWindow seekWindow) {
    return new SinglePeriodTimeline(id, false, C.UNSET_TIME_US, seekWindow);
  }

  /**
   * Creates a final timeline with one period of known duration and an empty seek window.
   *
   * @param id The identifier for the period.
   * @param durationUs The duration of the period, in microseconds.
   * @return A new, unseekable, final timeline with one period.
   */
  public static Timeline createUnseekableFinalTimeline(Object id, long durationUs) {
    return new SinglePeriodTimeline(id, true, durationUs / 1000, SeekWindow.UNSEEKABLE);
  }

  /**
   * Creates a final timeline with one period of known duration and a seek window extending from
   * zero to its duration.
   *
   * @param id The identifier for the period.
   * @param durationUs The duration of the period, in microseconds.
   * @return A new, seekable, final timeline with one period.
   */
  public static Timeline createSeekableFinalTimeline(Object id, long durationUs) {
    return new SinglePeriodTimeline(id, true, durationUs / 1000,
        SeekWindow.createWindowFromZero(durationUs));
  }

  private final Object id;
  private final boolean isFinal;
  private final long durationMs;
  private final SeekWindow seekWindow;

  private SinglePeriodTimeline(Object id, boolean isFinal, long durationMs, SeekWindow seekWindow) {
    this.id = Assertions.checkNotNull(id);
    this.isFinal = isFinal;
    this.durationMs = durationMs;
    this.seekWindow = seekWindow;
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public boolean isFinal() {
    return isFinal;
  }

  @Override
  public long getPeriodDuration(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException("Index " + index + " out of bounds");
    }
    return durationMs;
  }

  @Override
  public Object getPeriodId(int index) {
    return index == 0 ? id : null;
  }

  @Override
  public int getIndexOfPeriod(Object id) {
    return id.equals(this.id) ? 0 : Timeline.NO_PERIOD_INDEX;
  }

  @Override
  public int getSeekWindowCount() {
    return 1;
  }

  @Override
  public SeekWindow getSeekWindow(int index) {
    return seekWindow;
  }

}

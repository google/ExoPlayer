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
 * The player's timeline consisting of one or more periods. Instances are immutable.
 */
public interface Timeline {

  /**
   * Returned by {@link #getIndexOfPeriod(Object)} if no period index corresponds to the specified
   * identifier.
   */
  int NO_PERIOD_INDEX = -1;

  /**
   * Returns the number of periods in the timeline.
   */
  int getPeriodCount();

  /**
   * Returns the absolute start time of the timeline in milliseconds.
   */
  long getAbsoluteStartTime();

  /**
   * Returns the duration of the period at {@code periodIndex} in the timeline, in milliseconds, or
   * {@link ExoPlayer#UNKNOWN_TIME} if not known.
   *
   * @param periodIndex The index of the period.
   * @return The duration of the period in milliseconds, or {@link ExoPlayer#UNKNOWN_TIME}.
   */
  long getPeriodDurationMs(int periodIndex);

  /**
   * Returns the duration of the period at {@code periodIndex} in the timeline, in microseconds, or
   * {@link C#UNSET_TIME_US} if not known.
   *
   * @param periodIndex The index of the period.
   * @return The duration of the period in microseconds, or {@link C#UNSET_TIME_US}.
   */
  long getPeriodDurationUs(int periodIndex);

  /**
   * Returns a unique identifier for the period at {@code periodIndex}, or {@code null} if the
   * period at {@code periodIndex} is not known. The identifier is stable across {@link Timeline}
   * changes.
   *
   * @param periodIndex A period index.
   * @return An identifier for the period, or {@code null} if the period is not known.
   */
  Object getPeriodId(int periodIndex);

  /**
   * Returns the {@link Window} to which the period with the specified index belongs.
   *
   * @param periodIndex The period index.
   * @return The corresponding window.
   */
  Window getPeriodWindow(int periodIndex);

  /**
   * Returns the index of the window to which the period with the specified index belongs.
   *
   * @param periodIndex The period index.
   * @return The index of the corresponding window.
   */
  int getPeriodWindowIndex(int periodIndex);

  /**
   * Returns the index of the period identified by {@code id}, or {@link #NO_PERIOD_INDEX} if the
   * period is not in the timeline.
   *
   * @param id An identifier for a period.
   * @return The index of the period, or {@link #NO_PERIOD_INDEX} if the period was not found.
   */
  int getIndexOfPeriod(Object id);

  /**
   * Returns the number of windows that can be accessed via {@link #getWindow(int)}.
   */
  int getWindowCount();

  /**
   * Returns the {@link Window} at the specified index.
   *
   * @param windowIndex The window index.
   */
  Window getWindow(int windowIndex);

  /**
   * Returns the index of the first period belonging to the {@link Window} at the specified index.
   *
   * @param windowIndex The window index.
   * @return The index of the first period in the window.
   */
  int getWindowFirstPeriodIndex(int windowIndex);

  /**
   * Returns the index of the last period belonging to the {@link Window} at the specified index.
   *
   * @param windowIndex The window index.
   * @return The index of the last period in the window.
   */
  int getWindowLastPeriodIndex(int windowIndex);

  /**
   * Returns the start position of the specified window in the first period belonging to it, in
   * microseconds.
   *
   * @param windowIndex The window index.
   * @return The start position of the window in the first period belonging to it.
   */
  long getWindowOffsetInFirstPeriodUs(int windowIndex);

}

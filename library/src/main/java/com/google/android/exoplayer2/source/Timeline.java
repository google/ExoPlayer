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

import com.google.android.exoplayer2.ExoPlayer;

/**
 * The player's timeline consisting of one or more periods. Instances are immutable.
 */
public interface Timeline {

  /**
   * Returned by {@link #getPeriodCount()} when the number of periods is not known.
   */
  int UNKNOWN_PERIOD_COUNT = -1;

  /**
   * Returned by {@link #getIndexOfPeriod(Object)} if no period index corresponds to the specified
   * identifier.
   */
  int NO_PERIOD_INDEX = -1;

  /**
   * Returns the number of periods in the timeline, or {@link #UNKNOWN_PERIOD_COUNT} if not known.
   * If {@link #isFinal()} returns {@code true}, the number of periods must be known.
   */
  int getPeriodCount();

  /**
   * Returns whether the timeline is final, which means it will not change.
   */
  boolean isFinal();

  /**
   * Returns the absolute start time of the timeline in milliseconds.
   */
  long getAbsoluteStartTime();

  /**
   * Returns the duration of the period at {@code index} in the timeline, in milliseconds, or
   * {@link ExoPlayer#UNKNOWN_TIME} if not known.
   *
   * @param index The index of the period.
   * @return The duration of the period in milliseconds, or {@link ExoPlayer#UNKNOWN_TIME}.
   */
  long getPeriodDuration(int index);

  /**
   * Returns a unique identifier for the period at {@code index}, or {@code null} if the period at
   * {@code index} is not known. The identifier is stable across {@link Timeline} instances.
   * <p>
   * When the timeline changes the player uses period identifiers to determine what periods are
   * unchanged. Implementations that associate an object with each period can return the object for
   * the provided index to guarantee uniqueness. Other implementations must be careful to return
   * identifiers that can't clash with (for example) identifiers used by other timelines that may be
   * concatenated with this one.
   *
   * @param index A period index.
   * @return An identifier for the period, or {@code null} if the period is not known.
   */
  Object getPeriodId(int index);

  /**
   * Returns the index of the period identified by {@code id}, or {@link #NO_PERIOD_INDEX} if the
   * period is not in the timeline.
   *
   * @param id An identifier for a period.
   * @return The index of the period, or {@link #NO_PERIOD_INDEX} if the period was not found.
   */
  int getIndexOfPeriod(Object id);

  /**
   * Returns the number of seek windows that can be accessed via {@link #getSeekWindow(int)}.
   */
  int getSeekWindowCount();

  /**
   * Returns the {@link SeekWindow} at {@code index}, which represents positions that can be seeked
   * to in the timeline. The seek windows may change when
   * {@link MediaSource.Listener#onSourceInfoRefreshed(Timeline, Object)} is called.
   */
  SeekWindow getSeekWindow(int index);

}

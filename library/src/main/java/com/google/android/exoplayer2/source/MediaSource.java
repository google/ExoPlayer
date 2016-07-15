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

import java.io.IOException;

/**
 * A source of media consisting of one or more {@link MediaPeriod}s.
 */
public interface MediaSource {

  /**
   * Returned by {@link #getPeriodCount()} if the number of periods is not known.
   */
  int UNKNOWN_PERIOD_COUNT = -1;

  /**
   * Starts preparation of the source.
   */
  void prepareSource();

  /**
   * Returns the number of periods in the source, or {@link #UNKNOWN_PERIOD_COUNT} if the number
   * of periods is not yet known.
   */
  int getPeriodCount();

  /**
   * Returns a {@link MediaPeriod} corresponding to the period at the specified index, or
   * {@code null} if the period at the specified index is not yet available.
   *
   * @param index The index of the period. Must be less than {@link #getPeriodCount()} unless the
   *     period count is {@link #UNKNOWN_PERIOD_COUNT}.
   * @return A {@link MediaPeriod}, or {@code null} if the source at the specified index is not yet
   *     available.
   * @throws IOException If there is an error that's preventing the source from becoming prepared or
   *     creating periods.
   */
  MediaPeriod createPeriod(int index) throws IOException;

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required. It may be called in any
   * state.
   */
  void releaseSource();

}

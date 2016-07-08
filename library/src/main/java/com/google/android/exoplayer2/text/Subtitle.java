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
package com.google.android.exoplayer2.text;

import java.util.List;

/**
 * A subtitle that contains textual data associated with time indices.
 */
public interface Subtitle {

  /**
   * Gets the index of the first event that occurs after a given time (exclusive).
   *
   * @param timeUs The time in microseconds.
   * @return The index of the next event, or -1 if there are no events after the specified time.
   */
  int getNextEventTimeIndex(long timeUs);

  /**
   * Gets the number of event times, where events are defined as points in time at which the cues
   * returned by {@link #getCues(long)} changes.
   *
   * @return The number of event times.
   */
  int getEventTimeCount();

  /**
   * Gets the event time at a specified index.
   *
   * @param index The index of the event time to obtain.
   * @return The event time in microseconds.
   */
  long getEventTime(int index);

  /**
   * Retrieve the subtitle cues that should be displayed at a given time.
   *
   * @param timeUs The time in microseconds.
   * @return A list of cues that should be displayed, possibly empty.
   */
  List<Cue> getCues(long timeUs);

}

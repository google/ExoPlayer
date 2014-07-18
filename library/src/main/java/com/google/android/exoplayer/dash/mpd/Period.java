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
package com.google.android.exoplayer.dash.mpd;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates media content components over a contiguous period of time.
 */
public final class Period {

  /**
   * The period identifier, if one exists.
   */
  public final String id;

  /**
   * The start time of the period in milliseconds.
   */
  public final long startMs;

  /**
   * The duration of the period in milliseconds, or -1 if the duration is unknown.
   */
  public final long durationMs;

  /**
   * The adaptation sets belonging to the period.
   */
  public final List<AdaptationSet> adaptationSets;

  /**
   * @param id The period identifier. May be null.
   * @param start The start time of the period in milliseconds.
   * @param duration The duration of the period in milliseconds, or -1 if the duration is unknown.
   * @param adaptationSets The adaptation sets belonging to the period.
   */
  public Period(String id, long start, long duration, List<AdaptationSet> adaptationSets) {
    this.id = id;
    this.startMs = start;
    this.durationMs = duration;
    this.adaptationSets = Collections.unmodifiableList(adaptationSets);
  }

}

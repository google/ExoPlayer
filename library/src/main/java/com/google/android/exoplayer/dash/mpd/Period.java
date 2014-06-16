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

  public final int id;

  public final long start;

  public final long duration;

  public final List<AdaptationSet> adaptationSets;

  public final List<Segment.Timeline> segmentList;

  public final int segmentStartNumber;

  public final int segmentTimescale;

  public final long presentationTimeOffset;

  public Period(int id, long start, long duration, List<AdaptationSet> adaptationSets) {
    this(id, start, duration, adaptationSets, null, 0, 0, 0);
  }

  public Period(int id, long start, long duration, List<AdaptationSet> adaptationSets,
      List<Segment.Timeline> segmentList, int segmentStartNumber, int segmentTimescale) {
    this(id, start, duration, adaptationSets, segmentList, segmentStartNumber, segmentTimescale, 0);
  }

  public Period(int id, long start, long duration, List<AdaptationSet> adaptationSets,
      List<Segment.Timeline> segmentList, int segmentStartNumber, int segmentTimescale,
      long presentationTimeOffset) {
    this.id = id;
    this.start = start;
    this.duration = duration;
    this.adaptationSets = Collections.unmodifiableList(adaptationSets);
    if (segmentList != null) {
      this.segmentList = Collections.unmodifiableList(segmentList);
    } else {
      this.segmentList = null;
    }
    this.segmentStartNumber = segmentStartNumber;
    this.segmentTimescale = segmentTimescale;
    this.presentationTimeOffset = presentationTimeOffset;
  }

}

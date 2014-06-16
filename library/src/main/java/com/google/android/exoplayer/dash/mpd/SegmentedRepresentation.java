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

import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.upstream.DataSpec;

import android.net.Uri;

import java.util.List;

/**
 * Represents a DASH Representation which uses the SegmentList structure (i.e. it has a list of
 * Segment URLs instead of a single URL).
 */
public class SegmentedRepresentation extends Representation {

  private List<Segment> segmentList;

  public SegmentedRepresentation(String contentId, Format format, Uri uri, long initializationStart,
      long initializationEnd, long indexStart, long indexEnd, long periodStart, long periodDuration,
      List<Segment> segmentList) {
    super(contentId, -1, format, uri, DataSpec.LENGTH_UNBOUNDED, initializationStart,
        initializationEnd, indexStart, indexEnd, periodStart, periodDuration);
    this.segmentList = segmentList;
  }

  public int getNumSegments() {
    return segmentList.size();
  }

  public Segment getSegment(int i) {
    return segmentList.get(i);
  }

}

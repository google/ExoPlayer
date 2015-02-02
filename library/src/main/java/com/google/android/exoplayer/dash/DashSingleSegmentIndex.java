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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.dash.mpd.RangedUri;

/**
 * A {@link DashSegmentIndex} that defines a single segment.
 */
public class DashSingleSegmentIndex implements DashSegmentIndex {

  private final long startTimeUs;
  private final long durationUs;
  private final RangedUri uri;

  /**
   * @param startTimeUs The start time of the segment, in microseconds.
   * @param durationUs The duration of the segment, in microseconds.
   * @param uri A {@link RangedUri} defining the location of the segment data.
   */
  public DashSingleSegmentIndex(long startTimeUs, long durationUs, RangedUri uri) {
    this.startTimeUs = startTimeUs;
    this.durationUs = durationUs;
    this.uri = uri;
  }

  @Override
  public int getSegmentNum(long timeUs) {
    return 0;
  }

  @Override
  public long getTimeUs(int segmentNum) {
    return startTimeUs;
  }

  @Override
  public long getDurationUs(int segmentNum) {
    return durationUs;
  }

  @Override
  public RangedUri getSegmentUrl(int segmentNum) {
    return uri;
  }

  @Override
  public int getFirstSegmentNum() {
    return 0;
  }

  @Override
  public int getLastSegmentNum() {
    return 0;
  }

  @Override
  public boolean isExplicit() {
    return true;
  }

}

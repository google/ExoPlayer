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
 * Indexes the segments within a media stream.
 *
 * TODO: Generalize to cover all chunk streaming modes (e.g. SmoothStreaming) if possible.
 */
public interface DashSegmentIndex {

  /**
   * Returns the segment number of the segment containing a given media time.
   *
   * @param timeUs The time in microseconds.
   * @return The segment number of the corresponding segment.
   */
  int getSegmentNum(long timeUs);

  /**
   * Returns the start time of a segment.
   *
   * @param segmentNum The segment number.
   * @return The corresponding start time in microseconds.
   */
  long getTimeUs(int segmentNum);

  /**
   * Returns the duration of a segment.
   *
   * @param segmentNum The segment number.
   * @return The duration of the segment, in microseconds.
   */
  long getDurationUs(int segmentNum);

  /**
   * Returns a {@link RangedUri} defining the location of a segment.
   *
   * @param segmentNum The segment number.
   * @return The {@link RangedUri} defining the location of the data.
   */
  RangedUri getSegmentUrl(int segmentNum);

  /**
   * Returns the segment number of the first segment.
   *
   * @return The segment number of the first segment.
   */
  int getFirstSegmentNum();

  /**
   * Returns the segment number of the last segment.
   *
   * @return The segment number of the last segment.
   */
  int getLastSegmentNum();

}

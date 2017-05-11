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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.TimelineTest;
import com.google.android.exoplayer2.TimelineTest.FakeTimeline;
import com.google.android.exoplayer2.TimelineTest.TimelineVerifier;
import junit.framework.TestCase;

/**
 * Unit tests for {@link ConcatenatingMediaSource}.
 */
public final class ConcatenatingMediaSourceTest extends TestCase {

  public void testSingleMediaSource() {
    Timeline timeline = getConcatenatedTimeline(false, new FakeTimeline(3, 111));
    new TimelineVerifier(timeline)
        .assertWindowIds(111)
        .assertPeriodCounts(3)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0);

    timeline = getConcatenatedTimeline(true, new FakeTimeline(3, 111));
    new TimelineVerifier(timeline)
        .assertWindowIds(111)
        .assertPeriodCounts(3)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0);
  }

  public void testMultipleMediaSources() {
    Timeline[] timelines = { new FakeTimeline(3, 111), new FakeTimeline(1, 222),
        new FakeTimeline(3, 333) };
    Timeline timeline = getConcatenatedTimeline(false, timelines);
    new TimelineVerifier(timeline)
        .assertWindowIds(111, 222, 333)
        .assertPeriodCounts(3, 1, 3)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0, 1, 2)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 2, 0, 1)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0, 1, 2)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 1, 2, 0);

    timeline = getConcatenatedTimeline(true, timelines);
    new TimelineVerifier(timeline)
        .assertWindowIds(111, 222, 333)
        .assertPeriodCounts(3, 1, 3)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 2, 0, 1)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 2, 0, 1)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 1, 2, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 1, 2, 0);
  }

  public void testNestedMediaSources() {
    Timeline timeline = getConcatenatedTimeline(false,
        getConcatenatedTimeline(false, new FakeTimeline(1, 111), new FakeTimeline(1, 222)),
        getConcatenatedTimeline(true, new FakeTimeline(1, 333), new FakeTimeline(1, 444)));
    new TimelineVerifier(timeline)
        .assertWindowIds(111, 222, 333, 444)
        .assertPeriodCounts(1, 1, 1, 1)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1, 2)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0, 1, 3, 2)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 3, 0, 1, 2)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, 1, 2, 3, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0, 1, 3, 2)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 1, 2, 3, 0);
  }

  /**
   * Wraps the specified timelines in a {@link ConcatenatingMediaSource} and returns
   * the concatenated timeline.
   */
  private static Timeline getConcatenatedTimeline(boolean isRepeatOneAtomic,
      Timeline... timelines) {
    MediaSource[] mediaSources = new MediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      mediaSources[i] = TimelineTest.stubMediaSourceSourceWithTimeline(timelines[i]);
    }
    return TimelineTest.extractTimelineFromMediaSource(
        new ConcatenatingMediaSource(isRepeatOneAtomic, mediaSources));
  }


}

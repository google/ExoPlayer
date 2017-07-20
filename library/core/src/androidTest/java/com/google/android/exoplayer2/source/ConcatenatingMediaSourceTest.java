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
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import junit.framework.TestCase;

/**
 * Unit tests for {@link ConcatenatingMediaSource}.
 */
public final class ConcatenatingMediaSourceTest extends TestCase {

  public void testSingleMediaSource() {
    Timeline timeline = getConcatenatedTimeline(false, createFakeTimeline(3, 111));
    TimelineAsserts.assertWindowIds(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 3);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 0);

    timeline = getConcatenatedTimeline(true, createFakeTimeline(3, 111));
    TimelineAsserts.assertWindowIds(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 3);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 0);
  }

  public void testMultipleMediaSources() {
    Timeline[] timelines = { createFakeTimeline(3, 111), createFakeTimeline(1, 222),
        createFakeTimeline(3, 333) };
    Timeline timeline = getConcatenatedTimeline(false, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 3, 1, 3);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 0);

    timeline = getConcatenatedTimeline(true, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 3, 1, 3);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 0);
  }

  public void testNestedMediaSources() {
    Timeline timeline = getConcatenatedTimeline(false,
        getConcatenatedTimeline(false, createFakeTimeline(1, 111), createFakeTimeline(1, 222)),
        getConcatenatedTimeline(true, createFakeTimeline(1, 333), createFakeTimeline(1, 444)));
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333, 444);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 3, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 3, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, 1, 2, 3, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 3, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 3, 0);
  }

  /**
   * Wraps the specified timelines in a {@link ConcatenatingMediaSource} and returns
   * the concatenated timeline.
   */
  private static Timeline getConcatenatedTimeline(boolean isRepeatOneAtomic,
      Timeline... timelines) {
    MediaSource[] mediaSources = new MediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      mediaSources[i] = new FakeMediaSource(timelines[i], null);
    }
    return TestUtil.extractTimelineFromMediaSource(
        new ConcatenatingMediaSource(isRepeatOneAtomic, mediaSources));
  }

  private static FakeTimeline createFakeTimeline(int periodCount, int windowId) {
    return new FakeTimeline(new TimelineWindowDefinition(periodCount, windowId));
  }

}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Unit tests for {@link LoopingMediaSource}.
 */
public class LoopingMediaSourceTest extends TestCase {

  private final Timeline multiWindowTimeline;

  public LoopingMediaSourceTest() {
    multiWindowTimeline = TestUtil.extractTimelineFromMediaSource(new FakeMediaSource(
        new FakeTimeline(new TimelineWindowDefinition(1, 111),
            new TimelineWindowDefinition(1, 222), new TimelineWindowDefinition(1, 333)), null));
  }

  public void testSingleLoop() {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 0);
  }

  public void testMultiLoop() {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, 3);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333, 111, 222, 333, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF,
        C.INDEX_UNSET, 0, 1, 2, 3, 4, 5, 6, 7, 8);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE,
        0, 1, 2, 3, 4, 5, 6, 7, 8);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL,
        8, 0, 1, 2, 3, 4, 5, 6, 7);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF,
        1, 2, 3, 4, 5, 6, 7, 8, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE,
        0, 1, 2, 3, 4, 5, 6, 7, 8);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL,
        1, 2, 3, 4, 5, 6, 7, 8, 0);
  }

  public void testInfiniteLoop() {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, Integer.MAX_VALUE);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, 1, 2, 0);
  }

  /**
   * Wraps the specified timeline in a {@link LoopingMediaSource} and returns
   * the looping timeline.
   */
  private static Timeline getLoopingTimeline(Timeline timeline, int loopCount) {
    MediaSource mediaSource = new FakeMediaSource(timeline, null);
    return TestUtil.extractTimelineFromMediaSource(
        new LoopingMediaSource(mediaSource, loopCount));
  }

}

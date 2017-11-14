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
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import junit.framework.TestCase;

/**
 * Unit tests for {@link ConcatenatingMediaSource}.
 */
public final class ConcatenatingMediaSourceTest extends TestCase {

  public void testEmptyConcatenation() {
    for (boolean atomic : new boolean[] {false, true}) {
      Timeline timeline = getConcatenatedTimeline(atomic);
      TimelineAsserts.assertEmpty(timeline);

      timeline = getConcatenatedTimeline(atomic, Timeline.EMPTY);
      TimelineAsserts.assertEmpty(timeline);

      timeline = getConcatenatedTimeline(atomic, Timeline.EMPTY, Timeline.EMPTY, Timeline.EMPTY);
      TimelineAsserts.assertEmpty(timeline);
    }
  }

  public void testSingleMediaSource() {
    Timeline timeline = getConcatenatedTimeline(false, createFakeTimeline(3, 111));
    TimelineAsserts.assertWindowIds(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 3);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 0);
    }

    timeline = getConcatenatedTimeline(true, createFakeTimeline(3, 111));
    TimelineAsserts.assertWindowIds(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 3);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 0);
    }
  }

  public void testMultipleMediaSources() {
    Timeline[] timelines = { createFakeTimeline(3, 111), createFakeTimeline(1, 222),
        createFakeTimeline(3, 333) };
    Timeline timeline = getConcatenatedTimeline(false, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 3, 1, 3);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 1);
    assertEquals(0, timeline.getFirstWindowIndex(false));
    assertEquals(2, timeline.getLastWindowIndex(false));
    assertEquals(2, timeline.getFirstWindowIndex(true));
    assertEquals(0, timeline.getLastWindowIndex(true));

    timeline = getConcatenatedTimeline(true, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 3, 1, 3);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled,
          2, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled,
          2, 0, 1);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          1, 2, C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 1, 2, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 1, 2, 0);
      assertEquals(0, timeline.getFirstWindowIndex(shuffled));
      assertEquals(2, timeline.getLastWindowIndex(shuffled));
    }
  }

  public void testNestedMediaSources() {
    Timeline timeline = getConcatenatedTimeline(false,
        getConcatenatedTimeline(false, createFakeTimeline(1, 111), createFakeTimeline(1, 222)),
        getConcatenatedTimeline(true, createFakeTimeline(1, 333), createFakeTimeline(1, 444)));
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333, 444);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        C.INDEX_UNSET, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false,
        0, 1, 3, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false,
        3, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        1, 2, 3, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 3, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 3, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        1, 3, C.INDEX_UNSET, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true,
        0, 1, 3, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true,
        1, 3, 0, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        C.INDEX_UNSET, 0, 3, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 3, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 3, 1);
  }

  public void testEmptyTimelineMediaSources() {
    // Empty timelines in the front, back, and the middle (single and multiple in a row).
    Timeline[] timelines = { Timeline.EMPTY, createFakeTimeline(1, 111), Timeline.EMPTY,
        Timeline.EMPTY, createFakeTimeline(2, 222), Timeline.EMPTY, createFakeTimeline(3, 333),
        Timeline.EMPTY };
    Timeline timeline = getConcatenatedTimeline(false, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 2, 3);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 1);
    assertEquals(0, timeline.getFirstWindowIndex(false));
    assertEquals(2, timeline.getLastWindowIndex(false));
    assertEquals(2, timeline.getFirstWindowIndex(true));
    assertEquals(0, timeline.getLastWindowIndex(true));

    timeline = getConcatenatedTimeline(true, timelines);
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 2, 3);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          C.INDEX_UNSET, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled,
          2, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled,
          2, 0, 1);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled,
          1, 2, C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 1, 2, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 1, 2, 0);
      assertEquals(0, timeline.getFirstWindowIndex(shuffled));
      assertEquals(2, timeline.getLastWindowIndex(shuffled));
    }
  }

  public void testPeriodCreationWithAds() throws InterruptedException {
    // Create media source with ad child source.
    Timeline timelineContentOnly = new FakeTimeline(
        new TimelineWindowDefinition(2, 111, true, false, 10 * C.MICROS_PER_SECOND));
    Timeline timelineWithAds = new FakeTimeline(
        new TimelineWindowDefinition(2, 222, true, false, 10 * C.MICROS_PER_SECOND, 1, 1));
    FakeMediaSource mediaSourceContentOnly = new FakeMediaSource(timelineContentOnly, null);
    FakeMediaSource mediaSourceWithAds = new FakeMediaSource(timelineWithAds, null);
    ConcatenatingMediaSource mediaSource = new ConcatenatingMediaSource(mediaSourceContentOnly,
        mediaSourceWithAds);

    // Prepare and assert timeline contains ad groups.
    Timeline timeline = TestUtil.extractTimelineFromMediaSource(mediaSource);
    TimelineAsserts.assertAdGroupCounts(timeline, 0, 0, 1, 1);

    // Create all periods and assert period creation of child media sources has been called.
    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline, 10_000);
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0, 0, 0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1, 0, 0));
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
    ConcatenatingMediaSource mediaSource = new ConcatenatingMediaSource(isRepeatOneAtomic,
        new FakeShuffleOrder(mediaSources.length), mediaSources);
    return TestUtil.extractTimelineFromMediaSource(mediaSource);
  }

  private static FakeTimeline createFakeTimeline(int periodCount, int windowId) {
    return new FakeTimeline(new TimelineWindowDefinition(periodCount, windowId));
  }

}

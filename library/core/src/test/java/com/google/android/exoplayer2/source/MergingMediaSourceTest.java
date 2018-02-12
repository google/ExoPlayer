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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.RobolectricUtil;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MergingMediaSource.IllegalMergeException;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link MergingMediaSource}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class MergingMediaSourceTest {

  @Test
  public void testMergingDynamicTimelines() throws IOException {
    FakeTimeline firstTimeline =
        new FakeTimeline(new TimelineWindowDefinition(true, true, C.TIME_UNSET));
    FakeTimeline secondTimeline =
        new FakeTimeline(new TimelineWindowDefinition(true, true, C.TIME_UNSET));
    testMergingMediaSourcePrepare(firstTimeline, secondTimeline);
  }

  @Test
  public void testMergingStaticTimelines() throws IOException {
    FakeTimeline firstTimeline = new FakeTimeline(new TimelineWindowDefinition(true, false, 20));
    FakeTimeline secondTimeline = new FakeTimeline(new TimelineWindowDefinition(true, false, 10));
    testMergingMediaSourcePrepare(firstTimeline, secondTimeline);
  }

  @Test
  public void testMergingTimelinesWithDifferentPeriodCounts() throws IOException {
    FakeTimeline firstTimeline = new FakeTimeline(new TimelineWindowDefinition(1, null));
    FakeTimeline secondTimeline = new FakeTimeline(new TimelineWindowDefinition(2, null));
    try {
      testMergingMediaSourcePrepare(firstTimeline, secondTimeline);
      fail("Expected merging to fail.");
    } catch (IllegalMergeException e) {
      assertThat(e.reason).isEqualTo(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
    }
  }

  /**
   * Wraps the specified timelines in a {@link MergingMediaSource}, prepares it and checks that it
   * forwards the first of the wrapped timelines.
   */
  private static void testMergingMediaSourcePrepare(Timeline... timelines) throws IOException {
    FakeMediaSource[] mediaSources = new FakeMediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      mediaSources[i] = new FakeMediaSource(timelines[i], null);
    }
    MergingMediaSource mediaSource = new MergingMediaSource(mediaSources);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource, null);
    try {
      Timeline timeline = testRunner.prepareSource();
      // The merged timeline should always be the one from the first child.
      assertThat(timeline).isEqualTo(timelines[0]);
      testRunner.releaseSource();
      for (int i = 0; i < mediaSources.length; i++) {
        mediaSources[i].assertReleased();
      }
    } finally {
      testRunner.release();
    }
  }
}

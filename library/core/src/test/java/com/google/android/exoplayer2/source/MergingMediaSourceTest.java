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
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MergingMediaSource.IllegalMergeException;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MergingMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class MergingMediaSourceTest {

  @Test
  public void prepare_withoutDurationClipping_usesTimelineOfFirstSource() throws IOException {
    FakeTimeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 30));
    FakeTimeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ C.TIME_UNSET));
    FakeTimeline timeline3 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 10));

    Timeline mergedTimeline =
        prepareMergingMediaSource(/* clipDurations= */ false, timeline1, timeline2, timeline3);

    assertThat(mergedTimeline).isEqualTo(timeline1);
  }

  @Test
  public void prepare_withDurationClipping_usesDurationOfShortestSource() throws IOException {
    FakeTimeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 30));
    FakeTimeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ C.TIME_UNSET));
    FakeTimeline timeline3 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 10));

    Timeline mergedTimeline =
        prepareMergingMediaSource(/* clipDurations= */ true, timeline1, timeline2, timeline3);

    assertThat(mergedTimeline).isEqualTo(timeline3);
  }

  @Test
  public void prepare_differentPeriodCounts_fails() throws IOException {
    FakeTimeline firstTimeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1));
    FakeTimeline secondTimeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 2));

    IllegalMergeException exception =
        assertThrows(
            IllegalMergeException.class,
            () ->
                prepareMergingMediaSource(
                    /* clipDurations= */ false, firstTimeline, secondTimeline));
    assertThat(exception.reason).isEqualTo(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
  }

  @Test
  public void createPeriod_createsChildPeriods() throws Exception {
    FakeMediaSource[] mediaSources = new FakeMediaSource[2];
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i] = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 2));
    }
    MergingMediaSource mediaSource = new MergingMediaSource(mediaSources);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource, null);
    try {
      testRunner.prepareSource();
      testRunner.assertPrepareAndReleaseAllPeriods();
      for (FakeMediaSource element : mediaSources) {
        assertThat(element.getCreatedMediaPeriods()).isNotEmpty();
      }
      testRunner.releaseSource();
    } finally {
      testRunner.release();
    }
  }

  /**
   * Wraps the specified timelines in a {@link MergingMediaSource}, prepares it and returns the
   * merged timeline.
   */
  private static Timeline prepareMergingMediaSource(boolean clipDurations, Timeline... timelines)
      throws IOException {
    FakeMediaSource[] mediaSources = new FakeMediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      mediaSources[i] = new FakeMediaSource(timelines[i]);
    }
    MergingMediaSource mergingMediaSource =
        new MergingMediaSource(/* adjustPeriodTimeOffsets= */ false, clipDurations, mediaSources);
    MediaSourceTestRunner testRunner =
        new MediaSourceTestRunner(mergingMediaSource, /* allocator= */ null);
    try {
      Timeline timeline = testRunner.prepareSource();
      testRunner.releaseSource();
      for (FakeMediaSource mediaSource : mediaSources) {
        mediaSource.assertReleased();
      }
      return timeline;
    } finally {
      testRunner.release();
    }
  }
}

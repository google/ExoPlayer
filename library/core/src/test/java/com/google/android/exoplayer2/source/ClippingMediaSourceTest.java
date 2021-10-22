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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.ClippingMediaSource.IllegalClippingException;
import com.google.android.exoplayer2.source.MaskingMediaSource.PlaceholderTimeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ClippingMediaSource}. */
@RunWith(AndroidJUnit4.class)
public final class ClippingMediaSourceTest {

  private static final long TEST_PERIOD_DURATION_US = 1_000_000;
  private static final long TEST_CLIP_AMOUNT_US = 300_000;

  private Window window;
  private Period period;

  @Before
  public void setUp() {
    window = new Timeline.Window();
    period = new Timeline.Period();
  }

  @Test
  public void noClipping() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            TEST_PERIOD_DURATION_US,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline = getClippedTimeline(timeline, 0, TEST_PERIOD_DURATION_US);

    assertThat(clippedTimeline.getWindowCount()).isEqualTo(1);
    assertThat(clippedTimeline.getPeriodCount()).isEqualTo(1);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US);
  }

  @Test
  public void clippingUnseekableWindowThrows() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            TEST_PERIOD_DURATION_US,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    // If the unseekable window isn't clipped, clipping succeeds.
    getClippedTimeline(timeline, 0, TEST_PERIOD_DURATION_US);
    try {
      // If the unseekable window is clipped, clipping fails.
      getClippedTimeline(timeline, 1, TEST_PERIOD_DURATION_US);
      fail("Expected clipping to fail.");
    } catch (IllegalClippingException e) {
      assertThat(e.reason).isEqualTo(IllegalClippingException.REASON_NOT_SEEKABLE_TO_START);
    }
  }

  @Test
  public void clippingUnseekableWindowWithUnknownDurationThrows() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    // If the unseekable window isn't clipped, clipping succeeds.
    getClippedTimeline(timeline, /* startUs= */ 0, TEST_PERIOD_DURATION_US);
    try {
      // If the unseekable window is clipped, clipping fails.
      getClippedTimeline(timeline, /* startUs= */ 1, TEST_PERIOD_DURATION_US);
      fail("Expected clipping to fail.");
    } catch (IllegalClippingException e) {
      assertThat(e.reason).isEqualTo(IllegalClippingException.REASON_NOT_SEEKABLE_TO_START);
    }
  }

  @Test
  public void clippingStart() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            TEST_PERIOD_DURATION_US,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline =
        getClippedTimeline(timeline, TEST_CLIP_AMOUNT_US, TEST_PERIOD_DURATION_US);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US);
  }

  @Test
  public void clippingEnd() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            TEST_PERIOD_DURATION_US,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline =
        getClippedTimeline(timeline, 0, TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
  }

  @Test
  public void clippingStartAndEndInitial() throws IOException {
    // Timeline that's dynamic and not seekable. A child source might report such a timeline prior
    // to it having loaded sufficient data to establish its duration and seekability. Such timelines
    // should not result in clipping failure.
    Timeline timeline = new PlaceholderTimeline(MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline =
        getClippedTimeline(
            timeline, TEST_CLIP_AMOUNT_US, TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 2);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 3);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 2);
  }

  @Test
  public void clippingToEndOfSourceWithDurationSetsDuration() throws IOException {
    // Create a child timeline that has a known duration.
    Timeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ TEST_PERIOD_DURATION_US,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    // When clipping to the end, the clipped timeline should also have a duration.
    Timeline clippedTimeline =
        getClippedTimeline(timeline, TEST_CLIP_AMOUNT_US, C.TIME_END_OF_SOURCE);
    assertThat(clippedTimeline.getWindow(/* windowIndex= */ 0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
  }

  @Test
  public void clippingToEndOfSourceWithUnsetDurationDoesNotSetDuration() throws IOException {
    // Create a child timeline that has an unknown duration.
    Timeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    // When clipping to the end, the clipped timeline should also have an unset duration.
    Timeline clippedTimeline =
        getClippedTimeline(timeline, TEST_CLIP_AMOUNT_US, C.TIME_END_OF_SOURCE);
    assertThat(clippedTimeline.getWindow(/* windowIndex= */ 0, window).getDurationUs())
        .isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void clippingStartAndEnd() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            TEST_PERIOD_DURATION_US,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline =
        getClippedTimeline(
            timeline, TEST_CLIP_AMOUNT_US, TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 2);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 3);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US * 2);
  }

  @Test
  public void clippingFromDefaultPosition() throws IOException {
    Timeline timeline =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 3 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline clippedTimeline = getClippedTimeline(timeline, /* durationUs= */ TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getWindow(0, window).getDurationUs()).isEqualTo(TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimeline.getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimeline.getPeriod(0, period).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + 2 * TEST_CLIP_AMOUNT_US);
  }

  @Test
  public void allowDynamicUpdatesWithOverlappingLiveWindow() throws IOException {
    Timeline timeline1 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    Timeline timeline2 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 3 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline[] clippedTimelines =
        getClippedTimelines(
            /* startUs= */ 0,
            /* endUs= */ TEST_PERIOD_DURATION_US,
            /* allowDynamicUpdates= */ true,
            /* fromDefaultPosition= */ true,
            timeline1,
            timeline2);
    assertThat(clippedTimelines[0].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[0].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[0].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getPeriod(0, period).getDurationUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[1].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[1].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getPeriod(0, period).getDurationUs())
        .isEqualTo(3 * TEST_PERIOD_DURATION_US);
  }

  @Test
  public void allowDynamicUpdatesWithNonOverlappingLiveWindow() throws IOException {
    Timeline timeline1 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    Timeline timeline2 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 4 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ 3 * TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline[] clippedTimelines =
        getClippedTimelines(
            /* startUs= */ 0,
            /* endUs= */ TEST_PERIOD_DURATION_US,
            /* allowDynamicUpdates= */ true,
            /* fromDefaultPosition= */ true,
            timeline1,
            timeline2);
    assertThat(clippedTimelines[0].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[0].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[0].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getPeriod(0, period).getDurationUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[1].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[1].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(3 * TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getPeriod(0, period).getDurationUs())
        .isEqualTo(4 * TEST_PERIOD_DURATION_US);
  }

  @Test
  public void disallowDynamicUpdatesWithOverlappingLiveWindow() throws IOException {
    Timeline timeline1 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    Timeline timeline2 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 3 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline[] clippedTimelines =
        getClippedTimelines(
            /* startUs= */ 0,
            /* endUs= */ TEST_PERIOD_DURATION_US,
            /* allowDynamicUpdates= */ false,
            /* fromDefaultPosition= */ true,
            timeline1,
            timeline2);
    assertThat(clippedTimelines[0].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[0].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[0].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getPeriod(0, period).getDurationUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDefaultPositionUs())
        .isEqualTo(TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[1].getWindow(0, window).isDynamic).isFalse();
    assertThat(clippedTimelines[1].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getPeriod(0, period).getDurationUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
  }

  @Test
  public void disallowDynamicUpdatesWithNonOverlappingLiveWindow() throws IOException {
    Timeline timeline1 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 2 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    Timeline timeline2 =
        new SinglePeriodTimeline(
            /* periodDurationUs= */ 4 * TEST_PERIOD_DURATION_US,
            /* windowDurationUs= */ TEST_PERIOD_DURATION_US,
            /* windowPositionInPeriodUs= */ 3 * TEST_PERIOD_DURATION_US,
            /* windowDefaultStartPositionUs= */ TEST_CLIP_AMOUNT_US,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));

    Timeline[] clippedTimelines =
        getClippedTimelines(
            /* startUs= */ 0,
            /* endUs= */ TEST_PERIOD_DURATION_US,
            /* allowDynamicUpdates= */ false,
            /* fromDefaultPosition= */ true,
            timeline1,
            timeline2);
    assertThat(clippedTimelines[0].getWindow(0, window).getDurationUs())
        .isEqualTo(TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[0].getWindow(0, window).isDynamic).isTrue();
    assertThat(clippedTimelines[0].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(TEST_PERIOD_DURATION_US + TEST_CLIP_AMOUNT_US);
    assertThat(clippedTimelines[0].getPeriod(0, period).getDurationUs())
        .isEqualTo(2 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getWindow(0, window).getDurationUs()).isEqualTo(0);
    assertThat(clippedTimelines[1].getWindow(0, window).getDefaultPositionUs()).isEqualTo(0);
    assertThat(clippedTimelines[1].getWindow(0, window).isDynamic).isFalse();
    assertThat(clippedTimelines[1].getWindow(0, window).getPositionInFirstPeriodUs())
        .isEqualTo(3 * TEST_PERIOD_DURATION_US);
    assertThat(clippedTimelines[1].getPeriod(0, period).getDurationUs())
        .isEqualTo(3 * TEST_PERIOD_DURATION_US);
  }

  @Test
  public void windowAndPeriodIndices() throws IOException {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(1, 111, true, false, TEST_PERIOD_DURATION_US));
    Timeline clippedTimeline =
        getClippedTimeline(
            timeline, TEST_CLIP_AMOUNT_US, TEST_PERIOD_DURATION_US - TEST_CLIP_AMOUNT_US);
    TimelineAsserts.assertWindowTags(clippedTimeline, 111);
    TimelineAsserts.assertPeriodCounts(clippedTimeline, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        clippedTimeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(clippedTimeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertPreviousWindowIndices(clippedTimeline, Player.REPEAT_MODE_ALL, false, 0);
    TimelineAsserts.assertNextWindowIndices(
        clippedTimeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(clippedTimeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertNextWindowIndices(clippedTimeline, Player.REPEAT_MODE_ALL, false, 0);
  }

  /**
   * Wraps the specified timeline in a {@link ClippingMediaSource} and returns the clipped timeline.
   */
  private static Timeline getClippedTimeline(Timeline timeline, long startUs, long endUs)
      throws IOException {
    FakeMediaSource fakeMediaSource = new FakeMediaSource(timeline);
    ClippingMediaSource mediaSource = new ClippingMediaSource(fakeMediaSource, startUs, endUs);
    return getClippedTimelines(fakeMediaSource, mediaSource)[0];
  }

  /**
   * Wraps the specified timeline in a {@link ClippingMediaSource} and returns the clipped timeline.
   */
  private static Timeline getClippedTimeline(Timeline timeline, long durationUs)
      throws IOException {
    FakeMediaSource fakeMediaSource = new FakeMediaSource(timeline);
    ClippingMediaSource mediaSource = new ClippingMediaSource(fakeMediaSource, durationUs);
    return getClippedTimelines(fakeMediaSource, mediaSource)[0];
  }

  /**
   * Wraps the specified timelines in a {@link ClippingMediaSource} and returns the clipped timeline
   * for each timeline update.
   */
  private static Timeline[] getClippedTimelines(
      long startUs,
      long endUs,
      boolean allowDynamicUpdates,
      boolean fromDefaultPosition,
      Timeline firstTimeline,
      Timeline... additionalTimelines)
      throws IOException {
    FakeMediaSource fakeMediaSource = new FakeMediaSource(firstTimeline);
    ClippingMediaSource mediaSource =
        new ClippingMediaSource(
            fakeMediaSource,
            startUs,
            endUs,
            /* enableInitialDiscontinuity= */ true,
            allowDynamicUpdates,
            fromDefaultPosition);
    return getClippedTimelines(fakeMediaSource, mediaSource, additionalTimelines);
  }

  private static Timeline[] getClippedTimelines(
      FakeMediaSource fakeMediaSource,
      ClippingMediaSource clippingMediaSource,
      Timeline... additionalTimelines)
      throws IOException {
    MediaSourceTestRunner testRunner =
        new MediaSourceTestRunner(clippingMediaSource, /* allocator= */ null);
    Timeline[] clippedTimelines = new Timeline[additionalTimelines.length + 1];
    try {
      clippedTimelines[0] = testRunner.prepareSource();
      MediaPeriod mediaPeriod =
          testRunner.createPeriod(
              new MediaPeriodId(
                  clippedTimelines[0].getUidOfPeriod(/* periodIndex= */ 0),
                  /* windowSequenceNumber= */ 0));
      for (int i = 0; i < additionalTimelines.length; i++) {
        fakeMediaSource.setNewSourceInfo(additionalTimelines[i]);
        clippedTimelines[i + 1] = testRunner.assertTimelineChangeBlocking();
      }
      testRunner.releasePeriod(mediaPeriod);
      testRunner.releaseSource();
      fakeMediaSource.assertReleased();
      return clippedTimelines;
    } finally {
      testRunner.release();
    }
  }
}

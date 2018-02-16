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
import static org.mockito.Mockito.verify;

import android.os.ConditionVariable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RobolectricUtil;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link DynamicConcatenatingMediaSource} */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public final class DynamicConcatenatingMediaSourceTest {

  private DynamicConcatenatingMediaSource mediaSource;
  private MediaSourceTestRunner testRunner;

  @Before
  public void setUp() throws Exception {
    mediaSource =
        new DynamicConcatenatingMediaSource(/* isAtomic= */ false, new FakeShuffleOrder(0));
    testRunner = new MediaSourceTestRunner(mediaSource, null);
  }

  @After
  public void tearDown() throws Exception {
    testRunner.release();
  }

  @Test
  public void testPlaylistChangesAfterPreparation() throws IOException, InterruptedException {
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertEmpty(timeline);

    FakeMediaSource[] childSources = createMediaSources(7);

    // Add first source.
    mediaSource.addMediaSource(childSources[0]);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);

    // Add at front of queue.
    mediaSource.addMediaSource(0, childSources[1]);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1);
    TimelineAsserts.assertWindowIds(timeline, 222, 111);

    // Add at back of queue.
    mediaSource.addMediaSource(childSources[2]);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 111, 333);

    // Add in the middle.
    mediaSource.addMediaSource(1, childSources[3]);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 333);

    // Add bulk.
    mediaSource.addMediaSources(
        3, Arrays.<MediaSource>asList(childSources[4], childSources[5], childSources[6]));
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Move sources.
    mediaSource.moveMediaSource(2, 3);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 5, 1, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 555, 111, 666, 777, 333);
    mediaSource.moveMediaSource(3, 2);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);
    mediaSource.moveMediaSource(0, 6);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 4, 1, 5, 6, 7, 3, 2);
    TimelineAsserts.assertWindowIds(timeline, 444, 111, 555, 666, 777, 333, 222);
    mediaSource.moveMediaSource(6, 0);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Remove in the middle.
    mediaSource.removeMediaSource(3);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.removeMediaSource(3);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.removeMediaSource(3);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.removeMediaSource(1);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 111, 333);
    for (int i = 3; i <= 6; i++) {
      childSources[i].assertReleased();
    }

    // Assert correct next and previous indices behavior after some insertions and removals.
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 2, 0, 1);
    assertThat(timeline.getFirstWindowIndex(false)).isEqualTo(0);
    assertThat(timeline.getLastWindowIndex(false)).isEqualTo(timeline.getWindowCount() - 1);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 1, 2, 0);
    assertThat(timeline.getFirstWindowIndex(true)).isEqualTo(timeline.getWindowCount() - 1);
    assertThat(timeline.getLastWindowIndex(true)).isEqualTo(0);

    // Assert all periods can be prepared.
    testRunner.assertPrepareAndReleaseAllPeriods();

    // Remove at front of queue.
    mediaSource.removeMediaSource(0);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 111, 333);
    childSources[1].assertReleased();

    // Remove at back of queue.
    mediaSource.removeMediaSource(1);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);
    childSources[2].assertReleased();

    // Remove last source.
    mediaSource.removeMediaSource(0);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertEmpty(timeline);
    childSources[3].assertReleased();
  }

  @Test
  public void testPlaylistChangesBeforePreparation() throws IOException, InterruptedException {
    FakeMediaSource[] childSources = createMediaSources(4);
    mediaSource.addMediaSource(childSources[0]);
    mediaSource.addMediaSource(childSources[1]);
    mediaSource.addMediaSource(0, childSources[2]);
    mediaSource.moveMediaSource(0, 2);
    mediaSource.removeMediaSource(0);
    mediaSource.moveMediaSource(1, 0);
    mediaSource.addMediaSource(1, childSources[3]);
    testRunner.assertNoTimelineChange();

    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertPeriodCounts(timeline, 3, 4, 2);
    TimelineAsserts.assertWindowIds(timeline, 333, 444, 222);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, 1, 2, C.INDEX_UNSET);

    testRunner.assertPrepareAndReleaseAllPeriods();
    mediaSource.releaseSource();
    for (int i = 1; i < 4; i++) {
      childSources[i].assertReleased();
    }
  }

  @Test
  public void testPlaylistWithLazyMediaSource() throws IOException, InterruptedException {
    // Create some normal (immediately preparing) sources and some lazy sources whose timeline
    // updates need to be triggered.
    FakeMediaSource[] fastSources = createMediaSources(2);
    final FakeMediaSource[] lazySources = new FakeMediaSource[4];
    for (int i = 0; i < 4; i++) {
      lazySources[i] = new FakeMediaSource(null, null);
    }

    // Add lazy sources and normal sources before preparation. Also remove one lazy source again
    // before preparation to check it doesn't throw or change the result.
    mediaSource.addMediaSource(lazySources[0]);
    mediaSource.addMediaSource(0, fastSources[0]);
    mediaSource.removeMediaSource(1);
    mediaSource.addMediaSource(1, lazySources[1]);
    testRunner.assertNoTimelineChange();

    // Prepare and assert that the timeline contains all information for normal sources while having
    // placeholder information for lazy sources.
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1);
    TimelineAsserts.assertWindowIds(timeline, 111, null);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, true);

    // Trigger source info refresh for lazy source and check that the timeline now contains all
    // information for all windows.
    testRunner.runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            lazySources[1].setNewSourceInfo(createFakeTimeline(8), null);
          }
        });
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 9);
    TimelineAsserts.assertWindowIds(timeline, 111, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false);
    testRunner.assertPrepareAndReleaseAllPeriods();

    // Add further lazy and normal sources after preparation. Also remove one lazy source again to
    // check it doesn't throw or change the result.
    mediaSource.addMediaSource(1, lazySources[2]);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.addMediaSource(2, fastSources[1]);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.addMediaSource(0, lazySources[3]);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.removeMediaSource(2);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, null, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, true, false, false, false);

    // Create a period from an unprepared lazy media source and assert Callback.onPrepared is not
    // called yet.
    MediaPeriod lazyPeriod =
        testRunner.createPeriod(
            new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 0));
    CountDownLatch preparedCondition = testRunner.preparePeriod(lazyPeriod, 0);
    assertThat(preparedCondition.getCount()).isEqualTo(1);

    // Assert that a second period can also be created and released without problems.
    MediaPeriod secondLazyPeriod =
        testRunner.createPeriod(
            new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 0));
    testRunner.releasePeriod(secondLazyPeriod);

    // Trigger source info refresh for lazy media source. Assert that now all information is
    // available again and the previously created period now also finished preparing.
    testRunner.runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            lazySources[3].setNewSourceInfo(createFakeTimeline(7), null);
          }
        });
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 8, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, 888, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false, false, false);
    assertThat(preparedCondition.getCount()).isEqualTo(0);

    // Release the period and source.
    testRunner.releasePeriod(lazyPeriod);
    testRunner.releaseSource();

    // Assert all sources were fully released.
    for (FakeMediaSource fastSource : fastSources) {
      fastSource.assertReleased();
    }
    for (FakeMediaSource lazySource : lazySources) {
      lazySource.assertReleased();
    }
  }

  @Test
  public void testEmptyTimelineMediaSource() throws IOException, InterruptedException {
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSource(new FakeMediaSource(Timeline.EMPTY, null));
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSources(
        Arrays.asList(
            new MediaSource[] {
              new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null),
              new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null),
              new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null)
            }));
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertEmpty(timeline);

    // Insert non-empty media source to leave empty sources at the start, the end, and the middle
    // (with single and multiple empty sources in a row).
    MediaSource[] mediaSources = createMediaSources(3);
    mediaSource.addMediaSource(1, mediaSources[0]);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.addMediaSource(4, mediaSources[1]);
    testRunner.assertTimelineChangeBlocking();
    mediaSource.addMediaSource(6, mediaSources[2]);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 2, 3);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, true, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 1);
    assertThat(timeline.getFirstWindowIndex(false)).isEqualTo(0);
    assertThat(timeline.getLastWindowIndex(false)).isEqualTo(2);
    assertThat(timeline.getFirstWindowIndex(true)).isEqualTo(2);
    assertThat(timeline.getLastWindowIndex(true)).isEqualTo(0);
    testRunner.assertPrepareAndReleaseAllPeriods();
  }

  @Test
  public void testDynamicChangeOfEmptyTimelines() throws IOException {
    FakeMediaSource[] childSources =
        new FakeMediaSource[] {
          new FakeMediaSource(Timeline.EMPTY, /* manifest= */ null),
          new FakeMediaSource(Timeline.EMPTY, /* manifest= */ null),
          new FakeMediaSource(Timeline.EMPTY, /* manifest= */ null),
        };
    Timeline nonEmptyTimeline = new FakeTimeline(/* windowCount = */ 1);

    mediaSource.addMediaSources(Arrays.<MediaSource>asList(childSources));
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertEmpty(timeline);

    childSources[0].setNewSourceInfo(nonEmptyTimeline, /* newManifest== */ null);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1);

    childSources[2].setNewSourceInfo(nonEmptyTimeline, /* newManifest== */ null);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1);

    childSources[1].setNewSourceInfo(nonEmptyTimeline, /* newManifest== */ null);
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1);
  }

  @Test
  public void testIllegalArguments() {
    MediaSource validSource = new FakeMediaSource(createFakeTimeline(1), null);

    // Null sources.
    try {
      mediaSource.addMediaSource(null);
      fail("Null mediaSource not allowed.");
    } catch (NullPointerException e) {
      // Expected.
    }

    MediaSource[] mediaSources = {validSource, null};
    try {
      mediaSource.addMediaSources(Arrays.asList(mediaSources));
      fail("Null mediaSource not allowed.");
    } catch (NullPointerException e) {
      // Expected.
    }

    // Duplicate sources.
    mediaSource.addMediaSource(validSource);
    try {
      mediaSource.addMediaSource(validSource);
      fail("Duplicate mediaSource not allowed.");
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    mediaSources =
        new MediaSource[] {new FakeMediaSource(createFakeTimeline(2), null), validSource};
    try {
      mediaSource.addMediaSources(Arrays.asList(mediaSources));
      fail("Duplicate mediaSource not allowed.");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void testCustomCallbackBeforePreparationAddSingle() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackBeforePreparationAddMultiple() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(
        Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
        runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackBeforePreparationAddSingleWithIndex() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackBeforePreparationAddMultipleWithIndex() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(
        /* index */ 0,
        Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
        runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackBeforePreparationRemove() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(createFakeMediaSource());
    mediaSource.removeMediaSource(/* index */ 0, runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackBeforePreparationMove() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(
        Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}));
    mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0, runnable);
    verify(runnable).run();
  }

  @Test
  public void testCustomCallbackAfterPreparationAddSingle() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSource(createFakeMediaSource(), timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(1);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testCustomCallbackAfterPreparationAddMultiple() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSources(
                  Arrays.asList(
                      new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
                  timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(2);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testCustomCallbackAfterPreparationAddSingleWithIndex() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(), timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(1);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testCustomCallbackAfterPreparationAddMultipleWithIndex() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSources(
                  /* index */ 0,
                  Arrays.asList(
                      new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
                  timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(2);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testCustomCallbackAfterPreparationRemove() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSource(createFakeMediaSource());
            }
          });
      testRunner.assertTimelineChangeBlocking();

      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.removeMediaSource(/* index */ 0, timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(0);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testCustomCallbackAfterPreparationMove() throws IOException {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.addMediaSources(
                  Arrays.asList(
                      new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}));
            }
          });
      testRunner.assertTimelineChangeBlocking();

      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0, timelineGrabber);
            }
          });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertThat(timeline.getWindowCount()).isEqualTo(2);
    } finally {
      dummyMainThread.release();
    }
  }

  @Test
  public void testPeriodCreationWithAds() throws IOException, InterruptedException {
    // Create dynamic media source with ad child source.
    Timeline timelineContentOnly =
        new FakeTimeline(
            new TimelineWindowDefinition(2, 111, true, false, 10 * C.MICROS_PER_SECOND));
    Timeline timelineWithAds =
        new FakeTimeline(
            new TimelineWindowDefinition(
                2,
                222,
                true,
                false,
                10 * C.MICROS_PER_SECOND,
                FakeTimeline.createAdPlaybackState(
                    /* adsPerAdGroup= */ 1, /* adGroupTimesUs= */ 0)));
    FakeMediaSource mediaSourceContentOnly = new FakeMediaSource(timelineContentOnly, null);
    FakeMediaSource mediaSourceWithAds = new FakeMediaSource(timelineWithAds, null);
    mediaSource.addMediaSource(mediaSourceContentOnly);
    mediaSource.addMediaSource(mediaSourceWithAds);

    Timeline timeline = testRunner.prepareSource();

    // Assert the timeline contains ad groups.
    TimelineAsserts.assertAdGroupCounts(timeline, 0, 0, 1, 1);

    // Create all periods and assert period creation of child media sources has been called.
    testRunner.assertPrepareAndReleaseAllPeriods();
    mediaSourceContentOnly.assertMediaPeriodCreated(
        new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 0));
    mediaSourceContentOnly.assertMediaPeriodCreated(
        new MediaPeriodId(/* periodIndex= */ 1, /* windowSequenceNumber= */ 0));
    mediaSourceWithAds.assertMediaPeriodCreated(
        new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 1));
    mediaSourceWithAds.assertMediaPeriodCreated(
        new MediaPeriodId(/* periodIndex= */ 1, /* windowSequenceNumber= */ 1));
    mediaSourceWithAds.assertMediaPeriodCreated(
        new MediaPeriodId(
            /* periodIndex= */ 0,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 1));
    mediaSourceWithAds.assertMediaPeriodCreated(
        new MediaPeriodId(
            /* periodIndex= */ 1,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 1));
  }

  @Test
  public void testAtomicTimelineWindowOrder() throws IOException {
    // Release default test runner with non-atomic media source and replace with new test runner.
    testRunner.release();
    DynamicConcatenatingMediaSource mediaSource =
        new DynamicConcatenatingMediaSource(/* isAtomic= */ true, new FakeShuffleOrder(0));
    testRunner = new MediaSourceTestRunner(mediaSource, null);
    mediaSource.addMediaSources(Arrays.<MediaSource>asList(createMediaSources(3)));
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 2, 3);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true, C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true, 2, 0, 1);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true, 1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false, 1, 2, 0);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true, 1, 2, 0);
    assertThat(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false)).isEqualTo(0);
    assertThat(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true)).isEqualTo(0);
    assertThat(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ false)).isEqualTo(2);
    assertThat(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ true)).isEqualTo(2);
  }

  @Test
  public void testNestedTimeline() throws IOException {
    DynamicConcatenatingMediaSource nestedSource1 =
        new DynamicConcatenatingMediaSource(/* isAtomic= */ false, new FakeShuffleOrder(0));
    DynamicConcatenatingMediaSource nestedSource2 =
        new DynamicConcatenatingMediaSource(/* isAtomic= */ true, new FakeShuffleOrder(0));
    mediaSource.addMediaSource(nestedSource1);
    mediaSource.addMediaSource(nestedSource2);
    testRunner.prepareSource();
    FakeMediaSource[] childSources = createMediaSources(4);
    nestedSource1.addMediaSource(childSources[0]);
    testRunner.assertTimelineChangeBlocking();
    nestedSource1.addMediaSource(childSources[1]);
    testRunner.assertTimelineChangeBlocking();
    nestedSource2.addMediaSource(childSources[2]);
    testRunner.assertTimelineChangeBlocking();
    nestedSource2.addMediaSource(childSources[3]);
    Timeline timeline = testRunner.assertTimelineChangeBlocking();

    TimelineAsserts.assertWindowIds(timeline, 111, 222, 333, 444);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 2, 3, 4);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false, C.INDEX_UNSET, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false, 0, 1, 3, 2);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false, 3, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false, 1, 2, 3, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false, 0, 1, 3, 2);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false, 1, 2, 3, 0);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true, 1, 3, C.INDEX_UNSET, 2);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true, 0, 1, 3, 2);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true, 1, 3, 0, 2);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true, C.INDEX_UNSET, 0, 3, 1);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true, 0, 1, 3, 2);
    TimelineAsserts.assertNextWindowIndices(
        timeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true, 2, 0, 3, 1);
  }

  @Test
  public void testRemoveChildSourceWithActiveMediaPeriod() throws IOException {
    FakeMediaSource childSource = createFakeMediaSource();
    mediaSource.addMediaSource(childSource);
    testRunner.prepareSource();
    MediaPeriod mediaPeriod =
        testRunner.createPeriod(
            new MediaPeriodId(/* periodIndex= */ 0, /* windowSequenceNumber= */ 0));
    mediaSource.removeMediaSource(/* index= */ 0);
    testRunner.assertTimelineChangeBlocking();
    testRunner.releasePeriod(mediaPeriod);
    childSource.assertReleased();
    testRunner.releaseSource();
  }

  private static FakeMediaSource[] createMediaSources(int count) {
    FakeMediaSource[] sources = new FakeMediaSource[count];
    for (int i = 0; i < count; i++) {
      sources[i] = new FakeMediaSource(createFakeTimeline(i), null);
    }
    return sources;
  }

  private static FakeMediaSource createFakeMediaSource() {
    return new FakeMediaSource(createFakeTimeline(/* index */ 0), null);
  }

  private static FakeTimeline createFakeTimeline(int index) {
    return new FakeTimeline(new TimelineWindowDefinition(index + 1, (index + 1) * 111));
  }

  private static final class TimelineGrabber implements Runnable {

    private final MediaSourceTestRunner testRunner;
    private final ConditionVariable finishedCondition;

    private Timeline timeline;
    private AssertionError error;

    public TimelineGrabber(MediaSourceTestRunner testRunner) {
      this.testRunner = testRunner;
      finishedCondition = new ConditionVariable();
    }

    @Override
    public void run() {
      try {
        timeline = testRunner.assertTimelineChange();
      } catch (AssertionError e) {
        error = e;
      }
      finishedCondition.open();
    }

    public Timeline assertTimelineChangeBlocking() {
      assertThat(finishedCondition.block(MediaSourceTestRunner.TIMEOUT_MS)).isTrue();
      if (error != null) {
        throw error;
      }
      return timeline;
    }
  }
}

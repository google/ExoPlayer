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

import static org.mockito.Mockito.verify;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import java.util.Arrays;
import junit.framework.TestCase;
import org.mockito.Mockito;

/**
 * Unit tests for {@link DynamicConcatenatingMediaSource}
 */
public final class DynamicConcatenatingMediaSourceTest extends TestCase {

  private DynamicConcatenatingMediaSource mediaSource;
  private MediaSourceTestRunner testRunner;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mediaSource = new DynamicConcatenatingMediaSource(new FakeShuffleOrder(0));
    testRunner = new MediaSourceTestRunner(mediaSource, null);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    testRunner.release();
  }

  public void testPlaylistChangesAfterPreparation() {
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
    mediaSource.addMediaSources(3, Arrays.<MediaSource>asList(childSources[4], childSources[5],
        childSources[6]));
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
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 1, 2, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 2, 0, 1);
    assertEquals(0, timeline.getFirstWindowIndex(false));
    assertEquals(timeline.getWindowCount() - 1, timeline.getLastWindowIndex(false));
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 2, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, true, 0, 1, 2);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, true, 1, 2, 0);
    assertEquals(timeline.getWindowCount() - 1, timeline.getFirstWindowIndex(true));
    assertEquals(0, timeline.getLastWindowIndex(true));

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

  public void testPlaylistChangesBeforePreparation() {
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
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        1, 2, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, false,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        C.INDEX_UNSET, 0, 1);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_OFF, true,
        1, 2, C.INDEX_UNSET);

    testRunner.assertPrepareAndReleaseAllPeriods();
    mediaSource.releaseSource();
    for (int i = 1; i < 4; i++) {
      childSources[i].assertReleased();
    }
  }

  public void testPlaylistWithLazyMediaSource() {
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
    testRunner.runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        lazySources[1].setNewSourceInfo(createFakeTimeline(8), null);
      }
    });
    timeline = testRunner.assertTimelineChange();
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
    MediaPeriod lazyPeriod = testRunner.createPeriod(new MediaPeriodId(0));
    ConditionVariable preparedCondition = testRunner.preparePeriod(lazyPeriod, 0);
    assertFalse(preparedCondition.block(1));

    // Assert that a second period can also be created and released without problems.
    MediaPeriod secondLazyPeriod = testRunner.createPeriod(new MediaPeriodId(0));
    testRunner.releasePeriod(secondLazyPeriod);

    // Trigger source info refresh for lazy media source. Assert that now all information is
    // available again and the previously created period now also finished preparing.
    testRunner.runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        lazySources[3].setNewSourceInfo(createFakeTimeline(7), null);
      }
    });
    timeline = testRunner.assertTimelineChange();
    TimelineAsserts.assertPeriodCounts(timeline, 8, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, 888, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false, false, false);
    assertTrue(preparedCondition.block(1));

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

  public void testEmptyTimelineMediaSource() {
    Timeline timeline = testRunner.prepareSource();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSource(new FakeMediaSource(Timeline.EMPTY, null));
    timeline = testRunner.assertTimelineChangeBlocking();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSources(Arrays.asList(new MediaSource[] {
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
    testRunner.assertPrepareAndReleaseAllPeriods();
  }

  public void testIllegalArguments() {
    MediaSource validSource = new FakeMediaSource(createFakeTimeline(1), null);

    // Null sources.
    try {
      mediaSource.addMediaSource(null);
      fail("Null mediaSource not allowed.");
    } catch (NullPointerException e) {
      // Expected.
    }

    MediaSource[] mediaSources = { validSource, null };
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

    mediaSources = new MediaSource[] {
        new FakeMediaSource(createFakeTimeline(2), null), validSource };
    try {
      mediaSource.addMediaSources(Arrays.asList(mediaSources));
      fail("Duplicate mediaSource not allowed.");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  public void testCustomCallbackBeforePreparationAddSingle() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddMultiple() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(Arrays.asList(
        new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddSingleWithIndex() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddMultipleWithIndex() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(/* index */ 0,
        Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
        runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationRemove() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(createFakeMediaSource());
    mediaSource.removeMediaSource(/* index */ 0, runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationMove() {
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(
        Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}));
    mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0, runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackAfterPreparationAddSingle() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSource(createFakeMediaSource(), timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(1, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testCustomCallbackAfterPreparationAddMultiple() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSources(
              Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
              timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(2, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testCustomCallbackAfterPreparationAddSingleWithIndex() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(), timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(1, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testCustomCallbackAfterPreparationAddMultipleWithIndex() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSources(/* index */ 0,
              Arrays.asList(new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}),
              timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(2, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testCustomCallbackAfterPreparationRemove() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSource(createFakeMediaSource());
        }
      });
      testRunner.assertTimelineChangeBlocking();

      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.removeMediaSource(/* index */ 0, timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(0, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testCustomCallbackAfterPreparationMove() {
    DummyMainThread dummyMainThread = new DummyMainThread();
    try {
      testRunner.prepareSource();
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.addMediaSources(Arrays.asList(
              new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}));
        }
      });
      testRunner.assertTimelineChangeBlocking();

      final TimelineGrabber timelineGrabber = new TimelineGrabber(testRunner);
      dummyMainThread.runOnMainThread(new Runnable() {
        @Override
        public void run() {
          mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0,
              timelineGrabber);
        }
      });
      Timeline timeline = timelineGrabber.assertTimelineChangeBlocking();
      assertEquals(2, timeline.getWindowCount());
    } finally {
      dummyMainThread.release();
    }
  }

  public void testPeriodCreationWithAds() throws InterruptedException {
    // Create dynamic media source with ad child source.
    Timeline timelineContentOnly = new FakeTimeline(
        new TimelineWindowDefinition(2, 111, true, false, 10 * C.MICROS_PER_SECOND));
    Timeline timelineWithAds = new FakeTimeline(
        new TimelineWindowDefinition(2, 222, true, false, 10 * C.MICROS_PER_SECOND, 1, 1));
    FakeMediaSource mediaSourceContentOnly = new FakeMediaSource(timelineContentOnly, null);
    FakeMediaSource mediaSourceWithAds = new FakeMediaSource(timelineWithAds, null);
    mediaSource.addMediaSource(mediaSourceContentOnly);
    mediaSource.addMediaSource(mediaSourceWithAds);

    Timeline timeline = testRunner.prepareSource();

    // Assert the timeline contains ad groups.
    TimelineAsserts.assertAdGroupCounts(timeline, 0, 0, 1, 1);

    // Create all periods and assert period creation of child media sources has been called.
    testRunner.assertPrepareAndReleaseAllPeriods();
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0, 0, 0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1, 0, 0));
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

  private static final class DummyMainThread {

    private final HandlerThread thread;
    private final Handler handler;

    private DummyMainThread() {
      thread = new HandlerThread("DummyMainThread");
      thread.start();
      handler = new Handler(thread.getLooper());
    }

    /**
     * Runs the provided {@link Runnable} on the main thread, blocking until execution completes.
     *
     * @param runnable The {@link Runnable} to run.
     */
    public void runOnMainThread(final Runnable runnable) {
      final ConditionVariable finishedCondition = new ConditionVariable();
      handler.post(new Runnable() {
        @Override
        public void run() {
          runnable.run();
          finishedCondition.open();
        }
      });
      assertTrue(finishedCondition.block(MediaSourceTestRunner.TIMEOUT_MS));
    }

    public void release() {
      thread.quit();
    }

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
      assertTrue(finishedCondition.block(MediaSourceTestRunner.TIMEOUT_MS));
      if (error != null) {
        throw error;
      }
      return timeline;
    }

  }

}

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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.source.MediaSource.Listener;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;
import java.util.Arrays;
import junit.framework.TestCase;
import org.mockito.Mockito;

/**
 * Unit tests for {@link DynamicConcatenatingMediaSource}
 */
public final class DynamicConcatenatingMediaSourceTest extends TestCase {

  private static final int TIMEOUT_MS = 10000;

  private Timeline timeline;
  private boolean timelineUpdated;
  private boolean customRunnableCalled;

  public void testPlaylistChangesAfterPreparation() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(7);
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource(
        new FakeShuffleOrder(0));
    prepareAndListenToTimelineUpdates(mediaSource);
    assertNotNull(timeline);
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);

    // Add first source.
    mediaSource.addMediaSource(childSources[0]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);

    // Add at front of queue.
    mediaSource.addMediaSource(0, childSources[1]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1);
    TimelineAsserts.assertWindowIds(timeline, 222, 111);

    // Add at back of queue.
    mediaSource.addMediaSource(childSources[2]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 111, 333);

    // Add in the middle.
    mediaSource.addMediaSource(1, childSources[3]);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 333);

    // Add bulk.
    mediaSource.addMediaSources(3, Arrays.asList((MediaSource) childSources[4],
        (MediaSource) childSources[5], (MediaSource) childSources[6]));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Move sources.
    mediaSource.moveMediaSource(2, 3);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 5, 1, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 555, 111, 666, 777, 333);
    mediaSource.moveMediaSource(3, 2);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);
    mediaSource.moveMediaSource(0, 6);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 4, 1, 5, 6, 7, 3, 2);
    TimelineAsserts.assertWindowIds(timeline, 444, 111, 555, 666, 777, 333, 222);
    mediaSource.moveMediaSource(6, 0);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 2, 4, 1, 5, 6, 7, 3);
    TimelineAsserts.assertWindowIds(timeline, 222, 444, 111, 555, 666, 777, 333);

    // Remove in the middle.
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(3);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(1);
    waitForTimelineUpdate();
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
    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline,
        TIMEOUT_MS);

    // Remove at front of queue.
    mediaSource.removeMediaSource(0);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 3);
    TimelineAsserts.assertWindowIds(timeline, 111, 333);
    childSources[1].assertReleased();

    // Remove at back of queue.
    mediaSource.removeMediaSource(1);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertWindowIds(timeline, 111);
    childSources[2].assertReleased();

    // Remove last source.
    mediaSource.removeMediaSource(0);
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);
    childSources[3].assertReleased();
  }

  public void testPlaylistChangesBeforePreparation() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(4);
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource(
        new FakeShuffleOrder(0));
    mediaSource.addMediaSource(childSources[0]);
    mediaSource.addMediaSource(childSources[1]);
    mediaSource.addMediaSource(0, childSources[2]);
    mediaSource.moveMediaSource(0, 2);
    mediaSource.removeMediaSource(0);
    mediaSource.moveMediaSource(1, 0);
    mediaSource.addMediaSource(1, childSources[3]);
    assertNull(timeline);

    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    assertNotNull(timeline);
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

    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline,
        TIMEOUT_MS);
    mediaSource.releaseSource();
    for (int i = 1; i < 4; i++) {
      childSources[i].assertReleased();
    }
  }

  public void testPlaylistWithLazyMediaSource() throws InterruptedException {
    timeline = null;
    FakeMediaSource[] childSources = createMediaSources(2);
    LazyMediaSource[] lazySources = new LazyMediaSource[4];
    for (int i = 0; i < 4; i++) {
      lazySources[i] = new LazyMediaSource();
    }

    //Add lazy sources before preparation
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    mediaSource.addMediaSource(lazySources[0]);
    mediaSource.addMediaSource(0, childSources[0]);
    mediaSource.removeMediaSource(1);
    mediaSource.addMediaSource(1, lazySources[1]);
    assertNull(timeline);
    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    assertNotNull(timeline);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1);
    TimelineAsserts.assertWindowIds(timeline, 111, null);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, true);

    lazySources[1].triggerTimelineUpdate(createFakeTimeline(8));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 9);
    TimelineAsserts.assertWindowIds(timeline, 111, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false);
    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline,
        TIMEOUT_MS);

    //Add lazy sources after preparation (and also try to prepare media period from lazy source).
    mediaSource.addMediaSource(1, lazySources[2]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(2, childSources[1]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(0, lazySources[3]);
    waitForTimelineUpdate();
    mediaSource.removeMediaSource(2);
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, null, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, true, false, false, false);

    MediaPeriod lazyPeriod = mediaSource.createPeriod(new MediaPeriodId(0), null);
    assertNotNull(lazyPeriod);
    final ConditionVariable lazyPeriodPrepared = new ConditionVariable();
    lazyPeriod.prepare(new Callback() {
      @Override
      public void onPrepared(MediaPeriod mediaPeriod) {
        lazyPeriodPrepared.open();
      }
      @Override
      public void onContinueLoadingRequested(MediaPeriod source) {}
    }, 0);
    assertFalse(lazyPeriodPrepared.block(1));
    MediaPeriod secondLazyPeriod = mediaSource.createPeriod(new MediaPeriodId(0), null);
    assertNotNull(secondLazyPeriod);
    mediaSource.releasePeriod(secondLazyPeriod);

    lazySources[3].triggerTimelineUpdate(createFakeTimeline(7));
    waitForTimelineUpdate();
    TimelineAsserts.assertPeriodCounts(timeline, 8, 1, 2, 9);
    TimelineAsserts.assertWindowIds(timeline, 888, 111, 222, 999);
    TimelineAsserts.assertWindowIsDynamic(timeline, false, false, false, false);
    assertTrue(lazyPeriodPrepared.block(TIMEOUT_MS));
    mediaSource.releasePeriod(lazyPeriod);

    mediaSource.releaseSource();
    childSources[0].assertReleased();
    childSources[1].assertReleased();
  }

  public void testEmptyTimelineMediaSource() throws InterruptedException {
    timeline = null;
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource(
        new FakeShuffleOrder(0));
    prepareAndListenToTimelineUpdates(mediaSource);
    assertNotNull(timeline);
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSource(new FakeMediaSource(Timeline.EMPTY, null));
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);

    mediaSource.addMediaSources(Arrays.asList(new MediaSource[] {
        new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null),
        new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null),
        new FakeMediaSource(Timeline.EMPTY, null), new FakeMediaSource(Timeline.EMPTY, null)
    }));
    waitForTimelineUpdate();
    TimelineAsserts.assertEmpty(timeline);

    // Insert non-empty media source to leave empty sources at the start, the end, and the middle
    // (with single and multiple empty sources in a row).
    MediaSource[] mediaSources = createMediaSources(3);
    mediaSource.addMediaSource(1, mediaSources[0]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(4, mediaSources[1]);
    waitForTimelineUpdate();
    mediaSource.addMediaSource(6, mediaSources[2]);
    waitForTimelineUpdate();
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
    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline,
        TIMEOUT_MS);
  }

  public void testIllegalArguments() {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
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
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddMultiple() {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(Arrays.asList(
        new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddSingleWithIndex() {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationAddMultipleWithIndex() {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);

    mediaSource.addMediaSources(/* index */ 0, Arrays.asList(
        new MediaSource[]{createFakeMediaSource(), createFakeMediaSource()}), runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationRemove() throws InterruptedException {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);
    mediaSource.addMediaSource(createFakeMediaSource());

    mediaSource.removeMediaSource(/* index */ 0, runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackBeforePreparationMove() throws InterruptedException {
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    Runnable runnable = Mockito.mock(Runnable.class);
    mediaSource.addMediaSources(Arrays.asList(
        new MediaSource[]{createFakeMediaSource(), createFakeMediaSource()}));

    mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0, runnable);
    verify(runnable).run();
  }

  public void testCustomCallbackAfterPreparationAddSingle() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSource(createFakeMediaSource(), runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testCustomCallbackAfterPreparationAddMultiple() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSources(Arrays.asList(
            new MediaSource[] {createFakeMediaSource(), createFakeMediaSource()}), runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testCustomCallbackAfterPreparationAddSingleWithIndex() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSource(/* index */ 0, createFakeMediaSource(),
            runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testCustomCallbackAfterPreparationAddMultipleWithIndex() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSources(/* index */ 0, Arrays.asList(
            new MediaSource[]{createFakeMediaSource(), createFakeMediaSource()}), runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testCustomCallbackAfterPreparationRemove() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();
    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSource(createFakeMediaSource());
      }
    });
    waitForTimelineUpdate();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.removeMediaSource(/* index */ 0, runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testCustomCallbackAfterPreparationMove() throws InterruptedException {
    final DynamicConcatenatingMediaSourceAndHandler sourceHandlerPair =
        setUpDynamicMediaSourceOnHandlerThread();
    final Runnable runnable = createCustomRunnable();
    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.addMediaSources(Arrays.asList(
            new MediaSource[]{createFakeMediaSource(), createFakeMediaSource()}));
      }
    });
    waitForTimelineUpdate();

    sourceHandlerPair.handler.post(new Runnable() {
      @Override
      public void run() {
        sourceHandlerPair.mediaSource.moveMediaSource(/* fromIndex */ 1, /* toIndex */ 0,
            runnable);
      }
    });
    waitForCustomRunnable();
  }

  public void testPeriodCreationWithAds() throws InterruptedException {
    // Create dynamic media source with ad child source.
    Timeline timelineContentOnly = new FakeTimeline(
        new TimelineWindowDefinition(2, 111, true, false, 10 * C.MICROS_PER_SECOND));
    Timeline timelineWithAds = new FakeTimeline(
        new TimelineWindowDefinition(2, 222, true, false, 10 * C.MICROS_PER_SECOND, 1, 1));
    FakeMediaSource mediaSourceContentOnly = new FakeMediaSource(timelineContentOnly, null);
    FakeMediaSource mediaSourceWithAds = new FakeMediaSource(timelineWithAds, null);
    DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    mediaSource.addMediaSource(mediaSourceContentOnly);
    mediaSource.addMediaSource(mediaSourceWithAds);
    assertNull(timeline);

    // Prepare and assert timeline contains ad groups.
    prepareAndListenToTimelineUpdates(mediaSource);
    waitForTimelineUpdate();
    TimelineAsserts.assertAdGroupCounts(timeline, 0, 0, 1, 1);

    // Create all periods and assert period creation of child media sources has been called.
    TimelineAsserts.assertAllPeriodsCanBeCreatedPreparedAndReleased(mediaSource, timeline,
        TIMEOUT_MS);
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceContentOnly.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(0, 0, 0));
    mediaSourceWithAds.assertMediaPeriodCreated(new MediaPeriodId(1, 0, 0));
  }

  private DynamicConcatenatingMediaSourceAndHandler setUpDynamicMediaSourceOnHandlerThread()
      throws InterruptedException {
    HandlerThread handlerThread = new HandlerThread("TestCustomCallbackExecutionThread");
    handlerThread.start();
    Handler.Callback handlerCallback = Mockito.mock(Handler.Callback.class);
    when(handlerCallback.handleMessage(any(Message.class))).thenReturn(false);
    Handler handler = new Handler(handlerThread.getLooper(), handlerCallback);
    final DynamicConcatenatingMediaSource mediaSource = new DynamicConcatenatingMediaSource();
    handler.post(new Runnable() {
      @Override
      public void run() {
        prepareAndListenToTimelineUpdates(mediaSource);
      }
    });
    waitForTimelineUpdate();
    return new DynamicConcatenatingMediaSourceAndHandler(mediaSource, handler);
  }

  private void prepareAndListenToTimelineUpdates(MediaSource mediaSource) {
    mediaSource.prepareSource(new StubExoPlayer(), true, new Listener() {
      @Override
      public void onSourceInfoRefreshed(MediaSource source, Timeline newTimeline, Object manifest) {
        timeline = newTimeline;
        synchronized (DynamicConcatenatingMediaSourceTest.this) {
          timelineUpdated = true;
          DynamicConcatenatingMediaSourceTest.this.notify();
        }
      }
    });
  }

  private synchronized void waitForTimelineUpdate() throws InterruptedException {
    long deadlineMs = System.currentTimeMillis() + TIMEOUT_MS;
    while (!timelineUpdated) {
      wait(TIMEOUT_MS);
      if (System.currentTimeMillis() >= deadlineMs) {
        fail("No timeline update occurred within timeout.");
      }
    }
    timelineUpdated = false;
  }

  private Runnable createCustomRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        synchronized (DynamicConcatenatingMediaSourceTest.this) {
          assertTrue(timelineUpdated);
          timelineUpdated = false;
          customRunnableCalled = true;
          DynamicConcatenatingMediaSourceTest.this.notify();
        }
      }
    };
  }

  private synchronized void waitForCustomRunnable() throws InterruptedException {
    long deadlineMs = System.currentTimeMillis() + TIMEOUT_MS;
    while (!customRunnableCalled) {
      wait(TIMEOUT_MS);
      if (System.currentTimeMillis() >= deadlineMs) {
        fail("No custom runnable call occurred within timeout.");
      }
    }
    customRunnableCalled = false;
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

  private static class DynamicConcatenatingMediaSourceAndHandler {

    public final DynamicConcatenatingMediaSource mediaSource;
    public final Handler handler;

    public DynamicConcatenatingMediaSourceAndHandler(DynamicConcatenatingMediaSource mediaSource,
        Handler handler) {
      this.mediaSource = mediaSource;
      this.handler = handler;
    }

  }

  private static class LazyMediaSource implements MediaSource {

    private Listener listener;

    public void triggerTimelineUpdate(Timeline timeline) {
      listener.onSourceInfoRefreshed(this, timeline, null);
    }

    @Override
    public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
      this.listener = listener;
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
      return new FakeMediaPeriod(TrackGroupArray.EMPTY);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
    }

    @Override
    public void releaseSource() {
    }

  }

  /**
   * Stub ExoPlayer which only accepts custom messages and runs them on a separate handler thread.
   */
  private static class StubExoPlayer implements ExoPlayer, Handler.Callback {

    private final Handler handler;

    public StubExoPlayer() {
      HandlerThread handlerThread = new HandlerThread("StubExoPlayerThread");
      handlerThread.start();
      handler = new Handler(handlerThread.getLooper(), this);
    }

    @Override
    public Looper getPlaybackLooper() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(Player.EventListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeListener(Player.EventListener listener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPlaybackState() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean getPlayWhenReady() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setRepeatMode(@RepeatMode int repeatMode) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRepeatMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean getShuffleModeEnabled() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLoading() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekToDefaultPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekTo(long positionMs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekTo(int windowIndex, long positionMs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void release() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void sendMessages(ExoPlayerMessage... messages) {
      handler.obtainMessage(0, messages).sendToTarget();
    }

    @Override
    public void blockingSendMessages(ExoPlayerMessage... messages) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRendererCount() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRendererType(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
      throw new UnsupportedOperationException();
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getCurrentManifest() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Timeline getCurrentTimeline() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentPeriodIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentWindowIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getNextWindowIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPreviousWindowIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getDuration() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCurrentPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getBufferedPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getBufferedPercentage() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrentWindowDynamic() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrentWindowSeekable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPlayingAd() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentAdGroupIndex() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getContentPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean handleMessage(Message msg) {
      ExoPlayerMessage[] messages = (ExoPlayerMessage[]) msg.obj;
      for (ExoPlayerMessage message : messages) {
        try {
          message.target.handleMessage(message.messageType, message.message);
        } catch (ExoPlaybackException e) {
          fail("Unexpected ExoPlaybackException.");
        }
      }
      return true;
    }
  }

}

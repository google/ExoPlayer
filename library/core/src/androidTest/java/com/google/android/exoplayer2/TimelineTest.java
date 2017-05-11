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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.ExoPlayer.RepeatMode;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.Listener;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit test for {@link Timeline}.
 */
public class TimelineTest extends TestCase {

  /**
   * Fake timeline with multiple periods and user-defined window id.
   */
  public static final class FakeTimeline extends Timeline {

    private static final int WINDOW_DURATION_US = 1000000;

    private final int periodCount;
    private final int id;

    public FakeTimeline(int periodCount, int id) {
      this.periodCount = periodCount;
      this.id = id;
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      return window.set(id, 0, 0, true, false, 0, WINDOW_DURATION_US, 0, periodCount - 1, 0);
    }

    @Override
    public int getPeriodCount() {
      return periodCount;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return period.set(new int[] { id, periodIndex }, null, 0, WINDOW_DURATION_US, 0, false);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return C.INDEX_UNSET;
    }
  }

  /**
   * Returns a stub {@link MediaSource} with the specified {@link Timeline} in its source info.
   */
  public static MediaSource stubMediaSourceSourceWithTimeline(final Timeline timeline) {
    return new MediaSource() {
      @Override
      public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
        listener.onSourceInfoRefreshed(timeline, null);
      }

      @Override
      public void maybeThrowSourceInfoRefreshError() throws IOException {
      }

      @Override
      public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
        return null;
      }

      @Override
      public void releasePeriod(MediaPeriod mediaPeriod) {
      }

      @Override
      public void releaseSource() {
      }
    };
  }

  /**
   * Works in conjunction with {@code stubMediaSourceSourceWithTimeline} to extract the Timeline
   * from a media source.
   */
  public static Timeline extractTimelineFromMediaSource(MediaSource mediaSource) {
    class TimelineListener implements Listener {
      private Timeline timeline;
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        this.timeline = timeline;
      }
    }
    TimelineListener listener = new TimelineListener();
    mediaSource.prepareSource(null, true, listener);
    return listener.timeline;
  }

  /**
   * Verify the behaviour of {@link Timeline#getNextWindowIndex(int, int)},
   * {@link Timeline#getPreviousWindowIndex(int, int)},
   * {@link Timeline#getWindow(int, Window, boolean)},
   * {@link Timeline#getNextPeriodIndex(int, Period, Window, int)}, and
   * {@link Timeline#getPeriod(int, Period, boolean)}.
   */
  public static final class TimelineVerifier {

    private final Timeline timeline;

    public TimelineVerifier(Timeline timeline) {
      this.timeline = timeline;
    }

    public TimelineVerifier assertWindowIds(int... expectedWindowIds) {
      Window window = new Window();
      assertEquals(expectedWindowIds.length, timeline.getWindowCount());
      for (int i = 0; i < timeline.getWindowCount(); i++) {
        timeline.getWindow(i, window, true);
        assertEquals(expectedWindowIds[i], window.id);
      }
      return this;
    }

    public TimelineVerifier assertPreviousWindowIndices(@RepeatMode int repeatMode,
        int... expectedPreviousWindowIndices) {
      for (int i = 0; i < timeline.getWindowCount(); i++) {
        assertEquals(expectedPreviousWindowIndices[i],
            timeline.getPreviousWindowIndex(i, repeatMode));
      }
      return this;
    }

    public TimelineVerifier assertNextWindowIndices(@RepeatMode int repeatMode,
        int... expectedNextWindowIndices) {
      for (int i = 0; i < timeline.getWindowCount(); i++) {
        assertEquals(expectedNextWindowIndices[i],
            timeline.getNextWindowIndex(i, repeatMode));
      }
      return this;
    }

    public TimelineVerifier assertPeriodCounts(int... expectedPeriodCounts) {
      int windowCount = timeline.getWindowCount();
      int[] accumulatedPeriodCounts = new int[windowCount + 1];
      accumulatedPeriodCounts[0] = 0;
      for (int i = 0; i < windowCount; i++) {
        accumulatedPeriodCounts[i + 1] = accumulatedPeriodCounts[i] + expectedPeriodCounts[i];
      }
      assertEquals(accumulatedPeriodCounts[accumulatedPeriodCounts.length - 1],
          timeline.getPeriodCount());
      Window window = new Window();
      Period period = new Period();
      for (int i = 0; i < windowCount; i++) {
        timeline.getWindow(i, window, true);
        assertEquals(accumulatedPeriodCounts[i], window.firstPeriodIndex);
        assertEquals(accumulatedPeriodCounts[i + 1] - 1, window.lastPeriodIndex);
      }
      int expectedWindowIndex = 0;
      for (int i = 0; i < timeline.getPeriodCount(); i++) {
        timeline.getPeriod(i, period, true);
        while (i >= accumulatedPeriodCounts[expectedWindowIndex + 1]) {
          expectedWindowIndex++;
        }
        assertEquals(expectedWindowIndex, period.windowIndex);
        assertEquals(i - accumulatedPeriodCounts[expectedWindowIndex], ((int[]) period.id)[1]);
        if (i < accumulatedPeriodCounts[expectedWindowIndex + 1] - 1) {
          assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_OFF));
          assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_ONE));
          assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_ALL));
        } else {
          int nextWindowOff = timeline.getNextWindowIndex(expectedWindowIndex,
              ExoPlayer.REPEAT_MODE_OFF);
          int nextWindowOne = timeline.getNextWindowIndex(expectedWindowIndex,
              ExoPlayer.REPEAT_MODE_ONE);
          int nextWindowAll = timeline.getNextWindowIndex(expectedWindowIndex,
              ExoPlayer.REPEAT_MODE_ALL);
          int nextPeriodOff = nextWindowOff == C.INDEX_UNSET ? C.INDEX_UNSET
              : accumulatedPeriodCounts[nextWindowOff];
          int nextPeriodOne = nextWindowOne == C.INDEX_UNSET ? C.INDEX_UNSET
              : accumulatedPeriodCounts[nextWindowOne];
          int nextPeriodAll = nextWindowAll == C.INDEX_UNSET ? C.INDEX_UNSET
              : accumulatedPeriodCounts[nextWindowAll];
          assertEquals(nextPeriodOff, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_OFF));
          assertEquals(nextPeriodOne, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_ONE));
          assertEquals(nextPeriodAll, timeline.getNextPeriodIndex(i, period, window,
              ExoPlayer.REPEAT_MODE_ALL));
        }
      }
      return this;
    }
  }

  public void testEmptyTimeline() {
    new TimelineVerifier(Timeline.EMPTY)
        .assertWindowIds()
        .assertPeriodCounts();
  }

  public void testSinglePeriodTimeline() {
    Timeline timeline = new FakeTimeline(1, 111);
    new TimelineVerifier(timeline)
        .assertWindowIds(111)
        .assertPeriodCounts(1)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0);
  }

  public void testMultiPeriodTimeline() {
    Timeline timeline = new FakeTimeline(5, 111);
    new TimelineVerifier(timeline)
        .assertWindowIds(111)
        .assertPeriodCounts(5)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertPreviousWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_OFF, C.INDEX_UNSET)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ONE, 0)
        .assertNextWindowIndices(ExoPlayer.REPEAT_MODE_ALL, 0);
  }
}

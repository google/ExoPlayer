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
package com.google.android.exoplayer2.testutil;

import static junit.framework.Assert.assertEquals;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;

/**
 * Unit test for {@link Timeline}.
 */
public final class TimelineAsserts {

  private TimelineAsserts() {}

  /**
   * Assert that timeline is empty (i.e. has no windows or periods).
   */
  public static void assertEmpty(Timeline timeline) {
    assertWindowIds(timeline);
    assertPeriodCounts(timeline);
  }

  /**
   * Asserts that window IDs are set correctly.
   *
   * @param expectedWindowIds A list of expected window IDs. If an ID is unknown or not important
   *     {@code null} can be passed to skip this window.
   */
  public static void assertWindowIds(Timeline timeline, Object... expectedWindowIds) {
    Window window = new Window();
    assertEquals(expectedWindowIds.length, timeline.getWindowCount());
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      timeline.getWindow(i, window, true);
      if (expectedWindowIds[i] != null) {
        assertEquals(expectedWindowIds[i], window.id);
      }
    }
  }

  /**
   * Asserts that window properties {@link Window}.isDynamic are set correctly..
   */
  public static void assertWindowIsDynamic(Timeline timeline, boolean... windowIsDynamic) {
    Window window = new Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      timeline.getWindow(i, window, true);
      assertEquals(windowIsDynamic[i], window.isDynamic);
    }
  }

  /**
   * Asserts that previous window indices for each window are set correctly depending on the repeat
   * mode.
   */
  public static void assertPreviousWindowIndices(Timeline timeline,
      @Player.RepeatMode int repeatMode, int... expectedPreviousWindowIndices) {
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertEquals(expectedPreviousWindowIndices[i],
          timeline.getPreviousWindowIndex(i, repeatMode));
    }
  }

  /**
   * Asserts that next window indices for each window are set correctly depending on the repeat
   * mode.
   */
  public static void assertNextWindowIndices(Timeline timeline, @Player.RepeatMode int repeatMode,
      int... expectedNextWindowIndices) {
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertEquals(expectedNextWindowIndices[i],
          timeline.getNextWindowIndex(i, repeatMode));
    }
  }

  /**
   * Asserts that period counts for each window are set correctly. Also asserts that
   * {@link Window#firstPeriodIndex} and {@link Window#lastPeriodIndex} are set correctly, and it
   * asserts the correct behavior of {@link Timeline#getNextWindowIndex(int, int)}.
   */
  public static void assertPeriodCounts(Timeline timeline, int... expectedPeriodCounts) {
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
      if (i < accumulatedPeriodCounts[expectedWindowIndex + 1] - 1) {
        assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window, Player.REPEAT_MODE_OFF));
        assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window, Player.REPEAT_MODE_ONE));
        assertEquals(i + 1, timeline.getNextPeriodIndex(i, period, window, Player.REPEAT_MODE_ALL));
      } else {
        int nextWindowOff = timeline.getNextWindowIndex(expectedWindowIndex,
            Player.REPEAT_MODE_OFF);
        int nextWindowOne = timeline.getNextWindowIndex(expectedWindowIndex,
            Player.REPEAT_MODE_ONE);
        int nextWindowAll = timeline.getNextWindowIndex(expectedWindowIndex,
            Player.REPEAT_MODE_ALL);
        int nextPeriodOff = nextWindowOff == C.INDEX_UNSET ? C.INDEX_UNSET
            : accumulatedPeriodCounts[nextWindowOff];
        int nextPeriodOne = nextWindowOne == C.INDEX_UNSET ? C.INDEX_UNSET
            : accumulatedPeriodCounts[nextWindowOne];
        int nextPeriodAll = nextWindowAll == C.INDEX_UNSET ? C.INDEX_UNSET
            : accumulatedPeriodCounts[nextWindowAll];
        assertEquals(nextPeriodOff, timeline.getNextPeriodIndex(i, period, window,
            Player.REPEAT_MODE_OFF));
        assertEquals(nextPeriodOne, timeline.getNextPeriodIndex(i, period, window,
            Player.REPEAT_MODE_ONE));
        assertEquals(nextPeriodAll, timeline.getNextPeriodIndex(i, period, window,
            Player.REPEAT_MODE_ALL));
      }
    }
  }

}

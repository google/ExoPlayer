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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;

/**
 * Fake {@link Timeline} which can be setup to return custom {@link TimelineWindowDefinition}s.
 */
public final class FakeTimeline extends Timeline {

  /**
   * Definition used to define a {@link FakeTimeline}.
   */
  public static final class TimelineWindowDefinition {

    private static final int WINDOW_DURATION_US = 100000;

    public final int periodCount;
    public final Object id;
    public final boolean isSeekable;
    public final boolean isDynamic;
    public final long durationUs;

    public TimelineWindowDefinition(int periodCount, Object id) {
      this(periodCount, id, true, false, WINDOW_DURATION_US);
    }

    public TimelineWindowDefinition(boolean isSeekable, boolean isDynamic, long durationUs) {
      this(1, 0, isSeekable, isDynamic, durationUs);
    }

    public TimelineWindowDefinition(int periodCount, Object id, boolean isSeekable,
        boolean isDynamic, long durationUs) {
      this.periodCount = periodCount;
      this.id = id;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.durationUs = durationUs;
    }

  }

  private final TimelineWindowDefinition[] windowDefinitions;
  private final int[] periodOffsets;

  public FakeTimeline(TimelineWindowDefinition... windowDefinitions) {
    this.windowDefinitions = windowDefinitions;
    periodOffsets = new int[windowDefinitions.length + 1];
    periodOffsets[0] = 0;
    for (int i = 0; i < windowDefinitions.length; i++) {
      periodOffsets[i + 1] = periodOffsets[i] + windowDefinitions[i].periodCount;
    }
  }

  @Override
  public int getWindowCount() {
    return windowDefinitions.length;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    Object id = setIds ? windowDefinition.id : null;
    return window.set(id, C.TIME_UNSET, C.TIME_UNSET, windowDefinition.isSeekable,
        windowDefinition.isDynamic, 0, windowDefinition.durationUs, periodOffsets[windowIndex],
        periodOffsets[windowIndex + 1] - 1, 0);
  }

  @Override
  public int getPeriodCount() {
    return periodOffsets[periodOffsets.length - 1];
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int windowIndex = Util.binarySearchFloor(periodOffsets, periodIndex, true, false);
    int windowPeriodIndex = periodIndex - periodOffsets[windowIndex];
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    Object id = setIds ? windowPeriodIndex : null;
    Object uid = setIds ? periodIndex : null;
    long periodDurationUs = windowDefinition.durationUs / windowDefinition.periodCount;
    return period.set(id, uid, windowIndex, periodDurationUs, periodDurationUs * windowPeriodIndex);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Integer)) {
      return C.INDEX_UNSET;
    }
    int index = (Integer) uid;
    return index >= 0 && index < getPeriodCount() ? index : C.INDEX_UNSET;
  }

}

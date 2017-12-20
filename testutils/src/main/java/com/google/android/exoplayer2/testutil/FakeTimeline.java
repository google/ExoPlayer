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
import java.util.Arrays;

/**
 * Fake {@link Timeline} which can be setup to return custom {@link TimelineWindowDefinition}s.
 */
public final class FakeTimeline extends Timeline {

  /**
   * Definition used to define a {@link FakeTimeline}.
   */
  public static final class TimelineWindowDefinition {

    /**
     * Default test window duration in microseconds.
     */
    public static final int DEFAULT_WINDOW_DURATION_US = 100_000;

    public final int periodCount;
    public final Object id;
    public final boolean isSeekable;
    public final boolean isDynamic;
    public final long durationUs;
    public final int adGroupsPerPeriodCount;
    public final int adsPerAdGroupCount;

    /**
     * Creates a seekable, non-dynamic window definition with one period with a duration of
     * {@link #DEFAULT_WINDOW_DURATION_US}.
     */
    public TimelineWindowDefinition() {
      this(1, 0, true, false, DEFAULT_WINDOW_DURATION_US);
    }

    /**
     * Creates a seekable, non-dynamic window definition with a duration of
     * {@link #DEFAULT_WINDOW_DURATION_US}.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     */
    public TimelineWindowDefinition(int periodCount, Object id) {
      this(periodCount, id, true, false, DEFAULT_WINDOW_DURATION_US);
    }

    /**
     * Creates a window definition with one period.
     *
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     */
    public TimelineWindowDefinition(boolean isSeekable, boolean isDynamic, long durationUs) {
      this(1, 0, isSeekable, isDynamic, durationUs);
    }

    /**
     * Creates a window definition.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     */
    public TimelineWindowDefinition(int periodCount, Object id, boolean isSeekable,
        boolean isDynamic, long durationUs) {
      this(periodCount, id, isSeekable, isDynamic, durationUs, 0, 0);
    }

    /**
     * Creates a window definition with ad groups.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     * @param adGroupsCountPerPeriod The number of ad groups in each period. The position of the ad
     *     groups is equally distributed in each period starting.
     * @param adsPerAdGroupCount The number of ads in each ad group.
     */
    public TimelineWindowDefinition(int periodCount, Object id, boolean isSeekable,
        boolean isDynamic, long durationUs, int adGroupsCountPerPeriod, int adsPerAdGroupCount) {
      this.periodCount = periodCount;
      this.id = id;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.durationUs = durationUs;
      this.adGroupsPerPeriodCount = adGroupsCountPerPeriod;
      this.adsPerAdGroupCount = adsPerAdGroupCount;
    }

  }

  private static final long AD_DURATION_US = 10 * C.MICROS_PER_SECOND;

  private final TimelineWindowDefinition[] windowDefinitions;
  private final int[] periodOffsets;

  /**
   * Creates a fake timeline with the given number of seekable, non-dynamic windows with one period
   * with a duration of {@link TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US} each.
   *
   * @param windowCount The number of windows.
   */
  public FakeTimeline(int windowCount) {
    this(createDefaultWindowDefinitions(windowCount));
  }

  /**
   * Creates a fake timeline with the given window definitions.
   *
   * @param windowDefinitions A list of {@link TimelineWindowDefinition}s.
   */
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
    long positionInWindowUs = periodDurationUs * windowPeriodIndex;
    if (windowDefinition.adGroupsPerPeriodCount == 0) {
      return period.set(id, uid, windowIndex, periodDurationUs, positionInWindowUs);
    } else {
      int adGroups = windowDefinition.adGroupsPerPeriodCount;
      long[] adGroupTimesUs = new long[adGroups];
      int[] adCounts = new int[adGroups];
      int[] adLoadedAndPlayedCounts = new int[adGroups];
      long[][] adDurationsUs = new long[adGroups][];
      long adResumePositionUs = 0;
      long adGroupOffset = adGroups > 1 ? periodDurationUs / (adGroups - 1) : 0;
      for (int i = 0; i < adGroups; i++) {
        adGroupTimesUs[i] = i * adGroupOffset;
        adCounts[i] = windowDefinition.adsPerAdGroupCount;
        adLoadedAndPlayedCounts[i] = 0;
        adDurationsUs[i] = new long[adCounts[i]];
        Arrays.fill(adDurationsUs[i], AD_DURATION_US);
      }
      return period.set(id, uid, windowIndex, periodDurationUs, positionInWindowUs, adGroupTimesUs,
          adCounts, adLoadedAndPlayedCounts, adLoadedAndPlayedCounts, adDurationsUs,
          adResumePositionUs);
    }
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Integer)) {
      return C.INDEX_UNSET;
    }
    int index = (Integer) uid;
    return index >= 0 && index < getPeriodCount() ? index : C.INDEX_UNSET;
  }

  private static TimelineWindowDefinition[] createDefaultWindowDefinitions(int windowCount) {
    TimelineWindowDefinition[] windowDefinitions = new TimelineWindowDefinition[windowCount];
    Arrays.fill(windowDefinitions, new TimelineWindowDefinition());
    return windowDefinitions;
  }

}

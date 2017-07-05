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
package com.google.android.exoplayer2.ext.ima;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link Timeline} for sources that have ads.
 */
public final class SinglePeriodAdTimeline extends Timeline {

  private final Timeline contentTimeline;
  private final long[] adGroupTimesUs;
  private final boolean[] hasPlayedAdGroup;
  private final int[] adCounts;
  private final boolean[][] isAdAvailable;
  private final long[][] adDurationsUs;

  /**
   * Creates a new timeline with a single period containing the specified ads.
   *
   * @param contentTimeline The timeline of the content alongside which ads will be played. It must
   *     have one window and one period.
   * @param adGroupTimesUs The times of ad groups relative to the start of the period, in
   *     microseconds. A final element with the value {@link C#TIME_END_OF_SOURCE} indicates that
   *     the period has a postroll ad.
   * @param hasPlayedAdGroup Whether each ad group has been played.
   * @param adCounts The number of ads in each ad group. An element may be {@link C#LENGTH_UNSET}
   *     if the number of ads is not yet known.
   * @param isAdAvailable Whether each ad in each ad group is available.
   * @param adDurationsUs The duration of each ad in each ad group, in microseconds. An element
   *     may be {@link C#TIME_UNSET} if the duration is not yet known.
   */
  public SinglePeriodAdTimeline(Timeline contentTimeline, long[] adGroupTimesUs,
      boolean[] hasPlayedAdGroup, int[] adCounts, boolean[][] isAdAvailable,
      long[][] adDurationsUs) {
    Assertions.checkState(contentTimeline.getPeriodCount() == 1);
    Assertions.checkState(contentTimeline.getWindowCount() == 1);
    this.contentTimeline = contentTimeline;
    this.adGroupTimesUs = adGroupTimesUs;
    this.hasPlayedAdGroup = hasPlayedAdGroup;
    this.adCounts = adCounts;
    this.isAdAvailable = isAdAvailable;
    this.adDurationsUs = adDurationsUs;
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    return contentTimeline.getWindow(windowIndex, window, setIds, defaultPositionProjectionUs);
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    contentTimeline.getPeriod(periodIndex, period, setIds);
    period.set(period.id, period.uid, period.windowIndex, period.durationUs,
        period.getPositionInWindowUs(), adGroupTimesUs, hasPlayedAdGroup, adCounts,
        isAdAvailable, adDurationsUs);
    return period;
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    return contentTimeline.getIndexOfPeriod(uid);
  }

}

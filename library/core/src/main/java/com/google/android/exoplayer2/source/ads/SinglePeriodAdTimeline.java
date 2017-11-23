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
package com.google.android.exoplayer2.source.ads;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ForwardingTimeline;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link Timeline} for sources that have ads.
 */
/* package */ final class SinglePeriodAdTimeline extends ForwardingTimeline {

  private final long[] adGroupTimesUs;
  private final int[] adCounts;
  private final int[] adsLoadedCounts;
  private final int[] adsPlayedCounts;
  private final long[][] adDurationsUs;
  private final long adResumePositionUs;
  private final long contentDurationUs;

  /**
   * Creates a new timeline with a single period containing the specified ads.
   *
   * @param contentTimeline The timeline of the content alongside which ads will be played. It must
   *     have one window and one period.
   * @param adGroupTimesUs The times of ad groups relative to the start of the period, in
   *     microseconds. A final element with the value {@link C#TIME_END_OF_SOURCE} indicates that
   *     the period has a postroll ad.
   * @param adCounts The number of ads in each ad group. An element may be {@link C#LENGTH_UNSET}
   *     if the number of ads is not yet known.
   * @param adsLoadedCounts The number of ads loaded so far in each ad group.
   * @param adsPlayedCounts The number of ads played so far in each ad group.
   * @param adDurationsUs The duration of each ad in each ad group, in microseconds. An element
   *     may be {@link C#TIME_UNSET} if the duration is not yet known.
   * @param adResumePositionUs The position offset in the earliest unplayed ad at which to begin
   *     playback, in microseconds.
   * @param contentDurationUs The content duration in microseconds, if known. {@link C#TIME_UNSET}
   *     otherwise.
   */
  public SinglePeriodAdTimeline(Timeline contentTimeline, long[] adGroupTimesUs, int[] adCounts,
      int[] adsLoadedCounts, int[] adsPlayedCounts, long[][] adDurationsUs, long adResumePositionUs,
      long contentDurationUs) {
    super(contentTimeline);
    Assertions.checkState(contentTimeline.getPeriodCount() == 1);
    Assertions.checkState(contentTimeline.getWindowCount() == 1);
    this.adGroupTimesUs = adGroupTimesUs;
    this.adCounts = adCounts;
    this.adsLoadedCounts = adsLoadedCounts;
    this.adsPlayedCounts = adsPlayedCounts;
    this.adDurationsUs = adDurationsUs;
    this.adResumePositionUs = adResumePositionUs;
    this.contentDurationUs = contentDurationUs;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    timeline.getPeriod(periodIndex, period, setIds);
    period.set(period.id, period.uid, period.windowIndex, period.durationUs,
        period.getPositionInWindowUs(), adGroupTimesUs, adCounts, adsLoadedCounts, adsPlayedCounts,
        adDurationsUs, adResumePositionUs);
    return period;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    window = super.getWindow(windowIndex, window, setIds, defaultPositionProjectionUs);
    if (window.durationUs == C.TIME_UNSET) {
      window.durationUs = contentDurationUs;
    }
    return window;
  }

}

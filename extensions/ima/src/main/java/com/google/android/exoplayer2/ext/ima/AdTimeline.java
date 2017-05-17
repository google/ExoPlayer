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

import android.util.Pair;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;

/**
 * A {@link Timeline} for {@link ImaAdsMediaSource}.
 */
/* package */ final class AdTimeline extends Timeline {

  private static final Object AD_ID = new Object();

  /**
   * Builder for ad timelines.
   */
  public static final class Builder {

    private final Timeline contentTimeline;
    private final long contentDurationUs;
    private final ArrayList<Boolean> isAd;
    private final ArrayList<Ad> ads;
    private final ArrayList<Long> startTimesUs;
    private final ArrayList<Long> endTimesUs;
    private final ArrayList<Object> uids;

    /**
     * Creates a new ad timeline builder using the specified {@code contentTimeline} as the timeline
     * of the content within which to insert ad breaks.
     *
     * @param contentTimeline The timeline of the content within which to insert ad breaks.
     */
    public Builder(Timeline contentTimeline) {
      this.contentTimeline = contentTimeline;
      contentDurationUs = contentTimeline.getPeriod(0, new Period()).durationUs;
      isAd = new ArrayList<>();
      ads = new ArrayList<>();
      startTimesUs = new ArrayList<>();
      endTimesUs = new ArrayList<>();
      uids = new ArrayList<>();
    }

    /**
     * Adds an ad period. Each individual ad in an ad pod is represented by a separate ad period.
     *
     * @param ad The {@link Ad} instance representing the ad break, or {@code null} if not known.
     * @param adBreakIndex The index of the ad break that contains the ad in the timeline.
     * @param adIndexInAdBreak The index of the ad in its ad break.
     * @param durationUs The duration of the ad, in microseconds. May be {@link C#TIME_UNSET}.
     * @return The builder.
     */
    public Builder addAdPeriod(Ad ad, int adBreakIndex, int adIndexInAdBreak, long durationUs) {
      isAd.add(true);
      ads.add(ad);
      startTimesUs.add(0L);
      endTimesUs.add(durationUs);
      uids.add(Pair.create(adBreakIndex, adIndexInAdBreak));
      return this;
    }

    /**
     * Adds a content period.
     *
     * @param startTimeUs The start time of the period relative to the start of the content
     *     timeline, in microseconds.
     * @param endTimeUs The end time of the period relative to the start of the content timeline, in
     *     microseconds. May be {@link C#TIME_UNSET} to include the rest of the content.
     * @return The builder.
     */
    public Builder addContent(long startTimeUs, long endTimeUs) {
      ads.add(null);
      isAd.add(false);
      startTimesUs.add(startTimeUs);
      endTimesUs.add(endTimeUs == C.TIME_UNSET ? contentDurationUs : endTimeUs);
      uids.add(Pair.create(startTimeUs, endTimeUs));
      return this;
    }

    /**
     * Builds and returns the ad timeline.
     */
    public AdTimeline build() {
      int periodCount = uids.size();
      Assertions.checkState(periodCount > 0);
      Ad[] ads = new Ad[periodCount];
      boolean[] isAd = new boolean[periodCount];
      long[] startTimesUs = new long[periodCount];
      long[] endTimesUs = new long[periodCount];
      for (int i = 0; i < periodCount; i++) {
        ads[i] = this.ads.get(i);
        isAd[i] = this.isAd.get(i);
        startTimesUs[i] = this.startTimesUs.get(i);
        endTimesUs[i] = this.endTimesUs.get(i);
      }
      Object[] uids = this.uids.toArray(new Object[periodCount]);
      return new AdTimeline(contentTimeline, isAd, ads, startTimesUs, endTimesUs, uids);
    }

  }

  private final Period contentPeriod;
  private final Window contentWindow;
  private final boolean[] isAd;
  private final Ad[] ads;
  private final long[] startTimesUs;
  private final long[] endTimesUs;
  private final Object[] uids;

  private AdTimeline(Timeline contentTimeline, boolean[] isAd, Ad[] ads, long[] startTimesUs,
      long[] endTimesUs, Object[] uids) {
    contentWindow = contentTimeline.getWindow(0, new Window(), true);
    contentPeriod = contentTimeline.getPeriod(0, new Period(), true);
    this.isAd = isAd;
    this.ads = ads;
    this.startTimesUs = startTimesUs;
    this.endTimesUs = endTimesUs;
    this.uids = uids;
  }

  /**
   * Returns whether the period at {@code index} contains ad media.
   */
  public boolean isPeriodAd(int index) {
    return isAd[index];
  }

  /**
   * Returns the duration of the content within which ads have been inserted, in microseconds.
   */
  public long getContentDurationUs() {
    return contentPeriod.durationUs;
  }

  /**
   * Returns the start time of the period at {@code periodIndex} relative to the start of the
   * content, in microseconds.
   *
   * @throws IllegalArgumentException Thrown if the period at {@code periodIndex} is not a content
   *     period.
   */
  public long getContentStartTimeUs(int periodIndex) {
    Assertions.checkArgument(!isAd[periodIndex]);
    return startTimesUs[periodIndex];
  }

  /**
   * Returns the end time of the period at {@code periodIndex} relative to the start of the content,
   * in microseconds.
   *
   * @throws IllegalArgumentException Thrown if the period at {@code periodIndex} is not a content
   *     period.
   */
  public long getContentEndTimeUs(int periodIndex) {
    Assertions.checkArgument(!isAd[periodIndex]);
    return endTimesUs[periodIndex];
  }

  /**
   * Returns the index of the ad break to which the period at {@code periodIndex} belongs.
   *
   * @param periodIndex The period index.
   * @return The index of the ad break to which the period belongs.
   * @throws IllegalArgumentException Thrown if the period at {@code periodIndex} is not an ad.
   */
  public int getAdBreakIndex(int periodIndex) {
    Assertions.checkArgument(isAd[periodIndex]);
    int adBreakIndex = 0;
    for (int i = 1; i < periodIndex; i++) {
      if (!isAd[i] && isAd[i - 1]) {
        adBreakIndex++;
      }
    }
    return adBreakIndex;
  }

  /**
   * Returns the index of the ad at {@code periodIndex} in its ad break.
   *
   * @param periodIndex The period index.
   * @return The index of the ad at {@code periodIndex} in its ad break.
   * @throws IllegalArgumentException Thrown if the period at {@code periodIndex} is not an ad.
   */
  public int getAdIndexInAdBreak(int periodIndex) {
    Assertions.checkArgument(isAd[periodIndex]);
    int adIndex = 0;
    for (int i = 0; i < periodIndex; i++) {
      if (isAd[i]) {
        adIndex++;
      } else {
        adIndex = 0;
      }
    }
    return adIndex;
  }

  @Override
  public int getWindowCount() {
    return uids.length;
  }

  @Override
  public int getNextWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    if (repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
      repeatMode = ExoPlayer.REPEAT_MODE_ALL;
    }
    return super.getNextWindowIndex(windowIndex, repeatMode);
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    if (repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
      repeatMode = ExoPlayer.REPEAT_MODE_ALL;
    }
    return super.getPreviousWindowIndex(windowIndex, repeatMode);
  }

  @Override
  public Window getWindow(int index, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    long startTimeUs = startTimesUs[index];
    long durationUs = endTimesUs[index] - startTimeUs;
    if (isAd[index]) {
      window.set(ads[index], C.TIME_UNSET, C.TIME_UNSET, false, false, 0L, durationUs, index, index,
          0L);
    } else {
      window.set(contentWindow.id, contentWindow.presentationStartTimeMs + C.usToMs(startTimeUs),
          contentWindow.windowStartTimeMs + C.usToMs(startTimeUs), contentWindow.isSeekable, false,
          0L, durationUs, index, index, 0L);
    }
    return window;
  }

  @Override
  public int getPeriodCount() {
    return uids.length;
  }

  @Override
  public Period getPeriod(int index, Period period, boolean setIds) {
    Object id = setIds ? (isAd[index] ? AD_ID : contentPeriod.id) : null;
    return period.set(id, uids[index], index, endTimesUs[index] - startTimesUs[index], 0,
        isAd[index]);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    for (int i = 0; i < uids.length; i++) {
      if (Util.areEqual(uid, uids[i])) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

}

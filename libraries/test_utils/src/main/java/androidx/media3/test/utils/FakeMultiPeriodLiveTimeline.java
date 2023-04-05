/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.common.util.Util.sum;
import static androidx.media3.common.util.Util.usToMs;

import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;

/**
 * A fake {@link Timeline} that produces a live window with periods according to the available time
 * range.
 *
 * <p>The parameters passed to the {@linkplain #FakeMultiPeriodLiveTimeline constructor} define the
 * availability start time, the window size and {@code now}. Use {@link #advanceNowUs(long)} to
 * advance the live window of the timeline accordingly.
 *
 * <p>The first available period with {@link Period#id ID} 0 (zero) starts at {@code
 * availabilityStartTimeUs}. The {@link Window live window} starts at {@code now -
 * liveWindowDurationUs} with the first period of the window having its ID relative to the first
 * available period.
 *
 * <p>Periods are either of type content or ad as defined by the ad sequence pattern. A period is an
 * ad if {@code adSequencePattern[id % adSequencePattern.length]} evaluates to true. Ad periods have
 * a duration of {@link #AD_PERIOD_DURATION_MS} and content periods have a duration of {@link
 * #PERIOD_DURATION_MS}.
 */
@UnstableApi
public class FakeMultiPeriodLiveTimeline extends Timeline {

  public static final long AD_PERIOD_DURATION_MS = 10_000L;
  public static final long PERIOD_DURATION_MS = 30_000L;

  private final boolean[] adSequencePattern;
  private final MediaItem mediaItem;
  private final long availabilityStartTimeUs;
  private final long liveWindowDurationUs;
  private final boolean isContentTimeline;
  private final boolean populateAds;
  private final boolean playedAds;
  private final long[] periodDurationUsPattern;

  private long nowUs;
  private ImmutableList<PeriodData> periods;

  /**
   * Creates an instance.
   *
   * @param availabilityStartTimeMs The start time of the available time range, UNIX epoch in
   *     milliseconds.
   * @param liveWindowDurationUs The duration of the live window.
   * @param nowUs The current time that determines the end of the live window.
   * @param adSequencePattern The repeating pattern of periods starting at {@code
   *     availabilityStartTimeMs}. True is an ad period, and false a content period.
   * @param periodDurationMsPattern The repeating pattern of periods durations starting at {@code
   *     availabilityStartTimeMs}, in milliseconds. Must have the same length as {@code
   *     adSequencePattern}.
   * @param isContentTimeline Whether the timeline is a content timeline without {@link
   *     AdPlaybackState}s.
   * @param populateAds Whether to populate ads in the same way if an ad event has been received.
   * @param playedAds Whether ads should be marked as played if populated.
   */
  public FakeMultiPeriodLiveTimeline(
      long availabilityStartTimeMs,
      long liveWindowDurationUs,
      long nowUs,
      boolean[] adSequencePattern,
      long[] periodDurationMsPattern,
      boolean isContentTimeline,
      boolean populateAds,
      boolean playedAds) {
    checkArgument(nowUs - liveWindowDurationUs >= msToUs(availabilityStartTimeMs));
    checkArgument(adSequencePattern.length == periodDurationMsPattern.length);
    this.availabilityStartTimeUs = msToUs(availabilityStartTimeMs);
    this.liveWindowDurationUs = liveWindowDurationUs;
    this.nowUs = nowUs;
    this.adSequencePattern = Arrays.copyOf(adSequencePattern, adSequencePattern.length);
    periodDurationUsPattern = new long[periodDurationMsPattern.length];
    for (int i = 0; i < periodDurationMsPattern.length; i++) {
      periodDurationUsPattern[i] = msToUs(periodDurationMsPattern[i]);
    }
    this.isContentTimeline = isContentTimeline;
    this.populateAds = populateAds;
    this.playedAds = playedAds;
    mediaItem = new MediaItem.Builder().build();
    periods =
        invalidate(
            msToUs(availabilityStartTimeMs),
            liveWindowDurationUs,
            nowUs,
            adSequencePattern,
            periodDurationUsPattern,
            isContentTimeline,
            populateAds,
            playedAds);
  }

  /** Advances the live window by the given duration, in microseconds. */
  public void advanceNowUs(long durationUs) {
    nowUs += durationUs;
    periods =
        invalidate(
            availabilityStartTimeUs,
            liveWindowDurationUs,
            nowUs,
            adSequencePattern,
            periodDurationUsPattern,
            isContentTimeline,
            populateAds,
            playedAds);
  }

  /**
   * The window's start time in microseconds since the Unix epoch, or {@link C#TIME_UNSET} if
   * unknown or not applicable.
   */
  public long getWindowStartTimeUs() {
    Window window = getWindow(/* windowIndex= */ 0, new Window());
    // Revert us/ms truncation introduced in `getWindow()`. This is identical to the truncation
    // applied in the Media3 `DashMediaSource.DashTimeline` and can be reverted in the same way.
    return window.windowStartTimeMs != C.TIME_UNSET
        ? msToUs(window.windowStartTimeMs) + (window.positionInFirstPeriodUs % 1000)
        : C.TIME_UNSET;
  }

  /**
   * Returns the period start time since Unix epoch, in microseconds.
   *
   * <p>Note: The returned value has millisecond precision only, so the trailing 3 digits are always
   * zeros.
   */
  public long getPeriodStartTimeUs(int periodIndex) {
    return msToUs(periods.get(periodIndex).periodStartTimeMs);
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    checkArgument(windowIndex == 0);
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().build();
    long positionInFirstPeriodUs = -periods.get(0).positionInWindowUs;
    window.set(
        /* uid= */ "live-window",
        mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ periods.get(0).periodStartTimeMs + usToMs(positionInFirstPeriodUs),
        /* elapsedRealtimeEpochOffsetMs= */ 0,
        /* isSeekable= */ true,
        /* isDynamic= */ true,
        liveConfiguration,
        /* defaultPositionUs= */ liveWindowDurationUs - msToUs(liveConfiguration.targetOffsetMs),
        /* durationUs= */ liveWindowDurationUs,
        /* firstPeriodIndex= */ 0,
        /* lastPeriodIndex= */ getPeriodCount() - 1,
        positionInFirstPeriodUs);
    return window;
  }

  @Override
  public int getPeriodCount() {
    return periods.size();
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    PeriodData periodData = periods.get(periodIndex);
    period.set(
        periodData.id,
        periodData.uid,
        /* windowIndex= */ 0,
        /* durationUs= */ periodIndex < getPeriodCount() - 1 ? periodData.durationUs : C.TIME_UNSET,
        periodData.positionInWindowUs,
        periodData.adPlaybackState,
        /* isPlaceholder= */ false);
    return period;
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    for (int i = 0; i < periods.size(); i++) {
      if (uid.equals(periods.get(i).uid)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    return periods.get(periodIndex).uid;
  }

  private static ImmutableList<PeriodData> invalidate(
      long availabilityStartTimeUs,
      long liveWindowDurationUs,
      long now,
      boolean[] adSequencePattern,
      long[] periodDurationUsPattern,
      boolean isContentTimeline,
      boolean populateAds,
      boolean playedAds) {
    long windowStartTimeUs = now - liveWindowDurationUs;
    int sequencePeriodCount = adSequencePattern.length;
    long sequenceDurationUs = sum(periodDurationUsPattern);
    long skippedSequenceCount = (windowStartTimeUs - availabilityStartTimeUs) / sequenceDurationUs;
    // Search the first period of the live window.
    int firstPeriodIndex = (int) (skippedSequenceCount * sequencePeriodCount);
    long firstPeriodDurationUs = periodDurationUsPattern[firstPeriodIndex % sequencePeriodCount];
    long firstPeriodEndTimeUs =
        availabilityStartTimeUs
            + (sequenceDurationUs * skippedSequenceCount)
            + firstPeriodDurationUs;
    while (firstPeriodEndTimeUs <= windowStartTimeUs) {
      firstPeriodDurationUs = periodDurationUsPattern[++firstPeriodIndex % sequencePeriodCount];
      firstPeriodEndTimeUs += firstPeriodDurationUs;
    }
    ImmutableList.Builder<PeriodData> liveWindow = new ImmutableList.Builder<>();
    long lastPeriodStartTimeUs = firstPeriodEndTimeUs - firstPeriodDurationUs;
    int lastPeriodIndex = firstPeriodIndex;
    // Add periods to the window from the first period until we find a period start after `now`.
    while (lastPeriodStartTimeUs < now) {
      long periodDurationUs = periodDurationUsPattern[lastPeriodIndex % sequencePeriodCount];
      boolean isAd = adSequencePattern[lastPeriodIndex % sequencePeriodCount];
      AdPlaybackState adPlaybackState = AdPlaybackState.NONE;
      if (!isContentTimeline) {
        adPlaybackState = new AdPlaybackState("adsId").withLivePostrollPlaceholderAppended();
        if (isAd && populateAds) {
          adPlaybackState =
              adPlaybackState
                  .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0)
                  .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
                  .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                  .withAdDurationsUs(
                      /* adGroupIndex= */ 0, /* adDurationsUs...= */ periodDurationUs)
                  .withContentResumeOffsetUs(
                      /* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ periodDurationUs);
          if (playedAds) {
            adPlaybackState =
                adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
          }
        }
      }
      liveWindow.add(
          new PeriodData(
              /* id= */ lastPeriodIndex++,
              periodDurationUs,
              /* positionInWindowUs= */ lastPeriodStartTimeUs - windowStartTimeUs,
              /* periodStartTimeMs= */ usToMs(lastPeriodStartTimeUs),
              isAd,
              adPlaybackState));
      lastPeriodStartTimeUs += periodDurationUs;
    }
    return liveWindow.build();
  }

  private static class PeriodData {

    private final int id;
    private final Object uid;
    private final long durationUs;
    private final long positionInWindowUs;
    private final long periodStartTimeMs;
    private final AdPlaybackState adPlaybackState;

    /** Creates an instance. */
    public PeriodData(
        int id,
        long durationUs,
        long positionInWindowUs,
        long periodStartTimeMs,
        boolean isAd,
        AdPlaybackState adPlaybackState) {
      this.id = id;
      this.periodStartTimeMs = periodStartTimeMs;
      this.uid = "uid-" + id + "[" + (isAd ? "a" : "c") + "]";
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      this.adPlaybackState = adPlaybackState;
    }
  }
}

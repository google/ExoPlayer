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

import static java.lang.Math.min;

import android.net.Uri;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/** Fake {@link Timeline} which can be setup to return custom {@link TimelineWindowDefinition}s. */
public final class FakeTimeline extends Timeline {

  /**
   * Definition used to define a {@link FakeTimeline}.
   */
  public static final class TimelineWindowDefinition {

    /** Default window duration in microseconds. */
    public static final long DEFAULT_WINDOW_DURATION_US = 10 * C.MICROS_PER_SECOND;

    /** Default offset of a window in its first period in microseconds. */
    public static final long DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US = 123 * C.MICROS_PER_SECOND;

    public final int periodCount;
    public final Object id;
    public final MediaItem mediaItem;
    public final boolean isSeekable;
    public final boolean isDynamic;
    public final boolean isLive;
    public final boolean isPlaceholder;
    public final long durationUs;
    public final long defaultPositionUs;
    public final long windowOffsetInFirstPeriodUs;
    public final AdPlaybackState adPlaybackState;

    /**
     * Creates a window definition that corresponds to a placeholder timeline using the given tag.
     *
     * @param tag The tag to use in the timeline.
     */
    public static TimelineWindowDefinition createPlaceholder(Object tag) {
      return new TimelineWindowDefinition(
          /* periodCount= */ 1,
          /* id= */ tag,
          /* isSeekable= */ false,
          /* isDynamic= */ true,
          /* isLive= */ false,
          /* isPlaceholder= */ true,
          /* durationUs= */ C.TIME_UNSET,
          /* defaultPositionUs= */ 0,
          /* windowOffsetInFirstPeriodUs= */ 0,
          AdPlaybackState.NONE);
    }

    /**
     * Creates a seekable, non-dynamic window definition with a duration of {@link
     * #DEFAULT_WINDOW_DURATION_US}.
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
      this(periodCount, id, isSeekable, isDynamic, durationUs, AdPlaybackState.NONE);
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
     * @param adPlaybackState The ad playback state.
     */
    public TimelineWindowDefinition(
        int periodCount,
        Object id,
        boolean isSeekable,
        boolean isDynamic,
        long durationUs,
        AdPlaybackState adPlaybackState) {
      this(
          periodCount,
          id,
          isSeekable,
          isDynamic,
          /* isLive= */ isDynamic,
          /* isPlaceholder= */ false,
          durationUs,
          /* defaultPositionUs= */ 0,
          DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
          adPlaybackState);
    }

    /**
     * Creates a window definition with ad groups.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param isLive Whether the window is live.
     * @param isPlaceholder Whether the window is a placeholder.
     * @param durationUs The duration of the window in microseconds.
     * @param defaultPositionUs The default position of the window in microseconds.
     * @param windowOffsetInFirstPeriodUs The offset of the window in the first period, in
     *     microseconds.
     * @param adPlaybackState The ad playback state.
     */
    public TimelineWindowDefinition(
        int periodCount,
        Object id,
        boolean isSeekable,
        boolean isDynamic,
        boolean isLive,
        boolean isPlaceholder,
        long durationUs,
        long defaultPositionUs,
        long windowOffsetInFirstPeriodUs,
        AdPlaybackState adPlaybackState) {
      this(
          periodCount,
          id,
          isSeekable,
          isDynamic,
          isLive,
          isPlaceholder,
          durationUs,
          defaultPositionUs,
          windowOffsetInFirstPeriodUs,
          adPlaybackState,
          FAKE_MEDIA_ITEM.buildUpon().setTag(id).build());
    }

    /**
     * Creates a window definition with ad groups and a custom media item.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param isLive Whether the window is live.
     * @param isPlaceholder Whether the window is a placeholder.
     * @param durationUs The duration of the window in microseconds.
     * @param defaultPositionUs The default position of the window in microseconds.
     * @param windowOffsetInFirstPeriodUs The offset of the window in the first period, in
     *     microseconds.
     * @param adPlaybackState The ad playback state.
     * @param mediaItem The media item to include in the timeline.
     */
    public TimelineWindowDefinition(
        int periodCount,
        Object id,
        boolean isSeekable,
        boolean isDynamic,
        boolean isLive,
        boolean isPlaceholder,
        long durationUs,
        long defaultPositionUs,
        long windowOffsetInFirstPeriodUs,
        AdPlaybackState adPlaybackState,
        MediaItem mediaItem) {
      Assertions.checkArgument(durationUs != C.TIME_UNSET || periodCount == 1);
      this.periodCount = periodCount;
      this.id = id;
      this.mediaItem = mediaItem;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.isLive = isLive;
      this.isPlaceholder = isPlaceholder;
      this.durationUs = durationUs;
      this.defaultPositionUs = defaultPositionUs;
      this.windowOffsetInFirstPeriodUs = windowOffsetInFirstPeriodUs;
      this.adPlaybackState = adPlaybackState;
    }
  }

  /** The fake media item used by the fake timeline. */
  public static final MediaItem FAKE_MEDIA_ITEM =
      new MediaItem.Builder().setMediaId("FakeTimeline").setUri(Uri.EMPTY).build();

  private static final long AD_DURATION_US = 5 * C.MICROS_PER_SECOND;

  private final TimelineWindowDefinition[] windowDefinitions;
  private final Object[] manifests;
  private final int[] periodOffsets;
  private final FakeShuffleOrder fakeShuffleOrder;

  /**
   * Returns an ad playback state with the specified number of ads in each of the specified ad
   * groups, each ten seconds long.
   *
   * @param adsPerAdGroup The number of ads per ad group.
   * @param adGroupTimesUs The times of ad groups, in microseconds.
   * @return The ad playback state.
   */
  public static AdPlaybackState createAdPlaybackState(int adsPerAdGroup, long... adGroupTimesUs) {
    int adGroupCount = adGroupTimesUs.length;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), adGroupTimesUs);
    long[][] adDurationsUs = new long[adGroupCount][];
    for (int i = 0; i < adGroupCount; i++) {
      adPlaybackState = adPlaybackState.withAdCount(/* adGroupIndex= */ i, adsPerAdGroup);
      for (int j = 0; j < adsPerAdGroup; j++) {
        adPlaybackState =
            adPlaybackState.withAdUri(
                /* adGroupIndex= */ i,
                /* adIndexInAdGroup= */ j,
                Uri.parse("https://example.com/ad/" + i + "/" + j));
      }
      adDurationsUs[i] = new long[adsPerAdGroup];
      Arrays.fill(adDurationsUs[i], AD_DURATION_US);
    }
    adPlaybackState = adPlaybackState.withAdDurationsUs(adDurationsUs);

    return adPlaybackState;
  }

  /**
   * Create a fake timeline with one seekable, non-dynamic window with one period and a duration of
   * {@link TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US}.
   */
  public FakeTimeline() {
    this(/* windowCount= */ 1);
  }

  /**
   * Creates a fake timeline with the given number of seekable, non-dynamic windows with one period
   * with a duration of {@link TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US} each.
   *
   * @param windowCount The number of windows.
   * @param manifests The manifests of the windows.
   */
  public FakeTimeline(int windowCount, Object... manifests) {
    this(manifests, createDefaultWindowDefinitions(windowCount));
  }

  /**
   * Creates a fake timeline with the given window definitions.
   *
   * @param windowDefinitions A list of {@link TimelineWindowDefinition}s.
   */
  public FakeTimeline(TimelineWindowDefinition... windowDefinitions) {
    this(new Object[0], windowDefinitions);
  }

  /**
   * Creates a fake timeline with the given window definitions.
   *
   * @param windowDefinitions A list of {@link TimelineWindowDefinition}s.
   */
  public FakeTimeline(Object[] manifests, TimelineWindowDefinition... windowDefinitions) {
    this.manifests = new Object[windowDefinitions.length];
    System.arraycopy(manifests, 0, this.manifests, 0, min(this.manifests.length, manifests.length));
    this.windowDefinitions = windowDefinitions;
    periodOffsets = new int[windowDefinitions.length + 1];
    periodOffsets[0] = 0;
    for (int i = 0; i < windowDefinitions.length; i++) {
      periodOffsets[i + 1] = periodOffsets[i] + windowDefinitions[i].periodCount;
    }
    fakeShuffleOrder = new FakeShuffleOrder(windowDefinitions.length);
  }

  @Override
  public int getWindowCount() {
    return windowDefinitions.length;
  }

  @Override
  public int getNextWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    }
    if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_ALL
          ? getFirstWindowIndex(shuffleModeEnabled)
          : C.INDEX_UNSET;
    }
    return shuffleModeEnabled ? fakeShuffleOrder.getNextIndex(windowIndex) : windowIndex + 1;
  }

  @Override
  public int getPreviousWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    }
    if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_ALL
          ? getLastWindowIndex(shuffleModeEnabled)
          : C.INDEX_UNSET;
    }
    return shuffleModeEnabled ? fakeShuffleOrder.getPreviousIndex(windowIndex) : windowIndex - 1;
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return shuffleModeEnabled
        ? fakeShuffleOrder.getLastIndex()
        : super.getLastWindowIndex(/* shuffleModeEnabled= */ false);
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return shuffleModeEnabled
        ? fakeShuffleOrder.getFirstIndex()
        : super.getFirstWindowIndex(/* shuffleModeEnabled= */ false);
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    window.set(
        /* uid= */ windowDefinition.id,
        windowDefinition.mediaItem,
        manifests[windowIndex],
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ windowDefinition.isLive
            ? C.usToMs(windowDefinition.windowOffsetInFirstPeriodUs)
            : C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ windowDefinition.isLive ? 0 : C.TIME_UNSET,
        windowDefinition.isSeekable,
        windowDefinition.isDynamic,
        windowDefinition.isLive ? windowDefinition.mediaItem.liveConfiguration : null,
        windowDefinition.defaultPositionUs,
        windowDefinition.durationUs,
        periodOffsets[windowIndex],
        periodOffsets[windowIndex + 1] - 1,
        windowDefinition.windowOffsetInFirstPeriodUs);
    window.isPlaceholder = windowDefinition.isPlaceholder;
    return window;
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
    Object uid = setIds ? Pair.create(windowDefinition.id, windowPeriodIndex) : null;
    // Arbitrarily set period duration by distributing window duration equally among all periods.
    long periodDurationUs =
        windowDefinition.durationUs == C.TIME_UNSET
            ? C.TIME_UNSET
            : windowDefinition.durationUs / windowDefinition.periodCount;
    long positionInWindowUs;
    if (windowPeriodIndex == 0) {
      if (windowDefinition.durationUs != C.TIME_UNSET) {
        periodDurationUs += windowDefinition.windowOffsetInFirstPeriodUs;
      }
      positionInWindowUs = -windowDefinition.windowOffsetInFirstPeriodUs;
    } else {
      positionInWindowUs = periodDurationUs * windowPeriodIndex;
    }
    period.set(
        id,
        uid,
        windowIndex,
        periodDurationUs,
        positionInWindowUs,
        windowDefinition.adPlaybackState,
        windowDefinition.isPlaceholder);
    return period;
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    for (int i = 0; i < getPeriodCount(); i++) {
      if (getUidOfPeriod(i).equals(uid)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, getPeriodCount());
    int windowIndex =
        Util.binarySearchFloor(
            periodOffsets, periodIndex, /* inclusive= */ true, /* stayInBounds= */ false);
    int windowPeriodIndex = periodIndex - periodOffsets[windowIndex];
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    return Pair.create(windowDefinition.id, windowPeriodIndex);
  }

  private static TimelineWindowDefinition[] createDefaultWindowDefinitions(int windowCount) {
    TimelineWindowDefinition[] windowDefinitions = new TimelineWindowDefinition[windowCount];
    for (int i = 0; i < windowCount; i++) {
      windowDefinitions[i] = new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ i);
    }
    return windowDefinitions;
  }

}

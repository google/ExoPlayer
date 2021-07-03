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
package com.google.android.exoplayer2.ext.cast;

import android.net.Uri;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import java.util.Arrays;

/** A {@link Timeline} for Cast media queues. */
/* package */ final class CastTimeline extends Timeline {

  /** Holds {@link Timeline} related data for a Cast media item. */
  public static final class ItemData {

    /** Holds no media information. */
    public static final ItemData EMPTY =
        new ItemData(
            /* windowDurationUs= */ C.TIME_UNSET,
            /* periodDurationUs= */ C.TIME_UNSET,
            /* defaultPositionUs= */ C.TIME_UNSET,
            /* positionInFirstPeriodUs= */ 0,
            /* isLive= */ false,
            /* isMovingLiveWindow */ false);

    /** The duration of the seekable window in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long windowDurationUs;
    /**
     * The duration of the underlying period in microseconds, or {@link C#TIME_UNSET} if unknown.
     * For vod content this will match the window duration. For live content this will be
     * {@link C#TIME_UNSET} or the duration from 0 until the end of the stream.
     */
    public final long periodDurationUs;
    /**
     * The default start position of the item in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public final long defaultPositionUs;
    /** The window offset from the beginning of the period in microseconds. */
    public final long windowStartOffsetUs;
    /** Whether the item is live content, or {@code false} if unknown. */
    public final boolean isLive;
    /**
     * Whether the current seekable range is a fixed-length moving window (true) or if it is an
     * expanding range (false). Only applicable if {@link #isLive} is true.
     */
    public final boolean isMovingLiveWindow;

    /**
     * Creates an instance.
     *
     * @param windowDurationUs See {@link #windowDurationUs}.
     * @param periodDurationUs See {@link #periodDurationUs}.
     * @param defaultPositionUs See {@link #defaultPositionUs}.
     * @param windowStartOffsetUs See {@link #windowStartOffsetUs}
     * @param isLive See {@link #isLive}.
     * @param isMovingLiveWindow See {@link #isMovingLiveWindow}.
     */
    public ItemData(long windowDurationUs, long periodDurationUs, long defaultPositionUs,
        long windowStartOffsetUs, boolean isLive, boolean isMovingLiveWindow) {
      this.windowDurationUs = windowDurationUs;
      this.periodDurationUs = periodDurationUs;
      this.defaultPositionUs = defaultPositionUs;
      this.windowStartOffsetUs = windowStartOffsetUs;
      this.isLive = isLive;
      this.isMovingLiveWindow = isMovingLiveWindow;
    }

    /**
     * Returns a copy of this instance with the given values.
     *
     * @param windowDurationUs See {@link #windowDurationUs}.
     * @param periodDurationUs See {@link #periodDurationUs}.
     * @param defaultPositionUs See {@link #defaultPositionUs}.
     * @param windowStartOffsetUs See {@link #windowStartOffsetUs}
     * @param isLive See {@link #isLive}.
     * @param isMovingLiveWindow See {@link #isMovingLiveWindow}.
     */
    public ItemData copyWithNewValues(long windowDurationUs, long periodDurationUs,
        long defaultPositionUs, long windowStartOffsetUs, boolean isLive,
        boolean isMovingLiveWindow) {
      if (windowDurationUs == this.windowDurationUs
          && periodDurationUs == this.periodDurationUs
          && defaultPositionUs == this.defaultPositionUs
          && windowStartOffsetUs == this.windowStartOffsetUs
          && isLive == this.isLive
          && isMovingLiveWindow == this.isMovingLiveWindow) {
        return this;
      }
      return new ItemData(windowDurationUs, periodDurationUs, defaultPositionUs,
          windowStartOffsetUs, isLive, isMovingLiveWindow);
    }
  }

  /** {@link Timeline} for a cast queue that has no items. */
  public static final CastTimeline EMPTY_CAST_TIMELINE =
      new CastTimeline(new int[0], new SparseArray<>());

  private final SparseIntArray idsToIndex;
  private final int[] ids;
  private final long[] windowDurationsUs;
  private final long[] periodDurationsUs;
  private final long[] defaultPositionsUs;
  private final long[] windowStartOffsetUs;
  private final boolean[] isLive;
  private final boolean[] isMovingLiveWindow;
  private final long creationUnixTimeMs;

  /**
   * Creates a Cast timeline from the given data.
   *
   * @param itemIds The ids of the items in the timeline.
   * @param itemIdToData Maps item ids to {@link ItemData}.
   */
  public CastTimeline(int[] itemIds, SparseArray<ItemData> itemIdToData) {
    creationUnixTimeMs = SystemClock.elapsedRealtime();
    int itemCount = itemIds.length;
    idsToIndex = new SparseIntArray(itemCount);
    ids = Arrays.copyOf(itemIds, itemCount);
    windowDurationsUs = new long[itemCount];
    periodDurationsUs = new long[itemCount];
    defaultPositionsUs = new long[itemCount];
    windowStartOffsetUs = new long[itemCount];
    isLive = new boolean[itemCount];
    isMovingLiveWindow = new boolean[itemCount];
    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      idsToIndex.put(id, i);
      ItemData data = itemIdToData.get(id, ItemData.EMPTY);
      windowDurationsUs[i] = data.windowDurationUs;
      periodDurationsUs[i] = data.periodDurationUs;
      defaultPositionsUs[i] = data.defaultPositionUs == C.TIME_UNSET ? 0 : data.defaultPositionUs;
      windowStartOffsetUs[i] = data.windowStartOffsetUs;
      isLive[i] = data.isLive;
      isMovingLiveWindow[i] = data.isMovingLiveWindow;
    }
  }

  // Timeline implementation.

  @Override
  public int getWindowCount() {
    return ids.length;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    long windowDurationUs = windowDurationsUs[windowIndex];
    long periodDurationsUs = this.periodDurationsUs[windowIndex];
    long windowStartOffsetUs = this.windowStartOffsetUs[windowIndex];

    // Account for the elapsed time since this Timeline was created.
    boolean windowIsLive = isLive[windowIndex];
    if (windowIsLive && windowDurationUs != C.TIME_UNSET) {
      long elapsedTimeUs = C.msToUs(SystemClock.elapsedRealtime() - creationUnixTimeMs);
      if (isMovingLiveWindow[windowIndex]) {
        windowStartOffsetUs += elapsedTimeUs;
      } else {
        windowDurationUs += elapsedTimeUs;
      }
    }

    boolean isDynamic = windowDurationUs == C.TIME_UNSET || windowDurationUs != periodDurationsUs;
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(Uri.EMPTY).setTag(ids[windowIndex]).build();
    return window.set(
        /* uid= */ ids[windowIndex],
        /* mediaItem= */ mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* isSeekable= */ windowDurationUs != C.TIME_UNSET,
        /* isDynamic */ isDynamic,
        /* liveConfiguration */ windowIsLive ? mediaItem.liveConfiguration : null,
        /* defaultPositionUs */ defaultPositionsUs[windowIndex],
        /* durationUs */ windowDurationUs,
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ windowStartOffsetUs);
  }

  @Override
  public int getPeriodCount() {
    return ids.length;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int id = ids[periodIndex];
    long positionInWindowUs = -windowStartOffsetUs[periodIndex];
    if (isLive[periodIndex] && isMovingLiveWindow[periodIndex]) {
      long elapsedTimeUs = C.msToUs(SystemClock.elapsedRealtime() - creationUnixTimeMs);
      positionInWindowUs -= elapsedTimeUs;
    }
    return period.set(id, id, periodIndex, periodDurationsUs[periodIndex], positionInWindowUs);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    return uid instanceof Integer ? idsToIndex.get((int) uid, C.INDEX_UNSET) : C.INDEX_UNSET;
  }

  @Override
  public Integer getUidOfPeriod(int periodIndex) {
    return ids[periodIndex];
  }

  // equals and hashCode implementations.

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof CastTimeline)) {
      return false;
    }
    CastTimeline that = (CastTimeline) other;
    return Arrays.equals(ids, that.ids)
        && Arrays.equals(windowDurationsUs, that.windowDurationsUs)
        && Arrays.equals(periodDurationsUs, that.periodDurationsUs)
        && Arrays.equals(defaultPositionsUs, that.defaultPositionsUs)
        && Arrays.equals(windowStartOffsetUs, that.windowStartOffsetUs)
        && Arrays.equals(isLive, that.isLive)
        && Arrays.equals(isMovingLiveWindow, that.isMovingLiveWindow);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(ids);
    result = 31 * result + Arrays.hashCode(windowDurationsUs);
    result = 31 * result + Arrays.hashCode(periodDurationsUs);
    result = 31 * result + Arrays.hashCode(defaultPositionsUs);
    result = 31 * result + Arrays.hashCode(windowStartOffsetUs);
    result = 31 * result + Arrays.hashCode(isLive);
    result = 31 * result + Arrays.hashCode(isMovingLiveWindow);
    return result;
  }
}

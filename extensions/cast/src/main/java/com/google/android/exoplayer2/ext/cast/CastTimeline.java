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

import android.util.SparseArray;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.gms.cast.MediaInfo;
import java.util.Arrays;

/** A {@link Timeline} for Cast media queues. */
/* package */ final class CastTimeline extends Timeline {

  /** Holds {@link Timeline} related data for a Cast media item. */
  public static final class ItemData {

    /* package */ static final String UNKNOWN_CONTENT_ID = "UNKNOWN_CONTENT_ID";

    /** Holds no media information. */
    public static final ItemData EMPTY =
        new ItemData(
            /* durationUs= */ C.TIME_UNSET,
            /* defaultPositionUs= */ C.TIME_UNSET,
            /* isLive= */ false,
            MediaItem.EMPTY,
            UNKNOWN_CONTENT_ID);

    /** The duration of the item in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long durationUs;
    /**
     * The default start position of the item in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public final long defaultPositionUs;
    /** Whether the item is live content, or {@code false} if unknown. */
    public final boolean isLive;
    /** The original media item that has been set or added to the playlist. */
    public final MediaItem mediaItem;
    /** The {@linkplain MediaInfo#getContentId() content ID} of the cast media queue item. */
    public final String contentId;

    /**
     * Creates an instance.
     *
     * @param durationUs See {@link #durationsUs}.
     * @param defaultPositionUs See {@link #defaultPositionUs}.
     * @param isLive See {@link #isLive}.
     * @param mediaItem See {@link #mediaItem}.
     * @param contentId See {@link #contentId}.
     */
    public ItemData(
        long durationUs,
        long defaultPositionUs,
        boolean isLive,
        MediaItem mediaItem,
        String contentId) {
      this.durationUs = durationUs;
      this.defaultPositionUs = defaultPositionUs;
      this.isLive = isLive;
      this.mediaItem = mediaItem;
      this.contentId = contentId;
    }

    /**
     * Returns a copy of this instance with the given values.
     *
     * @param durationUs The duration in microseconds, or {@link C#TIME_UNSET} if unknown.
     * @param defaultPositionUs The default start position in microseconds, or {@link C#TIME_UNSET}
     *     if unknown.
     * @param isLive Whether the item is live, or {@code false} if unknown.
     * @param mediaItem The media item.
     * @param contentId The content ID.
     */
    public ItemData copyWithNewValues(
        long durationUs,
        long defaultPositionUs,
        boolean isLive,
        MediaItem mediaItem,
        String contentId) {
      if (durationUs == this.durationUs
          && defaultPositionUs == this.defaultPositionUs
          && isLive == this.isLive
          && contentId.equals(this.contentId)
          && mediaItem.equals(this.mediaItem)) {
        return this;
      }
      return new ItemData(durationUs, defaultPositionUs, isLive, mediaItem, contentId);
    }
  }

  /** {@link Timeline} for a cast queue that has no items. */
  public static final CastTimeline EMPTY_CAST_TIMELINE =
      new CastTimeline(new int[0], new SparseArray<>());

  private final SparseIntArray idsToIndex;
  private final MediaItem[] mediaItems;
  private final int[] ids;
  private final long[] durationsUs;
  private final long[] defaultPositionsUs;
  private final boolean[] isLive;

  /**
   * Creates a Cast timeline from the given data.
   *
   * @param itemIds The ids of the items in the timeline.
   * @param itemIdToData Maps item ids to {@link ItemData}.
   */
  public CastTimeline(int[] itemIds, SparseArray<ItemData> itemIdToData) {
    int itemCount = itemIds.length;
    idsToIndex = new SparseIntArray(itemCount);
    ids = Arrays.copyOf(itemIds, itemCount);
    durationsUs = new long[itemCount];
    defaultPositionsUs = new long[itemCount];
    isLive = new boolean[itemCount];
    mediaItems = new MediaItem[itemCount];
    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      idsToIndex.put(id, i);
      ItemData data = itemIdToData.get(id, ItemData.EMPTY);
      mediaItems[i] = data.mediaItem;
      durationsUs[i] = data.durationUs;
      defaultPositionsUs[i] = data.defaultPositionUs == C.TIME_UNSET ? 0 : data.defaultPositionUs;
      isLive[i] = data.isLive;
    }
  }

  // Timeline implementation.

  @Override
  public int getWindowCount() {
    return ids.length;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    long durationUs = durationsUs[windowIndex];
    boolean isDynamic = durationUs == C.TIME_UNSET;
    return window.set(
        /* uid= */ ids[windowIndex],
        /* mediaItem= */ mediaItems[windowIndex],
        /* manifest= */ null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* isSeekable= */ !isDynamic,
        isDynamic,
        isLive[windowIndex] ? mediaItems[windowIndex].liveConfiguration : null,
        defaultPositionsUs[windowIndex],
        durationUs,
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ 0);
  }

  @Override
  public int getPeriodCount() {
    return ids.length;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int id = ids[periodIndex];
    return period.set(id, id, periodIndex, durationsUs[periodIndex], 0);
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
        && Arrays.equals(durationsUs, that.durationsUs)
        && Arrays.equals(defaultPositionsUs, that.defaultPositionsUs)
        && Arrays.equals(isLive, that.isLive);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(ids);
    result = 31 * result + Arrays.hashCode(durationsUs);
    result = 31 * result + Arrays.hashCode(defaultPositionsUs);
    result = 31 * result + Arrays.hashCode(isLive);
    return result;
  }
}

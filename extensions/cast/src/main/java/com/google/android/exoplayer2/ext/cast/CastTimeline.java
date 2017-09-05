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

import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Timeline} for Cast media queues.
 */
/* package */ final class CastTimeline extends Timeline {

  public static final CastTimeline EMPTY_CAST_TIMELINE =
      new CastTimeline(Collections.<MediaQueueItem>emptyList());

  private final SparseIntArray idsToIndex;
  private final int[] ids;
  private final long[] durationsUs;
  private final long[] defaultPositionsUs;

  public CastTimeline(List<MediaQueueItem> items) {
    int itemCount = items.size();
    int index = 0;
    idsToIndex = new SparseIntArray(itemCount);
    ids = new int[itemCount];
    durationsUs = new long[itemCount];
    defaultPositionsUs = new long[itemCount];
    for (MediaQueueItem item : items) {
      int itemId = item.getItemId();
      ids[index] = itemId;
      idsToIndex.put(itemId, index);
      durationsUs[index] = getStreamDurationUs(item.getMedia());
      defaultPositionsUs[index] = (long) (item.getStartTime() * C.MICROS_PER_SECOND);
      index++;
    }
  }

  @Override
  public int getWindowCount() {
    return ids.length;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    long durationUs = durationsUs[windowIndex];
    boolean isDynamic = durationUs == C.TIME_UNSET;
    return window.set(ids[windowIndex], C.TIME_UNSET, C.TIME_UNSET, !isDynamic, isDynamic,
        defaultPositionsUs[windowIndex], durationUs, windowIndex, windowIndex, 0);
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

  /**
   * Returns whether the timeline represents a given {@code MediaQueueItem} list.
   *
   * @param items The {@code MediaQueueItem} list.
   * @return Whether the timeline represents {@code items}.
   */
  /* package */ boolean represents(List<MediaQueueItem> items) {
    if (ids.length != items.size()) {
      return false;
    }
    int index = 0;
    for (MediaQueueItem item : items) {
      if (ids[index] != item.getItemId()
          || durationsUs[index] != getStreamDurationUs(item.getMedia())
          || defaultPositionsUs[index] != (long) (item.getStartTime() * C.MICROS_PER_SECOND)) {
        return false;
      }
      index++;
    }
    return true;
  }

  private static long getStreamDurationUs(MediaInfo mediaInfo) {
    long durationMs = mediaInfo != null ? mediaInfo.getStreamDuration()
        : MediaInfo.UNKNOWN_DURATION;
    return durationMs != MediaInfo.UNKNOWN_DURATION ? C.msToUs(durationMs) : C.TIME_UNSET;
  }

}

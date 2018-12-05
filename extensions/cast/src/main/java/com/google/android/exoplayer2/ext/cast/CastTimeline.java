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

import android.support.annotation.Nullable;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link Timeline} for Cast media queues.
 */
/* package */ final class CastTimeline extends Timeline {

  public static final CastTimeline EMPTY_CAST_TIMELINE =
      new CastTimeline(Collections.emptyList(), Collections.emptyMap());

  private final SparseIntArray idsToIndex;
  private final int[] ids;
  private final long[] durationsUs;
  private final long[] defaultPositionsUs;

  /**
   * @param items A list of cast media queue items to represent.
   * @param contentIdToDurationUsMap A map of content id to duration in microseconds.
   */
  public CastTimeline(List<MediaQueueItem> items, Map<String, Long> contentIdToDurationUsMap) {
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
      MediaInfo mediaInfo = item.getMedia();
      String contentId = mediaInfo.getContentId();
      durationsUs[index] =
          contentIdToDurationUsMap.containsKey(contentId)
              ? contentIdToDurationUsMap.get(contentId)
              : CastUtils.getStreamDurationUs(mediaInfo);
      defaultPositionsUs[index] = (long) (item.getStartTime() * C.MICROS_PER_SECOND);
      index++;
    }
  }

  // Timeline implementation.

  @Override
  public int getWindowCount() {
    return ids.length;
  }

  @Override
  public Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    long durationUs = durationsUs[windowIndex];
    boolean isDynamic = durationUs == C.TIME_UNSET;
    Object tag = setTag ? ids[windowIndex] : null;
    return window.set(
        tag,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* isSeekable= */ !isDynamic,
        isDynamic,
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
        && Arrays.equals(defaultPositionsUs, that.defaultPositionsUs);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(ids);
    result = 31 * result + Arrays.hashCode(durationsUs);
    result = 31 * result + Arrays.hashCode(defaultPositionsUs);
    return result;
  }

}

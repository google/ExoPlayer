/*
 * Copyright (C) 2018 The Android Open Source Project
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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLiveSeekableRange;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Creates {@link CastTimeline CastTimelines} from cast receiver app status updates.
 *
 * <p>This class keeps track of the duration reported by the current item to fill any missing
 * durations in the media queue items [See internal: b/65152553].
 */
/* package */ final class CastTimelineTracker {

  private final SparseArray<CastTimeline.ItemData> itemIdToData;

  public CastTimelineTracker() {
    itemIdToData = new SparseArray<>();
  }

  /**
   * Returns a {@link CastTimeline} that represents the state of the given {@code
   * remoteMediaClient}.
   *
   * <p>Returned timelines may contain values obtained from {@code remoteMediaClient} in previous
   * invocations of this method.
   *
   * @param remoteMediaClient The Cast media client.
   * @return A {@link CastTimeline} that represents the given {@code remoteMediaClient} status.
   */
  public CastTimeline getCastTimeline(RemoteMediaClient remoteMediaClient) {
    // TODO: Reset state when the app instance changes [Internal ref: b/129672468].
    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
    if (mediaStatus == null) {
      return CastTimeline.EMPTY_CAST_TIMELINE;
    }

    ArrayList<Integer> itemIdsList = new ArrayList<>();
    int currentItemId = mediaStatus.getCurrentItemId();

    for (MediaQueueItem item : mediaStatus.getQueueItems()) {
      int itemId = item.getItemId();
      itemIdsList.add(itemId);

      long defaultPositionUs = (long) (item.getStartTime() * C.MICROS_PER_SECOND);
      if (itemId == currentItemId) {
        updateItemData(currentItemId, mediaStatus, mediaStatus.getMediaInfo(), defaultPositionUs);
      } else {
        updateItemData(itemId, null, item.getMedia(), defaultPositionUs);
      }
    }

    // If the queue is empty or does not contain the active item update based on the current media
    // status only.
    if (!itemIdsList.contains(currentItemId)) {
      updateItemData(currentItemId, mediaStatus, mediaStatus.getMediaInfo(), C.TIME_UNSET);
    }

    int[] itemIds = Ints.toArray(itemIdsList);
    if (itemIds.length > 0) {
      // Only remove unused items when there is something in the queue to avoid removing all entries
      // if the remote media client clears the queue temporarily. See [Internal ref: b/128825216].
      removeUnusedItemDataEntries(itemIds);
    }

    return new CastTimeline(itemIds, itemIdToData);
  }

  /**
   * Update the item data for itemId based on the mediaStatus and mediaInfo.
   *
   * @param itemId the id of the queue item.
   * @param mediaStatus the {@link MediaStatus} of the item if it is active or null.
   * @param mediaInfo the {@link MediaInfo} of the item.
   * @param defaultPositionUs the default position in microseconds.
   */
  private void updateItemData(
      int itemId, @Nullable MediaStatus mediaStatus, @Nullable MediaInfo mediaInfo,
      long defaultPositionUs) {
    CastTimeline.ItemData previousData = itemIdToData.get(itemId, CastTimeline.ItemData.EMPTY);

    boolean isLive = mediaInfo == null
        ? previousData.isLive
        : mediaInfo.getStreamType() == MediaInfo.STREAM_TYPE_LIVE;

    long windowDurationUs;
    long periodDurationUs;
    long windowOffsetUs = 0;
    boolean isMovingLiveWindow = false;

    @Nullable MediaLiveSeekableRange liveSeekableRange =
        mediaStatus != null ? mediaStatus.getLiveSeekableRange() : null;
    if (isLive && liveSeekableRange != null) {
      long startTime = liveSeekableRange.getStartTime();
      long endTime = liveSeekableRange.getEndTime();
      long durationMs = endTime - startTime;
      if (durationMs > 0) {
        // Create a window that matches the seekable range of the stream. It might not start at 0.
        windowOffsetUs = C.msToUs(startTime);
        windowDurationUs = C.msToUs(durationMs);
        isMovingLiveWindow = liveSeekableRange.isMovingWindow();
        if (liveSeekableRange.isLiveDone()) {
          periodDurationUs = C.msToUs(endTime);
        } else {
          periodDurationUs = C.TIME_UNSET;
        }
      } else {
        periodDurationUs = C.TIME_UNSET;
        windowDurationUs = C.TIME_UNSET;
      }
    } else {
      long mediaInfoDuration = CastUtils.getStreamDurationUs(mediaInfo);
      windowDurationUs =
          mediaInfoDuration == C.TIME_UNSET ? previousData.windowDurationUs : mediaInfoDuration;
      periodDurationUs = windowDurationUs;
    }

    if (defaultPositionUs == C.TIME_UNSET) {
      defaultPositionUs = previousData.defaultPositionUs;
    }

    CastTimeline.ItemData itemData = previousData
        .copyWithNewValues(windowDurationUs, periodDurationUs, defaultPositionUs,
            windowOffsetUs, isLive, isMovingLiveWindow);
    itemIdToData.put(itemId, itemData);
  }

  private void removeUnusedItemDataEntries(int[] itemIds) {
    HashSet<Integer> scratchItemIds = new HashSet<>(/* initialCapacity= */ itemIds.length * 2);
    for (int id : itemIds) {
      scratchItemIds.add(id);
    }

    int index = 0;
    while (index < itemIdToData.size()) {
      if (!scratchItemIds.contains(itemIdToData.keyAt(index))) {
        itemIdToData.removeAt(index);
      } else {
        index++;
      }
    }
  }
}

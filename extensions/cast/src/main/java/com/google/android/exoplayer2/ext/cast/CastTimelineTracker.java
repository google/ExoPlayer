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

import static com.google.android.exoplayer2.ext.cast.CastTimeline.ItemData.UNKNOWN_CONTENT_ID;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Creates {@link CastTimeline CastTimelines} from cast receiver app status updates.
 *
 * <p>This class keeps track of the duration reported by the current item to fill any missing
 * durations in the media queue items [See internal: b/65152553].
 */
/* package */ final class CastTimelineTracker {

  private final SparseArray<CastTimeline.ItemData> itemIdToData;
  private final MediaItemConverter mediaItemConverter;
  @VisibleForTesting /* package */ final HashMap<String, MediaItem> mediaItemsByContentId;

  /**
   * Creates an instance.
   *
   * @param mediaItemConverter The converter used to convert from a {@link MediaQueueItem} to a
   *     {@link MediaItem}.
   */
  public CastTimelineTracker(MediaItemConverter mediaItemConverter) {
    this.mediaItemConverter = mediaItemConverter;
    itemIdToData = new SparseArray<>();
    mediaItemsByContentId = new HashMap<>();
  }

  /**
   * Called when media items {@linkplain Player#setMediaItems have been set to the playlist} and are
   * sent to the cast playback queue. A future queue update of the {@link RemoteMediaClient} will
   * reflect this addition.
   *
   * @param mediaItems The media items that have been set.
   * @param mediaQueueItems The corresponding media queue items.
   */
  public void onMediaItemsSet(List<MediaItem> mediaItems, MediaQueueItem[] mediaQueueItems) {
    mediaItemsByContentId.clear();
    onMediaItemsAdded(mediaItems, mediaQueueItems);
  }

  /**
   * Called when media items {@linkplain Player#addMediaItems(List) have been added} and are sent to
   * the cast playback queue. A future queue update of the {@link RemoteMediaClient} will reflect
   * this addition.
   *
   * @param mediaItems The media items that have been added.
   * @param mediaQueueItems The corresponding media queue items.
   */
  public void onMediaItemsAdded(List<MediaItem> mediaItems, MediaQueueItem[] mediaQueueItems) {
    for (int i = 0; i < mediaItems.size(); i++) {
      mediaItemsByContentId.put(
          checkNotNull(mediaQueueItems[i].getMedia()).getContentId(), mediaItems.get(i));
    }
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
    int[] itemIds = remoteMediaClient.getMediaQueue().getItemIds();
    if (itemIds.length > 0) {
      // Only remove unused items when there is something in the queue to avoid removing all entries
      // if the remote media client clears the queue temporarily. See [Internal ref: b/128825216].
      removeUnusedItemDataEntries(itemIds);
    }

    // TODO: Reset state when the app instance changes [Internal ref: b/129672468].
    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
    if (mediaStatus == null) {
      return CastTimeline.EMPTY_CAST_TIMELINE;
    }

    int currentItemId = mediaStatus.getCurrentItemId();
    String currentContentId = checkStateNotNull(mediaStatus.getMediaInfo()).getContentId();
    MediaItem mediaItem = mediaItemsByContentId.get(currentContentId);
    updateItemData(
        currentItemId,
        mediaItem != null ? mediaItem : MediaItem.EMPTY,
        mediaStatus.getMediaInfo(),
        currentContentId,
        /* defaultPositionUs= */ C.TIME_UNSET);

    for (MediaQueueItem queueItem : mediaStatus.getQueueItems()) {
      long defaultPositionUs = (long) (queueItem.getStartTime() * C.MICROS_PER_SECOND);
      @Nullable MediaInfo mediaInfo = queueItem.getMedia();
      String contentId = mediaInfo != null ? mediaInfo.getContentId() : UNKNOWN_CONTENT_ID;
      mediaItem = mediaItemsByContentId.get(contentId);
      updateItemData(
          queueItem.getItemId(),
          mediaItem != null ? mediaItem : mediaItemConverter.toMediaItem(queueItem),
          mediaInfo,
          contentId,
          defaultPositionUs);
    }
    return new CastTimeline(itemIds, itemIdToData);
  }

  private void updateItemData(
      int itemId,
      MediaItem mediaItem,
      @Nullable MediaInfo mediaInfo,
      String contentId,
      long defaultPositionUs) {
    CastTimeline.ItemData previousData = itemIdToData.get(itemId, CastTimeline.ItemData.EMPTY);
    long durationUs = CastUtils.getStreamDurationUs(mediaInfo);
    if (durationUs == C.TIME_UNSET) {
      durationUs = previousData.durationUs;
    }
    boolean isLive =
        mediaInfo == null
            ? previousData.isLive
            : mediaInfo.getStreamType() == MediaInfo.STREAM_TYPE_LIVE;
    if (defaultPositionUs == C.TIME_UNSET) {
      defaultPositionUs = previousData.defaultPositionUs;
    }
    itemIdToData.put(
        itemId,
        previousData.copyWithNewValues(
            durationUs, defaultPositionUs, isLive, mediaItem, contentId));
  }

  private void removeUnusedItemDataEntries(int[] itemIds) {
    HashSet<Integer> scratchItemIds = new HashSet<>(/* initialCapacity= */ itemIds.length * 2);
    for (int id : itemIds) {
      scratchItemIds.add(id);
    }

    int index = 0;
    while (index < itemIdToData.size()) {
      if (!scratchItemIds.contains(itemIdToData.keyAt(index))) {
        CastTimeline.ItemData itemData = itemIdToData.valueAt(index);
        mediaItemsByContentId.remove(itemData.contentId);
        itemIdToData.removeAt(index);
      } else {
        index++;
      }
    }
  }
}

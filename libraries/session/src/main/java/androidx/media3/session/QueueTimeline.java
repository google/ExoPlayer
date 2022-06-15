/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.session;

import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable class to represent the current {@link Timeline} backed by {@link QueueItem}.
 *
 * <p>This supports the fake item that represents the removed but currently playing media item. In
 * that case, a fake item would be inserted at the end of the {@link MediaItem media item list}
 * converted from {@link QueueItem queue item list}. Without the fake item support, the timeline
 * should be always recreated to handle the case when the fake item is no longer necessary and
 * timeline change isn't precisely detected. Queue item doesn't support equals(), so it's better not
 * to use equals() on the converted MediaItem.
 */
/* package */ final class QueueTimeline extends Timeline {

  public static final QueueTimeline DEFAULT =
      new QueueTimeline(ImmutableList.of(), ImmutableMap.of(), /* fakeMediaItem= */ null);

  private static final Object FAKE_WINDOW_UID = new Object();

  private final ImmutableList<MediaItem> mediaItems;
  private final Map<MediaItem, Long> unmodifiableMediaItemToQueueIdMap;
  @Nullable private final MediaItem fakeMediaItem;

  private QueueTimeline(
      ImmutableList<MediaItem> mediaItems,
      Map<MediaItem, Long> unmodifiableMediaItemToQueueIdMap,
      @Nullable MediaItem fakeMediaItem) {
    this.mediaItems = mediaItems;
    this.unmodifiableMediaItemToQueueIdMap = unmodifiableMediaItemToQueueIdMap;
    this.fakeMediaItem = fakeMediaItem;
  }

  public QueueTimeline(QueueTimeline queueTimeline) {
    this.mediaItems = queueTimeline.mediaItems;
    this.unmodifiableMediaItemToQueueIdMap = queueTimeline.unmodifiableMediaItemToQueueIdMap;
    this.fakeMediaItem = queueTimeline.fakeMediaItem;
  }

  public QueueTimeline copyWithFakeMediaItem(@Nullable MediaItem fakeMediaItem) {
    return new QueueTimeline(mediaItems, unmodifiableMediaItemToQueueIdMap, fakeMediaItem);
  }

  public QueueTimeline copyWithNewMediaItem(int replaceIndex, MediaItem newMediaItem) {
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, replaceIndex));
    newMediaItemsBuilder.add(newMediaItem);
    newMediaItemsBuilder.addAll(mediaItems.subList(replaceIndex + 1, mediaItems.size()));
    return new QueueTimeline(
        newMediaItemsBuilder.build(), unmodifiableMediaItemToQueueIdMap, fakeMediaItem);
  }

  public QueueTimeline copyWithNewMediaItems(int index, List<MediaItem> newMediaItems) {
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, index));
    newMediaItemsBuilder.addAll(newMediaItems);
    newMediaItemsBuilder.addAll(mediaItems.subList(index, mediaItems.size()));
    return new QueueTimeline(
        newMediaItemsBuilder.build(), unmodifiableMediaItemToQueueIdMap, fakeMediaItem);
  }

  public QueueTimeline copyWithRemovedMediaItems(int fromIndex, int toIndex) {
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, fromIndex));
    newMediaItemsBuilder.addAll(mediaItems.subList(toIndex, mediaItems.size()));
    return new QueueTimeline(
        newMediaItemsBuilder.build(), unmodifiableMediaItemToQueueIdMap, fakeMediaItem);
  }

  public QueueTimeline copyWithMovedMediaItems(int fromIndex, int toIndex, int newIndex) {
    List<MediaItem> list = new ArrayList<>(mediaItems);
    Util.moveItems(list, fromIndex, toIndex, newIndex);
    return new QueueTimeline(
        new ImmutableList.Builder<MediaItem>().addAll(list).build(),
        unmodifiableMediaItemToQueueIdMap,
        fakeMediaItem);
  }

  public static QueueTimeline create(List<QueueItem> queue) {
    ImmutableList.Builder<MediaItem> mediaItemsBuilder = new ImmutableList.Builder<>();
    IdentityHashMap<MediaItem, Long> mediaItemToQueueIdMap = new IdentityHashMap<>();
    for (int i = 0; i < queue.size(); i++) {
      QueueItem queueItem = queue.get(i);
      MediaItem mediaItem = MediaUtils.convertToMediaItem(queueItem);
      mediaItemsBuilder.add(mediaItem);
      mediaItemToQueueIdMap.put(mediaItem, queueItem.getQueueId());
    }
    return new QueueTimeline(
        mediaItemsBuilder.build(),
        Collections.unmodifiableMap(mediaItemToQueueIdMap),
        /* fakeMediaItem= */ null);
  }

  public long getQueueId(int mediaItemIndex) {
    @Nullable MediaItem mediaItem = mediaItems.get(mediaItemIndex);
    if (mediaItem == null) {
      return QueueItem.UNKNOWN_ID;
    }
    Long queueId = unmodifiableMediaItemToQueueIdMap.get(mediaItem);
    return queueId == null ? QueueItem.UNKNOWN_ID : queueId;
  }

  @Nullable
  public MediaItem getMediaItemAt(int mediaItemIndex) {
    if (mediaItemIndex >= 0 && mediaItemIndex < mediaItems.size()) {
      return mediaItems.get(mediaItemIndex);
    }
    return (mediaItemIndex == mediaItems.size()) ? fakeMediaItem : null;
  }

  public int findIndexOf(MediaItem mediaItem) {
    if (mediaItem == fakeMediaItem) {
      return mediaItems.size();
    }
    int mediaItemIndex = mediaItems.indexOf(mediaItem);
    return mediaItemIndex == -1 ? C.INDEX_UNSET : mediaItemIndex;
  }

  @Override
  public int getWindowCount() {
    return mediaItems.size() + ((fakeMediaItem == null) ? 0 : 1);
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    // TODO(b/149713425): Set duration if it's available from MediaMetadataCompat.
    MediaItem mediaItem;
    if (windowIndex == mediaItems.size() && fakeMediaItem != null) {
      mediaItem = fakeMediaItem;
    } else {
      mediaItem = mediaItems.get(windowIndex);
    }
    return getWindow(window, mediaItem, windowIndex);
  }

  @Override
  public int getPeriodCount() {
    return getWindowCount();
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    // TODO(b/149713425): Set duration if it's available from MediaMetadataCompat.
    period.set(
        /* id= */ null,
        /* uid= */ null,
        /* windowIndex= */ periodIndex,
        /* durationUs= */ C.TIME_UNSET,
        /* positionInWindowUs= */ 0);
    return period;
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof QueueTimeline)) {
      return false;
    }
    QueueTimeline other = (QueueTimeline) obj;
    return mediaItems == other.mediaItems
        && unmodifiableMediaItemToQueueIdMap == other.unmodifiableMediaItemToQueueIdMap
        && fakeMediaItem == other.fakeMediaItem;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mediaItems, unmodifiableMediaItemToQueueIdMap, fakeMediaItem);
  }

  private static Window getWindow(Window window, MediaItem mediaItem, int windowIndex) {
    window.set(
        FAKE_WINDOW_UID,
        mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* liveConfiguration= */ null,
        /* defaultPositionUs= */ 0,
        /* durationUs= */ C.TIME_UNSET,
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ 0);
    return window;
  }
}

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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.support.v4.media.MediaMetadataCompat;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable class to represent the current {@link Timeline} backed by {@linkplain QueueItem
 * queue items}.
 *
 * <p>This timeline supports the case in which the current {@link MediaMetadataCompat} is not
 * included in the queue of the session. In such a case a fake media item is inserted at the end of
 * the timeline and the size of the timeline is by one larger than the size of the corresponding
 * queue in the session.
 */
/* package */ final class QueueTimeline extends Timeline {

  public static final QueueTimeline DEFAULT =
      new QueueTimeline(ImmutableList.of(), ImmutableMap.of(), /* fakeMediaItem= */ null);

  private static final Object FAKE_WINDOW_UID = new Object();

  private final ImmutableList<MediaItem> mediaItems;
  private final ImmutableMap<MediaItem, Long> mediaItemToQueueIdMap;
  @Nullable private final MediaItem fakeMediaItem;

  /** Creates a new instance. */
  public QueueTimeline(QueueTimeline queueTimeline) {
    this.mediaItems = queueTimeline.mediaItems;
    this.mediaItemToQueueIdMap = queueTimeline.mediaItemToQueueIdMap;
    this.fakeMediaItem = queueTimeline.fakeMediaItem;
  }

  private QueueTimeline(
      ImmutableList<MediaItem> mediaItems,
      ImmutableMap<MediaItem, Long> mediaItemToQueueIdMap,
      @Nullable MediaItem fakeMediaItem) {
    this.mediaItems = mediaItems;
    this.mediaItemToQueueIdMap = mediaItemToQueueIdMap;
    this.fakeMediaItem = fakeMediaItem;
  }

  /** Creates a {@link QueueTimeline} from a list of {@linkplain QueueItem queue items}. */
  public static QueueTimeline create(List<QueueItem> queue) {
    ImmutableList.Builder<MediaItem> mediaItemsBuilder = new ImmutableList.Builder<>();
    ImmutableMap.Builder<MediaItem, Long> mediaItemToQueueIdMap = new ImmutableMap.Builder<>();
    for (int i = 0; i < queue.size(); i++) {
      QueueItem queueItem = queue.get(i);
      MediaItem mediaItem = MediaUtils.convertToMediaItem(queueItem);
      mediaItemsBuilder.add(mediaItem);
      mediaItemToQueueIdMap.put(mediaItem, queueItem.getQueueId());
    }
    return new QueueTimeline(
        mediaItemsBuilder.build(), mediaItemToQueueIdMap.buildOrThrow(), /* fakeMediaItem= */ null);
  }

  /**
   * Gets the queue ID of the media item at the given index or {@link QueueItem#UNKNOWN_ID} if not
   * known.
   *
   * @param mediaItemIndex The media item index.
   * @return The corresponding queue ID or {@link QueueItem#UNKNOWN_ID} if not known.
   */
  public long getQueueId(int mediaItemIndex) {
    MediaItem mediaItem = getMediaItemAt(mediaItemIndex);
    @Nullable Long queueId = mediaItemToQueueIdMap.get(mediaItem);
    return queueId == null ? QueueItem.UNKNOWN_ID : queueId;
  }

  /**
   * Copies the timeline with the given fake media item.
   *
   * @param fakeMediaItem The fake media item.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithFakeMediaItem(@Nullable MediaItem fakeMediaItem) {
    return new QueueTimeline(mediaItems, mediaItemToQueueIdMap, fakeMediaItem);
  }

  /**
   * Replaces the media item at {@code replaceIndex} with the new media item.
   *
   * @param replaceIndex The index at which to replace the media item.
   * @param newMediaItem The new media item that replaces the old one.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithNewMediaItem(int replaceIndex, MediaItem newMediaItem) {
    checkArgument(
        replaceIndex < mediaItems.size()
            || (replaceIndex == mediaItems.size() && fakeMediaItem != null));
    if (replaceIndex == mediaItems.size()) {
      return new QueueTimeline(mediaItems, mediaItemToQueueIdMap, newMediaItem);
    }
    MediaItem oldMediaItem = mediaItems.get(replaceIndex);
    // Create the new play list.
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, replaceIndex));
    newMediaItemsBuilder.add(newMediaItem);
    newMediaItemsBuilder.addAll(mediaItems.subList(replaceIndex + 1, mediaItems.size()));
    // Update the map of items to queue IDs accordingly.
    Map<MediaItem, Long> newMediaItemToQueueIdMap = new HashMap<>(mediaItemToQueueIdMap);
    Long queueId = checkNotNull(newMediaItemToQueueIdMap.remove(oldMediaItem));
    newMediaItemToQueueIdMap.put(newMediaItem, queueId);
    return new QueueTimeline(
        newMediaItemsBuilder.build(), ImmutableMap.copyOf(newMediaItemToQueueIdMap), fakeMediaItem);
  }

  /**
   * Replaces the media item at the given index with a list of new media items. The timeline grows
   * by one less than the size of the new list of items.
   *
   * @param index The index of the media item to be replaced.
   * @param newMediaItems The list of new {@linkplain MediaItem media items} to insert.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithNewMediaItems(int index, List<MediaItem> newMediaItems) {
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, index));
    newMediaItemsBuilder.addAll(newMediaItems);
    newMediaItemsBuilder.addAll(mediaItems.subList(index, mediaItems.size()));
    return new QueueTimeline(newMediaItemsBuilder.build(), mediaItemToQueueIdMap, fakeMediaItem);
  }

  /**
   * Removes the range of media items in the current timeline.
   *
   * @param fromIndex The index to start removing items from.
   * @param toIndex The index up to which to remove items (exclusive).
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithRemovedMediaItems(int fromIndex, int toIndex) {
    ImmutableList.Builder<MediaItem> newMediaItemsBuilder = new ImmutableList.Builder<>();
    newMediaItemsBuilder.addAll(mediaItems.subList(0, fromIndex));
    newMediaItemsBuilder.addAll(mediaItems.subList(toIndex, mediaItems.size()));
    return new QueueTimeline(newMediaItemsBuilder.build(), mediaItemToQueueIdMap, fakeMediaItem);
  }

  /**
   * Moves the defined range of media items to a new position.
   *
   * @param fromIndex The start index of the range to be moved.
   * @param toIndex The (exclusive) end index of the range to be moved.
   * @param newIndex The new index to move the first item of the range to.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithMovedMediaItems(int fromIndex, int toIndex, int newIndex) {
    List<MediaItem> list = new ArrayList<>(mediaItems);
    Util.moveItems(list, fromIndex, toIndex, newIndex);
    return new QueueTimeline(
        new ImmutableList.Builder<MediaItem>().addAll(list).build(),
        mediaItemToQueueIdMap,
        fakeMediaItem);
  }

  /**
   * Returns the media item index of the given media item in the timeline, or {@link C#INDEX_UNSET}
   * if the item is not part of this timeline.
   *
   * @param mediaItem The media item of interest.
   * @return The index of the item or {@link C#INDEX_UNSET} if the item is not part of the timeline.
   */
  public int indexOf(MediaItem mediaItem) {
    if (mediaItem.equals(fakeMediaItem)) {
      return mediaItems.size();
    }
    int mediaItemIndex = mediaItems.indexOf(mediaItem);
    return mediaItemIndex == -1 ? C.INDEX_UNSET : mediaItemIndex;
  }

  @Nullable
  public MediaItem getMediaItemAt(int mediaItemIndex) {
    if (mediaItemIndex >= 0 && mediaItemIndex < mediaItems.size()) {
      return mediaItems.get(mediaItemIndex);
    }
    return (mediaItemIndex == mediaItems.size()) ? fakeMediaItem : null;
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
    return Objects.equal(mediaItems, other.mediaItems)
        && Objects.equal(mediaItemToQueueIdMap, other.mediaItemToQueueIdMap)
        && Objects.equal(fakeMediaItem, other.fakeMediaItem);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mediaItems, mediaItemToQueueIdMap, fakeMediaItem);
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

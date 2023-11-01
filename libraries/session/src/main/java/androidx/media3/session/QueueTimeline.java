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
import static androidx.media3.common.util.Util.msToUs;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

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
      new QueueTimeline(ImmutableList.of(), /* fakeQueuedMediaItem= */ null);

  private static final Object FAKE_WINDOW_UID = new Object();

  private final ImmutableList<QueuedMediaItem> queuedMediaItems;
  @Nullable private final QueuedMediaItem fakeQueuedMediaItem;

  private QueueTimeline(
      ImmutableList<QueuedMediaItem> queuedMediaItems,
      @Nullable QueuedMediaItem fakeQueuedMediaItem) {
    this.queuedMediaItems = queuedMediaItems;
    this.fakeQueuedMediaItem = fakeQueuedMediaItem;
  }

  /** Creates a {@link QueueTimeline} from a list of {@linkplain QueueItem queue items}. */
  public static QueueTimeline create(List<QueueItem> queue) {
    ImmutableList.Builder<QueuedMediaItem> queuedMediaItemsBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < queue.size(); i++) {
      QueueItem queueItem = queue.get(i);
      MediaItem mediaItem = LegacyConversions.convertToMediaItem(queueItem);
      queuedMediaItemsBuilder.add(
          new QueuedMediaItem(mediaItem, queueItem.getQueueId(), /* durationMs= */ C.TIME_UNSET));
    }
    return new QueueTimeline(queuedMediaItemsBuilder.build(), /* fakeQueuedMediaItem= */ null);
  }

  /** Returns a copy of the current queue timeline. */
  public QueueTimeline copy() {
    return new QueueTimeline(queuedMediaItems, fakeQueuedMediaItem);
  }

  /**
   * Gets the queue ID of the media item at the given index or {@link QueueItem#UNKNOWN_ID} if not
   * known.
   *
   * @param mediaItemIndex The media item index.
   * @return The corresponding queue ID or {@link QueueItem#UNKNOWN_ID} if not known.
   */
  public long getQueueId(int mediaItemIndex) {
    return mediaItemIndex >= 0 && mediaItemIndex < queuedMediaItems.size()
        ? queuedMediaItems.get(mediaItemIndex).queueId
        : QueueItem.UNKNOWN_ID;
  }

  /**
   * Copies the timeline with the given fake media item.
   *
   * @param fakeMediaItem The fake media item.
   * @param durationMs The duration of the fake media item, in milliseconds, or {@link C#TIME_UNSET}
   *     if unknown.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithFakeMediaItem(MediaItem fakeMediaItem, long durationMs) {
    return new QueueTimeline(
        queuedMediaItems, new QueuedMediaItem(fakeMediaItem, QueueItem.UNKNOWN_ID, durationMs));
  }

  /** Copies the timeline while clearing any previously set fake media item. */
  public QueueTimeline copyWithClearedFakeMediaItem() {
    return new QueueTimeline(queuedMediaItems, /* fakeQueuedMediaItem= */ null);
  }

  /**
   * Replaces the media item at {@code replaceIndex} with the new media item.
   *
   * @param replaceIndex The index at which to replace the media item.
   * @param newMediaItem The new media item that replaces the old one.
   * @param durationMs The duration of the media item, in milliseconds, or {@link C#TIME_UNSET} if
   *     unknown.
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithNewMediaItem(
      int replaceIndex, MediaItem newMediaItem, long durationMs) {
    checkArgument(
        replaceIndex < queuedMediaItems.size()
            || (replaceIndex == queuedMediaItems.size() && fakeQueuedMediaItem != null));
    if (replaceIndex == queuedMediaItems.size()) {
      return new QueueTimeline(
          queuedMediaItems, new QueuedMediaItem(newMediaItem, QueueItem.UNKNOWN_ID, durationMs));
    }
    long queueId = queuedMediaItems.get(replaceIndex).queueId;
    ImmutableList.Builder<QueuedMediaItem> queuedItemsBuilder = new ImmutableList.Builder<>();
    queuedItemsBuilder.addAll(queuedMediaItems.subList(0, replaceIndex));
    queuedItemsBuilder.add(new QueuedMediaItem(newMediaItem, queueId, durationMs));
    queuedItemsBuilder.addAll(queuedMediaItems.subList(replaceIndex + 1, queuedMediaItems.size()));
    return new QueueTimeline(queuedItemsBuilder.build(), fakeQueuedMediaItem);
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
    ImmutableList.Builder<QueuedMediaItem> queuedItemsBuilder = new ImmutableList.Builder<>();
    queuedItemsBuilder.addAll(queuedMediaItems.subList(0, index));
    for (int i = 0; i < newMediaItems.size(); i++) {
      queuedItemsBuilder.add(
          new QueuedMediaItem(
              newMediaItems.get(i), QueueItem.UNKNOWN_ID, /* durationMs= */ C.TIME_UNSET));
    }
    queuedItemsBuilder.addAll(queuedMediaItems.subList(index, queuedMediaItems.size()));
    return new QueueTimeline(queuedItemsBuilder.build(), fakeQueuedMediaItem);
  }

  /**
   * Removes the range of media items in the current timeline.
   *
   * @param fromIndex The index to start removing items from.
   * @param toIndex The index up to which to remove items (exclusive).
   * @return A new {@link QueueTimeline} reflecting the update.
   */
  public QueueTimeline copyWithRemovedMediaItems(int fromIndex, int toIndex) {
    ImmutableList.Builder<QueuedMediaItem> queuedItemsBuilder = new ImmutableList.Builder<>();
    queuedItemsBuilder.addAll(queuedMediaItems.subList(0, fromIndex));
    queuedItemsBuilder.addAll(queuedMediaItems.subList(toIndex, queuedMediaItems.size()));
    return new QueueTimeline(queuedItemsBuilder.build(), fakeQueuedMediaItem);
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
    List<QueuedMediaItem> list = new ArrayList<>(queuedMediaItems);
    Util.moveItems(list, fromIndex, toIndex, newIndex);
    return new QueueTimeline(ImmutableList.copyOf(list), fakeQueuedMediaItem);
  }

  /** Returns whether the timeline contains the given {@link MediaItem}. */
  public boolean contains(MediaItem mediaItem) {
    if (fakeQueuedMediaItem != null && mediaItem.equals(fakeQueuedMediaItem.mediaItem)) {
      return true;
    }
    for (int i = 0; i < queuedMediaItems.size(); i++) {
      if (mediaItem.equals(queuedMediaItems.get(i).mediaItem)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public MediaItem getMediaItemAt(int mediaItemIndex) {
    return mediaItemIndex >= getWindowCount() ? null : getQueuedMediaItem(mediaItemIndex).mediaItem;
  }

  @Override
  public int getWindowCount() {
    return queuedMediaItems.size() + ((fakeQueuedMediaItem == null) ? 0 : 1);
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    QueuedMediaItem queuedMediaItem = getQueuedMediaItem(windowIndex);
    window.set(
        FAKE_WINDOW_UID,
        queuedMediaItem.mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* liveConfiguration= */ null,
        /* defaultPositionUs= */ 0,
        /* durationUs= */ msToUs(queuedMediaItem.durationMs),
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ 0);
    return window;
  }

  @Override
  public int getPeriodCount() {
    return getWindowCount();
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    QueuedMediaItem queuedMediaItem = getQueuedMediaItem(periodIndex);
    period.set(
        /* id= */ queuedMediaItem.queueId,
        /* uid= */ null,
        /* windowIndex= */ periodIndex,
        /* durationUs= */ msToUs(queuedMediaItem.durationMs),
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
    return Objects.equal(queuedMediaItems, other.queuedMediaItems)
        && Objects.equal(fakeQueuedMediaItem, other.fakeQueuedMediaItem);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(queuedMediaItems, fakeQueuedMediaItem);
  }

  private QueuedMediaItem getQueuedMediaItem(int index) {
    return index == queuedMediaItems.size() && fakeQueuedMediaItem != null
        ? fakeQueuedMediaItem
        : queuedMediaItems.get(index);
  }

  private static final class QueuedMediaItem {

    public final MediaItem mediaItem;
    public final long queueId;
    public final long durationMs;

    public QueuedMediaItem(MediaItem mediaItem, long queueId, long durationMs) {
      this.mediaItem = mediaItem;
      this.queueId = queueId;
      this.durationMs = durationMs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof QueuedMediaItem)) {
        return false;
      }
      QueuedMediaItem that = (QueuedMediaItem) o;
      return queueId == that.queueId
          && mediaItem.equals(that.mediaItem)
          && durationMs == that.durationMs;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + (int) (queueId ^ (queueId >>> 32));
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (int) (durationMs ^ (durationMs >>> 32));
      return result;
    }
  }
}

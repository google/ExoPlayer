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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link Timeline} for Cast receiver app media queues.
 *
 * <p>Each {@link MediaItem} in the timeline is exposed as a window. Unprepared media items are
 * exposed as an unset-duration {@link Window}, with a single unset-duration {@link Period}.
 */
/* package */ final class ExoCastTimeline extends Timeline {

  /** Opaque object that uniquely identifies a period across timeline changes. */
  public interface PeriodUid {}

  /** A timeline for an empty media queue. */
  public static final ExoCastTimeline EMPTY =
      createTimelineFor(
          Collections.emptyList(), Collections.emptyMap(), new ShuffleOrder.DefaultShuffleOrder(0));

  /**
   * Creates {@link PeriodUid} from the given arguments.
   *
   * @param itemUuid The UUID that identifies the item.
   * @param periodId The id of the period for which the unique identifier is required.
   * @return An opaque unique identifier for a period.
   */
  public static PeriodUid createPeriodUid(UUID itemUuid, Object periodId) {
    return new PeriodUidImpl(itemUuid, periodId);
  }

  /**
   * Returns a new timeline representing the given media queue information.
   *
   * @param mediaItems The media items conforming the timeline.
   * @param mediaItemInfoMap Maps {@link MediaItem media items} in {@code mediaItems} to a {@link
   *     MediaItemInfo} through their {@link MediaItem#uuid}. Media items may not have a {@link
   *     MediaItemInfo} mapped to them.
   * @param shuffleOrder The {@link ShuffleOrder} of the timeline. {@link ShuffleOrder#getLength()}
   *     must be equal to {@code mediaItems.size()}.
   * @return A new timeline representing the given media queue information.
   */
  public static ExoCastTimeline createTimelineFor(
      List<MediaItem> mediaItems,
      Map<UUID, MediaItemInfo> mediaItemInfoMap,
      ShuffleOrder shuffleOrder) {
    Assertions.checkArgument(mediaItems.size() == shuffleOrder.getLength());
    int[] accumulativePeriodCount = new int[mediaItems.size()];
    int periodCount = 0;
    for (int i = 0; i < accumulativePeriodCount.length; i++) {
      periodCount += getInfoOrEmpty(mediaItemInfoMap, mediaItems.get(i).uuid).periods.size();
      accumulativePeriodCount[i] = periodCount;
    }
    HashMap<UUID, Integer> uuidToIndex = new HashMap<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      uuidToIndex.put(mediaItems.get(i).uuid, i);
    }
    return new ExoCastTimeline(
        Collections.unmodifiableList(new ArrayList<>(mediaItems)),
        Collections.unmodifiableMap(new HashMap<>(mediaItemInfoMap)),
        Collections.unmodifiableMap(new HashMap<>(uuidToIndex)),
        shuffleOrder,
        accumulativePeriodCount);
  }

  // Timeline backing information.
  private final List<MediaItem> mediaItems;
  private final Map<UUID, MediaItemInfo> mediaItemInfoMap;
  private final ShuffleOrder shuffleOrder;

  // Precomputed for quick access.
  private final Map<UUID, Integer> uuidToIndex;
  private final int[] accumulativePeriodCount;

  private ExoCastTimeline(
      List<MediaItem> mediaItems,
      Map<UUID, MediaItemInfo> mediaItemInfoMap,
      Map<UUID, Integer> uuidToIndex,
      ShuffleOrder shuffleOrder,
      int[] accumulativePeriodCount) {
    this.mediaItems = mediaItems;
    this.mediaItemInfoMap = mediaItemInfoMap;
    this.uuidToIndex = uuidToIndex;
    this.shuffleOrder = shuffleOrder;
    this.accumulativePeriodCount = accumulativePeriodCount;
  }

  /**
   * Returns whether the given media queue information would produce a timeline equivalent to this
   * one.
   *
   * @see ExoCastTimeline#createTimelineFor(List, Map, ShuffleOrder)
   */
  public boolean representsMediaQueue(
      List<MediaItem> mediaItems,
      Map<UUID, MediaItemInfo> mediaItemInfoMap,
      ShuffleOrder shuffleOrder) {
    if (this.shuffleOrder.getLength() != shuffleOrder.getLength()) {
      return false;
    }

    int index = shuffleOrder.getFirstIndex();
    if (this.shuffleOrder.getFirstIndex() != index) {
      return false;
    }
    while (index != C.INDEX_UNSET) {
      int nextIndex = shuffleOrder.getNextIndex(index);
      if (nextIndex != this.shuffleOrder.getNextIndex(index)) {
        return false;
      }
      index = nextIndex;
    }

    if (mediaItems.size() != this.mediaItems.size()) {
      return false;
    }
    for (int i = 0; i < mediaItems.size(); i++) {
      UUID uuid = mediaItems.get(i).uuid;
      MediaItemInfo mediaItemInfo = getInfoOrEmpty(mediaItemInfoMap, uuid);
      if (!uuid.equals(this.mediaItems.get(i).uuid)
          || !mediaItemInfo.equals(getInfoOrEmpty(this.mediaItemInfoMap, uuid))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the index of the window that contains the period identified by the given {@code
   * periodUid} or {@link C#INDEX_UNSET} if this timeline does not contain any period with the given
   * {@code periodUid}.
   */
  public int getWindowIndexContainingPeriod(PeriodUid periodUid) {
    if (!(periodUid instanceof PeriodUidImpl)) {
      return C.INDEX_UNSET;
    }
    return getWindowIndexFromUuid(((PeriodUidImpl) periodUid).itemUuid);
  }

  /**
   * Returns the index of the window that represents the media item with the given {@code uuid} or
   * {@link C#INDEX_UNSET} if no item in this timeline has the given {@code uuid}.
   */
  public int getWindowIndexFromUuid(UUID uuid) {
    Integer index = uuidToIndex.get(uuid);
    return index != null ? index : C.INDEX_UNSET;
  }

  // Timeline implementation.

  @Override
  public int getWindowCount() {
    return mediaItems.size();
  }

  @Override
  public Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    MediaItem mediaItem = mediaItems.get(windowIndex);
    MediaItemInfo mediaItemInfo = getInfoOrEmpty(mediaItemInfoMap, mediaItem.uuid);
    return window.set(
        /* tag= */ setTag ? mediaItem.attachment : null,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* isSeekable= */ mediaItemInfo.isSeekable,
        /* isDynamic= */ mediaItemInfo.isDynamic,
        /* defaultPositionUs= */ mediaItemInfo.defaultStartPositionUs,
        /* durationUs= */ mediaItemInfo.windowDurationUs,
        /* firstPeriodIndex= */ windowIndex == 0 ? 0 : accumulativePeriodCount[windowIndex - 1],
        /* lastPeriodIndex= */ accumulativePeriodCount[windowIndex] - 1,
        mediaItemInfo.positionInFirstPeriodUs);
  }

  @Override
  public int getPeriodCount() {
    return mediaItems.isEmpty() ? 0 : accumulativePeriodCount[accumulativePeriodCount.length - 1];
  }

  @Override
  public Period getPeriodByUid(Object periodUidObject, Period period) {
    return getPeriodInternal((PeriodUidImpl) periodUidObject, period, /* setIds= */ true);
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    return getPeriodInternal((PeriodUidImpl) getUidOfPeriod(periodIndex), period, setIds);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof PeriodUidImpl)) {
      return C.INDEX_UNSET;
    }
    PeriodUidImpl periodUid = (PeriodUidImpl) uid;
    UUID uuid = periodUid.itemUuid;
    Integer itemIndex = uuidToIndex.get(uuid);
    if (itemIndex == null) {
      return C.INDEX_UNSET;
    }
    int indexOfPeriodInItem =
        getInfoOrEmpty(mediaItemInfoMap, uuid).getIndexOfPeriod(periodUid.periodId);
    if (indexOfPeriodInItem == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    return indexOfPeriodInItem + (itemIndex == 0 ? 0 : accumulativePeriodCount[itemIndex - 1]);
  }

  @Override
  public PeriodUid getUidOfPeriod(int periodIndex) {
    int mediaItemIndex = getMediaItemIndexForPeriodIndex(periodIndex);
    int periodIndexInMediaItem =
        periodIndex - (mediaItemIndex > 0 ? accumulativePeriodCount[mediaItemIndex - 1] : 0);
    UUID uuid = mediaItems.get(mediaItemIndex).uuid;
    MediaItemInfo mediaItemInfo = getInfoOrEmpty(mediaItemInfoMap, uuid);
    return new PeriodUidImpl(uuid, mediaItemInfo.periods.get(periodIndexInMediaItem).id);
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return shuffleModeEnabled ? shuffleOrder.getFirstIndex() : 0;
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return shuffleModeEnabled ? shuffleOrder.getLastIndex() : mediaItems.size() - 1;
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    } else if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_OFF
          ? C.INDEX_UNSET
          : getLastWindowIndex(shuffleModeEnabled);
    } else if (shuffleModeEnabled) {
      return shuffleOrder.getPreviousIndex(windowIndex);
    } else {
      return windowIndex - 1;
    }
  }

  @Override
  public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
    if (repeatMode == Player.REPEAT_MODE_ONE) {
      return windowIndex;
    } else if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
      return repeatMode == Player.REPEAT_MODE_OFF
          ? C.INDEX_UNSET
          : getFirstWindowIndex(shuffleModeEnabled);
    } else if (shuffleModeEnabled) {
      return shuffleOrder.getNextIndex(windowIndex);
    } else {
      return windowIndex + 1;
    }
  }

  // Internal methods.

  private Period getPeriodInternal(PeriodUidImpl uid, Period period, boolean setIds) {
    UUID uuid = uid.itemUuid;
    int itemIndex = Assertions.checkNotNull(uuidToIndex.get(uuid));
    MediaItemInfo mediaItemInfo = getInfoOrEmpty(mediaItemInfoMap, uuid);
    MediaItemInfo.Period mediaInfoPeriod =
        mediaItemInfo.periods.get(mediaItemInfo.getIndexOfPeriod(uid.periodId));
    return period.set(
        setIds ? mediaInfoPeriod.id : null,
        setIds ? uid : null,
        /* windowIndex= */ itemIndex,
        mediaInfoPeriod.durationUs,
        mediaInfoPeriod.positionInWindowUs);
  }

  private int getMediaItemIndexForPeriodIndex(int periodIndex) {
    return Util.binarySearchCeil(
        accumulativePeriodCount, periodIndex, /* inclusive= */ false, /* stayInBounds= */ false);
  }

  private static MediaItemInfo getInfoOrEmpty(Map<UUID, MediaItemInfo> map, UUID uuid) {
    MediaItemInfo info = map.get(uuid);
    return info != null ? info : MediaItemInfo.EMPTY;
  }

  // Internal classes.

  private static final class PeriodUidImpl implements PeriodUid {

    public final UUID itemUuid;
    public final Object periodId;

    private PeriodUidImpl(UUID itemUuid, Object periodId) {
      this.itemUuid = itemUuid;
      this.periodId = periodId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      PeriodUidImpl periodUid = (PeriodUidImpl) other;
      return itemUuid.equals(periodUid.itemUuid) && periodId.equals(periodUid.periodId);
    }

    @Override
    public int hashCode() {
      int result = itemUuid.hashCode();
      result = 31 * result + periodId.hashCode();
      return result;
    }
  }
}

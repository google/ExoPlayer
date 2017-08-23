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
package com.google.android.exoplayer2.source;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;

/**
 * Abstract base class for the concatenation of one or more {@link Timeline}s.
 */
/* package */ abstract class AbstractConcatenatedTimeline extends Timeline {

  private final int childCount;
  private final ShuffleOrder shuffleOrder;

  /**
   * Sets up a concatenated timeline with a shuffle order of child timelines.
   *
   * @param shuffleOrder A shuffle order of child timelines. The number of child timelines must
   *     match the number of elements in the shuffle order.
   */
  public AbstractConcatenatedTimeline(ShuffleOrder shuffleOrder) {
    this.shuffleOrder = shuffleOrder;
    this.childCount = shuffleOrder.getLength();
  }

  @Override
  public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int nextWindowIndexInChild = getTimelineByChildIndex(childIndex).getNextWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_OFF : repeatMode,
        shuffleModeEnabled);
    if (nextWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + nextWindowIndexInChild;
    }
    int nextChildIndex = shuffleModeEnabled ? shuffleOrder.getNextIndex(childIndex)
        : childIndex + 1;
    if (nextChildIndex != C.INDEX_UNSET && nextChildIndex < childCount) {
      return getFirstWindowIndexByChildIndex(nextChildIndex)
          + getTimelineByChildIndex(nextChildIndex).getFirstWindowIndex(shuffleModeEnabled);
    }
    if (repeatMode == Player.REPEAT_MODE_ALL) {
      return getFirstWindowIndex(shuffleModeEnabled);
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int previousWindowIndexInChild = getTimelineByChildIndex(childIndex).getPreviousWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_OFF : repeatMode,
        shuffleModeEnabled);
    if (previousWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + previousWindowIndexInChild;
    }
    int previousChildIndex = shuffleModeEnabled ? shuffleOrder.getPreviousIndex(childIndex)
        : childIndex - 1;
    if (previousChildIndex != C.INDEX_UNSET && previousChildIndex >= 0) {
      return getFirstWindowIndexByChildIndex(previousChildIndex)
          + getTimelineByChildIndex(previousChildIndex).getLastWindowIndex(shuffleModeEnabled);
    }
    if (repeatMode == Player.REPEAT_MODE_ALL) {
      return getLastWindowIndex(shuffleModeEnabled);
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    int lastChildIndex = shuffleModeEnabled ? shuffleOrder.getLastIndex() : childCount - 1;
    return getFirstWindowIndexByChildIndex(lastChildIndex)
        + getTimelineByChildIndex(lastChildIndex).getLastWindowIndex(shuffleModeEnabled);
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    int firstChildIndex = shuffleModeEnabled ? shuffleOrder.getFirstIndex() : 0;
    return getFirstWindowIndexByChildIndex(firstChildIndex)
        + getTimelineByChildIndex(firstChildIndex).getFirstWindowIndex(shuffleModeEnabled);
  }

  @Override
  public final Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexByChildIndex(childIndex);
    getTimelineByChildIndex(childIndex).getWindow(windowIndex - firstWindowIndexInChild, window,
        setIds, defaultPositionProjectionUs);
    window.firstPeriodIndex += firstPeriodIndexInChild;
    window.lastPeriodIndex += firstPeriodIndexInChild;
    return window;
  }

  @Override
  public final Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int childIndex = getChildIndexByPeriodIndex(periodIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexByChildIndex(childIndex);
    getTimelineByChildIndex(childIndex).getPeriod(periodIndex - firstPeriodIndexInChild, period,
        setIds);
    period.windowIndex += firstWindowIndexInChild;
    if (setIds) {
      period.uid = Pair.create(getChildUidByChildIndex(childIndex), period.uid);
    }
    return period;
  }

  @Override
  public final int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Pair)) {
      return C.INDEX_UNSET;
    }
    Pair<?, ?> childUidAndPeriodUid = (Pair<?, ?>) uid;
    Object childUid = childUidAndPeriodUid.first;
    Object periodUid = childUidAndPeriodUid.second;
    int childIndex = getChildIndexByChildUid(childUid);
    if (childIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int periodIndexInChild = getTimelineByChildIndex(childIndex).getIndexOfPeriod(periodUid);
    return periodIndexInChild == C.INDEX_UNSET ? C.INDEX_UNSET
        : getFirstPeriodIndexByChildIndex(childIndex) + periodIndexInChild;
  }

  /**
   * Returns the index of the child timeline containing the given period index.
   *
   * @param periodIndex A valid period index within the bounds of the timeline.
   */
  protected abstract int getChildIndexByPeriodIndex(int periodIndex);

  /**
   * Returns the index of the child timeline containing the given window index.
   *
   * @param windowIndex A valid window index within the bounds of the timeline.
   */
  protected abstract int getChildIndexByWindowIndex(int windowIndex);

  /**
   * Returns the index of the child timeline with the given UID or {@link C#INDEX_UNSET} if not
   * found.
   *
   * @param childUid A child UID.
   * @return Index of child timeline or {@link C#INDEX_UNSET} if UID was not found.
   */
  protected abstract int getChildIndexByChildUid(Object childUid);

  /**
   * Returns the child timeline for the child with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract Timeline getTimelineByChildIndex(int childIndex);

  /**
   * Returns the first period index belonging to the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract int getFirstPeriodIndexByChildIndex(int childIndex);

  /**
   * Returns the first window index belonging to the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract int getFirstWindowIndexByChildIndex(int childIndex);

  /**
   * Returns the UID of the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract Object getChildUidByChildIndex(int childIndex);

}

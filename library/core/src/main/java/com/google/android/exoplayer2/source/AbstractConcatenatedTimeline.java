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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;

/**
 * Abstract base class for the concatenation of one or more {@link Timeline}s.
 */
/* package */ abstract class AbstractConcatenatedTimeline extends Timeline {

  /**
   * Meta data of a child timeline.
   */
  protected static final class ChildDataHolder {

    /**
     * Child timeline.
     */
    public Timeline timeline;

    /**
     * First period index belonging to the child timeline.
     */
    public int firstPeriodIndexInChild;

    /**
     * First window index belonging to the child timeline.
     */
    public int firstWindowIndexInChild;

    /**
     * UID of child timeline.
     */
    public Object uid;

    /**
     * Set child holder data.
     *
     * @param timeline Child timeline.
     * @param firstPeriodIndexInChild First period index belonging to the child timeline.
     * @param firstWindowIndexInChild First window index belonging to the child timeline.
     * @param uid UID of child timeline.
     */
    public void setData(Timeline timeline, int firstPeriodIndexInChild, int firstWindowIndexInChild,
        Object uid) {
      this.timeline = timeline;
      this.firstPeriodIndexInChild = firstPeriodIndexInChild;
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.uid = uid;
    }

  }

  private final ChildDataHolder childDataHolder;

  public AbstractConcatenatedTimeline() {
    childDataHolder = new ChildDataHolder();
  }

  @Override
  public int getNextWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    getChildDataByWindowIndex(windowIndex, childDataHolder);
    int firstWindowIndexInChild = childDataHolder.firstWindowIndexInChild;
    int nextWindowIndexInChild = childDataHolder.timeline.getNextWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == ExoPlayer.REPEAT_MODE_ALL ? ExoPlayer.REPEAT_MODE_OFF : repeatMode);
    if (nextWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + nextWindowIndexInChild;
    } else {
      firstWindowIndexInChild += childDataHolder.timeline.getWindowCount();
      if (firstWindowIndexInChild < getWindowCount()) {
        return firstWindowIndexInChild;
      } else if (repeatMode == ExoPlayer.REPEAT_MODE_ALL) {
        return 0;
      } else {
        return C.INDEX_UNSET;
      }
    }
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    getChildDataByWindowIndex(windowIndex, childDataHolder);
    int firstWindowIndexInChild = childDataHolder.firstWindowIndexInChild;
    int previousWindowIndexInChild = childDataHolder.timeline.getPreviousWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == ExoPlayer.REPEAT_MODE_ALL ? ExoPlayer.REPEAT_MODE_OFF : repeatMode);
    if (previousWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + previousWindowIndexInChild;
    } else {
      if (firstWindowIndexInChild > 0) {
        return firstWindowIndexInChild - 1;
      } else if (repeatMode == ExoPlayer.REPEAT_MODE_ALL) {
        return getWindowCount() - 1;
      } else {
        return C.INDEX_UNSET;
      }
    }
  }

  @Override
  public final Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    getChildDataByWindowIndex(windowIndex, childDataHolder);
    int firstWindowIndexInChild = childDataHolder.firstWindowIndexInChild;
    int firstPeriodIndexInChild = childDataHolder.firstPeriodIndexInChild;
    childDataHolder.timeline.getWindow(windowIndex - firstWindowIndexInChild, window, setIds,
        defaultPositionProjectionUs);
    window.firstPeriodIndex += firstPeriodIndexInChild;
    window.lastPeriodIndex += firstPeriodIndexInChild;
    return window;
  }

  @Override
  public final Period getPeriod(int periodIndex, Period period, boolean setIds) {
    getChildDataByPeriodIndex(periodIndex, childDataHolder);
    int firstWindowIndexInChild = childDataHolder.firstWindowIndexInChild;
    int firstPeriodIndexInChild = childDataHolder.firstPeriodIndexInChild;
    childDataHolder.timeline.getPeriod(periodIndex - firstPeriodIndexInChild, period, setIds);
    period.windowIndex += firstWindowIndexInChild;
    if (setIds) {
      period.uid = Pair.create(childDataHolder.uid, period.uid);
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
    if (!getChildDataByChildUid(childUid, childDataHolder)) {
      return C.INDEX_UNSET;
    }
    int periodIndexInChild = childDataHolder.timeline.getIndexOfPeriod(periodUid);
    return periodIndexInChild == C.INDEX_UNSET ? C.INDEX_UNSET
        : childDataHolder.firstPeriodIndexInChild + periodIndexInChild;
  }

  /**
   * Populates {@link ChildDataHolder} for the child timeline containing the given period index.
   *
   * @param periodIndex A valid period index within the bounds of the timeline.
   * @param childData A data holder to be populated.
   */
  protected abstract void getChildDataByPeriodIndex(int periodIndex, ChildDataHolder childData);

  /**
   * Populates {@link ChildDataHolder} for the child timeline containing the given window index.
   *
   * @param windowIndex A valid window index within the bounds of the timeline.
   * @param childData A data holder to be populated.
   */
  protected abstract void getChildDataByWindowIndex(int windowIndex, ChildDataHolder childData);

  /**
   * Populates {@link ChildDataHolder} for the child timeline with the given UID.
   *
   * @param childUid A child UID.
   * @param childData A data holder to be populated.
   * @return Whether a child with the given UID was found.
   */
  protected abstract boolean getChildDataByChildUid(Object childUid, ChildDataHolder childData);

}

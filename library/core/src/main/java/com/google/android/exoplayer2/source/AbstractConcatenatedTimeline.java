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

  @Override
  public int getNextWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    int childIndex = getChildIndexForWindow(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
    int nextWindowIndexInChild = getChild(childIndex).getNextWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == ExoPlayer.REPEAT_MODE_ALL ? ExoPlayer.REPEAT_MODE_OFF : repeatMode);
    if (nextWindowIndexInChild == C.INDEX_UNSET) {
      if (childIndex < getChildCount() - 1) {
        childIndex++;
      } else if (repeatMode == ExoPlayer.REPEAT_MODE_ALL) {
        childIndex = 0;
      } else {
        return C.INDEX_UNSET;
      }
      firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
      nextWindowIndexInChild = 0;
    }
    return firstWindowIndexInChild + nextWindowIndexInChild;
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
    int childIndex = getChildIndexForWindow(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
    int previousWindowIndexInChild = getChild(childIndex).getPreviousWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == ExoPlayer.REPEAT_MODE_ALL ? ExoPlayer.REPEAT_MODE_OFF : repeatMode);
    if (previousWindowIndexInChild == C.INDEX_UNSET) {
      if (childIndex > 0) {
        childIndex--;
      } else if (repeatMode == ExoPlayer.REPEAT_MODE_ALL) {
        childIndex = getChildCount() - 1;
      } else {
        return C.INDEX_UNSET;
      }
      firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
      previousWindowIndexInChild = getChild(childIndex).getWindowCount() - 1;
    }
    return firstWindowIndexInChild + previousWindowIndexInChild;
  }

  @Override
  public final Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    int childIndex = getChildIndexForWindow(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexInChild(childIndex);
    getChild(childIndex).getWindow(windowIndex - firstWindowIndexInChild, window, setIds,
        defaultPositionProjectionUs);
    window.firstPeriodIndex += firstPeriodIndexInChild;
    window.lastPeriodIndex += firstPeriodIndexInChild;
    return window;
  }

  @Override
  public final Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int childIndex = getChildIndexForPeriod(periodIndex);
    int firstWindowIndexInChild = getFirstWindowIndexInChild(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexInChild(childIndex);
    getChild(childIndex).getPeriod(periodIndex - firstPeriodIndexInChild, period, setIds);
    period.windowIndex += firstWindowIndexInChild;
    if (setIds) {
      period.uid = Pair.create(childIndex, period.uid);
    }
    return period;
  }

  @Override
  public final int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Pair)) {
      return C.INDEX_UNSET;
    }
    Pair<?, ?> childIndexAndPeriodId = (Pair<?, ?>) uid;
    if (!(childIndexAndPeriodId.first instanceof Integer)) {
      return C.INDEX_UNSET;
    }
    int childIndex = (Integer) childIndexAndPeriodId.first;
    Object periodId = childIndexAndPeriodId.second;
    if (childIndex < 0 || childIndex >= getChildCount()) {
      return C.INDEX_UNSET;
    }
    int periodIndexInChild = getChild(childIndex).getIndexOfPeriod(periodId);
    return periodIndexInChild == C.INDEX_UNSET ? C.INDEX_UNSET
        : getFirstPeriodIndexInChild(childIndex) + periodIndexInChild;
  }

  /**
   * Returns the number of concatenated child timelines.
   */
  protected abstract int getChildCount();

  /**
   * Returns a child timeline by index.
   */
  protected abstract Timeline getChild(int childIndex);

  /**
   * Returns the index of the child timeline to which the period with the given index belongs.
   */
  protected abstract int getChildIndexForPeriod(int periodIndex);

  /**
   * Returns the first period index belonging to the child timeline with the given index.
   */
  protected abstract int getFirstPeriodIndexInChild(int childIndex);

  /**
   * Returns the index of the child timeline to which the window with the given index belongs.
   */
  protected abstract int getChildIndexForWindow(int windowIndex);

  /**
   * Returns the first window index belonging to the child timeline with the given index.
   */
  protected abstract int getFirstWindowIndexInChild(int childIndex);

}

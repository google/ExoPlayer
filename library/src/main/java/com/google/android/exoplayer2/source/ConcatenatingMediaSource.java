/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Window;
import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Concatenates multiple {@link MediaSource}s.
 */
public final class ConcatenatingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;
  private final Object[] manifests;
  private final Map<MediaPeriod, Integer> sourceIndexByMediaPeriod;

  private ConcatenatedTimeline timeline;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    timelines = new Timeline[mediaSources.length];
    manifests = new Object[mediaSources.length];
    sourceIndexByMediaPeriod = new HashMap<>();
  }

  @Override
  public void prepareSource(final Listener listener) {
    for (int i = 0; i < mediaSources.length; i++) {
      final int index = i;
      mediaSources[i].prepareSource(new Listener() {

        @Override
        public void onSourceInfoRefreshed(Timeline sourceTimeline, Object manifest) {
          timelines[index] = sourceTimeline;
          manifests[index] = manifest;
          for (Timeline timeline : timelines) {
            if (timeline == null) {
              // Don't invoke the listener until all sources have timelines.
              return;
            }
          }
          timeline = new ConcatenatedTimeline(timelines.clone());
          listener.onSourceInfoRefreshed(timeline, manifests.clone());
        }

      });
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  public MediaPeriod createPeriod(int index, Callback callback, Allocator allocator,
      long positionUs) {
    int sourceIndex = timeline.getSourceIndexForPeriod(index);
    int periodIndexInSource = index - timeline.getFirstPeriodIndexInSource(sourceIndex);
    MediaPeriod mediaPeriod = mediaSources[sourceIndex].createPeriod(periodIndexInSource, callback,
        allocator, positionUs);
    sourceIndexByMediaPeriod.put(mediaPeriod, sourceIndex);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    int sourceIndex = sourceIndexByMediaPeriod.get(mediaPeriod);
    sourceIndexByMediaPeriod.remove(mediaPeriod);
    mediaSources[sourceIndex].releasePeriod(mediaPeriod);
  }

  @Override
  public void releaseSource() {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.releaseSource();
    }
  }

  /**
   * A {@link Timeline} that is the concatenation of one or more {@link Timeline}s.
   */
  private static final class ConcatenatedTimeline implements Timeline {

    private final Timeline[] timelines;
    private final int[] sourcePeriodOffsets;
    private final int[] sourceWindowOffsets;

    public ConcatenatedTimeline(Timeline[] timelines) {
      int[] sourcePeriodOffsets = new int[timelines.length];
      int[] sourceWindowOffsets = new int[timelines.length];
      int periodCount = 0;
      int windowCount = 0;
      for (int i = 0; i < timelines.length; i++) {
        Timeline timeline = timelines[i];
        periodCount += timeline.getPeriodCount();
        sourcePeriodOffsets[i] = periodCount;
        windowCount += timeline.getWindowCount();
        sourceWindowOffsets[i] = windowCount;
      }
      this.timelines = timelines;
      this.sourcePeriodOffsets = sourcePeriodOffsets;
      this.sourceWindowOffsets = sourceWindowOffsets;
    }

    @Override
    public long getAbsoluteStartTime() {
      return timelines[0].getAbsoluteStartTime();
    }

    @Override
    public int getPeriodCount() {
      return sourcePeriodOffsets[sourcePeriodOffsets.length - 1];
    }

    @Override
    public long getPeriodDurationMs(int periodIndex) {
      int sourceIndex = getSourceIndexForPeriod(periodIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      return timelines[sourceIndex].getPeriodDurationMs(periodIndex - firstPeriodIndexInSource);
    }

    @Override
    public long getPeriodDurationUs(int periodIndex) {
      int sourceIndex = getSourceIndexForPeriod(periodIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      return timelines[sourceIndex].getPeriodDurationUs(periodIndex - firstPeriodIndexInSource);
    }

    @Override
    public Object getPeriodId(int periodIndex) {
      int sourceIndex = getSourceIndexForPeriod(periodIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(periodIndex);
      Object periodId = timelines[sourceIndex].getPeriodId(periodIndex - firstPeriodIndexInSource);
      return Pair.create(sourceIndex, periodId);
    }

    @Override
    public Window getPeriodWindow(int periodIndex) {
      return getWindow(getPeriodWindowIndex(periodIndex));
    }

    @Override
    public int getPeriodWindowIndex(int periodIndex) {
      int sourceIndex = getSourceIndexForPeriod(periodIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(periodIndex);
      return (sourceIndex > 0 ? sourceWindowOffsets[sourceIndex - 1] : 0)
          + timelines[sourceIndex].getPeriodWindowIndex(periodIndex - firstPeriodIndexInSource);
    }

    @Override
    public int getIndexOfPeriod(Object id) {
      if (!(id instanceof Pair)) {
        return NO_PERIOD_INDEX;
      }
      Pair sourceIndexAndPeriodId = (Pair) id;
      if (!(sourceIndexAndPeriodId.first instanceof Integer)) {
        return NO_PERIOD_INDEX;
      }
      int sourceIndex = (int) sourceIndexAndPeriodId.first;
      Object periodId = sourceIndexAndPeriodId.second;
      if (sourceIndex < 0 || sourceIndex >= timelines.length) {
        return NO_PERIOD_INDEX;
      }
      int periodIndexInSource = timelines[sourceIndex].getIndexOfPeriod(periodId);
      return periodIndexInSource == NO_PERIOD_INDEX ? NO_PERIOD_INDEX
          : getFirstPeriodIndexInSource(sourceIndex) + periodIndexInSource;
    }

    @Override
    public int getWindowCount() {
      return sourceWindowOffsets[sourceWindowOffsets.length - 1];
    }

    @Override
    public Window getWindow(int windowIndex) {
      int sourceIndex = getSourceIndexForWindow(windowIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      return timelines[sourceIndex].getWindow(windowIndex - firstWindowIndexInSource);
    }

    @Override
    public int getWindowFirstPeriodIndex(int windowIndex) {
      int sourceIndex = getSourceIndexForWindow(windowIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      return firstPeriodIndexInSource + timelines[sourceIndex].getWindowFirstPeriodIndex(
          windowIndex - firstWindowIndexInSource);
    }

    @Override
    public int getWindowLastPeriodIndex(int windowIndex) {
      int sourceIndex = getSourceIndexForWindow(windowIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      return firstPeriodIndexInSource + timelines[sourceIndex].getWindowLastPeriodIndex(
          windowIndex - firstWindowIndexInSource);
    }

    @Override
    public long getWindowOffsetInFirstPeriodUs(int windowIndex) {
      int sourceIndex = getSourceIndexForWindow(windowIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      return timelines[sourceIndex].getWindowOffsetInFirstPeriodUs(
          windowIndex - firstWindowIndexInSource);
    }

    private int getSourceIndexForPeriod(int periodIndex) {
      return Util.binarySearchFloor(sourcePeriodOffsets, periodIndex, true, false) + 1;
    }

    private int getFirstPeriodIndexInSource(int sourceIndex) {
      return sourceIndex == 0 ? 0 : sourcePeriodOffsets[sourceIndex - 1];
    }

    private int getSourceIndexForWindow(int windowIndex) {
      return Util.binarySearchFloor(sourceWindowOffsets, windowIndex, true, false) + 1;
    }

    private int getFirstWindowIndexInSource(int sourceIndex) {
      return sourceIndex == 0 ? 0 : sourceWindowOffsets[sourceIndex - 1];
    }

  }

}

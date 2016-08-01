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

import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;

/**
 * Concatenates multiple {@link MediaSource}s.
 */
public final class ConcatenatingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;

  private ConcatenatedTimeline timeline;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    timelines = new Timeline[mediaSources.length];
  }

  @Override
  public void prepareSource(final InvalidationListener listener) {
    for (int i = 0; i < mediaSources.length; i++) {
      final int index = i;
      mediaSources[i].prepareSource(new InvalidationListener() {
        @Override
        public void onTimelineChanged(Timeline timeline) {
          timelines[index] = timeline;
          ConcatenatingMediaSource.this.timeline = new ConcatenatedTimeline(timelines.clone());
          listener.onTimelineChanged(ConcatenatingMediaSource.this.timeline);
        }
      });
    }
  }

  @Override
  public int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldConcatenatedTimeline)
      throws IOException {
    ConcatenatedTimeline oldTimeline = (ConcatenatedTimeline) oldConcatenatedTimeline;
    int sourceIndex = oldTimeline.getSourceIndexForPeriod(oldPlayingPeriodIndex);
    int oldFirstPeriodIndex = oldTimeline.getFirstPeriodIndexInSource(sourceIndex);
    int firstPeriodIndex = timeline.getFirstPeriodIndexInSource(sourceIndex);
    return firstPeriodIndex == Timeline.NO_PERIOD_INDEX ? Timeline.NO_PERIOD_INDEX
        : firstPeriodIndex + mediaSources[sourceIndex].getNewPlayingPeriodIndex(
            oldPlayingPeriodIndex - oldFirstPeriodIndex, oldTimeline.timelines[sourceIndex]);
  }

  @Override
  public Position getDefaultStartPosition(int index) {
    int sourceIndex = timeline.getSourceIndexForPeriod(index);
    int sourceFirstPeriodIndex = timeline.getFirstPeriodIndexInSource(sourceIndex);
    Position defaultStartPosition =
        mediaSources[sourceIndex].getDefaultStartPosition(index - sourceFirstPeriodIndex);
    return new Position(defaultStartPosition.periodIndex + sourceFirstPeriodIndex,
        defaultStartPosition.positionUs);
  }

  @Override
  public MediaPeriod createPeriod(int index) throws IOException {
    int sourceIndex = timeline.getSourceIndexForPeriod(index);
    int periodIndexInSource = index - timeline.getFirstPeriodIndexInSource(sourceIndex);
    return mediaSources[sourceIndex].createPeriod(periodIndexInSource);
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
    private final Object[] manifests;
    private final int count;
    private final boolean isFinal;
    private int[] sourceOffsets;

    public ConcatenatedTimeline(Timeline[] timelines) {
      this.timelines = timelines;

      int[] sourceOffsets = new int[timelines.length];
      int sourceIndexOffset = 0;
      for (int i = 0; i < timelines.length; i++) {
        Timeline manifest = timelines[i];
        int periodCount;
        if (manifest == null
            || (periodCount = manifest.getPeriodCount()) == Timeline.UNKNOWN_PERIOD_COUNT) {
          sourceOffsets = Arrays.copyOf(sourceOffsets, i);
          break;
        }
        sourceIndexOffset += periodCount;
        sourceOffsets[i] = sourceIndexOffset;
      }
      this.sourceOffsets = sourceOffsets;
      count = sourceOffsets.length == timelines.length ? sourceOffsets[sourceOffsets.length - 1]
          : UNKNOWN_PERIOD_COUNT;
      boolean isFinal = true;
      manifests = new Object[timelines.length];
      for (int i = 0; i < timelines.length; i++) {
        Timeline timeline = timelines[i];
        if (timeline != null) {
          manifests[i] = timeline.getManifest();
          if (!timeline.isFinal()) {
            isFinal = false;
          }
        }
      }
      this.isFinal = isFinal;
    }

    @Override
    public int getPeriodCount() {
      return count;
    }

    @Override
    public boolean isFinal() {
      return isFinal;
    }

    @Override
    public long getPeriodDuration(int index) {
      int sourceIndex = getSourceIndexForPeriod(index);
      return timelines[sourceIndex].getPeriodDuration(sourceIndex);
    }

    @Override
    public Object getPeriodId(int index) {
      int sourceIndex = getSourceIndexForPeriod(index);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(index);
      return timelines[sourceIndex].getPeriodId(index - firstPeriodIndexInSource);
    }

    @Override
    public int getIndexOfPeriod(Object id) {
      for (int sourceIndex = 0; sourceIndex < timelines.length; sourceIndex++) {
        int periodIndexInSource = timelines[sourceIndex].getIndexOfPeriod(id);
        if (periodIndexInSource != NO_PERIOD_INDEX) {
          int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
          return firstPeriodIndexInSource + periodIndexInSource;
        }
      }
      return NO_PERIOD_INDEX;
    }

    @Override
    public Object getManifest() {
      return manifests;
    }

    private int getSourceIndexForPeriod(int periodIndex) {
      return Util.binarySearchFloor(sourceOffsets, periodIndex, true, false) + 1;
    }

    private int getFirstPeriodIndexInSource(int sourceIndex) {
      return sourceIndex == 0 ? 0 : sourceIndex > sourceOffsets.length
          ? Timeline.NO_PERIOD_INDEX : sourceOffsets[sourceIndex - 1];
    }

  }

}

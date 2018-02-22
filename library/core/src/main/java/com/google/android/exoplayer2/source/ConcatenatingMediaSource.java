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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Concatenates multiple {@link MediaSource}s. It is valid for the same {@link MediaSource} instance
 * to be present more than once in the concatenation.
 */
public final class ConcatenatingMediaSource extends CompositeMediaSource<Integer> {

  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;
  private final Object[] manifests;
  private final Map<MediaPeriod, Integer> sourceIndexByMediaPeriod;
  private final boolean isAtomic;
  private final ShuffleOrder shuffleOrder;

  private Listener listener;
  private ConcatenatedTimeline timeline;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this(false, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenated media source shall be treated as atomic,
   *     i.e., treated as a single item for repeating and shuffling.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isAtomic, MediaSource... mediaSources) {
    this(isAtomic, new DefaultShuffleOrder(mediaSources.length), mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenated media source shall be treated as atomic,
   *     i.e., treated as a single item for repeating and shuffling.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources. The
   *     number of elements in the shuffle order must match the number of concatenated
   *     {@link MediaSource}s.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isAtomic, ShuffleOrder shuffleOrder,
      MediaSource... mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    Assertions.checkArgument(shuffleOrder.getLength() == mediaSources.length);
    this.mediaSources = mediaSources;
    this.isAtomic = isAtomic;
    this.shuffleOrder = shuffleOrder;
    timelines = new Timeline[mediaSources.length];
    manifests = new Object[mediaSources.length];
    sourceIndexByMediaPeriod = new HashMap<>();
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    super.prepareSource(player, isTopLevelSource, listener);
    this.listener = listener;
    boolean[] duplicateFlags = buildDuplicateFlags(mediaSources);
    if (mediaSources.length == 0) {
      listener.onSourceInfoRefreshed(this, Timeline.EMPTY, null);
    } else {
      for (int i = 0; i < mediaSources.length; i++) {
        if (!duplicateFlags[i]) {
          prepareChildSource(i, mediaSources[i]);
        }
      }
    }
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    int sourceIndex = timeline.getChildIndexByPeriodIndex(id.periodIndex);
    MediaPeriodId periodIdInSource = id.copyWithPeriodIndex(
        id.periodIndex - timeline.getFirstPeriodIndexByChildIndex(sourceIndex));
    MediaPeriod mediaPeriod = mediaSources[sourceIndex].createPeriod(periodIdInSource, allocator);
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
    super.releaseSource();
    listener = null;
    timeline = null;
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Integer sourceFirstIndex,
      MediaSource mediaSource,
      Timeline sourceTimeline,
      @Nullable Object sourceManifest) {
    // Set the timeline and manifest.
    timelines[sourceFirstIndex] = sourceTimeline;
    manifests[sourceFirstIndex] = sourceManifest;
    // Also set the timeline and manifest for any duplicate entries of the same source.
    for (int i = sourceFirstIndex + 1; i < mediaSources.length; i++) {
      if (mediaSources[i] == mediaSource) {
        timelines[i] = sourceTimeline;
        manifests[i] = sourceManifest;
      }
    }
    for (Timeline timeline : timelines) {
      if (timeline == null) {
        // Don't invoke the listener until all sources have timelines.
        return;
      }
    }
    timeline = new ConcatenatedTimeline(timelines.clone(), isAtomic, shuffleOrder);
    listener.onSourceInfoRefreshed(this, timeline, manifests.clone());
  }

  private static boolean[] buildDuplicateFlags(MediaSource[] mediaSources) {
    boolean[] duplicateFlags = new boolean[mediaSources.length];
    IdentityHashMap<MediaSource, Void> sources = new IdentityHashMap<>(mediaSources.length);
    for (int i = 0; i < mediaSources.length; i++) {
      MediaSource source = mediaSources[i];
      if (!sources.containsKey(source)) {
        sources.put(source, null);
      } else {
        duplicateFlags[i] = true;
      }
    }
    return duplicateFlags;
  }

  /**
   * A {@link Timeline} that is the concatenation of one or more {@link Timeline}s.
   */
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final Timeline[] timelines;
    private final int[] sourcePeriodOffsets;
    private final int[] sourceWindowOffsets;

    public ConcatenatedTimeline(Timeline[] timelines, boolean isAtomic, ShuffleOrder shuffleOrder) {
      super(isAtomic, shuffleOrder);
      int[] sourcePeriodOffsets = new int[timelines.length];
      int[] sourceWindowOffsets = new int[timelines.length];
      long periodCount = 0;
      int windowCount = 0;
      for (int i = 0; i < timelines.length; i++) {
        Timeline timeline = timelines[i];
        periodCount += timeline.getPeriodCount();
        Assertions.checkState(periodCount <= Integer.MAX_VALUE,
            "ConcatenatingMediaSource children contain too many periods");
        sourcePeriodOffsets[i] = (int) periodCount;
        windowCount += timeline.getWindowCount();
        sourceWindowOffsets[i] = windowCount;
      }
      this.timelines = timelines;
      this.sourcePeriodOffsets = sourcePeriodOffsets;
      this.sourceWindowOffsets = sourceWindowOffsets;
    }

    @Override
    public int getWindowCount() {
      return sourceWindowOffsets[sourceWindowOffsets.length - 1];
    }

    @Override
    public int getPeriodCount() {
      return sourcePeriodOffsets[sourcePeriodOffsets.length - 1];
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(sourcePeriodOffsets, periodIndex + 1, false, false) + 1;
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(sourceWindowOffsets, windowIndex + 1, false, false) + 1;
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      if (!(childUid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      return (Integer) childUid;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return childIndex == 0 ? 0 : sourcePeriodOffsets[childIndex - 1];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return childIndex == 0 ? 0 : sourceWindowOffsets[childIndex - 1];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return childIndex;
    }

  }

}


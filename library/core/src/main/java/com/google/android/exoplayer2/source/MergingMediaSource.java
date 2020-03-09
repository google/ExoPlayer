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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Merges multiple {@link MediaSource}s.
 *
 * <p>The {@link Timeline}s of the sources being merged must have the same number of periods.
 */
public final class MergingMediaSource extends CompositeMediaSource<Integer> {

  /**
   * Thrown when a {@link MergingMediaSource} cannot merge its sources.
   */
  public static final class IllegalMergeException extends IOException {

    /** The reason the merge failed. One of {@link #REASON_PERIOD_COUNT_MISMATCH}. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REASON_PERIOD_COUNT_MISMATCH})
    public @interface Reason {}
    /**
     * The sources have different period counts.
     */
    public static final int REASON_PERIOD_COUNT_MISMATCH = 0;

    /**
     * The reason the merge failed.
     */
    @Reason public final int reason;

    /**
     * @param reason The reason the merge failed.
     */
    public IllegalMergeException(@Reason int reason) {
      this.reason = reason;
    }

  }

  private static final int PERIOD_COUNT_UNSET = -1;

  private final boolean adjustPeriodTimeOffsets;
  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;
  private final ArrayList<MediaSource> pendingTimelineSources;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;

  private int periodCount;
  private long[][] periodTimeOffsetsUs;
  @Nullable private IllegalMergeException mergeError;

  /**
   * Creates a merging media source.
   *
   * <p>Offsets between the timestamps in the media sources will not be adjusted.
   *
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this(/* adjustPeriodTimeOffsets= */ false, mediaSources);
  }

  /**
   * Creates a merging media source.
   *
   * @param adjustPeriodTimeOffsets Whether to adjust timestamps of the merged media sources to all
   *     start at the same time.
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(boolean adjustPeriodTimeOffsets, MediaSource... mediaSources) {
    this(adjustPeriodTimeOffsets, new DefaultCompositeSequenceableLoaderFactory(), mediaSources);
  }

  /**
   * Creates a merging media source.
   *
   * @param adjustPeriodTimeOffsets Whether to adjust timestamps of the merged media sources to all
   *     start at the same time.
   * @param compositeSequenceableLoaderFactory A factory to create composite {@link
   *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
   *     audio etc...).
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(
      boolean adjustPeriodTimeOffsets,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      MediaSource... mediaSources) {
    this.adjustPeriodTimeOffsets = adjustPeriodTimeOffsets;
    this.mediaSources = mediaSources;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    pendingTimelineSources = new ArrayList<>(Arrays.asList(mediaSources));
    periodCount = PERIOD_COUNT_UNSET;
    timelines = new Timeline[mediaSources.length];
    periodTimeOffsetsUs = new long[0][];
  }

  @Override
  @Nullable
  public Object getTag() {
    return mediaSources.length > 0 ? mediaSources[0].getTag() : null;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    for (int i = 0; i < mediaSources.length; i++) {
      prepareChildSource(i, mediaSources[i]);
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (mergeError != null) {
      throw mergeError;
    }
    super.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    int periodIndex = timelines[0].getIndexOfPeriod(id.periodUid);
    for (int i = 0; i < periods.length; i++) {
      MediaPeriodId childMediaPeriodId =
          id.copyWithPeriodUid(timelines[i].getUidOfPeriod(periodIndex));
      periods[i] =
          mediaSources[i].createPeriod(
              childMediaPeriodId, allocator, startPositionUs - periodTimeOffsetsUs[periodIndex][i]);
    }
    return new MergingMediaPeriod(
        compositeSequenceableLoaderFactory, periodTimeOffsetsUs[periodIndex], periods);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MergingMediaPeriod mergingPeriod = (MergingMediaPeriod) mediaPeriod;
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i].releasePeriod(mergingPeriod.getChildPeriod(i));
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    Arrays.fill(timelines, null);
    periodCount = PERIOD_COUNT_UNSET;
    mergeError = null;
    pendingTimelineSources.clear();
    Collections.addAll(pendingTimelineSources, mediaSources);
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Integer id, MediaSource mediaSource, Timeline timeline) {
    if (mergeError != null) {
      return;
    }
    if (periodCount == PERIOD_COUNT_UNSET) {
      periodCount = timeline.getPeriodCount();
    } else if (timeline.getPeriodCount() != periodCount) {
      mergeError = new IllegalMergeException(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
      return;
    }
    if (periodTimeOffsetsUs.length == 0) {
      periodTimeOffsetsUs = new long[periodCount][timelines.length];
    }
    pendingTimelineSources.remove(mediaSource);
    timelines[id] = timeline;
    if (pendingTimelineSources.isEmpty()) {
      if (adjustPeriodTimeOffsets) {
        computePeriodTimeOffsets();
      }
      refreshSourceInfo(timelines[0]);
    }
  }

  @Override
  @Nullable
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      Integer id, MediaPeriodId mediaPeriodId) {
    return id == 0 ? mediaPeriodId : null;
  }

  private void computePeriodTimeOffsets() {
    Timeline.Period period = new Timeline.Period();
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      long primaryWindowOffsetUs =
          -timelines[0].getPeriod(periodIndex, period).getPositionInWindowUs();
      for (int timelineIndex = 1; timelineIndex < timelines.length; timelineIndex++) {
        long secondaryWindowOffsetUs =
            -timelines[timelineIndex].getPeriod(periodIndex, period).getPositionInWindowUs();
        periodTimeOffsetsUs[periodIndex][timelineIndex] =
            primaryWindowOffsetUs - secondaryWindowOffsetUs;
      }
    }
  }
}

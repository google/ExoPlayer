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
package com.google.android.exoplayer2.testutil;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;

/**
 * Fake {@link MediaSource} that provides a given timeline. Creating the period returns a {@link
 * FakeAdaptiveMediaPeriod} from the given {@link TrackGroupArray}.
 */
public class FakeAdaptiveMediaSource extends FakeMediaSource {

  private final FakeChunkSource.Factory chunkSourceFactory;

  public FakeAdaptiveMediaSource(
      Timeline timeline,
      TrackGroupArray trackGroupArray,
      FakeChunkSource.Factory chunkSourceFactory) {
    super(
        timeline,
        DrmSessionManager.DRM_UNSUPPORTED,
        /* trackDataFactory= */ (unusedFormat, unusedMediaPeriodId) -> {
          throw new RuntimeException("Unused TrackDataFactory");
        },
        trackGroupArray);
    this.chunkSourceFactory = chunkSourceFactory;
  }

  @Override
  protected MediaPeriod createMediaPeriod(
      MediaPeriodId id,
      TrackGroupArray trackGroupArray,
      Allocator allocator,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      @Nullable TransferListener transferListener) {
    Period period = Util.castNonNull(getTimeline()).getPeriodByUid(id.periodUid, new Period());
    return new FakeAdaptiveMediaPeriod(
        trackGroupArray,
        mediaSourceEventDispatcher,
        allocator,
        chunkSourceFactory,
        period.durationUs,
        transferListener);
  }

  @Override
  public void releaseMediaPeriod(MediaPeriod mediaPeriod) {
    ((FakeAdaptiveMediaPeriod) mediaPeriod).release();
  }
}

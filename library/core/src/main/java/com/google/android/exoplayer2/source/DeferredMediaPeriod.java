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

import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import java.io.IOException;

/**
 * Media period that wraps a media source and defers calling its
 * {@link MediaSource#createPeriod(MediaPeriodId, Allocator)} method until {@link #createPeriod()}
 * has been called. This is useful if you need to return a media period immediately but the media
 * source that should create it is not yet prepared.
 */
public final class DeferredMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

  public final MediaSource mediaSource;

  private final MediaPeriodId id;
  private final Allocator allocator;

  private MediaPeriod mediaPeriod;
  private Callback callback;
  private long preparePositionUs;

  public DeferredMediaPeriod(MediaSource mediaSource, MediaPeriodId id, Allocator allocator) {
    this.id = id;
    this.allocator = allocator;
    this.mediaSource = mediaSource;
  }

  /**
   * Calls {@link MediaSource#createPeriod(MediaPeriodId, Allocator)} on the wrapped source then
   * prepares it if {@link #prepare(Callback, long)} has been called. Call {@link #releasePeriod()}
   * to release the period.
   */
  public void createPeriod() {
    mediaPeriod = mediaSource.createPeriod(id, allocator);
    if (callback != null) {
      mediaPeriod.prepare(this, preparePositionUs);
    }
  }

  /**
   * Releases the period.
   */
  public void releasePeriod() {
    if (mediaPeriod != null) {
      mediaSource.releasePeriod(mediaPeriod);
    }
  }

  @Override
  public void prepare(Callback callback, long preparePositionUs) {
    this.callback = callback;
    this.preparePositionUs = preparePositionUs;
    if (mediaPeriod != null) {
      mediaPeriod.prepare(this, preparePositionUs);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    if (mediaPeriod != null) {
      mediaPeriod.maybeThrowPrepareError();
    } else {
      mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return mediaPeriod.getTrackGroups();
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    return mediaPeriod.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags,
        positionUs);
  }

  @Override
  public void discardBuffer(long positionUs) {
    mediaPeriod.discardBuffer(positionUs);
  }

  @Override
  public long readDiscontinuity() {
    return mediaPeriod.readDiscontinuity();
  }

  @Override
  public long getBufferedPositionUs() {
    return mediaPeriod.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    return mediaPeriod.seekToUs(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    return mediaPeriod.getNextLoadPositionUs();
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return mediaPeriod != null && mediaPeriod.continueLoading(positionUs);
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    callback.onContinueLoadingRequested(this);
  }

  // MediaPeriod.Callback implementation

  @Override
  public void onPrepared(MediaPeriod mediaPeriod) {
    callback.onPrepared(this);
  }

}

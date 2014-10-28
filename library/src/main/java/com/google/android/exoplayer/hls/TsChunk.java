/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

/**
 * A MPEG2TS chunk.
 */
public final class TsChunk extends HlsChunk {

  /**
   * The start time of the media contained by the chunk.
   */
  public final long startTimeUs;
  /**
   * The end time of the media contained by the chunk.
   */
  public final long endTimeUs;
  /**
   * The index of the next media chunk, or -1 if this is the last media chunk in the stream.
   */
  public final int nextChunkIndex;
  /**
   * The encoding discontinuity indicator.
   */
  private final boolean discontinuity;

  private boolean pendingDiscontinuity;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   * @param discontinuity The encoding discontinuity indicator.
   */
  public TsChunk(DataSource dataSource, DataSpec dataSpec, int trigger, long startTimeUs,
      long endTimeUs, int nextChunkIndex, boolean discontinuity) {
    super(dataSource, dataSpec, trigger);
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.nextChunkIndex = nextChunkIndex;
    this.discontinuity = discontinuity;
    this.pendingDiscontinuity = discontinuity;
  }

  public boolean isLastChunk() {
    return nextChunkIndex == -1;
  }

  public void reset() {
    resetReadPosition();
    pendingDiscontinuity = discontinuity;
  }

  public boolean hasPendingDiscontinuity() {
    return pendingDiscontinuity;
  }

  public void clearPendingDiscontinuity() {
    pendingDiscontinuity = false;
  }

}

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
   * The index of the variant in the master playlist.
   */
  public final int variantIndex;
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
  public final boolean discontinuity;
  /**
   * For each track, whether samples from the first keyframe (inclusive) should be discarded.
   */
  public final boolean discardFromFirstKeyframes;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param variantIndex The index of the variant in the master playlist.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   * @param discontinuity The encoding discontinuity indicator.
   * @param discardFromFirstKeyframes For each contained media stream, whether samples from the
   *     first keyframe (inclusive) should be discarded.
   */
  public TsChunk(DataSource dataSource, DataSpec dataSpec, int trigger, int variantIndex,
      long startTimeUs, long endTimeUs, int nextChunkIndex, boolean discontinuity,
      boolean discardFromFirstKeyframes) {
    super(dataSource, dataSpec, trigger);
    this.variantIndex = variantIndex;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.nextChunkIndex = nextChunkIndex;
    this.discontinuity = discontinuity;
    this.discardFromFirstKeyframes = discardFromFirstKeyframes;
  }

  public boolean isLastChunk() {
    return nextChunkIndex == -1;
  }

}

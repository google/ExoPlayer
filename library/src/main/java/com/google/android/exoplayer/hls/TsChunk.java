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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.io.IOException;

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
   * The chunk index.
   */
  public final int chunkIndex;
  /**
   * True if this is the last chunk in the media. False otherwise.
   */
  public final boolean isLastChunk;
  /**
   * The extractor into which this chunk is being consumed.
   */
  public final TsExtractor extractor;

  private volatile int loadPosition;
  private volatile boolean loadFinished;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param variantIndex The index of the variant in the master playlist.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param isLastChunk True if this is the last chunk in the media. False otherwise.
   */
  public TsChunk(DataSource dataSource, DataSpec dataSpec, TsExtractor tsExtractor,
      int variantIndex, long startTimeUs, long endTimeUs, int chunkIndex, boolean isLastChunk) {
    super(dataSource, dataSpec);
    this.extractor = tsExtractor;
    this.variantIndex = variantIndex;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.chunkIndex = chunkIndex;
    this.isLastChunk = isLastChunk;
  }

  @Override
  public void consume() throws IOException {
    // Do nothing.
  }

  @Override
  public boolean isLoadFinished() {
    return loadFinished;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec;
    if (loadPosition == 0) {
      loadDataSpec = dataSpec;
    } else {
      long remainingLength = dataSpec.length != C.LENGTH_UNBOUNDED
          ? dataSpec.length - loadPosition : C.LENGTH_UNBOUNDED;
      loadDataSpec = new DataSpec(dataSpec.uri, dataSpec.position + loadPosition,
          remainingLength, dataSpec.key);
    }
    try {
      dataSource.open(loadDataSpec);
      int bytesRead = 0;
      while (bytesRead != -1 && !loadCanceled) {
        bytesRead = extractor.read(dataSource);
      }
      loadFinished = !loadCanceled;
    } finally {
      dataSource.close();
    }
  }

}

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

import com.google.android.exoplayer.hls.parser.HlsExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.io.IOException;

/**
 * A MPEG2TS chunk.
 */
public final class TsChunk extends HlsChunk {

  private static final byte[] SCRATCH_SPACE = new byte[4096];

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
  public final HlsExtractor extractor;

  private int loadPosition;
  private volatile boolean loadFinished;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param extractor An extractor to parse samples from the data.
   * @param variantIndex The index of the variant in the master playlist.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param isLastChunk True if this is the last chunk in the media. False otherwise.
   */
  public TsChunk(DataSource dataSource, DataSpec dataSpec, HlsExtractor extractor,
      int variantIndex, long startTimeUs, long endTimeUs, int chunkIndex, boolean isLastChunk) {
    super(dataSource, dataSpec);
    this.extractor = extractor;
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
    try {
      dataSource.open(dataSpec);
      int bytesRead = 0;
      int bytesSkipped = 0;
      // If we previously fed part of this chunk to the extractor, skip it this time.
      // TODO: Ideally we'd construct a dataSpec that only loads the remainder of the data here,
      // rather than loading the whole chunk again and then skipping data we previously loaded. To
      // do this is straightforward for non-encrypted content, but more complicated for content
      // encrypted with AES, for which we'll need to modify the way that decryption is performed.
      while (bytesRead != -1 && !loadCanceled && bytesSkipped < loadPosition) {
        int skipLength = Math.min(loadPosition - bytesSkipped, SCRATCH_SPACE.length);
        bytesRead = dataSource.read(SCRATCH_SPACE, 0, skipLength);
        if (bytesRead != -1) {
          bytesSkipped += bytesRead;
        }
      }
      // Feed the remaining data into the extractor.
      while (bytesRead != -1 && !loadCanceled) {
        bytesRead = extractor.read(dataSource);
        if (bytesRead != -1) {
          loadPosition += bytesRead;
        }
      }
      loadFinished = !loadCanceled;
    } finally {
      dataSource.close();
    }
  }

}

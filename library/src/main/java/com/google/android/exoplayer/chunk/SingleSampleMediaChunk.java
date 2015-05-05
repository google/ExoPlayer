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
package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;

/**
 * A {@link BaseMediaChunk} for chunks consisting of a single raw sample.
 */
public final class SingleSampleMediaChunk extends BaseMediaChunk {

  private final MediaFormat sampleFormat;
  private final DrmInitData sampleDrmInitData;
  private final byte[] headerData;

  private boolean writtenHeader;

  private volatile int bytesLoaded;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param isLastChunk True if this is the last chunk in the media. False otherwise.
   * @param sampleFormat The format of the sample.
   * @param sampleDrmInitData The {@link DrmInitData} for the sample. Null if the sample is not drm
   *     protected.
   * @param headerData Custom header data for the sample. May be null. If set, the header data is
   *     prepended to the sample data. It is not reflected in the values returned by
   *     {@link #bytesLoaded()}.
   */
  public SingleSampleMediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger,
      Format format, long startTimeUs, long endTimeUs, int chunkIndex, boolean isLastChunk,
      MediaFormat sampleFormat, DrmInitData sampleDrmInitData, byte[] headerData) {
    super(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs, chunkIndex, isLastChunk,
        true);
    this.sampleFormat = sampleFormat;
    this.sampleDrmInitData = sampleDrmInitData;
    this.headerData = headerData;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  @Override
  public MediaFormat getMediaFormat() {
    return sampleFormat;
  }

  @Override
  public DrmInitData getDrmInitData() {
    return sampleDrmInitData;
  }

  // Loadable implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void load() throws IOException, InterruptedException {
    if (!writtenHeader) {
      if (headerData != null) {
        getOutput().sampleData(new ParsableByteArray(headerData), headerData.length);
      }
      writtenHeader = true;
    }

    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      // Create and open the input.
      dataSource.open(loadDataSpec);
      // Load the sample data.
      int result = 0;
      while (result != C.RESULT_END_OF_INPUT) {
        result = getOutput().sampleData(dataSource, Integer.MAX_VALUE);
        if (result != C.RESULT_END_OF_INPUT) {
          bytesLoaded += result;
        }
      }
      int sampleSize = bytesLoaded;
      if (headerData != null) {
        sampleSize += headerData.length;
      }
      getOutput().sampleMetadata(startTimeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
    } finally {
      dataSource.close();
    }
  }

}

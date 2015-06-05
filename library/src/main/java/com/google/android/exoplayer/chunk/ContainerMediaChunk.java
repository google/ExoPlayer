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

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.chunk.ChunkExtractorWrapper.SingleTrackOutput;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.DefaultExtractorInput;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.util.Log;

import java.io.IOException;

/**
 * A {@link BaseMediaChunk} that uses an {@link Extractor} to parse sample data.
 */
public class ContainerMediaChunk extends BaseMediaChunk implements SingleTrackOutput {

  private static final String TAG = "ContainerMediaChunk";

  private final ChunkExtractorWrapper extractorWrapper;
  private final long sampleOffsetUs;

  private MediaFormat mediaFormat;
  private DrmInitData drmInitData;

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
   * @param sampleOffsetUs An offset to add to the sample timestamps parsed by the extractor.
   * @param extractorWrapper A wrapped extractor to use for parsing the data.
   * @param mediaFormat The {@link MediaFormat} of the chunk, if known. May be null if the data is
   *     known to define its own format.
   * @param drmInitData The {@link DrmInitData} for the chunk. Null if the media is not drm
   *     protected. May also be null if the data is known to define its own initialization data.
   * @param isMediaFormatFinal True if {@code mediaFormat} and {@code drmInitData} are known to be
   *     correct and final. False if the data may define its own format or initialization data.
   */
  public ContainerMediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex, boolean isLastChunk, long sampleOffsetUs,
      ChunkExtractorWrapper extractorWrapper, MediaFormat mediaFormat, DrmInitData drmInitData,
      boolean isMediaFormatFinal) {
    super(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs, chunkIndex, isLastChunk,
        isMediaFormatFinal);
    this.extractorWrapper = extractorWrapper;
    this.sampleOffsetUs = sampleOffsetUs;
    this.mediaFormat = mediaFormat;
    this.drmInitData = drmInitData;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  @Override
  public MediaFormat getMediaFormat() {
    return mediaFormat;
  }

  @Override
  public DrmInitData getDrmInitData() {
    return drmInitData;
  }

  // SingleTrackOutput implementation.

  @Override
  public void seekMap(SeekMap seekMap) {
    Log.w(TAG, "Ignoring unexpected seekMap");
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

  @Override
  public void format(MediaFormat mediaFormat) {
    this.mediaFormat = mediaFormat;
  }

  @Override
  public int sampleData(ExtractorInput input, int length) throws IOException, InterruptedException {
    return getOutput().sampleData(input, length);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    getOutput().sampleData(data, length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    getOutput().sampleMetadata(timeUs + sampleOffsetUs, flags, size, offset, encryptionKey);
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
    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      // Create and open the input.
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (bytesLoaded == 0) {
        // Set the target to ourselves.
        extractorWrapper.init(this);
      }
      // Load and parse the initialization data.
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractorWrapper.read(input);
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
  }

}

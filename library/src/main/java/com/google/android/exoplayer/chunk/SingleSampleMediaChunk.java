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
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link MediaChunk} containing a single sample.
 */
public class SingleSampleMediaChunk extends MediaChunk {

  /**
   * The sample header data. May be null.
   */
  public final byte[] headerData;

  private final MediaFormat sampleFormat;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   * @param sampleFormat The format of the media contained by the chunk.
   */
  public SingleSampleMediaChunk(DataSource dataSource, DataSpec dataSpec, Format format,
      int trigger, long startTimeUs, long endTimeUs, int nextChunkIndex, MediaFormat sampleFormat) {
    this(dataSource, dataSpec, format, trigger, startTimeUs, endTimeUs, nextChunkIndex,
        sampleFormat, null);
  }

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   * @param sampleFormat The format of the media contained by the chunk.
   * @param headerData Custom header data for the sample. May be null. If set, the header data is
   *     prepended to the sample data returned when {@link #read(SampleHolder)} is called. It is
   *     however not considered part of the loaded data, and so is not prepended to the data
   *     returned by {@link #getLoadedData()}. It is also not reflected in the values returned by
   *     {@link #bytesLoaded()} and {@link #getLength()}.
   */
  public SingleSampleMediaChunk(DataSource dataSource, DataSpec dataSpec, Format format,
      int trigger, long startTimeUs, long endTimeUs, int nextChunkIndex, MediaFormat sampleFormat,
      byte[] headerData) {
    super(dataSource, dataSpec, format, trigger, startTimeUs, endTimeUs, nextChunkIndex);
    this.sampleFormat = sampleFormat;
    this.headerData = headerData;
  }

  @Override
  public boolean read(SampleHolder holder) {
    NonBlockingInputStream inputStream = getNonBlockingInputStream();
    Assertions.checkState(inputStream != null);
    if (!isLoadFinished()) {
      return false;
    }
    int bytesLoaded = (int) bytesLoaded();
    int sampleSize = bytesLoaded;
    if (headerData != null) {
      sampleSize += headerData.length;
    }
    if (holder.allowDataBufferReplacement &&
        (holder.data == null || holder.data.capacity() < sampleSize)) {
      holder.data = ByteBuffer.allocate(sampleSize);
    }
    int bytesRead;
    if (holder.data != null) {
      if (headerData != null) {
        holder.data.put(headerData);
      }
      bytesRead = inputStream.read(holder.data, bytesLoaded);
      holder.size = sampleSize;
    } else {
      bytesRead = inputStream.skip(bytesLoaded);
      holder.size = 0;
    }
    Assertions.checkState(bytesRead == bytesLoaded);
    holder.timeUs = startTimeUs;
    return true;
  }

  @Override
  public boolean seekTo(long positionUs, boolean allowNoop) {
    resetReadPosition();
    return true;
  }

  @Override
  public MediaFormat getMediaFormat() {
    return sampleFormat;
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    return null;
  }

}

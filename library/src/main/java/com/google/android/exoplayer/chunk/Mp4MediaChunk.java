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
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.util.Map;
import java.util.UUID;

/**
 * An Mp4 {@link MediaChunk}.
 */
public final class Mp4MediaChunk extends MediaChunk {

  private final FragmentedMp4Extractor extractor;
  private final long sampleOffsetUs;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param extractor The extractor that will be used to extract the samples.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param sampleOffsetUs An offset to subtract from the sample timestamps parsed by the extractor.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   */
  public Mp4MediaChunk(DataSource dataSource, DataSpec dataSpec, Format format,
      int trigger, FragmentedMp4Extractor extractor, long startTimeUs, long endTimeUs,
      long sampleOffsetUs, int nextChunkIndex) {
    super(dataSource, dataSpec, format, trigger, startTimeUs, endTimeUs, nextChunkIndex);
    this.extractor = extractor;
    this.sampleOffsetUs = sampleOffsetUs;
  }

  @Override
  public boolean seekTo(long positionUs, boolean allowNoop) {
    long seekTimeUs = positionUs + sampleOffsetUs;
    boolean isDiscontinuous = extractor.seekTo(seekTimeUs, allowNoop);
    if (isDiscontinuous) {
      resetReadPosition();
    }
    return isDiscontinuous;
  }

  @Override
  public boolean read(SampleHolder holder) throws ParserException {
    NonBlockingInputStream inputStream = getNonBlockingInputStream();
    Assertions.checkState(inputStream != null);
    int result = extractor.read(inputStream, holder);
    boolean sampleRead = (result & FragmentedMp4Extractor.RESULT_READ_SAMPLE_FULL) != 0;
    if (sampleRead) {
      holder.timeUs -= sampleOffsetUs;
    }
    return sampleRead;
  }

  @Override
  public MediaFormat getMediaFormat() {
    return extractor.getFormat();
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    return extractor.getPsshInfo();
  }

}

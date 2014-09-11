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
import com.google.android.exoplayer.parser.Extractor;
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

  private final Extractor extractor;
  private final boolean maybeSelfContained;
  private final long sampleOffsetUs;

  private boolean prepared;
  private MediaFormat mediaFormat;
  private Map<UUID, byte[]> psshInfo;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   * @param extractor The extractor that will be used to extract the samples.
   * @param maybeSelfContained Set to true if this chunk might be self contained, meaning it might
   *     contain a moov atom defining the media format of the chunk. This parameter can always be
   *     safely set to true. Setting to false where the chunk is known to not be self contained may
   *     improve startup latency.
   * @param sampleOffsetUs An offset to subtract from the sample timestamps parsed by the extractor.
   */
  public Mp4MediaChunk(DataSource dataSource, DataSpec dataSpec, Format format,
      int trigger, long startTimeUs, long endTimeUs, int nextChunkIndex,
      Extractor extractor, boolean maybeSelfContained, long sampleOffsetUs) {
    super(dataSource, dataSpec, format, trigger, startTimeUs, endTimeUs, nextChunkIndex);
    this.extractor = extractor;
    this.maybeSelfContained = maybeSelfContained;
    this.sampleOffsetUs = sampleOffsetUs;
  }

  @Override
  public void seekToStart() {
    extractor.seekTo(0, false);
    resetReadPosition();
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
  public boolean prepare() throws ParserException {
    if (!prepared) {
      if (maybeSelfContained) {
        // Read up to the first sample. Once we're there, we know that the extractor must have
        // parsed a moov atom if the chunk contains one.
        NonBlockingInputStream inputStream = getNonBlockingInputStream();
        Assertions.checkState(inputStream != null);
        int result = extractor.read(inputStream, null);
        prepared = (result & Extractor.RESULT_NEED_SAMPLE_HOLDER) != 0;
      } else {
        // We know there isn't a moov atom. The extractor must have parsed one from a separate
        // initialization chunk.
        prepared = true;
      }
      if (prepared) {
        mediaFormat = extractor.getFormat();
        psshInfo = extractor.getPsshInfo();
      }
    }
    return prepared;
  }

  @Override
  public boolean sampleAvailable() throws ParserException {
    NonBlockingInputStream inputStream = getNonBlockingInputStream();
    int result = extractor.read(inputStream, null);
    return (result & Extractor.RESULT_NEED_SAMPLE_HOLDER) != 0;
  }

  @Override
  public boolean read(SampleHolder holder) throws ParserException {
    NonBlockingInputStream inputStream = getNonBlockingInputStream();
    Assertions.checkState(inputStream != null);
    int result = extractor.read(inputStream, holder);
    boolean sampleRead = (result & Extractor.RESULT_READ_SAMPLE) != 0;
    if (sampleRead) {
      holder.timeUs -= sampleOffsetUs;
    }
    return sampleRead;
  }

  @Override
  public MediaFormat getMediaFormat() {
    return mediaFormat;
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    return psshInfo;
  }

}

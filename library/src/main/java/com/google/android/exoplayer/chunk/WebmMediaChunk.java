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
import com.google.android.exoplayer.parser.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.util.Map;
import java.util.UUID;

/**
 * A WebM {@link MediaChunk}.
 */
public final class WebmMediaChunk extends MediaChunk {

  private final WebmExtractor extractor;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param extractor The extractor that will be used to extract the samples.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   */
  public WebmMediaChunk(DataSource dataSource, DataSpec dataSpec, Format format,
      int trigger, WebmExtractor extractor, long startTimeUs, long endTimeUs,
      int nextChunkIndex) {
    super(dataSource, dataSpec, format, trigger, startTimeUs, endTimeUs, nextChunkIndex);
    this.extractor = extractor;
  }

  @Override
  public boolean seekTo(long positionUs, boolean allowNoop) {
    boolean isDiscontinuous = extractor.seekTo(positionUs, allowNoop);
    if (isDiscontinuous) {
      resetReadPosition();
    }
    return isDiscontinuous;
  }

  @Override
  public boolean read(SampleHolder holder) {
    NonBlockingInputStream inputStream = getNonBlockingInputStream();
    Assertions.checkState(inputStream != null);
    return extractor.read(inputStream, holder);
  }

  @Override
  public MediaFormat getMediaFormat() {
    return extractor.getFormat();
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    // TODO: Add support for Pssh to WebmExtractor
    return null;
  }

}

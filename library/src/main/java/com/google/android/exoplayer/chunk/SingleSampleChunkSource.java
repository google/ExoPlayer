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
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.io.IOException;
import java.util.List;

/**
 * A chunk source that provides a single chunk containing a single sample.
 * <p>
 * An example use case for this implementation is to act as the source for loading out-of-band
 * subtitles, where subtitles for the entire video are delivered as a single file.
 */
public class SingleSampleChunkSource implements ChunkSource {

  private final DataSource dataSource;
  private final DataSpec dataSpec;
  private final Format format;
  private final long durationUs;
  private final MediaFormat mediaFormat;
  private final TrackInfo trackInfo;

  /**
   * @param dataSource A {@link DataSource} suitable for loading the sample data.
   * @param dataSpec Defines the location of the sample.
   * @param format The format of the sample.
   * @param durationUs The duration of the sample in microseconds, or {@link C#UNKNOWN_TIME_US} if
   *     the duration is unknown.
   * @param mediaFormat The sample media format. May be null.
   */
  public SingleSampleChunkSource(DataSource dataSource, DataSpec dataSpec, Format format,
      long durationUs, MediaFormat mediaFormat) {
    this.dataSource = dataSource;
    this.dataSpec = dataSpec;
    this.format = format;
    this.durationUs = durationUs;
    this.mediaFormat = mediaFormat;
    trackInfo = new TrackInfo(format.mimeType, durationUs);
  }

  @Override
  public TrackInfo getTrackInfo() {
    return trackInfo;
  }

  @Override
  public void getMaxVideoDimensions(MediaFormat out) {
    // Do nothing.
  }

  @Override
  public void enable() {
    // Do nothing.
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    // Do nothing.
  }

  @Override
  public void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out) {
    if (!queue.isEmpty()) {
      // We've already provided the single sample.
      return;
    }
    out.chunk = initChunk();
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    // Do nothing.
  }

  @Override
  public IOException getError() {
    return null;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private SingleSampleMediaChunk initChunk() {
    return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_UNSPECIFIED, format, 0,
        durationUs, 0, true, mediaFormat, null, null);
  }

}

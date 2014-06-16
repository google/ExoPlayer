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
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import java.util.Map;
import java.util.UUID;

/**
 * An abstract base class for {@link Chunk}s that contain media samples.
 */
public abstract class MediaChunk extends Chunk {

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
   * Constructor for a chunk of media samples.
   *
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param format The format of the stream to which this chunk belongs.
   * @param trigger The reason for this chunk being selected.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param nextChunkIndex The index of the next chunk, or -1 if this is the last chunk.
   */
  public MediaChunk(DataSource dataSource, DataSpec dataSpec, Format format, int trigger,
      long startTimeUs, long endTimeUs, int nextChunkIndex) {
    super(dataSource, dataSpec, format, trigger);
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.nextChunkIndex = nextChunkIndex;
  }

  /**
   * Whether this is the last chunk in the stream.
   *
   * @return True if this is the last chunk in the stream. False otherwise.
   */
  public final boolean isLastChunk() {
    return nextChunkIndex == -1;
  }

  /**
   * Seeks to the beginning of the chunk.
   */
  public final void seekToStart() {
    seekTo(startTimeUs, false);
  }

  /**
   * Seeks to the specified position within the chunk.
   *
   * @param positionUs The desired seek time in microseconds.
   * @param allowNoop True if the seek is allowed to do nothing if the result is more accurate than
   *     seeking to a key frame. Always pass false if it is required that the next sample be a key
   *     frame.
   * @return True if the seek results in a discontinuity in the sequence of samples returned by
   *     {@link #read(SampleHolder)}. False otherwise.
   */
  public abstract boolean seekTo(long positionUs, boolean allowNoop);

  /**
   * Reads the next media sample from the chunk.
   *
   * @param holder A holder to store the read sample.
   * @return True if a sample was read. False if more data is still required.
   * @throws ParserException If an error occurs parsing the media data.
   * @throws IllegalStateException If called before {@link #init}, or after {@link #release}
   */
  public abstract boolean read(SampleHolder holder) throws ParserException;

  /**
   * Returns the media format of the samples contained within this chunk.
   *
   * @return The sample media format.
   */
  public abstract MediaFormat getMediaFormat();

  /**
   * Returns the pssh information associated with the chunk.
   *
   * @return The pssh information.
   */
  public abstract Map<UUID, byte[]> getPsshInfo();

}

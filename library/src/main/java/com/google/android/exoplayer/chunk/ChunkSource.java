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
import com.google.android.exoplayer.TrackInfo;

import java.io.IOException;
import java.util.List;

/**
 * A provider of {@link Chunk}s for a {@link ChunkSampleSource} to load.
 */
/*
 * TODO: Share more state between this interface and {@link ChunkSampleSource}. In particular
 * implementations of this class needs to know about errors, and should be more tightly integrated
 * into the process of resuming loading of a chunk after an error occurs.
 */
public interface ChunkSource {

  /**
   * Gets information about the track for which this instance provides {@link Chunk}s.
   * <p>
   * May be called when the source is disabled or enabled.
   *
   * @return Information about the track.
   */
  TrackInfo getTrackInfo();

  /**
   * Adaptive video {@link ChunkSource} implementations must set the maximum video dimensions on
   * the supplied {@link MediaFormat}. Other implementations do nothing.
   * <p>
   * Only called when the source is enabled.
   */
  void getMaxVideoDimensions(MediaFormat out);

  /**
   * Called when the source is enabled.
   */
  void enable();

  /**
   * Called when the source is disabled.
   *
   * @param queue A representation of the currently buffered {@link MediaChunk}s.
   */
  void disable(List<? extends MediaChunk> queue);

  /**
   * Indicates to the source that it should still be checking for updates to the stream.
   *
   * @param playbackPositionUs The current playback position.
   */
  void continueBuffering(long playbackPositionUs);

  /**
   * Updates the provided {@link ChunkOperationHolder} to contain the next operation that should
   * be performed by the calling {@link ChunkSampleSource}.
   * <p>
   * The next operation comprises of a possibly shortened queue length (shortened if the
   * implementation wishes for the caller to discard {@link MediaChunk}s from the queue), together
   * with the next {@link Chunk} to load. The next chunk may be a {@link MediaChunk} to be added to
   * the queue, or another {@link Chunk} type (e.g. to load initialization data), or null if the
   * source is not able to provide a chunk in its current state.
   *
   * @param queue A representation of the currently buffered {@link MediaChunk}s.
   * @param seekPositionUs If the queue is empty, this parameter must specify the seek position. If
   *     the queue is non-empty then this parameter is ignored.
   * @param playbackPositionUs The current playback position.
   * @param out A holder for the next operation, whose {@link ChunkOperationHolder#queueSize} is
   *     initially equal to the length of the queue, and whose {@link ChunkOperationHolder#chunk} is
   *     initially equal to null or a {@link Chunk} previously supplied by the {@link ChunkSource}
   *     that the caller has not yet finished loading. In the latter case the chunk can either be
   *     replaced or left unchanged. Note that leaving the chunk unchanged is both preferred and
   *     more efficient than replacing it with a new but identical chunk.
   */
  void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out);

  /**
   * If the {@link ChunkSource} is currently unable to provide chunks through
   * {@link ChunkSource#getChunkOperation}, then this method returns the underlying cause. Returns
   * null otherwise.
   *
   * @return An {@link IOException}, or null.
   */
  IOException getError();

  /**
   * Invoked when the {@link ChunkSampleSource} encounters an error loading a chunk obtained from
   * this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param e The error.
   */
  void onChunkLoadError(Chunk chunk, Exception e);

}

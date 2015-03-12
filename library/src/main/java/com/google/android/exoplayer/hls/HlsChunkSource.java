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

import com.google.android.exoplayer.MediaFormat;
import java.io.IOException;

/**
 * A temporary test source of HLS chunks.
 * <p>
 * TODO: Figure out whether this should merge with the chunk package, or whether the hls
 * implementation is going to naturally diverge.
 */
public interface HlsChunkSource {


  public long getDurationUs();

  /**
   * Adaptive implementations must set the maximum video dimensions on the supplied
   * {@link MediaFormat}. Other implementations do nothing.
   * <p>
   * Only called when the source is enabled.
   *
   * @param out The {@link MediaFormat} on which the maximum video dimensions should be set.
   */
  public void getMaxVideoDimensions(MediaFormat out);

  /**
   * Returns the next {@link HlsChunk} that should be loaded.
   *
   * @param previousTsChunk The previously loaded chunk that the next chunk should follow.
   * @param seekPositionUs If there is no previous chunk, this parameter must specify the seek
   *     position. If there is a previous chunk then this parameter is ignored.
   * @param playbackPositionUs The current playback position.
   * @return The next chunk to load.
   */
  public HlsChunk getChunkOperation(TsChunk previousTsChunk, long seekPositionUs,
                                    long playbackPositionUs);
  /**
   * Invoked when an error occurs loading a chunk.
   *
   * @param chunk The chunk whose load failed.
   * @param e The failure.
   * @return True if the error was handled by the source. False otherwise.
   */
  public boolean onLoadError(HlsChunk chunk, IOException e);
}

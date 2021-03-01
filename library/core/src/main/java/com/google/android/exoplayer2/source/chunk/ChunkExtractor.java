/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.chunk;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import java.io.IOException;
import java.util.List;

/**
 * Extracts samples and track {@link Format Formats} from chunks.
 *
 * <p>The {@link TrackOutputProvider} passed to {@link #init} provides the {@link TrackOutput
 * TrackOutputs} that receive the extracted data.
 */
public interface ChunkExtractor {

  /** Creates {@link ChunkExtractor} instances. */
  interface Factory {

    /**
     * Returns a new {@link ChunkExtractor} instance.
     *
     * @param primaryTrackType The type of the primary track. One of {@link C C.TRACK_TYPE_*}.
     * @param representationFormat The format of the representation to extract from.
     * @param enableEventMessageTrack Whether to enable the event message track.
     * @param closedCaptionFormats The {@link Format Formats} of the Closed-Caption tracks.
     * @return A new {@link ChunkExtractor} instance, or null if not applicable.
     */
    @Nullable
    ChunkExtractor createProgressiveMediaExtractor(
        int primaryTrackType,
        Format representationFormat,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable TrackOutput playerEmsgTrackOutput);
  }

  /** Provides {@link TrackOutput} instances to be written to during extraction. */
  interface TrackOutputProvider {

    /**
     * Called to get the {@link TrackOutput} for a specific track.
     *
     * <p>The same {@link TrackOutput} is returned if multiple calls are made with the same {@code
     * id}.
     *
     * @param id A track identifier.
     * @param type The type of the track. Typically one of the {@link C} {@code TRACK_TYPE_*}
     *     constants.
     * @return The {@link TrackOutput} for the given track identifier.
     */
    TrackOutput track(int id, int type);
  }

  /**
   * Returns the {@link ChunkIndex} most recently obtained from the chunks, or null if a {@link
   * ChunkIndex} has not been obtained.
   */
  @Nullable
  ChunkIndex getChunkIndex();

  /**
   * Returns the sample {@link Format}s for the tracks identified by the extractor, or null if the
   * extractor has not finished identifying tracks.
   */
  @Nullable
  Format[] getSampleFormats();

  /**
   * Initializes the wrapper to output to {@link TrackOutput}s provided by the specified {@link
   * TrackOutputProvider}, and configures the extractor to receive data from a new chunk.
   *
   * @param trackOutputProvider The provider of {@link TrackOutput}s that will receive sample data.
   * @param startTimeUs The start position in the new chunk, or {@link C#TIME_UNSET} to output
   *     samples from the start of the chunk.
   * @param endTimeUs The end position in the new chunk, or {@link C#TIME_UNSET} to output samples
   *     to the end of the chunk.
   */
  void init(@Nullable TrackOutputProvider trackOutputProvider, long startTimeUs, long endTimeUs);

  /** Releases any held resources. */
  void release();

  /**
   * Reads from the given {@link ExtractorInput}.
   *
   * @param input The input to read from.
   * @return Whether there is any data left to extract. Returns false if the end of input has been
   *     reached.
   * @throws IOException If an error occurred reading from or parsing the input.
   */
  boolean read(ExtractorInput input) throws IOException;
}

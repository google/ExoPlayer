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
package androidx.media3.exoplayer.source.chunk;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.List;

/**
 * Extracts samples and track {@link Format Formats} from chunks.
 *
 * <p>The {@link TrackOutputProvider} passed to {@link #init} provides the {@link TrackOutput
 * TrackOutputs} that receive the extracted data.
 */
@UnstableApi
public interface ChunkExtractor {

  /** Creates {@link ChunkExtractor} instances. */
  interface Factory {

    /**
     * Sets the {@link SubtitleParser.Factory} to use for parsing subtitles during extraction. The
     * default factory value is implementation dependent.
     *
     * @param subtitleParserFactory The {@link SubtitleParser.Factory} for parsing subtitles during
     *     extraction.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    default Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      return this;
    }

    /**
     * Sets whether subtitles should be parsed as part of extraction (before being added to the
     * sample queue) or as part of rendering (when being taken from the sample queue). Defaults to
     * {@code false} (i.e. subtitles will be parsed as part of rendering).
     *
     * <p>This method is experimental and will be renamed or removed in a future release.
     *
     * @param parseSubtitlesDuringExtraction Whether to parse subtitles during extraction or
     *     rendering.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    default Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      return this;
    }

    /**
     * Returns the output {@link Format} of emitted {@linkplain C#TRACK_TYPE_TEXT text samples}
     * which were originally in {@code sourceFormat}.
     *
     * <p>In many cases, where an {@link Extractor} emits samples from the source without mutation,
     * this method simply returns {@code sourceFormat}. In other cases, such as an {@link Extractor}
     * that transcodes subtitles from the {@code sourceFormat} to {@link
     * MimeTypes#APPLICATION_MEDIA3_CUES}, the format is updated to indicate the transcoding that is
     * taking place.
     *
     * <p>Non-text source formats are always returned without mutation.
     *
     * @param sourceFormat The original text-based format.
     * @return The {@link Format} that will be associated with a {@linkplain C#TRACK_TYPE_TEXT text
     *     track}.
     */
    default Format getOutputTextFormat(Format sourceFormat) {
      return sourceFormat;
    }

    /**
     * Returns a new {@link ChunkExtractor} instance.
     *
     * @param primaryTrackType The {@link C.TrackType type} of the primary track.
     * @param representationFormat The format of the representation to extract from.
     * @param enableEventMessageTrack Whether to enable the event message track.
     * @param closedCaptionFormats The {@link Format Formats} of the Closed-Caption tracks.
     * @param playerEmsgTrackOutput The {@link TrackOutput} for extracted EMSG messages, or null.
     * @param playerId The {@link PlayerId} of the player using this chunk extractor.
     * @return A new {@link ChunkExtractor} instance, or null if not applicable.
     */
    @Nullable
    ChunkExtractor createProgressiveMediaExtractor(
        @C.TrackType int primaryTrackType,
        Format representationFormat,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable TrackOutput playerEmsgTrackOutput,
        PlayerId playerId);
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
     * @param type The {@link C.TrackType type} of the track.
     * @return The {@link TrackOutput} for the given track identifier.
     */
    TrackOutput track(int id, @C.TrackType int type);
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

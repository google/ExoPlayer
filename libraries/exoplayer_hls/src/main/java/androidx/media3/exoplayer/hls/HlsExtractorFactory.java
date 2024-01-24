/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.media3.exoplayer.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Factory for HLS media chunk extractors. */
@UnstableApi
public interface HlsExtractorFactory {

  HlsExtractorFactory DEFAULT = new DefaultHlsExtractorFactory();

  /**
   * Creates an {@link Extractor} for extracting HLS media chunks.
   *
   * @param uri The URI of the media chunk.
   * @param format A {@link Format} associated with the chunk to extract.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the multivariant playlist.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param responseHeaders The HTTP response headers associated with the media segment or
   *     initialization section to extract.
   * @param sniffingExtractorInput The first extractor input that will be passed to the returned
   *     extractor's {@link Extractor#read(ExtractorInput, PositionHolder)}. Must only be used to
   *     call {@link Extractor#sniff(ExtractorInput)}.
   * @param playerId The {@link PlayerId} of the player using this extractors factory.
   * @return An {@link HlsMediaChunkExtractor}.
   * @throws IOException If an I/O error is encountered while sniffing.
   */
  HlsMediaChunkExtractor createExtractor(
      Uri uri,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput sniffingExtractorInput,
      PlayerId playerId)
      throws IOException;

  /**
   * Sets the {@link SubtitleParser.Factory} to use for parsing subtitles during extraction. The
   * default factory value is implementation dependent.
   *
   * @param subtitleParserFactory The {@link SubtitleParser.Factory} for parsing subtitles during
   *     extraction.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  default HlsExtractorFactory setSubtitleParserFactory(
      SubtitleParser.Factory subtitleParserFactory) {
    return this;
  }

  /**
   * Sets whether subtitles should be parsed as part of extraction (before being added to the sample
   * queue) or as part of rendering (when being taken from the sample queue). Defaults to {@code
   * false} (i.e. subtitles will be parsed as part of rendering).
   *
   * <p>This method is experimental and will be renamed or removed in a future release.
   *
   * @param parseSubtitlesDuringExtraction Whether to parse subtitles during extraction or
   *     rendering.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  default HlsExtractorFactory experimentalParseSubtitlesDuringExtraction(
      boolean parseSubtitlesDuringExtraction) {
    return this;
  }

  /**
   * Returns the output {@link Format} of emitted {@linkplain C#TRACK_TYPE_TEXT text samples} which
   * were originally in {@code sourceFormat}.
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
}

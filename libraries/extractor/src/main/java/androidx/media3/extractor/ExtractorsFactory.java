/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.extractor;

import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Map;

/** Factory for arrays of {@link Extractor} instances. */
@UnstableApi
public interface ExtractorsFactory {

  /**
   * Extractor factory that returns an empty list of extractors. Can be used whenever {@link
   * Extractor Extractors} are not required.
   */
  ExtractorsFactory EMPTY = () -> new Extractor[] {};

  /**
   * Enables transcoding of text track samples to {@link MimeTypes#APPLICATION_MEDIA3_CUES} before
   * the data is emitted to {@link TrackOutput}.
   *
   * <p>Transcoding is disabled by default.
   *
   * <p>This method is experimental and will be renamed or removed in a future release.
   *
   * @param textTrackTranscodingEnabled Whether to enable transcoding.
   * @return The factory, for convenience.
   */
  // TODO: b/289916598 - Flip this to default to enabled and deprecate it.
  @CanIgnoreReturnValue
  default ExtractorsFactory experimentalSetTextTrackTranscodingEnabled(
      boolean textTrackTranscodingEnabled) {
    return this;
  }

  /**
   * Sets a {@link SubtitleParser.Factory} to use when transcoding text tracks.
   *
   * <p>This is only works if {@link #experimentalSetTextTrackTranscodingEnabled(boolean)} is
   * enabled.
   *
   * @param subtitleParserFactory The factory for {@link SubtitleParser} instances.
   * @return The factory, for convenience.
   */
  default ExtractorsFactory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
    return this;
  }

  /** Returns an array of new {@link Extractor} instances. */
  Extractor[] createExtractors();

  /**
   * Returns an array of new {@link Extractor} instances.
   *
   * @param uri The {@link Uri} of the media to extract.
   * @param responseHeaders The response headers of the media to extract, or an empty map if there
   *     are none. The map lookup should be case-insensitive.
   * @return The {@link Extractor} instances.
   */
  default Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
    return createExtractors();
  }
}

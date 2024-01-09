/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.extractor.text;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A wrapping {@link Extractor} that transcodes {@linkplain C#TRACK_TYPE_TEXT text samples} from
 * supported subtitle formats to {@link MimeTypes#APPLICATION_MEDIA3_CUES}.
 *
 * <p>Samples emitted by the delegate {@link Extractor} to {@linkplain C#TRACK_TYPE_TEXT text
 * tracks} with a supported subtitle format are transcoded and the resulting {@link
 * MimeTypes#APPLICATION_MEDIA3_CUES} samples are emitted to the underlying {@link TrackOutput}.
 *
 * <p>Samples emitted by the delegate {@link Extractor} to non-text tracks (or text tracks with an
 * unsupported format) are passed through to the underlying {@link TrackOutput} without
 * modification.
 *
 * <p>Support for subtitle formats is determined by {@link
 * SubtitleParser.Factory#supportsFormat(Format)} on the {@link SubtitleParser.Factory} passed to
 * the constructor of this class.
 */
// TODO: b/318679808 - deprecate when all subtitle-related Extractors use
// SubtitleTranscodingExtractorOutput instead.
@UnstableApi
public class SubtitleTranscodingExtractor implements Extractor {

  private final Extractor delegate;
  private final SubtitleParser.Factory subtitleParserFactory;

  private @MonotonicNonNull SubtitleTranscodingExtractorOutput transcodingExtractorOutput;

  public SubtitleTranscodingExtractor(
      Extractor delegate, SubtitleParser.Factory subtitleParserFactory) {
    this.delegate = delegate;
    this.subtitleParserFactory = subtitleParserFactory;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return delegate.sniff(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    transcodingExtractorOutput =
        new SubtitleTranscodingExtractorOutput(output, subtitleParserFactory);
    delegate.init(transcodingExtractorOutput);
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    return delegate.read(input, seekPosition);
  }

  @Override
  public void seek(long position, long timeUs) {
    if (transcodingExtractorOutput != null) {
      transcodingExtractorOutput.resetSubtitleParsers();
    }
    delegate.seek(position, timeUs);
  }

  @Override
  public void release() {
    delegate.release();
  }

  @Override
  public Extractor getUnderlyingImplementation() {
    return delegate;
  }
}

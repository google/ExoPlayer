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
package com.google.android.exoplayer2.source.hls;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac4Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.text.SubtitleParser;
import com.google.android.exoplayer2.text.SubtitleTranscodingExtractor;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.IOException;

/**
 * {@link HlsMediaChunkExtractor} implementation that uses ExoPlayer app-bundled {@link Extractor
 * Extractors}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class BundledHlsMediaChunkExtractor implements HlsMediaChunkExtractor {

  private static final PositionHolder POSITION_HOLDER = new PositionHolder();

  @VisibleForTesting /* package */ final Extractor extractor;
  private final Format multivariantPlaylistFormat;
  private final TimestampAdjuster timestampAdjuster;
  @Nullable private final SubtitleParser.Factory subtitleParserFactory;

  /**
   * Creates a new instance.
   *
   * @param extractor The underlying {@link Extractor}.
   * @param multivariantPlaylistFormat The {@link Format} obtained from the multivariant playlist.
   * @param timestampAdjuster A {@link TimestampAdjuster} to adjust sample timestamps.
   */
  public BundledHlsMediaChunkExtractor(
      Extractor extractor, Format multivariantPlaylistFormat, TimestampAdjuster timestampAdjuster) {
    this(
        extractor,
        multivariantPlaylistFormat,
        timestampAdjuster,
        /* subtitleParserFactory= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param extractor The underlying {@link Extractor}.
   * @param multivariantPlaylistFormat The {@link Format} obtained from the multivariant playlist.
   * @param timestampAdjuster A {@link TimestampAdjuster} to adjust sample timestamps.
   * @param subtitleParserFactory A {@link SubtitleParser.Factory} to be used with WebVTT subtitles.
   *     If the value is null, subtitles will be parsed during decoding, otherwise - during
   *     extraction. Decoding will only work if this subtitleParserFactory supports the provided
   *     multivariantPlaylistFormat.
   */
  // TODO(b/289983417): Once the subtitle-parsing-during-extraction is the only available flow, make
  // this constructor public and remove @Nullable from subtitleParserFactory
  /* package */ BundledHlsMediaChunkExtractor(
      Extractor extractor,
      Format multivariantPlaylistFormat,
      TimestampAdjuster timestampAdjuster,
      @Nullable SubtitleParser.Factory subtitleParserFactory) {
    this.extractor = extractor;
    this.multivariantPlaylistFormat = multivariantPlaylistFormat;
    this.timestampAdjuster = timestampAdjuster;
    this.subtitleParserFactory = subtitleParserFactory;
  }

  @Override
  public void init(ExtractorOutput extractorOutput) {
    extractor.init(extractorOutput);
  }

  @Override
  public boolean read(ExtractorInput extractorInput) throws IOException {
    return extractor.read(extractorInput, POSITION_HOLDER) == Extractor.RESULT_CONTINUE;
  }

  @Override
  public boolean isPackedAudioExtractor() {
    Extractor underlyingExtractor = extractor.getUnderlyingImplementation();
    return underlyingExtractor instanceof AdtsExtractor
        || underlyingExtractor instanceof Ac3Extractor
        || underlyingExtractor instanceof Ac4Extractor
        || underlyingExtractor instanceof Mp3Extractor;
  }

  @Override
  public boolean isReusable() {
    Extractor underlyingExtractor = extractor.getUnderlyingImplementation();
    return underlyingExtractor instanceof TsExtractor
        || underlyingExtractor instanceof FragmentedMp4Extractor;
  }

  @Override
  public HlsMediaChunkExtractor recreate() {
    checkState(!isReusable());
    checkState(
        extractor.getUnderlyingImplementation() == extractor,
        "Can't recreate wrapped extractors. Outer type: " + extractor.getClass());
    Extractor newExtractorInstance;
    // LINT.IfChange(extractor_instantiation)
    if (extractor instanceof WebvttExtractor) {
      newExtractorInstance =
          new WebvttExtractor(multivariantPlaylistFormat.language, timestampAdjuster);
      if (subtitleParserFactory != null
          && subtitleParserFactory.supportsFormat(multivariantPlaylistFormat)) {
        newExtractorInstance =
            new SubtitleTranscodingExtractor(newExtractorInstance, subtitleParserFactory);
      }
    } else if (extractor instanceof AdtsExtractor) {
      newExtractorInstance = new AdtsExtractor();
    } else if (extractor instanceof Ac3Extractor) {
      newExtractorInstance = new Ac3Extractor();
    } else if (extractor instanceof Ac4Extractor) {
      newExtractorInstance = new Ac4Extractor();
    } else if (extractor instanceof Mp3Extractor) {
      newExtractorInstance = new Mp3Extractor();
    } else {
      throw new IllegalStateException(
          "Unexpected extractor type for recreation: " + extractor.getClass().getSimpleName());
    }
    return new BundledHlsMediaChunkExtractor(
        newExtractorInstance, multivariantPlaylistFormat, timestampAdjuster, subtitleParserFactory);
  }

  @Override
  public void onTruncatedSegmentParsed() {
    extractor.seek(/* position= */ 0, /* timeUs= */ 0);
  }
}

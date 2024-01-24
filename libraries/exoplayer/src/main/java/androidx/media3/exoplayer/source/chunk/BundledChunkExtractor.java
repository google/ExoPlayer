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
package androidx.media3.exoplayer.source.chunk;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.ChunkIndex;
import androidx.media3.extractor.DummyTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.jpeg.JpegExtractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.png.PngExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleExtractor;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleTranscodingExtractor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@link ChunkExtractor} implementation that uses ExoPlayer app-bundled {@link Extractor
 * Extractors}.
 */
@UnstableApi
public final class BundledChunkExtractor implements ExtractorOutput, ChunkExtractor {

  /** {@link ChunkExtractor.Factory} for {@link BundledChunkExtractor}. */
  public static final class Factory implements ChunkExtractor.Factory {

    private SubtitleParser.Factory subtitleParserFactory;
    private boolean parseSubtitlesDuringExtraction;

    public Factory() {
      subtitleParserFactory = new DefaultSubtitleParserFactory();
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      this.subtitleParserFactory = checkNotNull(subtitleParserFactory);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
      return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation performs transcoding of the original format to {@link
     * MimeTypes#APPLICATION_MEDIA3_CUES} if it is supported by {@link SubtitleParser.Factory}.
     *
     * <p>To modify the support behavior, you can {@linkplain
     * #setSubtitleParserFactory(SubtitleParser.Factory) set your own subtitle parser factory}.
     */
    @Override
    public Format getOutputTextFormat(Format sourceFormat) {
      if (parseSubtitlesDuringExtraction && subtitleParserFactory.supportsFormat(sourceFormat)) {
        return sourceFormat
            .buildUpon()
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCueReplacementBehavior(
                subtitleParserFactory.getCueReplacementBehavior(sourceFormat))
            .setCodecs(
                sourceFormat.sampleMimeType
                    + (sourceFormat.codecs != null ? " " + sourceFormat.codecs : ""))
            .setSubsampleOffsetUs(Format.OFFSET_SAMPLE_RELATIVE)
            .build();
      } else {
        return sourceFormat;
      }
    }

    @Nullable
    @Override
    public ChunkExtractor createProgressiveMediaExtractor(
        @C.TrackType int primaryTrackType,
        Format representationFormat,
        boolean enableEventMessageTrack,
        List<Format> closedCaptionFormats,
        @Nullable TrackOutput playerEmsgTrackOutput,
        PlayerId playerId) {
      @Nullable String containerMimeType = representationFormat.containerMimeType;
      Extractor extractor;
      if (MimeTypes.isText(containerMimeType)) {
        if (!parseSubtitlesDuringExtraction) {
          // Subtitles will be parsed after decoding
          return null;
        } else {
          extractor =
              new SubtitleExtractor(
                  subtitleParserFactory.create(representationFormat), representationFormat);
        }
      } else if (MimeTypes.isMatroska(containerMimeType)) {
        @MatroskaExtractor.Flags int flags = MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES;
        if (!parseSubtitlesDuringExtraction) {
          flags |= MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
        }
        extractor = new MatroskaExtractor(subtitleParserFactory, flags);
      } else if (Objects.equals(containerMimeType, MimeTypes.IMAGE_JPEG)) {
        extractor = new JpegExtractor(JpegExtractor.FLAG_READ_IMAGE);
      } else if (Objects.equals(containerMimeType, MimeTypes.IMAGE_PNG)) {
        extractor = new PngExtractor();
      } else {
        @FragmentedMp4Extractor.Flags int flags = 0;
        if (enableEventMessageTrack) {
          flags |= FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK;
        }
        if (!parseSubtitlesDuringExtraction) {
          flags |= FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
        }
        extractor =
            new FragmentedMp4Extractor(
                subtitleParserFactory,
                flags,
                /* timestampAdjuster= */ null,
                /* sideloadedTrack= */ null,
                closedCaptionFormats,
                playerEmsgTrackOutput);
      }
      if (parseSubtitlesDuringExtraction
          && !MimeTypes.isText(containerMimeType)
          && !(extractor.getUnderlyingImplementation() instanceof FragmentedMp4Extractor)
          && !(extractor.getUnderlyingImplementation() instanceof MatroskaExtractor)) {
        extractor = new SubtitleTranscodingExtractor(extractor, subtitleParserFactory);
      }
      return new BundledChunkExtractor(extractor, primaryTrackType, representationFormat);
    }
  }

  /** {@link Factory} for {@link BundledChunkExtractor}. */
  public static final Factory FACTORY = new Factory();

  private static final PositionHolder POSITION_HOLDER = new PositionHolder();

  private final Extractor extractor;
  private final @C.TrackType int primaryTrackType;
  private final Format primaryTrackManifestFormat;
  private final SparseArray<BindingTrackOutput> bindingTrackOutputs;

  private boolean extractorInitialized;
  @Nullable private TrackOutputProvider trackOutputProvider;
  private long endTimeUs;
  private @MonotonicNonNull SeekMap seekMap;
  private Format @MonotonicNonNull [] sampleFormats;

  /**
   * Creates an instance.
   *
   * @param extractor The extractor to wrap.
   * @param primaryTrackType The {@link C.TrackType type} of the primary track.
   * @param primaryTrackManifestFormat A manifest defined {@link Format} whose data should be merged
   *     into any sample {@link Format} output from the {@link Extractor} for the primary track.
   */
  public BundledChunkExtractor(
      Extractor extractor, @C.TrackType int primaryTrackType, Format primaryTrackManifestFormat) {
    this.extractor = extractor;
    this.primaryTrackType = primaryTrackType;
    this.primaryTrackManifestFormat = primaryTrackManifestFormat;
    bindingTrackOutputs = new SparseArray<>();
  }

  // ChunkExtractor implementation.

  @Override
  @Nullable
  public ChunkIndex getChunkIndex() {
    return seekMap instanceof ChunkIndex ? (ChunkIndex) seekMap : null;
  }

  @Override
  @Nullable
  public Format[] getSampleFormats() {
    return sampleFormats;
  }

  @Override
  public void init(
      @Nullable TrackOutputProvider trackOutputProvider, long startTimeUs, long endTimeUs) {
    this.trackOutputProvider = trackOutputProvider;
    this.endTimeUs = endTimeUs;
    if (!extractorInitialized) {
      extractor.init(this);
      if (startTimeUs != C.TIME_UNSET) {
        extractor.seek(/* position= */ 0, startTimeUs);
      }
      extractorInitialized = true;
    } else {
      extractor.seek(/* position= */ 0, startTimeUs == C.TIME_UNSET ? 0 : startTimeUs);
      for (int i = 0; i < bindingTrackOutputs.size(); i++) {
        bindingTrackOutputs.valueAt(i).bind(trackOutputProvider, endTimeUs);
      }
    }
  }

  @Override
  public void release() {
    extractor.release();
  }

  @Override
  public boolean read(ExtractorInput input) throws IOException {
    int result = extractor.read(input, POSITION_HOLDER);
    Assertions.checkState(result != Extractor.RESULT_SEEK);
    return result == Extractor.RESULT_CONTINUE;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id, int type) {
    BindingTrackOutput bindingTrackOutput = bindingTrackOutputs.get(id);
    if (bindingTrackOutput == null) {
      // Assert that if we're seeing a new track we have not seen endTracks.
      Assertions.checkState(sampleFormats == null);
      // TODO: Manifest formats for embedded tracks should also be passed here.
      bindingTrackOutput =
          new BindingTrackOutput(
              id, type, type == primaryTrackType ? primaryTrackManifestFormat : null);
      bindingTrackOutput.bind(trackOutputProvider, endTimeUs);
      bindingTrackOutputs.put(id, bindingTrackOutput);
    }
    return bindingTrackOutput;
  }

  @Override
  public void endTracks() {
    Format[] sampleFormats = new Format[bindingTrackOutputs.size()];
    for (int i = 0; i < bindingTrackOutputs.size(); i++) {
      sampleFormats[i] = Assertions.checkStateNotNull(bindingTrackOutputs.valueAt(i).sampleFormat);
    }
    this.sampleFormats = sampleFormats;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  // Internal logic.

  private static final class BindingTrackOutput implements TrackOutput {

    private final int id;
    private final int type;
    @Nullable private final Format manifestFormat;
    private final DummyTrackOutput fakeTrackOutput;

    public @MonotonicNonNull Format sampleFormat;
    private @MonotonicNonNull TrackOutput trackOutput;
    private long endTimeUs;

    public BindingTrackOutput(int id, int type, @Nullable Format manifestFormat) {
      this.id = id;
      this.type = type;
      this.manifestFormat = manifestFormat;
      fakeTrackOutput = new DummyTrackOutput();
    }

    public void bind(@Nullable TrackOutputProvider trackOutputProvider, long endTimeUs) {
      if (trackOutputProvider == null) {
        trackOutput = fakeTrackOutput;
        return;
      }
      this.endTimeUs = endTimeUs;
      trackOutput = trackOutputProvider.track(id, type);
      if (sampleFormat != null) {
        trackOutput.format(sampleFormat);
      }
    }

    @Override
    public void format(Format format) {
      sampleFormat =
          manifestFormat != null ? format.withManifestFormatInfo(manifestFormat) : format;
      castNonNull(trackOutput).format(sampleFormat);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      return castNonNull(trackOutput).sampleData(input, length, allowEndOfInput);
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      castNonNull(trackOutput).sampleData(data, length);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      if (endTimeUs != C.TIME_UNSET && timeUs >= endTimeUs) {
        trackOutput = fakeTrackOutput;
      }
      castNonNull(trackOutput).sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }
  }
}

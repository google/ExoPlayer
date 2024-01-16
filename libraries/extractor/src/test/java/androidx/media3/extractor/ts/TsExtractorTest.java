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
package androidx.media3.extractor.ts;

import static androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;
import static androidx.media3.extractor.ts.TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES;
import static androidx.media3.extractor.ts.TsExtractor.MODE_MULTI_PMT;
import static androidx.media3.extractor.ts.TsExtractor.MODE_SINGLE_PMT;
import static com.google.common.truth.Truth.assertThat;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.TsPayloadReader.EsInfo;
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator;
import androidx.media3.test.utils.ExtractorAsserts;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link TsExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class TsExtractorTest {

  @Parameters(name = "{0},subtitlesParsedDuringExtraction={1}")
  public static List<Object[]> params() {
    List<Object[]> parameterList = new ArrayList<>();
    for (ExtractorAsserts.SimulationConfig config : ExtractorAsserts.configs()) {
      parameterList.add(new Object[] {config, /* subtitlesParsedDuringExtraction */ true});
      parameterList.add(new Object[] {config, /* subtitlesParsedDuringExtraction */ false});
    }
    return parameterList;
  }

  @Parameter(0)
  public ExtractorAsserts.SimulationConfig simulationConfig;

  @Parameter(1)
  public boolean subtitlesParsedDuringExtraction;

  @Test
  public void sampleWithH262AndMpegAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h262_mpeg_audio.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH263() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h263.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH264() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h264.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH264AndMpegAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h264_mpeg_audio.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH264NoAccessUnitDelimiters() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(
            subtitlesParsedDuringExtraction,
            MODE_SINGLE_PMT,
            new TimestampAdjuster(0),
            new DefaultTsPayloadReaderFactory(FLAG_DETECT_ACCESS_UNITS)),
        "media/ts/sample_h264_no_access_unit_delimiters.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH264AndDtsAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(
            subtitlesParsedDuringExtraction,
            MODE_SINGLE_PMT,
            new TimestampAdjuster(0),
            new DefaultTsPayloadReaderFactory(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)),
        "media/ts/sample_h264_dts_audio.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH265() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h265.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithH265RpsPred() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_h265_rps_pred.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithScte35() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_scte35.ts",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDeduplicateConsecutiveFormats(true)
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithAit() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_ait.ts",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDeduplicateConsecutiveFormats(true)
            .build(),
        simulationConfig);
  }

  @Test
  public void sampleWithDts() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_dts.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithDtsHd() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_dts_hd.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithDtsUhd() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_dts_uhd.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithAc3() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_ac3.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithAc4() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_ac4.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithEac3() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_eac3.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithEac3joc() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_eac3joc.ts",
        simulationConfig);
  }

  @Test
  public void sampleWithLatm() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_latm.ts",
        simulationConfig);
  }

  @Test
  public void streamWithJunkData() throws Exception {
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction),
        "media/ts/sample_with_junk",
        simulationConfig);
  }

  @Test
  public void customPesReader() throws Exception {
    CustomTsPayloadReaderFactory factory = new CustomTsPayloadReaderFactory(true, false);
    TsExtractor tsExtractor =
        (TsExtractor)
            getExtractorFactory(
                    subtitlesParsedDuringExtraction,
                    MODE_MULTI_PMT,
                    new TimestampAdjuster(0),
                    factory)
                .create();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_h262_mpeg_audio.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }
    CustomEsReader reader = factory.esReader;
    assertThat(reader.packetsRead).isEqualTo(2);
    TrackOutput trackOutput = reader.getTrackOutput();
    assertThat(trackOutput == output.trackOutputs.get(257 /* PID of audio track. */)).isTrue();
    assertThat(((FakeTrackOutput) trackOutput).lastFormat)
        .isEqualTo(
            new Format.Builder()
                .setId("1/257")
                .setSampleMimeType("mime")
                .setLanguage("und")
                .build());
  }

  @Test
  public void customInitialSectionReader() throws Exception {
    CustomTsPayloadReaderFactory factory = new CustomTsPayloadReaderFactory(false, true);
    TsExtractor tsExtractor =
        (TsExtractor)
            getExtractorFactory(
                    subtitlesParsedDuringExtraction,
                    MODE_MULTI_PMT,
                    new TimestampAdjuster(0),
                    factory)
                .create();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(), "media/ts/sample_with_sdt.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();
    tsExtractor.init(new FakeExtractorOutput());
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }
    assertThat(factory.sdtReader.consumedSdts).isEqualTo(2);
  }

  private static ExtractorAsserts.ExtractorFactory getExtractorFactory(
      boolean subtitlesParsedDuringExtraction) {
    return getExtractorFactory(
        subtitlesParsedDuringExtraction,
        MODE_SINGLE_PMT,
        new TimestampAdjuster(0),
        new DefaultTsPayloadReaderFactory(0));
  }

  private static ExtractorAsserts.ExtractorFactory getExtractorFactory(
      boolean subtitlesParsedDuringExtraction,
      @TsExtractor.Mode int mode,
      TimestampAdjuster timestampAdjuster,
      TsPayloadReader.Factory payloadReaderFactory) {
    SubtitleParser.Factory subtitleParserFactory;
    @TsExtractor.Flags int flags;
    if (subtitlesParsedDuringExtraction) {
      subtitleParserFactory = new DefaultSubtitleParserFactory();
      flags = 0;
    } else {
      subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;
      flags = FLAG_EMIT_RAW_SUBTITLE_DATA;
    }
    return () ->
        new TsExtractor(
            mode,
            flags,
            subtitleParserFactory,
            timestampAdjuster,
            payloadReaderFactory,
            DEFAULT_TIMESTAMP_SEARCH_BYTES);
  }

  private static final class CustomTsPayloadReaderFactory implements TsPayloadReader.Factory {

    private final boolean provideSdtReader;
    private final boolean provideCustomEsReader;
    private final TsPayloadReader.Factory defaultFactory;
    private CustomEsReader esReader;
    private SdtSectionReader sdtReader;

    public CustomTsPayloadReaderFactory(boolean provideCustomEsReader, boolean provideSdtReader) {
      this.provideCustomEsReader = provideCustomEsReader;
      this.provideSdtReader = provideSdtReader;
      defaultFactory = new DefaultTsPayloadReaderFactory();
    }

    @Override
    public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
      if (provideSdtReader) {
        assertThat(sdtReader).isNull();
        SparseArray<TsPayloadReader> mapping = new SparseArray<>();
        sdtReader = new SdtSectionReader();
        mapping.put(17, new SectionReader(sdtReader));
        return mapping;
      } else {
        return defaultFactory.createInitialPayloadReaders();
      }
    }

    @Override
    @Nullable
    public TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo) {
      if (provideCustomEsReader && streamType == 3) {
        esReader = new CustomEsReader(esInfo.language);
        return new PesReader(esReader);
      } else {
        return defaultFactory.createPayloadReader(streamType, esInfo);
      }
    }
  }

  private static final class CustomEsReader implements ElementaryStreamReader {

    private final String language;
    private TrackOutput output;
    public int packetsRead = 0;

    public CustomEsReader(String language) {
      this.language = language;
    }

    @Override
    public void seek() {}

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
      idGenerator.generateNewId();
      output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_UNKNOWN);
      output.format(
          new Format.Builder()
              .setId(idGenerator.getFormatId())
              .setSampleMimeType("mime")
              .setLanguage(language)
              .build());
    }

    @Override
    public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {}

    @Override
    public void consume(ParsableByteArray data) {}

    @Override
    public void packetFinished(boolean isEndOfInput) {
      packetsRead++;
    }

    public TrackOutput getTrackOutput() {
      return output;
    }
  }

  private static final class SdtSectionReader implements SectionPayloadReader {

    private int consumedSdts;

    @Override
    public void init(
        TimestampAdjuster timestampAdjuster,
        ExtractorOutput extractorOutput,
        TrackIdGenerator idGenerator) {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray sectionData) {
      // table_id(8), section_syntax_indicator(1), reserved_future_use(1), reserved(2),
      // section_length(12), transport_stream_id(16), reserved(2), version_number(5),
      // current_next_indicator(1), section_number(8), last_section_number(8),
      // original_network_id(16), reserved_future_use(8)
      sectionData.skipBytes(11);
      // Start of the service loop.
      assertThat(sectionData.readUnsignedShort()).isEqualTo(0x5566 /* arbitrary service id */);
      // reserved_future_use(6), EIT_schedule_flag(1), EIT_present_following_flag(1)
      sectionData.skipBytes(1);
      // Assert there is only one service.
      // Remove running_status(3), free_CA_mode(1) from the descriptors_loop_length with the mask.
      assertThat(sectionData.readUnsignedShort() & 0xFFF).isEqualTo(sectionData.bytesLeft());
      while (sectionData.bytesLeft() > 0) {
        int descriptorTag = sectionData.readUnsignedByte();
        int descriptorLength = sectionData.readUnsignedByte();
        if (descriptorTag == 72 /* service descriptor */) {
          assertThat(sectionData.readUnsignedByte()).isEqualTo(1); // Service type: Digital TV.
          int serviceProviderNameLength = sectionData.readUnsignedByte();
          assertThat(sectionData.readString(serviceProviderNameLength)).isEqualTo("Some provider");
          int serviceNameLength = sectionData.readUnsignedByte();
          assertThat(sectionData.readString(serviceNameLength)).isEqualTo("Some Channel");
        } else {
          sectionData.skipBytes(descriptorLength);
        }
      }
      consumedSdts++;
    }
  }
}

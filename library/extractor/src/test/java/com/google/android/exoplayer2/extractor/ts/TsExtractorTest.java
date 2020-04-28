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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
import static com.google.common.truth.Truth.assertThat;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link TsExtractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class TsExtractorTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter(0)
  public ExtractorAsserts.Config assertionConfig;

  @Test
  public void sampleWithH262AndMpegAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        TsExtractor::new, "ts/sample_h262_mpeg_audio.ts", assertionConfig);
  }

  @Test
  public void sampleWithH264AndMpegAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        TsExtractor::new, "ts/sample_h264_mpeg_audio.ts", assertionConfig);
  }

  @Test
  public void sampleWithH264NoAccessUnitDelimiters() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new TsExtractor(FLAG_DETECT_ACCESS_UNITS),
        "ts/sample_h264_no_access_unit_delimiters.ts",
        assertionConfig);
  }

  @Test
  public void sampleWithH264AndDtsAudio() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new TsExtractor(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS),
        "ts/sample_h264_dts_audio.ts",
        assertionConfig);
  }

  @Test
  public void sampleWithH265() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_h265.ts", assertionConfig);
  }

  @Test
  @Ignore
  // TODO(internal: b/153539929) Re-enable when ExtractorAsserts is less strict around repeated
  // formats and seeking.
  public void sampleWithScte35() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_scte35.ts", assertionConfig);
  }

  @Test
  @Ignore
  // TODO(internal: b/153539929) Re-enable when ExtractorAsserts is less strict around repeated
  // formats and seeking.
  public void sampleWithAit() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_ait.ts", assertionConfig);
  }

  @Test
  public void sampleWithAc3() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_ac3.ts", assertionConfig);
  }

  @Test
  public void sampleWithAc4() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_ac4.ts", assertionConfig);
  }

  @Test
  public void sampleWithEac3() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_eac3.ts", assertionConfig);
  }

  @Test
  public void sampleWithEac3joc() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_eac3joc.ts", assertionConfig);
  }

  @Test
  public void sampleWithLatm() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_latm.ts", assertionConfig);
  }

  @Test
  public void streamWithJunkData() throws Exception {
    ExtractorAsserts.assertBehavior(TsExtractor::new, "ts/sample_with_junk", assertionConfig);
  }

  @Test
  public void customPesReader() throws Exception {
    CustomTsPayloadReaderFactory factory = new CustomTsPayloadReaderFactory(true, false);
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_MULTI_PMT, new TimestampAdjuster(0), factory);
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(), "ts/sample_h262_mpeg_audio.ts"))
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
        new TsExtractor(TsExtractor.MODE_MULTI_PMT, new TimestampAdjuster(0), factory);
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(), "ts/sample_with_sdt.ts"))
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
    public void packetFinished() {
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

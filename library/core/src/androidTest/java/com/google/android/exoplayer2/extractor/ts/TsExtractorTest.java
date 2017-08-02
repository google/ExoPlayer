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

import android.test.InstrumentationTestCase;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.ExtractorAsserts.ExtractorFactory;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Unit test for {@link TsExtractor}.
 */
public final class TsExtractorTest extends InstrumentationTestCase {

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.

  public void testSample() throws Exception {
    ExtractorAsserts.assertBehavior(new ExtractorFactory() {
      @Override
      public Extractor create() {
        return new TsExtractor();
      }
    }, "ts/sample.ts", getInstrumentation());
  }

  public void testIncompleteSample() throws Exception {
    Random random = new Random(0);
    byte[] fileData = TestUtil.getByteArray(getInstrumentation(), "ts/sample.ts");
    ByteArrayOutputStream out = new ByteArrayOutputStream(fileData.length * 2);
    writeJunkData(out, random.nextInt(TS_PACKET_SIZE - 1) + 1);
    out.write(fileData, 0, TS_PACKET_SIZE * 5);
    for (int i = TS_PACKET_SIZE * 5; i < fileData.length; i += TS_PACKET_SIZE) {
      writeJunkData(out, random.nextInt(TS_PACKET_SIZE));
      out.write(fileData, i, TS_PACKET_SIZE);
    }
    out.write(TS_SYNC_BYTE);
    writeJunkData(out, random.nextInt(TS_PACKET_SIZE - 1) + 1);
    fileData = out.toByteArray();

    ExtractorAsserts.assertOutput(new ExtractorFactory() {
      @Override
      public Extractor create() {
        return new TsExtractor();
      }
    }, "ts/sample.ts", fileData, getInstrumentation());
  }

  public void testCustomPesReader() throws Exception {
    CustomTsPayloadReaderFactory factory = new CustomTsPayloadReaderFactory(true, false);
    TsExtractor tsExtractor = new TsExtractor(TsExtractor.MODE_MULTI_PMT, new TimestampAdjuster(0),
        factory);
    FakeExtractorInput input = new FakeExtractorInput.Builder()
        .setData(TestUtil.getByteArray(getInstrumentation(), "ts/sample.ts"))
        .setSimulateIOErrors(false)
        .setSimulateUnknownLength(false)
        .setSimulatePartialReads(false).build();
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
    }
    CustomEsReader reader = factory.esReader;
    assertEquals(2, reader.packetsRead);
    TrackOutput trackOutput = reader.getTrackOutput();
    assertTrue(trackOutput == output.trackOutputs.get(257 /* PID of audio track. */));
    assertEquals(
        Format.createTextSampleFormat("1/257", "mime", null, 0, 0, "und", null, 0),
        ((FakeTrackOutput) trackOutput).format);
  }

  public void testCustomInitialSectionReader() throws Exception {
    CustomTsPayloadReaderFactory factory = new CustomTsPayloadReaderFactory(false, true);
    TsExtractor tsExtractor = new TsExtractor(TsExtractor.MODE_MULTI_PMT, new TimestampAdjuster(0),
        factory);
    FakeExtractorInput input = new FakeExtractorInput.Builder()
        .setData(TestUtil.getByteArray(getInstrumentation(), "ts/sample_with_sdt.ts"))
        .setSimulateIOErrors(false)
        .setSimulateUnknownLength(false)
        .setSimulatePartialReads(false).build();
    tsExtractor.init(new FakeExtractorOutput());
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
    }
    assertEquals(1, factory.sdtReader.consumedSdts);
  }

  private static void writeJunkData(ByteArrayOutputStream out, int length) {
    for (int i = 0; i < length; i++) {
      if (((byte) i) == TS_SYNC_BYTE) {
        out.write(0);
      } else {
        out.write(i);
      }
    }
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
        assertNull(sdtReader);
        SparseArray<TsPayloadReader> mapping = new SparseArray<>();
        sdtReader = new SdtSectionReader();
        mapping.put(17, new SectionReader(sdtReader));
        return mapping;
      } else {
        return defaultFactory.createInitialPayloadReaders();
      }
    }

    @Override
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
    public void seek() {
    }

    @Override
    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
      idGenerator.generateNewId();
      output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_UNKNOWN);
      output.format(Format.createTextSampleFormat(idGenerator.getFormatId(), "mime", null, 0, 0,
          language, null, 0));
    }

    @Override
    public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    }

    @Override
    public void consume(ParsableByteArray data) {
    }

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
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
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
      assertEquals(0x5566 /* arbitrary service id */, sectionData.readUnsignedShort());
      // reserved_future_use(6), EIT_schedule_flag(1), EIT_present_following_flag(1)
      sectionData.skipBytes(1);
      // Assert there is only one service.
      // Remove running_status(3), free_CA_mode(1) from the descriptors_loop_length with the mask.
      assertEquals(sectionData.readUnsignedShort() & 0xFFF, sectionData.bytesLeft());
      while (sectionData.bytesLeft() > 0) {
        int descriptorTag = sectionData.readUnsignedByte();
        int descriptorLength = sectionData.readUnsignedByte();
        if (descriptorTag == 72 /* service descriptor */) {
          assertEquals(1, sectionData.readUnsignedByte()); // Service type: Digital TV.
          int serviceProviderNameLength = sectionData.readUnsignedByte();
          assertEquals("Some provider", sectionData.readString(serviceProviderNameLength));
          int serviceNameLength = sectionData.readUnsignedByte();
          assertEquals("Some Channel", sectionData.readString(serviceNameLength));
        } else {
          sectionData.skipBytes(descriptorLength);
        }
      }
      consumedSdts++;
    }

  }

}

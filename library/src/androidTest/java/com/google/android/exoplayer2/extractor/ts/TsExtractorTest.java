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
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TimestampAdjuster;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.ElementaryStreamReader.EsInfo;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Unit test for {@link TsExtractor}.
 */
public final class TsExtractorTest extends InstrumentationTestCase {

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.

  public void testSample() throws Exception {
    TestUtil.assertOutput(new TestUtil.ExtractorFactory() {
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

    TestUtil.assertOutput(new TestUtil.ExtractorFactory() {
      @Override
      public Extractor create() {
        return new TsExtractor();
      }
    }, "ts/sample.ts", fileData, getInstrumentation());
  }

  public void testCustomPesReader() throws Exception {
    CustomEsReaderFactory factory = new CustomEsReaderFactory();
    TsExtractor tsExtractor = new TsExtractor(new TimestampAdjuster(0), factory, false);
    FakeExtractorInput input = new FakeExtractorInput.Builder()
        .setData(TestUtil.getByteArray(getInstrumentation(), "ts/sample.ts"))
        .setSimulateIOErrors(false)
        .setSimulateUnknownLength(false)
        .setSimulatePartialReads(false).build();
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);
    tsExtractor.seek(input.getPosition());
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
    }
    CustomEsReader reader = factory.reader;
    assertEquals(2, reader.packetsRead);
    TrackOutput trackOutput = reader.getTrackOutput();
    assertTrue(trackOutput == output.trackOutputs.get(257 /* PID of audio track. */));
    assertEquals(
        Format.createTextSampleFormat("Overriding format", "mime", null, 0, 0, "und", null, 0),
        ((FakeTrackOutput) trackOutput).format);
  }

  private static void writeJunkData(ByteArrayOutputStream out, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      if (((byte) i) == TS_SYNC_BYTE) {
        out.write(0);
      } else {
        out.write(i);
      }
    }
  }

  private static final class CustomEsReader extends ElementaryStreamReader {

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
    public void init(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
      output = extractorOutput.track(idGenerator.getNextId());
      output.format(Format.createTextSampleFormat("Overriding format", "mime", null, 0, 0,
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

  private static final class CustomEsReaderFactory implements ElementaryStreamReader.Factory {

    private final ElementaryStreamReader.Factory defaultFactory;
    private CustomEsReader reader;

    public CustomEsReaderFactory() {
      defaultFactory = new DefaultStreamReaderFactory();
    }

    @Override
    public ElementaryStreamReader createStreamReader(int streamType, EsInfo esInfo) {
      if (streamType == 3) {
        reader = new CustomEsReader(esInfo.language);
        return reader;
      } else {
        return defaultFactory.createStreamReader(streamType, esInfo);
      }
    }

  }

}

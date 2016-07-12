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
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.TestUtil;
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

  private static void writeJunkData(ByteArrayOutputStream out, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      if (((byte) i) == TS_SYNC_BYTE) {
        out.write(0);
      } else {
        out.write(i);
      }
    }
  }

}

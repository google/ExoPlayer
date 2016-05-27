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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;

import android.test.InstrumentationTestCase;

/**
 * Unit test for {@link OpusReader}.
 */
public final class OggExtractorFileTests extends InstrumentationTestCase {

  private static final String OPUS_TEST_FILE = "ogg/bear.opus";
  private static final String FLAC_TEST_FILE = "ogg/bear_flac.ogg";
  private static final String FLAC_NS_TEST_FILE = "ogg/bear_flac_noseektable.ogg";
  private static final String VORBIS_TEST_FILE = "ogg/bear_vorbis.ogg";
  private static final String DUMP_EXTENSION = ".dump";
  private static final String UNKNOWN_LENGTH_EXTENSION = ".unklen";

  public void testOpus() throws Exception {
    parseFile(OPUS_TEST_FILE);
  }

  public void testFlac() throws Exception {
    for (int i = 0; i < 8; i++) {
      testFlac((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
    }
  }

  private void testFlac(boolean simulateIOErrors, boolean simulateUnknownLength,
      boolean simulatePartialReads) throws Exception {
    FakeExtractorOutput extractorOutput = parseFile(FLAC_TEST_FILE, simulateIOErrors,
        simulateUnknownLength, simulatePartialReads);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    for (int i = 0; i < 33; i++) {
      byte[] sampleData = trackOutput.getSampleData(i);
      assertTrue(FlacReader.isAudioPacket(sampleData));
    }
  }

  public void testFlacNoSeektable() throws Exception {
    parseFile(FLAC_NS_TEST_FILE);
  }

  public void testVorbis() throws Exception {
    parseFile(VORBIS_TEST_FILE);
  }

  private void parseFile(String testFile) throws Exception {
    for (int i = 0; i < 8; i++) {
      parseFile(testFile, (i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
    }
  }

  private FakeExtractorOutput parseFile(String testFile, boolean simulateIOErrors,
      boolean simulateUnknownLength, boolean simulatePartialReads) throws Exception {
    byte[] fileData = TestUtil.getByteArray(getInstrumentation(), testFile);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(fileData)
        .setSimulateIOErrors(simulateIOErrors)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(simulatePartialReads).build();

    OggExtractor extractor = new OggExtractor();
    assertTrue(TestUtil.sniffTestData(extractor, input));
    input.resetPeekPosition();
    FakeExtractorOutput extractorOutput = TestUtil.consumeTestData(extractor, input, true);

    String dumpFile = testFile;
    if (simulateUnknownLength) {
      dumpFile += UNKNOWN_LENGTH_EXTENSION;
    }
    dumpFile += DUMP_EXTENSION;
    extractorOutput.assertOutput(getInstrumentation(), dumpFile);

    return extractorOutput;
  }

}

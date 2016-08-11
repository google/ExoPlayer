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
import com.google.android.exoplayer.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer.testutil.TestUtil;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit test for {@link OggExtractor}.
 */
public final class OggExtractorTest extends TestCase {

  private OggExtractor extractor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extractor = new OggExtractor();
  }

  public void testSniffVorbis() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x02),
        TestUtil.createByteArray(120, 120),  // Laces
        new byte[]{0x01, 'v', 'o', 'r', 'b', 'i', 's'});
    assertTrue(sniff(createInput(data)));
  }

  public void testSniffFlac() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x02),
        TestUtil.createByteArray(120, 120),  // Laces
        new byte[]{0x7F, 'F', 'L', 'A', 'C', ' ', ' '});
    assertTrue(sniff(createInput(data)));
  }

  public void testSniffFailsOpusFile() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x00),
        new byte[]{'O', 'p', 'u', 's'});
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffFailsInvalidOggHeader() throws Exception {
    byte[] data = TestData.buildOggHeader(0x00, 0, 1000, 0x00);
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffInvalidHeader() throws Exception {
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x02, 0, 1000, 0x02),
        TestUtil.createByteArray(120, 120),  // Laces
        new byte[]{0x7F, 'X', 'o', 'r', 'b', 'i', 's'});
    assertFalse(sniff(createInput(data)));
  }

  public void testSniffFailsEOF() throws Exception {
    byte[] data = TestData.buildOggHeader(0x02, 0, 1000, 0x00);
    assertFalse(sniff(createInput(data)));
  }

  private static FakeExtractorInput createInput(byte[] data) {
    return new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(true).setSimulatePartialReads(true).build();
  }

  private boolean sniff(FakeExtractorInput input) throws InterruptedException, IOException {
    while (true) {
      try {
        return extractor.sniff(input);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}

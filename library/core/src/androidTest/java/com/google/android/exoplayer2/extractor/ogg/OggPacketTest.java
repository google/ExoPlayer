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
package com.google.android.exoplayer2.extractor.ogg;

import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Unit test for {@link OggPacket}.
 */
public final class OggPacketTest extends InstrumentationTestCase {

  private static final String TEST_FILE = "ogg/bear.opus";

  private Random random;
  private OggPacket oggPacket;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    random = new Random(0);
    oggPacket = new OggPacket();
  }

  public void testReadPacketsWithEmptyPage() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(272, random);
    byte[] thirdPacket = TestUtil.buildTestData(256, random);
    byte[] fourthPacket = TestUtil.buildTestData(271, random);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            // First page with a single packet.
            TestData.buildOggHeader(0x02,  0, 1000, 0x01),
            TestUtil.createByteArray(0x08), // Laces
            firstPacket,
            // Second page with a single packet.
            TestData.buildOggHeader(0x00,  16, 1001, 0x02),
            TestUtil.createByteArray(0xFF, 0x11), // Laces
            secondPacket,
            // Third page with zero packets.
            TestData.buildOggHeader(0x00,  16, 1002, 0x00),
            // Fourth page with two packets.
            TestData.buildOggHeader(0x04,  128, 1003, 0x04),
            TestUtil.createByteArray(0xFF, 0x01, 0xFF, 0x10), // Laces
            thirdPacket,
            fourthPacket), true);

    assertReadPacket(input, firstPacket);
    assertTrue((oggPacket.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggPacket.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0x02, oggPacket.getPageHeader().type);
    assertEquals(27 + 1, oggPacket.getPageHeader().headerSize);
    assertEquals(8, oggPacket.getPageHeader().bodySize);
    assertEquals(0x00, oggPacket.getPageHeader().revision);
    assertEquals(1, oggPacket.getPageHeader().pageSegmentCount);
    assertEquals(1000, oggPacket.getPageHeader().pageSequenceNumber);
    assertEquals(4096, oggPacket.getPageHeader().streamSerialNumber);
    assertEquals(0, oggPacket.getPageHeader().granulePosition);

    assertReadPacket(input, secondPacket);
    assertFalse((oggPacket.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggPacket.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0, oggPacket.getPageHeader().type);
    assertEquals(27 + 2, oggPacket.getPageHeader().headerSize);
    assertEquals(255 + 17, oggPacket.getPageHeader().bodySize);
    assertEquals(2, oggPacket.getPageHeader().pageSegmentCount);
    assertEquals(1001, oggPacket.getPageHeader().pageSequenceNumber);
    assertEquals(16, oggPacket.getPageHeader().granulePosition);

    assertReadPacket(input, thirdPacket);
    assertFalse((oggPacket.getPageHeader().type & 0x02) == 0x02);
    assertTrue((oggPacket.getPageHeader().type & 0x04) == 0x04);
    assertEquals(4, oggPacket.getPageHeader().type);
    assertEquals(27 + 4, oggPacket.getPageHeader().headerSize);
    assertEquals(255 + 1 + 255 + 16, oggPacket.getPageHeader().bodySize);
    assertEquals(4, oggPacket.getPageHeader().pageSegmentCount);
    // Page 1002 is empty, so current page is 1003.
    assertEquals(1003, oggPacket.getPageHeader().pageSequenceNumber);
    assertEquals(128, oggPacket.getPageHeader().granulePosition);

    assertReadPacket(input, fourthPacket);

    assertReadEof(input);
  }

  public void testReadPacketWithZeroSizeTerminator() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(255, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x06, 0, 1000, 0x04),
            TestUtil.createByteArray(0xFF, 0x00, 0x00, 0x08), // Laces.
            firstPacket,
            secondPacket), true);

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadEof(input);
  }

  public void testReadContinuedPacketOverTwoPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(518);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            // First page.
            TestData.buildOggHeader(0x02, 0, 1000, 0x02),
            TestUtil.createByteArray(0xFF, 0xFF), // Laces.
            Arrays.copyOf(firstPacket, 510),
            // Second page (continued packet).
            TestData.buildOggHeader(0x05, 10, 1001, 0x01),
            TestUtil.createByteArray(0x08), // Laces.
            Arrays.copyOfRange(firstPacket, 510, 510 + 8)), true);

    assertReadPacket(input, firstPacket);
    assertTrue((oggPacket.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggPacket.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1001, oggPacket.getPageHeader().pageSequenceNumber);

    assertReadEof(input);
  }

  public void testReadContinuedPacketOverFourPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(1028);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            // First page.
            TestData.buildOggHeader(0x02, 0, 1000, 0x02),
            TestUtil.createByteArray(0xFF, 0xFF), // Laces.
            Arrays.copyOf(firstPacket, 510),
            // Second page (continued packet).
            TestData.buildOggHeader(0x01, 10, 1001, 0x01),
            TestUtil.createByteArray(0xFF), // Laces.
            Arrays.copyOfRange(firstPacket, 510, 510 + 255),
            // Third page (continued packet).
            TestData.buildOggHeader(0x01, 10, 1002, 0x01),
            TestUtil.createByteArray(0xFF), // Laces.
            Arrays.copyOfRange(firstPacket, 510 + 255, 510 + 255 + 255),
            // Fourth page (continued packet).
            TestData.buildOggHeader(0x05, 10, 1003, 0x01),
            TestUtil.createByteArray(0x08), // Laces.
            Arrays.copyOfRange(firstPacket, 510 + 255 + 255, 510 + 255 + 255 + 8)), true);

    assertReadPacket(input, firstPacket);
    assertTrue((oggPacket.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggPacket.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1003, oggPacket.getPageHeader().pageSequenceNumber);

    assertReadEof(input);
  }

  public void testReadDiscardContinuedPacketAtStart() throws Exception {
    byte[] pageBody = TestUtil.buildTestData(256 + 8);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            // Page with a continued packet at start.
            TestData.buildOggHeader(0x01, 10, 1001, 0x03),
            TestUtil.createByteArray(255, 1, 8), // Laces.
            pageBody), true);

    // Expect the first partial packet to be discarded.
    assertReadPacket(input, Arrays.copyOfRange(pageBody, 256, 256 + 8));
    assertReadEof(input);
  }

  public void testReadZeroSizedPacketsAtEndOfStream() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);
    byte[] thirdPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x02, 0, 1000, 0x01),
            TestUtil.createByteArray(0x08), // Laces.
            firstPacket,
            TestData.buildOggHeader(0x04, 0, 1001, 0x03),
            TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
            secondPacket,
            TestData.buildOggHeader(0x04, 0, 1002, 0x03),
            TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
            thirdPacket), true);

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadPacket(input, thirdPacket);
    assertReadEof(input);
  }


  public void testParseRealFile() throws IOException, InterruptedException {
    byte[] data = TestUtil.getByteArray(getInstrumentation(), TEST_FILE);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    int packetCounter = 0;
    while (readPacket(input)) {
      packetCounter++;
    }
    assertEquals(277, packetCounter);
  }

  private void assertReadPacket(FakeExtractorInput extractorInput, byte[] expected)
      throws IOException, InterruptedException {
    assertTrue(readPacket(extractorInput));
    ParsableByteArray payload = oggPacket.getPayload();
    MoreAsserts.assertEquals(expected, Arrays.copyOf(payload.data, payload.limit()));
  }

  private void assertReadEof(FakeExtractorInput extractorInput)
      throws IOException, InterruptedException {
    assertFalse(readPacket(extractorInput));
  }

  private boolean readPacket(FakeExtractorInput input)
      throws InterruptedException, IOException {
    while (true) {
      try {
        return oggPacket.populate(input);
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.google.android.exoplayer.util.ParsableByteArray;

import android.test.MoreAsserts;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Unit test for {@link OggReader}
 */
public final class OggReaderTest extends TestCase {

  private Random random;
  private OggReader oggReader;
  private ParsableByteArray scratch;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    random = new Random(0);
    oggReader = new OggReader();
    scratch = new ParsableByteArray(new byte[255 * 255], 0);
  }

  public void testReadPacketsWithEmptyPage() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(272, random);
    byte[] thirdPacket = TestUtil.buildTestData(256, random);
    byte[] fourthPacket = TestUtil.buildTestData(271, random);

    FakeExtractorInput input = createInput(
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
            fourthPacket));

    assertReadPacket(input, firstPacket);
    assertTrue((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0x02, oggReader.getPageHeader().type);
    assertEquals(27 + 1, oggReader.getPageHeader().headerSize);
    assertEquals(8, oggReader.getPageHeader().bodySize);
    assertEquals(0x00, oggReader.getPageHeader().revision);
    assertEquals(1, oggReader.getPageHeader().pageSegmentCount);
    assertEquals(1000, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(4096, oggReader.getPageHeader().streamSerialNumber);
    assertEquals(0, oggReader.getPageHeader().granulePosition);

    assertReadPacket(input, secondPacket);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0, oggReader.getPageHeader().type);
    assertEquals(27 + 2, oggReader.getPageHeader().headerSize);
    assertEquals(255 + 17, oggReader.getPageHeader().bodySize);
    assertEquals(2, oggReader.getPageHeader().pageSegmentCount);
    assertEquals(1001, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(16, oggReader.getPageHeader().granulePosition);

    assertReadPacket(input, thirdPacket);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertEquals(4, oggReader.getPageHeader().type);
    assertEquals(27 + 4, oggReader.getPageHeader().headerSize);
    assertEquals(255 + 1 + 255 + 16, oggReader.getPageHeader().bodySize);
    assertEquals(4, oggReader.getPageHeader().pageSegmentCount);
    // Page 1002 is empty, so current page is 1003.
    assertEquals(1003, oggReader.getPageHeader().pageSequenceNumber);
    assertEquals(128, oggReader.getPageHeader().granulePosition);

    assertReadPacket(input, fourthPacket);

    assertReadEof(input);
  }

  public void testReadPacketWithZeroSizeTerminator() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(255, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input = createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x06, 0, 1000, 0x04),
            TestUtil.createByteArray(0xFF, 0x00, 0x00, 0x08), // Laces.
            firstPacket,
            secondPacket));

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadEof(input);
  }

  public void testReadContinuedPacketOverTwoPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(518);

    FakeExtractorInput input = createInput(
        TestUtil.joinByteArrays(
            // First page.
            TestData.buildOggHeader(0x02, 0, 1000, 0x02),
            TestUtil.createByteArray(0xFF, 0xFF), // Laces.
            Arrays.copyOf(firstPacket, 510),
            // Second page (continued packet).
            TestData.buildOggHeader(0x05, 10, 1001, 0x01),
            TestUtil.createByteArray(0x08), // Laces.
            Arrays.copyOfRange(firstPacket, 510, 510 + 8)));

    assertReadPacket(input, firstPacket);
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1001, oggReader.getPageHeader().pageSequenceNumber);

    assertReadEof(input);
  }

  public void testReadContinuedPacketOverFourPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(1028);

    FakeExtractorInput input = createInput(
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
            Arrays.copyOfRange(firstPacket, 510 + 255 + 255, 510 + 255 + 255 + 8)));

    assertReadPacket(input, firstPacket);
    assertTrue((oggReader.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggReader.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1003, oggReader.getPageHeader().pageSequenceNumber);

    assertReadEof(input);
  }

  public void testReadZeroSizedPacketsAtEndOfStream() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);
    byte[] thirdPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input = createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x02, 0, 1000, 0x01),
            TestUtil.createByteArray(0x08), // Laces.
            firstPacket,
            TestData.buildOggHeader(0x04, 0, 1001, 0x03),
            TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
            secondPacket,
            TestData.buildOggHeader(0x04, 0, 1002, 0x03),
            TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
            thirdPacket));

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadPacket(input, thirdPacket);
    assertReadEof(input);
  }

  private static FakeExtractorInput createInput(byte[] data) {
    return new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(true).setSimulatePartialReads(true).build();
  }

  private void assertReadPacket(FakeExtractorInput extractorInput, byte[] expected)
      throws IOException, InterruptedException {
    scratch.reset();
    assertTrue(readPacket(extractorInput, scratch));
    MoreAsserts.assertEquals(expected, Arrays.copyOf(scratch.data, scratch.limit()));
  }

  private void assertReadEof(FakeExtractorInput extractorInput)
      throws IOException, InterruptedException {
    scratch.reset();
    assertFalse(readPacket(extractorInput, scratch));
  }

  private boolean readPacket(FakeExtractorInput input, ParsableByteArray scratch)
      throws InterruptedException, IOException {
    while (true) {
      try {
        return oggReader.readPacket(input, scratch);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}

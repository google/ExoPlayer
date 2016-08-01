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

import android.test.MoreAsserts;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit test for {@link OggParser}.
 */
public final class OggParserTest extends TestCase {

  private Random random;
  private OggParser oggParser;
  private ParsableByteArray scratch;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    random = new Random(0);
    oggParser = new OggParser();
    scratch = new ParsableByteArray(new byte[255 * 255], 0);
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
    assertTrue((oggParser.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggParser.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0x02, oggParser.getPageHeader().type);
    assertEquals(27 + 1, oggParser.getPageHeader().headerSize);
    assertEquals(8, oggParser.getPageHeader().bodySize);
    assertEquals(0x00, oggParser.getPageHeader().revision);
    assertEquals(1, oggParser.getPageHeader().pageSegmentCount);
    assertEquals(1000, oggParser.getPageHeader().pageSequenceNumber);
    assertEquals(4096, oggParser.getPageHeader().streamSerialNumber);
    assertEquals(0, oggParser.getPageHeader().granulePosition);

    assertReadPacket(input, secondPacket);
    assertFalse((oggParser.getPageHeader().type & 0x02) == 0x02);
    assertFalse((oggParser.getPageHeader().type & 0x04) == 0x04);
    assertEquals(0, oggParser.getPageHeader().type);
    assertEquals(27 + 2, oggParser.getPageHeader().headerSize);
    assertEquals(255 + 17, oggParser.getPageHeader().bodySize);
    assertEquals(2, oggParser.getPageHeader().pageSegmentCount);
    assertEquals(1001, oggParser.getPageHeader().pageSequenceNumber);
    assertEquals(16, oggParser.getPageHeader().granulePosition);

    assertReadPacket(input, thirdPacket);
    assertFalse((oggParser.getPageHeader().type & 0x02) == 0x02);
    assertTrue((oggParser.getPageHeader().type & 0x04) == 0x04);
    assertEquals(4, oggParser.getPageHeader().type);
    assertEquals(27 + 4, oggParser.getPageHeader().headerSize);
    assertEquals(255 + 1 + 255 + 16, oggParser.getPageHeader().bodySize);
    assertEquals(4, oggParser.getPageHeader().pageSegmentCount);
    // Page 1002 is empty, so current page is 1003.
    assertEquals(1003, oggParser.getPageHeader().pageSequenceNumber);
    assertEquals(128, oggParser.getPageHeader().granulePosition);

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
    assertTrue((oggParser.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggParser.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1001, oggParser.getPageHeader().pageSequenceNumber);

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
    assertTrue((oggParser.getPageHeader().type & 0x04) == 0x04);
    assertFalse((oggParser.getPageHeader().type & 0x02) == 0x02);
    assertEquals(1003, oggParser.getPageHeader().pageSequenceNumber);

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

  public void testSkipToPageOfGranule() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet), false);

    // expect to be granule of the previous page returned as elapsedSamples
    skipToPageOfGranule(input, 54000, 40000);
    // expect to be at the start of the third page
    assertEquals(2 * (30 + (3 * 254)), input.getPosition());
  }

  public void testSkipToPageOfGranulePreciseMatch() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet), false);

    skipToPageOfGranule(input, 40000, 20000);
    // expect to be at the start of the second page
    assertEquals((30 + (3 * 254)), input.getPosition());
  }

  public void testSkipToPageOfGranuleAfterTargetPage() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    FakeExtractorInput input = TestData.createInput(
        TestUtil.joinByteArrays(
            TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet,
            TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
            TestUtil.createByteArray(254, 254, 254), // Laces.
            packet), false);

    try {
      skipToPageOfGranule(input, 10000, 20000);
      fail();
    } catch (ParserException e) {
      // ignored
    }
    assertEquals(0, input.getPosition());
  }

  private void skipToPageOfGranule(ExtractorInput input, long granule,
      long elapsedSamplesExpected) throws IOException, InterruptedException {
    while (true) {
      try {
        assertEquals(elapsedSamplesExpected, oggParser.skipToPageOfGranule(input, granule));
        return;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        input.resetPeekPosition();
      }
    }
  }

  public void testReadGranuleOfLastPage() throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestUtil.buildTestData(100, random),
        TestData.buildOggHeader(0x00, 20000, 66, 3),
        TestUtil.createByteArray(254, 254, 254), // laces
        TestUtil.buildTestData(3 * 254, random),
        TestData.buildOggHeader(0x00, 40000, 67, 3),
        TestUtil.createByteArray(254, 254, 254), // laces
        TestUtil.buildTestData(3 * 254, random),
        TestData.buildOggHeader(0x05, 60000, 68, 3),
        TestUtil.createByteArray(254, 254, 254), // laces
        TestUtil.buildTestData(3 * 254, random)
    ), false);
    assertReadGranuleOfLastPage(input, 60000);
  }

  public void testReadGranuleOfLastPageAfterLastHeader() throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.buildTestData(100, random), false);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (EOFException e) {
      // ignored
    }
  }

  public void testReadGranuleOfLastPageWithUnboundedLength()
      throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(new byte[0], true);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  private void assertReadGranuleOfLastPage(FakeExtractorInput input, int expected)
      throws IOException, InterruptedException {
    while (true) {
      try {
        assertEquals(expected, oggParser.readGranuleOfLastPage(input));
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // ignored
      }
    }
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
        return oggParser.readPacket(input, scratch);
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignore.
      }
    }
  }

}

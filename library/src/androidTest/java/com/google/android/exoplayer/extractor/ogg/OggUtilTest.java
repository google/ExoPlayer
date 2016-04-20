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

import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.ParsableByteArray;

import junit.framework.TestCase;

import java.io.EOFException;
import java.io.IOException;
import java.util.Random;

/**
 * Unit test for {@link OggUtil}.
 */
public final class OggUtilTest extends TestCase {

  private Random random = new Random(0);

  public void testReadBits() throws Exception {
    assertEquals(0, OggUtil.readBits((byte) 0x00, 2, 2));
    assertEquals(1, OggUtil.readBits((byte) 0x02, 1, 1));
    assertEquals(15, OggUtil.readBits((byte) 0xF0, 4, 4));
    assertEquals(1, OggUtil.readBits((byte) 0x80, 1, 7));
  }

  public void testPopulatePageHeader() throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    ), true);
    OggUtil.PageHeader header = new OggUtil.PageHeader();
    ParsableByteArray byteArray = new ParsableByteArray(27 + 2);
    populatePageHeader(input, header, byteArray, false);

    assertEquals(0x01, header.type);
    assertEquals(27 + 2, header.headerSize);
    assertEquals(4, header.bodySize);
    assertEquals(2, header.pageSegmentCount);
    assertEquals(123456, header.granulePosition);
    assertEquals(4, header.pageSequenceNumber);
    assertEquals(0x1000, header.streamSerialNumber);
    assertEquals(0x100000, header.pageChecksum);
    assertEquals(0, header.revision);
  }

  public void testPopulatePageHeaderQuiteOnExceptionLessThan27Bytes()
      throws IOException, InterruptedException {
    FakeExtractorInput input = TestData.createInput(TestUtil.createByteArray(2, 2), false);
    OggUtil.PageHeader header = new OggUtil.PageHeader();
    ParsableByteArray byteArray = new ParsableByteArray(27 + 2);
    assertFalse(populatePageHeader(input, header, byteArray, true));
  }

  public void testPopulatePageHeaderQuiteOnExceptionNotOgg()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change from 'O' to 'o'
    headerBytes[0] = 'o';
    FakeExtractorInput input = TestData.createInput(headerBytes, false);
    OggUtil.PageHeader header = new OggUtil.PageHeader();
    ParsableByteArray byteArray = new ParsableByteArray(27 + 2);
    assertFalse(populatePageHeader(input, header, byteArray, true));
  }

  public void testPopulatePageHeaderQuiteOnExceptionWrongRevision()
      throws IOException, InterruptedException {
    byte[] headerBytes = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 123456, 4, 2),
        TestUtil.createByteArray(2, 2)
    );
    // change revision from 0 to 1
    headerBytes[4] = 0x01;
    FakeExtractorInput input = TestData.createInput(headerBytes, false);
    OggUtil.PageHeader header = new OggUtil.PageHeader();
    ParsableByteArray byteArray = new ParsableByteArray(27 + 2);
    assertFalse(populatePageHeader(input, header, byteArray, true));
  }

  private boolean populatePageHeader(FakeExtractorInput input, OggUtil.PageHeader header,
      ParsableByteArray byteArray, boolean quite) throws IOException, InterruptedException {
    while (true) {
      try {
        return OggUtil.populatePageHeader(input, header, byteArray, quite);
      } catch (SimulatedIOException e) {
        // ignored
      }
    }
  }

  public void testSkipToNextPage() throws Exception {
    FakeExtractorInput extractorInput = createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(4000, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(4000, extractorInput.getPosition());
  }

  public void testSkipToNextPageUnbounded() throws Exception {
    FakeExtractorInput extractorInput = createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(4000, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), true);
    skipToNextPage(extractorInput);
    assertEquals(4000, extractorInput.getPosition());
  }

  public void testSkipToNextPageOverlap() throws Exception {
    FakeExtractorInput extractorInput = createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(2046, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(2046, extractorInput.getPosition());
  }

  public void testSkipToNextPageOverlapUnbounded() throws Exception {
    FakeExtractorInput extractorInput = createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(2046, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), true);
    skipToNextPage(extractorInput);
    assertEquals(2046, extractorInput.getPosition());
  }

  public void testSkipToNextPageInputShorterThanPeekLength() throws Exception {
    FakeExtractorInput extractorInput = createInput(
        TestUtil.joinByteArrays(
            new byte[]{'x', 'O', 'g', 'g', 'S'}
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(1, extractorInput.getPosition());
  }

  public void testSkipToNextPageNoMatch() throws Exception {
    FakeExtractorInput extractorInput = createInput(new byte[]{'g', 'g', 'S', 'O', 'g', 'g'},
        false);
    try {
      skipToNextPage(extractorInput);
      fail();
    } catch (EOFException e) {
      // expected
    }
  }

  private static void skipToNextPage(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    while (true) {
      try {
        OggUtil.skipToNextPage(extractorInput);
        break;
      } catch (SimulatedIOException e) { /* ignored */ }
    }
  }

  private static FakeExtractorInput createInput(byte[] data, boolean simulateUnknownLength) {
    return new FakeExtractorInput.Builder().setData(data).setSimulateIOErrors(true)
        .setSimulateUnknownLength(simulateUnknownLength).setSimulatePartialReads(true).build();
  }
}


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

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.EOFException;
import java.io.IOException;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit test for {@link DefaultOggSeeker} utility methods.
 */
public class DefaultOggSeekerUtilMethodsTest extends TestCase {

  private final Random random = new Random(0);

  public void testSkipToNextPage() throws Exception {
    FakeExtractorInput extractorInput = TestData.createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(4000, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(4000, extractorInput.getPosition());
  }

  public void testSkipToNextPageOverlap() throws Exception {
    FakeExtractorInput extractorInput = TestData.createInput(
        TestUtil.joinByteArrays(
            TestUtil.buildTestData(2046, random),
            new byte[]{'O', 'g', 'g', 'S'},
            TestUtil.buildTestData(4000, random)
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(2046, extractorInput.getPosition());
  }

  public void testSkipToNextPageInputShorterThanPeekLength() throws Exception {
    FakeExtractorInput extractorInput = TestData.createInput(
        TestUtil.joinByteArrays(
            new byte[]{'x', 'O', 'g', 'g', 'S'}
        ), false);
    skipToNextPage(extractorInput);
    assertEquals(1, extractorInput.getPosition());
  }

  public void testSkipToNextPageNoMatch() throws Exception {
    FakeExtractorInput extractorInput = TestData.createInput(
        new byte[]{'g', 'g', 'S', 'O', 'g', 'g'}, false);
    try {
      skipToNextPage(extractorInput);
      fail();
    } catch (EOFException e) {
      // expected
    }
  }

  private static void skipToNextPage(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    DefaultOggSeeker oggSeeker = new DefaultOggSeeker(0, extractorInput.getLength(),
        new FlacReader(), 1, 2);
    while (true) {
      try {
        oggSeeker.skipToNextPage(extractorInput);
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) { /* ignored */ }
    }
  }

  public void testSkipToPageOfGranule() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    // expect to be granule of the previous page returned as elapsedSamples
    skipToPageOfGranule(input, 54000, 40000);
    // expect to be at the start of the third page
    assertEquals(2 * (30 + (3 * 254)), input.getPosition());
  }

  public void testSkipToPageOfGranulePreciseMatch() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    skipToPageOfGranule(input, 40000, 20000);
    // expect to be at the start of the second page
    assertEquals((30 + (3 * 254)), input.getPosition());
  }

  public void testSkipToPageOfGranuleAfterTargetPage() throws IOException, InterruptedException {
    byte[] packet = TestUtil.buildTestData(3 * 254, random);
    byte[] data = TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x01, 20000, 1000, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 40000, 1001, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet,
        TestData.buildOggHeader(0x04, 60000, 1002, 0x03),
        TestUtil.createByteArray(254, 254, 254), // Laces.
        packet);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    skipToPageOfGranule(input, 10000, -1);
    assertEquals(0, input.getPosition());
  }

  private void skipToPageOfGranule(ExtractorInput input, long granule,
      long elapsedSamplesExpected) throws IOException, InterruptedException {
    DefaultOggSeeker oggSeeker = new DefaultOggSeeker(0, input.getLength(), new FlacReader(), 1, 2);
    while (true) {
      try {
        assertEquals(elapsedSamplesExpected, oggSeeker.skipToPageOfGranule(input, granule, -1));
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
    DefaultOggSeeker oggSeeker = new DefaultOggSeeker(0, input.getLength(), new FlacReader(), 1, 2);
    while (true) {
      try {
        assertEquals(expected, oggSeeker.readGranuleOfLastPage(input));
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // ignored
      }
    }
  }

}

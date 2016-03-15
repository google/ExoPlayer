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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.TestUtil;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Unit test for {@link OggSeeker}.
 */
public final class OggSeekerTest extends TestCase {

  private OggSeeker oggSeeker;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    oggSeeker = new OggSeeker();
    oggSeeker.setup(1, 1);
  }

  public void testSetupUnboundAudioLength() {
    try {
      new OggSeeker().setup(C.LENGTH_UNBOUNDED, 1000);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  public void testSetupZeroOrNegativeTotalSamples() {
    try {
      new OggSeeker().setup(1000, 0);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
    try {
      new OggSeeker().setup(1000, -1000);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  public void testGetNextSeekPositionSetupNotCalled() throws IOException, InterruptedException {
    try {
      new OggSeeker().getNextSeekPosition(1000, TestData.createInput(new byte[0], false));
      fail();
    } catch (IllegalStateException e) {
      // ignored
    }
  }

  public void testGetNextSeekPositionMatch() throws IOException, InterruptedException {
    long targetGranule = 100000;
    long headerGranule = 52001;
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x00, headerGranule, 22, 2),
        TestUtil.createByteArray(54, 55) // laces
    ), false);
    long expectedPosition = -1;
    assertGetNextSeekPosition(expectedPosition, targetGranule, input);
  }

  public void testGetNextSeekPositionTooHigh() throws IOException, InterruptedException {
    long targetGranule = 100000;
    long headerGranule = 200000;
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x00, headerGranule, 22, 2),
        TestUtil.createByteArray(54, 55) // laces
    ), false);
    long doublePageSize = 2 * (input.getLength() + 54 + 55);
    long expectedPosition = -doublePageSize + (targetGranule - headerGranule);
    assertGetNextSeekPosition(expectedPosition, targetGranule, input);
  }

  public void testGetNextSeekPositionTooHighDistanceLower48000()
      throws IOException, InterruptedException {
    long targetGranule = 199999;
    long headerGranule = 200000;
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x00, headerGranule, 22, 2),
        TestUtil.createByteArray(54, 55) // laces
    ), false);
    long doublePageSize = 2 * (input.getLength() + 54 + 55);
    long expectedPosition = -doublePageSize - 1;
    assertGetNextSeekPosition(expectedPosition, targetGranule, input);
  }

  public void testGetNextSeekPositionTooLow() throws IOException, InterruptedException {
    long headerGranule = 200000;
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        TestData.buildOggHeader(0x00, headerGranule, 22, 2),
        TestUtil.createByteArray(54, 55) // laces
    ), false);
    long targetGranule = 300000;
    long expectedPosition = -(27 + 2 + 54 + 55) + (targetGranule - headerGranule);
    assertGetNextSeekPosition(expectedPosition, targetGranule, input);
  }

  private void assertGetNextSeekPosition(long expectedPosition, long targetGranule,
      FakeExtractorInput input) throws IOException, InterruptedException {
    while (true) {
      try {
        assertEquals(expectedPosition, oggSeeker.getNextSeekPosition(targetGranule, input));
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // ignored
      }
    }
  }

}

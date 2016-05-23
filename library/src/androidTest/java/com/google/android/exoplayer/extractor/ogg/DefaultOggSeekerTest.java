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
 * Unit test for {@link DefaultOggSeeker}.
 */
public final class DefaultOggSeekerTest extends TestCase {

  private static final long HEADER_GRANULE = 200000;
  private static final int START_POSITION = 0;
  private static final int END_POSITION = 1000000;
  private static final int TOTAL_SAMPLES = END_POSITION - START_POSITION;

  private DefaultOggSeeker oggSeeker;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    oggSeeker = DefaultOggSeeker.createOggSeekerForTesting(START_POSITION, END_POSITION,
        TOTAL_SAMPLES);
  }

  public void testSetupUnboundAudioLength() {
    try {
      new DefaultOggSeeker(0, C.LENGTH_UNBOUNDED, new FlacReader());
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  public void testGetNextSeekPositionMatch() throws IOException, InterruptedException {
    assertGetNextSeekPosition(HEADER_GRANULE + DefaultOggSeeker.MATCH_RANGE);
    assertGetNextSeekPosition(HEADER_GRANULE + 1);
  }

  public void testGetNextSeekPositionTooHigh() throws IOException, InterruptedException {
    assertGetNextSeekPosition(HEADER_GRANULE - 100000);
    assertGetNextSeekPosition(HEADER_GRANULE);
  }

  public void testGetNextSeekPositionTooLow() throws IOException, InterruptedException {
    assertGetNextSeekPosition(HEADER_GRANULE + DefaultOggSeeker.MATCH_RANGE + 1);
    assertGetNextSeekPosition(HEADER_GRANULE + 100000);
  }

  public void testGetNextSeekPositionBounds() throws IOException, InterruptedException {
    assertGetNextSeekPosition(HEADER_GRANULE + TOTAL_SAMPLES);
    assertGetNextSeekPosition(HEADER_GRANULE - TOTAL_SAMPLES);
  }

  private void assertGetNextSeekPosition(long targetGranule)
      throws IOException, InterruptedException {
    int pagePosition = 500000;
    FakeExtractorInput input = TestData.createInput(TestUtil.joinByteArrays(
        new byte[pagePosition],
        TestData.buildOggHeader(0x00, HEADER_GRANULE, 22, 2),
        TestUtil.createByteArray(54, 55) // laces
    ), false);
    input.setPosition(pagePosition);
    long granuleDiff = targetGranule - HEADER_GRANULE;
    long expectedPosition;
    if (granuleDiff > 0 && granuleDiff <= DefaultOggSeeker.MATCH_RANGE) {
      expectedPosition = -1;
    } else {
      long doublePageSize = (27 + 2 + 54 + 55) * (granuleDiff <= 0 ? 2 : 1);
      expectedPosition = pagePosition - doublePageSize + granuleDiff;
      expectedPosition = Math.max(expectedPosition, START_POSITION);
      expectedPosition = Math.min(expectedPosition, END_POSITION - 1);
    }
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

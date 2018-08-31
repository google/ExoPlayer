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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit test for {@link DefaultOggSeeker}. */
@RunWith(RobolectricTestRunner.class)
public final class DefaultOggSeekerTest {

  @Test
  public void testSetupWithUnsetEndPositionFails() {
    try {
      new DefaultOggSeeker(
          /* startPosition= */ 0,
          /* endPosition= */ C.LENGTH_UNSET,
          /* streamReader= */ new TestStreamReader(),
          /* firstPayloadPageSize= */ 1,
          /* firstPayloadPageGranulePosition= */ 1,
          /* firstPayloadPageIsLastPage= */ false);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  @Test
  public void testSeeking() throws IOException, InterruptedException {
    Random random = new Random(0);
    for (int i = 0; i < 100; i++) {
      testSeeking(random);
    }
  }

  private void testSeeking(Random random) throws IOException, InterruptedException {
    OggTestFile testFile = OggTestFile.generate(random, 1000);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(testFile.data).build();
    TestStreamReader streamReader = new TestStreamReader();
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* startPosition= */ 0,
            /* endPosition= */ testFile.data.length,
            /* streamReader= */ streamReader,
            /* firstPayloadPageSize= */ testFile.firstPayloadPageSize,
            /* firstPayloadPageGranulePosition= */ testFile.firstPayloadPageGranulePosition,
            /* firstPayloadPageIsLastPage= */ false);
    OggPageHeader pageHeader = new OggPageHeader();

    while (true) {
      long nextSeekPosition = oggSeeker.read(input);
      if (nextSeekPosition == -1) {
        break;
      }
      input.setPosition((int) nextSeekPosition);
    }

    // Test granule 0 from file start
    assertThat(seekTo(input, oggSeeker, 0, 0)).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test granule 0 from file end
    assertThat(seekTo(input, oggSeeker, 0, testFile.data.length - 1)).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    { // Test last granule
      long currentGranule = seekTo(input, oggSeeker, testFile.lastGranule, 0);
      long position = testFile.data.length;
      assertThat(
              (testFile.lastGranule > currentGranule && position > input.getPosition())
                  || (testFile.lastGranule == currentGranule && position == input.getPosition()))
          .isTrue();
    }

    { // Test exact granule
      input.setPosition(testFile.data.length / 2);
      oggSeeker.skipToNextPage(input);
      assertThat(pageHeader.populate(input, true)).isTrue();
      long position = input.getPosition() + pageHeader.headerSize + pageHeader.bodySize;
      long currentGranule = seekTo(input, oggSeeker, pageHeader.granulePosition, 0);
      assertThat(
              (pageHeader.granulePosition > currentGranule && position > input.getPosition())
                  || (pageHeader.granulePosition == currentGranule
                      && position == input.getPosition()))
          .isTrue();
    }

    for (int i = 0; i < 100; i += 1) {
      long targetGranule = (long) (random.nextDouble() * testFile.lastGranule);
      int initialPosition = random.nextInt(testFile.data.length);

      long currentGranule = seekTo(input, oggSeeker, targetGranule, initialPosition);
      long currentPosition = input.getPosition();

      assertWithMessage("getNextSeekPosition() didn't leave input on a page start.")
          .that(pageHeader.populate(input, true))
          .isTrue();

      if (currentGranule == 0) {
        assertThat(currentPosition).isEqualTo(0);
      } else {
        int previousPageStart = testFile.findPreviousPageStart(currentPosition);
        input.setPosition(previousPageStart);
        assertThat(pageHeader.populate(input, true)).isTrue();
        assertThat(currentGranule).isEqualTo(pageHeader.granulePosition);
      }

      input.setPosition((int) currentPosition);
      oggSeeker.skipToPageOfGranule(input, targetGranule, -1);
      long positionDiff = Math.abs(input.getPosition() - currentPosition);

      long granuleDiff = currentGranule - targetGranule;
      if ((granuleDiff > DefaultOggSeeker.MATCH_RANGE || granuleDiff < 0)
          && positionDiff > DefaultOggSeeker.MATCH_BYTE_RANGE) {
        fail(
            "granuleDiff ("
                + granuleDiff
                + ") or positionDiff ("
                + positionDiff
                + ") is more than allowed.");
      }
    }
  }

  private long seekTo(
      FakeExtractorInput input, DefaultOggSeeker oggSeeker, long targetGranule, int initialPosition)
      throws IOException, InterruptedException {
    long nextSeekPosition = initialPosition;
    int count = 0;
    oggSeeker.resetSeeking();

    do {
      input.setPosition((int) nextSeekPosition);
      nextSeekPosition = oggSeeker.getNextSeekPosition(targetGranule, input);

      if (count++ > 100) {
        fail("infinite loop?");
      }
    } while (nextSeekPosition >= 0);

    return -(nextSeekPosition + 2);
  }

  private static class TestStreamReader extends StreamReader {
    @Override
    protected long preparePayload(ParsableByteArray packet) {
      return 0;
    }

    @Override
    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData)
        throws IOException, InterruptedException {
      return false;
    }
  }
}

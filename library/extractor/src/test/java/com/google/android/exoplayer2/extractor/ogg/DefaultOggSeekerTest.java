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

import static com.google.android.exoplayer2.testutil.TestUtil.getByteArray;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultOggSeeker}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultOggSeekerTest {

  private final Random random = new Random(/* seed= */ 0);

  @Test
  public void setupWithUnsetEndPositionFails() {
    try {
      new DefaultOggSeeker(
          /* streamReader= */ new TestStreamReader(),
          /* payloadStartPosition= */ 0,
          /* payloadEndPosition= */ C.LENGTH_UNSET,
          /* firstPayloadPageSize= */ 1,
          /* firstPayloadPageGranulePosition= */ 1,
          /* firstPayloadPageIsLastPage= */ false);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  @Test
  public void seeking() throws Exception {
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "media/ogg/random_1000_pages");
    int granuleCount = 49269395;
    int firstPayloadPageSize = 2023;
    int firstPayloadPageGranuleCount = 57058;
    int lastPayloadPageSize = 282;
    int lastPayloadPageGranuleCount = 20806;

    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    TestStreamReader streamReader = new TestStreamReader();
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            streamReader,
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ data.length,
            firstPayloadPageSize,
            /* firstPayloadPageGranulePosition= */ firstPayloadPageGranuleCount,
            /* firstPayloadPageIsLastPage= */ false);
    OggPageHeader pageHeader = new OggPageHeader();

    while (true) {
      long nextSeekPosition = oggSeeker.read(input);
      if (nextSeekPosition == -1) {
        break;
      }
      input.setPosition((int) nextSeekPosition);
    }

    // Test granule 0 from file start.
    long granule = seekTo(input, oggSeeker, 0, 0);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test granule 0 from file end.
    granule = seekTo(input, oggSeeker, 0, data.length - 1);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test last granule.
    granule = seekTo(input, oggSeeker, granuleCount - 1, 0);
    assertThat(granule).isEqualTo(granuleCount - lastPayloadPageGranuleCount);
    assertThat(input.getPosition()).isEqualTo(data.length - lastPayloadPageSize);

    for (int i = 0; i < 100; i += 1) {
      long targetGranule = random.nextInt(granuleCount);
      int initialPosition = random.nextInt(data.length);
      granule = seekTo(input, oggSeeker, targetGranule, initialPosition);
      int currentPosition = (int) input.getPosition();
      if (granule == 0) {
        assertThat(currentPosition).isEqualTo(0);
      } else {
        int previousPageStart = findPreviousPageStart(data, currentPosition);
        input.setPosition(previousPageStart);
        pageHeader.populate(input, false);
        assertThat(granule).isEqualTo(pageHeader.granulePosition);
      }

      input.setPosition(currentPosition);
      pageHeader.populate(input, false);
      // The target granule should be within the current page.
      assertThat(granule).isAtMost(targetGranule);
      assertThat(targetGranule).isLessThan(pageHeader.granulePosition);
    }
  }

  @Test
  public void readGranuleOfLastPage() throws IOException {
    // This test stream has three headers with granule numbers 20000, 40000 and 60000.
    byte[] data =
        getByteArray(ApplicationProvider.getApplicationContext(), "media/ogg/three_headers");
    FakeExtractorInput input = createInput(data, /* simulateUnknownLength= */ false);
    assertReadGranuleOfLastPage(input, 60000);
  }

  @Test
  public void readGranuleOfLastPage_afterLastHeader_throwsException() throws Exception {
    FakeExtractorInput input =
        createInput(TestUtil.buildTestData(100, random), /* simulateUnknownLength= */ false);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (EOFException e) {
      // Ignored.
    }
  }

  @Test
  public void readGranuleOfLastPage_withUnboundedLength_throwsException() throws Exception {
    FakeExtractorInput input = createInput(new byte[0], /* simulateUnknownLength= */ true);
    try {
      assertReadGranuleOfLastPage(input, 60000);
      fail();
    } catch (IllegalArgumentException e) {
      // Ignored.
    }
  }

  private static void assertReadGranuleOfLastPage(FakeExtractorInput input, int expected)
      throws IOException {
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* streamReader= */ new FlacReader(),
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ input.getLength(),
            /* firstPayloadPageSize= */ 1,
            /* firstPayloadPageGranulePosition= */ 2,
            /* firstPayloadPageIsLastPage= */ false);
    while (true) {
      try {
        assertThat(oggSeeker.readGranuleOfLastPage(input)).isEqualTo(expected);
        break;
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignored.
      }
    }
  }

  private static FakeExtractorInput createInput(byte[] data, boolean simulateUnknownLength) {
    return new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateIOErrors(true)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(true)
        .build();
  }

  private static long seekTo(
      FakeExtractorInput input, DefaultOggSeeker oggSeeker, long targetGranule, int initialPosition)
      throws IOException {
    long nextSeekPosition = initialPosition;
    oggSeeker.startSeek(targetGranule);
    int count = 0;
    while (nextSeekPosition >= 0) {
      if (count++ > 100) {
        fail("Seek failed to converge in 100 iterations");
      }
      input.setPosition((int) nextSeekPosition);
      nextSeekPosition = oggSeeker.read(input);
    }
    return -(nextSeekPosition + 2);
  }

  private static int findPreviousPageStart(byte[] data, int position) {
    for (int i = position - 4; i >= 0; i--) {
      if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
        return i;
      }
    }
    fail();
    return -1;
  }

  private static class TestStreamReader extends StreamReader {
    @Override
    protected long preparePayload(ParsableByteArray packet) {
      return 0;
    }

    @Override
    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) {
      return false;
    }
  }
}

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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggPacket}. */
@RunWith(AndroidJUnit4.class)
public final class OggPacketTest {

  private static final String TEST_FILE = "media/ogg/bear.opus";

  private final Random random = new Random(/* seed= */ 0);
  private final OggPacket oggPacket = new OggPacket();

  @Test
  public void readPacketsWithEmptyPage() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(272, random);
    byte[] thirdPacket = TestUtil.buildTestData(256, random);
    byte[] fourthPacket = TestUtil.buildTestData(271, random);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/four_packets_with_empty_page"));

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isFalse();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(0x02);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 1);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(8);
    assertThat(oggPacket.getPageHeader().revision).isEqualTo(0x00);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(1);
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1000);
    assertThat(oggPacket.getPageHeader().streamSerialNumber).isEqualTo(4096);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(0);

    assertReadPacket(input, secondPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isFalse();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(0);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 2);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(255 + 17);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(2);
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1001);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(16);

    assertReadPacket(input, thirdPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(4);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 4);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(255 + 1 + 255 + 16);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(4);
    // Page 1002 is empty, so current page is 1003.
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1003);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(128);

    assertReadPacket(input, fourthPacket);

    assertReadEof(input);
  }

  @Test
  public void readPacketWithZeroSizeTerminator() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(255, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/packet_with_zero_size_terminator"));

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadEof(input);
  }

  @Test
  public void readContinuedPacketOverTwoPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(518);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/continued_packet_over_two_pages"));

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1001);

    assertReadEof(input);
  }

  @Test
  public void readContinuedPacketOverFourPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(1028);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/continued_packet_over_four_pages"));

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1003);

    assertReadEof(input);
  }

  @Test
  public void readDiscardContinuedPacketAtStart() throws Exception {
    byte[] pageBody = TestUtil.buildTestData(256 + 8);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/continued_packet_at_start"));

    // Expect the first partial packet to be discarded.
    assertReadPacket(input, Arrays.copyOfRange(pageBody, 256, 256 + 8));
    assertReadEof(input);
  }

  @Test
  public void readZeroSizedPacketsAtEndOfStream() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);
    byte[] thirdPacket = TestUtil.buildTestData(8, random);
    FakeExtractorInput input =
        createInput(
            getByteArray(
                ApplicationProvider.getApplicationContext(),
                "media/ogg/zero_sized_packets_at_end_of_stream"));

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadPacket(input, thirdPacket);
    assertReadEof(input);
  }

  @Test
  public void parseRealFile() throws IOException {
    byte[] data = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_FILE);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    int packetCounter = 0;
    while (readPacket(input)) {
      packetCounter++;
    }
    assertThat(packetCounter).isEqualTo(277);
  }

  private static FakeExtractorInput createInput(byte[] data) {
    return new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateIOErrors(true)
        .setSimulateUnknownLength(true)
        .setSimulatePartialReads(true)
        .build();
  }

  private void assertReadPacket(FakeExtractorInput extractorInput, byte[] expected)
      throws IOException {
    assertThat(readPacket(extractorInput)).isTrue();
    ParsableByteArray payload = oggPacket.getPayload();
    assertThat(Arrays.copyOf(payload.getData(), payload.limit())).isEqualTo(expected);
  }

  private void assertReadEof(FakeExtractorInput extractorInput) throws IOException {
    assertThat(readPacket(extractorInput)).isFalse();
  }

  private boolean readPacket(FakeExtractorInput input) throws IOException {
    while (true) {
      try {
        return oggPacket.populate(input);
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignore.
      }
    }
  }
}

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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.ParsableByteArray;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Test for {@link AdtsReader}.
 */
public class AdtsReaderTest extends TestCase {

  public static final byte[] ID3_DATA_1 = TestUtil.createByteArray(
      0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3d, 0x54, 0x58,
      0x58, 0x58, 0x00, 0x00, 0x00, 0x33, 0x00, 0x00, 0x03, 0x00, 0x20, 0x2a,
      0x2a, 0x2a, 0x20, 0x54, 0x48, 0x49, 0x53, 0x20, 0x49, 0x53, 0x20, 0x54,
      0x69, 0x6d, 0x65, 0x64, 0x20, 0x4d, 0x65, 0x74, 0x61, 0x44, 0x61, 0x74,
      0x61, 0x20, 0x40, 0x20, 0x2d, 0x2d, 0x20, 0x30, 0x30, 0x3a, 0x30, 0x30,
      0x3a, 0x30, 0x30, 0x2e, 0x30, 0x20, 0x2a, 0x2a, 0x2a, 0x20, 0x00);

  public static final byte[] ID3_DATA_2 = TestUtil.createByteArray(
      0x49,
      0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3f, 0x50, 0x52, 0x49,
      0x56, 0x00, 0x00, 0x00, 0x35, 0x00, 0x00, 0x63, 0x6f, 0x6d, 0x2e, 0x61,
      0x70, 0x70, 0x6c, 0x65, 0x2e, 0x73, 0x74, 0x72, 0x65, 0x61, 0x6d, 0x69,
      0x6e, 0x67, 0x2e, 0x74, 0x72, 0x61, 0x6e, 0x73, 0x70, 0x6f, 0x72, 0x74,
      0x53, 0x74, 0x72, 0x65, 0x61, 0x6d, 0x54, 0x69, 0x6d, 0x65, 0x73, 0x74,
      0x61, 0x6d, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0d, 0xbb, 0xa0);

  public static final byte[] ADTS_HEADER = TestUtil.createByteArray(
      0xff, 0xf1, 0x50, 0x80, 0x01, 0xdf, 0xfc);

  public static final byte[] ADTS_CONTENT = TestUtil.createByteArray(
      0x20, 0x00, 0x20, 0x00, 0x00, 0x80, 0x0e);

  private static final byte TEST_DATA[] = TestUtil.joinByteArrays(
      ID3_DATA_1,
      ID3_DATA_2,
      ADTS_HEADER,
      ADTS_CONTENT);

  private static final long ADTS_SAMPLE_DURATION = 23219L;

  private FakeTrackOutput adtsOutput;
  private FakeTrackOutput id3Output;
  private AdtsReader adtsReader;
  private ParsableByteArray data;
  private boolean firstFeed;

  @Override
  protected void setUp() throws Exception {
    adtsOutput = new FakeTrackOutput();
    id3Output = new FakeTrackOutput();
    adtsReader = new AdtsReader(adtsOutput, id3Output);
    data = new ParsableByteArray(TEST_DATA);
    firstFeed = true;
  }

  public void testSkipToNextSample() throws Exception {
    for (int i = 1; i <= ID3_DATA_1.length + ID3_DATA_2.length; i++) {
      data.setPosition(i);
      feed();
      // Once the data position set to ID3_DATA_1.length, no more id3 samples are read
      int id3SampleCount = Math.min(i, ID3_DATA_1.length);
      assertSampleCounts(id3SampleCount, i);
    }
  }

  public void testSkipToNextSampleResetsState() throws Exception {
    data = new ParsableByteArray(TestUtil.joinByteArrays(
        ADTS_HEADER,
        ADTS_CONTENT,
        // Adts sample missing the first sync byte
        Arrays.copyOfRange(ADTS_HEADER, 1, ADTS_HEADER.length),
        ADTS_CONTENT));
    feed();
    assertSampleCounts(0, 1);
    adtsOutput.assertSample(0, ADTS_CONTENT, 0, C.SAMPLE_FLAG_SYNC, null);
  }

  public void testNoData() throws Exception {
    feedLimited(0);
    assertSampleCounts(0, 0);
  }

  public void testNotEnoughDataForIdentifier() throws Exception {
    feedLimited(3 - 1);
    assertSampleCounts(0, 0);
  }

  public void testNotEnoughDataForHeader() throws Exception {
    feedLimited(10 - 1);
    assertSampleCounts(0, 0);
  }

  public void testNotEnoughDataForWholeId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length - 1);
    assertSampleCounts(0, 0);
  }

  public void testConsumeWholeId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length);
    assertSampleCounts(1, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.SAMPLE_FLAG_SYNC, null);
  }

  public void testMultiId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length + ID3_DATA_2.length - 1);
    assertSampleCounts(1, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.SAMPLE_FLAG_SYNC, null);
  }

  public void testMultiId3PacketConsumed() throws Exception {
    feedLimited(ID3_DATA_1.length + ID3_DATA_2.length);
    assertSampleCounts(2, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.SAMPLE_FLAG_SYNC, null);
    id3Output.assertSample(1, ID3_DATA_2, 0, C.SAMPLE_FLAG_SYNC, null);
  }

  public void testMultiPacketConsumed() throws Exception {
    for (int i = 0; i < 10; i++) {
      data.setPosition(0);
      feed();

      long timeUs = ADTS_SAMPLE_DURATION * i;
      int j = i * 2;
      assertSampleCounts(j + 2, i + 1);

      id3Output.assertSample(j, ID3_DATA_1, timeUs, C.SAMPLE_FLAG_SYNC, null);
      id3Output.assertSample(j + 1, ID3_DATA_2, timeUs, C.SAMPLE_FLAG_SYNC, null);
      adtsOutput.assertSample(i, ADTS_CONTENT, timeUs, C.SAMPLE_FLAG_SYNC, null);
    }
  }

  public void testAdtsDataOnly() throws Exception {
    data.setPosition(ID3_DATA_1.length + ID3_DATA_2.length);
    feed();
    assertSampleCounts(0, 1);
    adtsOutput.assertSample(0, ADTS_CONTENT, 0, C.SAMPLE_FLAG_SYNC, null);
  }

  private void feedLimited(int limit) {
    maybeStartPacket();
    data.setLimit(limit);
    feed();
  }

  private void feed() {
    maybeStartPacket();
    adtsReader.consume(data);
  }

  private void maybeStartPacket() {
    if (firstFeed) {
      adtsReader.packetStarted(0, true);
      firstFeed = false;
    }
  }

  private void assertSampleCounts(int id3SampleCount, int adtsSampleCount) {
    id3Output.assertSampleCount(id3SampleCount);
    adtsOutput.assertSampleCount(adtsSampleCount);
  }

}


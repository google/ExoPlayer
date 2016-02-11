/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.mp3;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.MpegAudioHeader;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.test.InstrumentationTestCase;

/**
 * Tests for {@link XingSeeker}.
 */
public final class XingSeekerTest extends InstrumentationTestCase {

  // Xing header/payload from http://storage.googleapis.com/exoplayer-test-media-0/play.mp3.
  private static final int XING_FRAME_HEADER_DATA = 0xFFFB3000;
  private static final byte[] XING_FRAME_PAYLOAD = Util.getBytesFromHexString(
      "00000007000008dd000e7919000205080a0d0f1214171a1c1e212426292c2e303336383b3d404245484a4c4f5254"
      + "575a5c5e616466696b6e707376787a7d808285878a8c8f929496999c9ea1a4a6a8abaeb0b3b5b8babdc0c2c4c7"
      + "cacccfd2d4d6d9dcdee1e3e6e8ebeef0f2f5f8fafd");
  private static final int XING_FRAME_POSITION = 157;

  /**
   * Size of the audio stream, encoded in {@link #XING_FRAME_PAYLOAD}.
   */
  private static final int STREAM_SIZE_BYTES = 948505;
  /**
   * Duration of the audio stream in microseconds, encoded in {@link #XING_FRAME_PAYLOAD}.
   */
  private static final int STREAM_DURATION_US = 59271836;
  /**
   * The length of the file in bytes.
   */
  private static final int INPUT_LENGTH = 948662;

  private XingSeeker seeker;
  private XingSeeker seekerWithInputLength;
  private int xingFrameSize;

  @Override
  public void setUp() throws Exception {
    MpegAudioHeader xingFrameHeader = new MpegAudioHeader();
    MpegAudioHeader.populateHeader(XING_FRAME_HEADER_DATA, xingFrameHeader);
    seeker = XingSeeker.create(xingFrameHeader, new ParsableByteArray(XING_FRAME_PAYLOAD),
        XING_FRAME_POSITION, C.UNKNOWN_TIME_US);
    seekerWithInputLength = XingSeeker.create(xingFrameHeader,
        new ParsableByteArray(XING_FRAME_PAYLOAD), XING_FRAME_POSITION, INPUT_LENGTH);
    xingFrameSize = xingFrameHeader.frameSize;
  }

  public void testGetTimeUsBeforeFirstAudioFrame() {
    assertEquals(0, seeker.getTimeUs(-1));
    assertEquals(0, seekerWithInputLength.getTimeUs(-1));
  }

  public void testGetTimeUsAtFirstAudioFrame() {
    assertEquals(0, seeker.getTimeUs(XING_FRAME_POSITION + xingFrameSize));
    assertEquals(0, seekerWithInputLength.getTimeUs(XING_FRAME_POSITION + xingFrameSize));
  }

  public void testGetTimeUsAtEndOfStream() {
    assertEquals(STREAM_DURATION_US,
        seeker.getTimeUs(XING_FRAME_POSITION + xingFrameSize + STREAM_SIZE_BYTES));
    assertEquals(STREAM_DURATION_US,
        seekerWithInputLength.getTimeUs(XING_FRAME_POSITION + xingFrameSize + STREAM_SIZE_BYTES));
  }

  public void testGetPositionAtStartOfStream() {
    assertEquals(XING_FRAME_POSITION + xingFrameSize, seeker.getPosition(0));
    assertEquals(XING_FRAME_POSITION + xingFrameSize, seekerWithInputLength.getPosition(0));
  }

  public void testGetPositionAtEndOfStream() {
    assertEquals(XING_FRAME_POSITION + STREAM_SIZE_BYTES - 1,
        seeker.getPosition(STREAM_DURATION_US));
    assertEquals(XING_FRAME_POSITION + STREAM_SIZE_BYTES - 1,
        seekerWithInputLength.getPosition(STREAM_DURATION_US));
  }

  public void testGetTimeForAllPositions() {
    for (int offset = xingFrameSize; offset < STREAM_SIZE_BYTES; offset++) {
      int position = XING_FRAME_POSITION + offset;
      long timeUs = seeker.getTimeUs(position);
      assertEquals(position, seeker.getPosition(timeUs));
      timeUs = seekerWithInputLength.getTimeUs(position);
      assertEquals(position, seekerWithInputLength.getPosition(timeUs));
    }
  }

}

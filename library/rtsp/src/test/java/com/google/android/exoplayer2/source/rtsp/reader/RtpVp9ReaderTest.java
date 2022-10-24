/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp.reader;

import static com.google.android.exoplayer2.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtpVp9Reader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpVp9ReaderTest {

  /** VP9 uses a 90 KHz media clock (RFC7741 Section 4.1). */
  private static final long MEDIA_CLOCK_FREQUENCY = 90_000;

  private static final byte[] PARTITION_1 = getBytesFromHexString("000102030405060708090A0B0C0D0E");
  // 000102030405060708090A
  private static final byte[] PARTITION_1_FRAGMENT_1 =
      Arrays.copyOf(PARTITION_1, /* newLength= */ 11);
  // 0B0C0D0E
  private static final byte[] PARTITION_1_FRAGMENT_2 =
      Arrays.copyOfRange(PARTITION_1, /* from= */ 11, /* to= */ 15);
  private static final long PARTITION_1_RTP_TIMESTAMP = 2599168056L;
  private static final RtpPacket PACKET_PARTITION_1_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp(PARTITION_1_RTP_TIMESTAMP)
          .setSequenceNumber(40289)
          .setMarker(false)
          .setPayloadData(Bytes.concat(getBytesFromHexString("08"), PARTITION_1_FRAGMENT_1))
          .build();
  private static final RtpPacket PACKET_PARTITION_1_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp(PARTITION_1_RTP_TIMESTAMP)
          .setSequenceNumber(40290)
          .setMarker(true)
          .setPayloadData(Bytes.concat(getBytesFromHexString("00"), PARTITION_1_FRAGMENT_2))
          .build();

  private static final byte[] PARTITION_2 = getBytesFromHexString("0D0C0B0A09080706050403020100");
  // 0D0C0B0A090807060504
  private static final byte[] PARTITION_2_FRAGMENT_1 =
      Arrays.copyOf(PARTITION_2, /* newLength= */ 10);
  // 03020100
  private static final byte[] PARTITION_2_FRAGMENT_2 =
      Arrays.copyOfRange(PARTITION_2, /* from= */ 10, /* to= */ 14);
  private static final long PARTITION_2_RTP_TIMESTAMP = 2599168344L;
  private static final RtpPacket PACKET_PARTITION_2_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp(PARTITION_2_RTP_TIMESTAMP)
          .setSequenceNumber(40291)
          .setMarker(false)
          .setPayloadData(Bytes.concat(getBytesFromHexString("08"), PARTITION_2_FRAGMENT_1))
          .build();
  private static final RtpPacket PACKET_PARTITION_2_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp(PARTITION_2_RTP_TIMESTAMP)
          .setSequenceNumber(40292)
          .setMarker(true)
          .setPayloadData(Bytes.concat(getBytesFromHexString("00"), PARTITION_2_FRAGMENT_2))
          .build();
  private static final long PARTITION_2_PRESENTATION_TIMESTAMP_US =
      Util.scaleLargeTimestamp(
          (PARTITION_2_RTP_TIMESTAMP - PARTITION_1_RTP_TIMESTAMP),
          /* multiplier= */ C.MICROS_PER_SECOND,
          /* divisor= */ MEDIA_CLOCK_FREQUENCY);

  private FakeExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    extractorOutput =
        new FakeExtractorOutput(
            (id, type) -> new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ true));
  }

  @Test
  public void consume_validPackets() {
    RtpVp9Reader vp9Reader = createVp9Reader();

    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        PACKET_PARTITION_1_FRAGMENT_1.timestamp, PACKET_PARTITION_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_2);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(PARTITION_1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(PARTITION_2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(PARTITION_2_PRESENTATION_TIMESTAMP_US);
  }

  @Test
  public void consume_fragmentedFrameMissingFirstFragment() {
    RtpVp9Reader vp9Reader = createVp9Reader();

    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    // First packet timing information is transmitted over RTSP, not RTP.
    vp9Reader.onReceivingFirstPacket(
        PACKET_PARTITION_1_FRAGMENT_1.timestamp, PACKET_PARTITION_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_2);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(1);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(PARTITION_2);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(PARTITION_2_PRESENTATION_TIMESTAMP_US);
  }

  @Test
  public void consume_fragmentedFrameMissingBoundaryFragment() {
    RtpVp9Reader vp9Reader = createVp9Reader();

    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        PACKET_PARTITION_1_FRAGMENT_1.timestamp, PACKET_PARTITION_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(PARTITION_1_FRAGMENT_1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(PARTITION_2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(PARTITION_2_PRESENTATION_TIMESTAMP_US);
  }

  @Test
  public void consume_outOfOrderFragmentedFrame() {
    RtpVp9Reader vp9Reader = createVp9Reader();

    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        PACKET_PARTITION_1_FRAGMENT_1.timestamp, PACKET_PARTITION_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_1);
    consume(vp9Reader, PACKET_PARTITION_1_FRAGMENT_2);
    consume(vp9Reader, PACKET_PARTITION_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(PARTITION_1_FRAGMENT_1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(PARTITION_2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(PARTITION_2_PRESENTATION_TIMESTAMP_US);
  }

  private static RtpVp9Reader createVp9Reader() {
    return new RtpVp9Reader(
        new RtpPayloadFormat(
            new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_VP9).build(),
            /* rtpPayloadType= */ 96,
            /* clockRate= */ (int) MEDIA_CLOCK_FREQUENCY,
            /* fmtpParameters= */ ImmutableMap.of(),
            RtpPayloadFormat.RTP_MEDIA_VP9));
  }

  private static void consume(RtpVp9Reader vp9Reader, RtpPacket rtpPacket) {
    vp9Reader.consume(
        new ParsableByteArray(rtpPacket.payloadData),
        rtpPacket.timestamp,
        rtpPacket.sequenceNumber,
        rtpPacket.marker);
  }
}

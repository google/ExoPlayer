/*
 * Copyright 2021 The Android Open Source Project
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link RtpAc3Reader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpAc3ReaderTest {

  private final RtpPacket frame1fragment1 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40289,
          /* marker= */ false,
          /* payloadData= */ getBytesFromHexString("02020102"));
  private final RtpPacket frame1fragment2 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40290,
          /* marker= */ true,
          /* payloadData= */ getBytesFromHexString("03020304"));
  private final RtpPacket frame2fragment1 =
      createRtpPacket(
          /* timestamp= */ 2599169592L,
          /* sequenceNumber= */ 40292,
          /* marker= */ false,
          /* payloadData= */ getBytesFromHexString("02020506"));
  private final RtpPacket frame2fragment2 =
      createRtpPacket(
          /* timestamp= */ 2599169592L,
          /* sequenceNumber= */ 40293,
          /* marker= */ true,
          /* payloadData= */ getBytesFromHexString("03020708"));

  private static final RtpPayloadFormat AC3_FORMAT =
      new RtpPayloadFormat(
          new Format.Builder()
              .setChannelCount(6)
              .setSampleMimeType(MimeTypes.AUDIO_AC3)
              .setSampleRate(48_000)
              .build(),
          /* rtpPayloadType= */ 97,
          /* clockRate= */ 48_000,
          /* fmtpParameters= */ ImmutableMap.of());

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private ParsableByteArray packetData;

  private RtpAc3Reader ac3Reader;
  private FakeTrackOutput trackOutput;
  @Mock private ExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    trackOutput = new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ true);
    when(extractorOutput.track(anyInt(), anyInt())).thenReturn(trackOutput);
    ac3Reader = new RtpAc3Reader(AC3_FORMAT);
    ac3Reader.createTracks(extractorOutput, /* trackId= */ 0);
  }

  @Test
  public void consume_allPackets() {
    ac3Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    packetData.reset(frame1fragment1.payloadData);
    ac3Reader.consume(
        packetData,
        frame1fragment1.timestamp,
        frame1fragment1.sequenceNumber,
        /* isFrameBoundary= */ frame1fragment1.marker);
    packetData.reset(frame1fragment2.payloadData);
    ac3Reader.consume(
        packetData,
        frame1fragment2.timestamp,
        frame1fragment2.sequenceNumber,
        /* isFrameBoundary= */ frame1fragment2.marker);
    packetData.reset(frame2fragment1.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment1.timestamp,
        frame2fragment1.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment1.marker);
    packetData.reset(frame2fragment2.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment2.timestamp,
        frame2fragment2.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment2.marker);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("01020304"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("05060708"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_fragmentedFrameMissingFirstFragment() {
    // First packet timing information is transmitted over RTSP, not RTP.
    ac3Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    packetData.reset(frame1fragment2.payloadData);
    ac3Reader.consume(
        packetData,
        frame1fragment2.timestamp,
        frame1fragment2.sequenceNumber,
        /* isFrameBoundary= */ frame1fragment2.marker);
    packetData.reset(frame2fragment1.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment1.timestamp,
        frame2fragment1.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment1.marker);
    packetData.reset(frame2fragment2.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment2.timestamp,
        frame2fragment2.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment2.marker);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("0304"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("05060708"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_fragmentedFrameMissingBoundaryFragment() {
    ac3Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    packetData.reset(frame1fragment1.payloadData);
    ac3Reader.consume(
        packetData,
        frame1fragment1.timestamp,
        frame1fragment1.sequenceNumber,
        /* isFrameBoundary= */ frame1fragment1.marker);
    packetData.reset(frame2fragment1.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment1.timestamp,
        frame2fragment1.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment1.marker);
    packetData.reset(frame2fragment2.payloadData);
    ac3Reader.consume(
        packetData,
        frame2fragment2.timestamp,
        frame2fragment2.sequenceNumber,
        /* isFrameBoundary= */ frame2fragment2.marker);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("0102"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("05060708"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  private static RtpPacket createRtpPacket(
      long timestamp, int sequenceNumber, boolean marker, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp((int) timestamp)
        .setSequenceNumber(sequenceNumber)
        .setMarker(marker)
        .setPayloadData(payloadData)
        .build();
  }
}

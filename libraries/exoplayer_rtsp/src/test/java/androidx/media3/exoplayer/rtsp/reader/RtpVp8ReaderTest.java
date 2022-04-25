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
package androidx.media3.exoplayer.rtsp.reader;

import static androidx.media3.common.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit test for {@link RtpVp8Reader}.
 */
@RunWith(AndroidJUnit4.class)
public final class RtpVp8ReaderTest {

  private final RtpPacket frame1fragment1 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40289,
          /* marker= */ false,
          /* payloadData= */ getBytesFromHexString("10000102030405060708090A"));
  private final RtpPacket frame1fragment2 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40290,
          /* marker= */ true,
          /* payloadData= */ getBytesFromHexString("000B0C0D0E"));
  private final byte[] frame1Data = getBytesFromHexString("000102030405060708090A0B0C0D0E");
  private final RtpPacket frame2fragment1 =
      createRtpPacket(
          /* timestamp= */ 2599168344L,
          /* sequenceNumber= */ 40291,
          /* marker= */ false,
          /* payloadData= */ getBytesFromHexString("100D0C0B0A090807060504"));
  // Add optional headers
  private final RtpPacket frame2fragment2 =
      createRtpPacket(
          /* timestamp= */ 2599168344L,
          /* sequenceNumber= */ 40292,
          /* marker= */ true,
          /* payloadData= */ getBytesFromHexString("80D6AA95396103020100"));
  private final byte[] frame2Data = getBytesFromHexString("0D0C0B0A09080706050403020100");

  private static final RtpPayloadFormat VP8_FORMAT =
      new RtpPayloadFormat(
          new Format.Builder()
              .setSampleMimeType(MimeTypes.VIDEO_VP8)
              .setSampleRate(500000)
              .build(),
          /* rtpPayloadType= */ 97,
          /* clockRate= */ 48_000,
          /* fmtpParameters= */ ImmutableMap.of());

  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();

  private ParsableByteArray packetData;

  private RtpVp8Reader vp8Reader;
  private FakeTrackOutput trackOutput;
  @Mock
  private ExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    trackOutput = new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ true);
    when(extractorOutput.track(anyInt(), anyInt())).thenReturn(trackOutput);
    vp8Reader = new RtpVp8Reader(VP8_FORMAT);
    vp8Reader.createTracks(extractorOutput, /* trackId= */ 0);
  }

  @Test
  public void consume_validPackets() {
    vp8Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    consume(frame1fragment1);
    consume(frame1fragment2);
    consume(frame2fragment1);
    consume(frame2fragment2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(frame1Data);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(frame2Data);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
  }

  @Test
  public void consume_fragmentedFrameMissingFirstFragment() {
    // First packet timing information is transmitted over RTSP, not RTP.
    vp8Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    consume(frame1fragment2);
    consume(frame2fragment1);
    consume(frame2fragment2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(1);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(frame2Data);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(3200);
  }

  @Test
  public void consume_fragmentedFrameMissingBoundaryFragment() {
    vp8Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    consume(frame1fragment1);
    consume(frame2fragment1);
    consume(frame2fragment2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0))
        .isEqualTo(getBytesFromHexString("000102030405060708090A"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(frame2Data);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
  }

  @Test
  public void consume_outOfOrderFragmentedFrame() {
    vp8Reader.onReceivingFirstPacket(frame1fragment1.timestamp, frame1fragment1.sequenceNumber);
    consume(frame1fragment1);
    consume(frame2fragment1);
    consume(frame1fragment2);
    consume(frame2fragment2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0))
        .isEqualTo(getBytesFromHexString("000102030405060708090A"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(frame2Data);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
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

  private void consume(RtpPacket rtpPacket) {
    packetData.reset(rtpPacket.payloadData);
    vp8Reader.consume(
        packetData,
        rtpPacket.timestamp,
        rtpPacket.sequenceNumber,
        /* isFrameBoundary= */ rtpPacket.marker);
  }
}

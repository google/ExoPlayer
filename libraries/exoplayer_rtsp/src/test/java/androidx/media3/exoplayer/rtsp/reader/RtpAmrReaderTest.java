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

/** Unit test for {@link RtpAmrReader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpAmrReaderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  private ParsableByteArray packetData;
  private FakeTrackOutput trackOutput;
  @Mock private ExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    trackOutput = new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ false);
    when(extractorOutput.track(anyInt(), anyInt())).thenReturn(trackOutput);
  }

  @Test
  public void consume_validPackets_AMR_NB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("00400102030405"));
    RtpPacket frame2 =
        createRtpPacket(
            /* timestamp= */ 2599169592L,
            /* sequenceNumber= */ 40290,
            /* payloadData= */ getBytesFromHexString("60C0060708090A"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
    packetData.reset(frame2.payloadData);
    amrReader.consume(
        packetData,
        frame2.timestamp,
        frame2.sequenceNumber,
        /* isFrameBoundary= */ frame2.marker);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("400102030405"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("C0060708090A"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(192000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void consume_invalidFrameType_AMR_NB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("00680102030405"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
  }

  @Test(expected = IllegalArgumentException.class)
  public void consume_invalidFrameSize_AMR_NB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("004001020304"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
  }

  @Test
  public void consume_validPackets_AMR_WB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR_WB);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("60870102030405060708090A0B0C0D0E0F1011"));
    RtpPacket frame2 =
        createRtpPacket(
            /* timestamp= */ 2599169592L,
            /* sequenceNumber= */ 40290,
            /* payloadData= */ getBytesFromHexString("007E"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
    packetData.reset(frame2.payloadData);
    amrReader.consume(
        packetData,
        frame2.timestamp,
        frame2.sequenceNumber,
        /* isFrameBoundary= */ frame2.marker);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(
        getBytesFromHexString("870102030405060708090A0B0C0D0E0F1011"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("7E"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(96000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void consume_invalidFrameType_AMR_WB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR_WB);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("00D501020304"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
  }

  @Test(expected = IllegalArgumentException.class)
  public void consume_invalidFrameSize_AMR_WB() {
    RtpAmrReader amrReader = createAmrReader(MimeTypes.AUDIO_AMR_WB);
    RtpPacket frame1 =
        createRtpPacket(
            /* timestamp= */ 2599168056L,
            /* sequenceNumber= */ 40289,
            /* payloadData= */ getBytesFromHexString("00A50102"));

    amrReader.createTracks(extractorOutput, /* trackId= */ 0);
    amrReader.onReceivingFirstPacket(frame1.timestamp, frame1.sequenceNumber);
    packetData.reset(frame1.payloadData);
    amrReader.consume(
        packetData,
        frame1.timestamp,
        frame1.sequenceNumber,
        /* isFrameBoundary= */ frame1.marker);
  }

  private static RtpPacket createRtpPacket(long timestamp, int sequenceNumber, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp((int) timestamp)
        .setSequenceNumber(sequenceNumber)
        .setMarker(false)
        .setPayloadData(payloadData)
        .build();
  }

  private static RtpAmrReader createAmrReader(String mimeType) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AMR:
        return new RtpAmrReader(createRtpPayloadFormat(mimeType, 8000));
      case MimeTypes.AUDIO_AMR_WB:
        return new RtpAmrReader(createRtpPayloadFormat(mimeType, 16000));
    }
    return null;
  }

  private static RtpPayloadFormat createRtpPayloadFormat(String mimeType, int sampleRate) {
    return new RtpPayloadFormat(
        new Format.Builder()
            .setChannelCount(1)
            .setSampleMimeType(mimeType)
            .setSampleRate(sampleRate)
            .build(),
        /* rtpPayloadType= */ 97,
        /* clockRate= */ sampleRate,
        /* fmtpParameters= */ ImmutableMap.of());
  }
}

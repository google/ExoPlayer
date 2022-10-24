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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtpPcmReader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpPcmReaderTest {

  // A typical RTP payload type for audio.
  private static final int RTP_PAYLOAD_TYPE = 97;
  private static final byte[] FRAME_1_PAYLOAD = TestUtil.buildTestData(/* length= */ 4);
  private static final byte[] FRAME_2_PAYLOAD = TestUtil.buildTestData(/* length= */ 4);

  private static final RtpPacket PACKET_1 =
      createRtpPacket(/* timestamp= */ 2599168056L, /* sequenceNumber= */ 40289, FRAME_1_PAYLOAD);
  private static final RtpPacket PACKET_2 =
      createRtpPacket(/* timestamp= */ 2599169592L, /* sequenceNumber= */ 40290, FRAME_2_PAYLOAD);

  private ParsableByteArray packetData;
  private FakeExtractorOutput extractorOutput;
  private RtpPcmReader pcmReader;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    extractorOutput = new FakeExtractorOutput();
  }

  @Test
  public void consume_twoDualChannelWav8bitPackets() {
    pcmReader =
        new RtpPcmReader(
            new RtpPayloadFormat(
                new Format.Builder()
                    .setChannelCount(2)
                    .setSampleMimeType(MimeTypes.AUDIO_WAV)
                    .setPcmEncoding(C.ENCODING_PCM_8BIT)
                    .setSampleRate(48_000)
                    .build(),
                /* rtpPayloadType= */ RTP_PAYLOAD_TYPE,
                /* clockRate= */ 48_000,
                /* fmtpParameters= */ ImmutableMap.of(),
                RtpPayloadFormat.RTP_MEDIA_PCM_L8));

    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(PACKET_1.timestamp, PACKET_1.sequenceNumber);
    consume(PACKET_1);
    consume(PACKET_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_twoSingleChannelWav16bitPackets() {
    pcmReader =
        new RtpPcmReader(
            new RtpPayloadFormat(
                new Format.Builder()
                    .setChannelCount(1)
                    .setSampleMimeType(MimeTypes.AUDIO_WAV)
                    .setPcmEncoding(C.ENCODING_PCM_16BIT_BIG_ENDIAN)
                    .setSampleRate(60_000)
                    .build(),
                /* rtpPayloadType= */ RTP_PAYLOAD_TYPE,
                /* clockRate= */ 60_000,
                /* fmtpParameters= */ ImmutableMap.of(),
                RtpPayloadFormat.RTP_MEDIA_PCM_L16));

    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(PACKET_1.timestamp, PACKET_1.sequenceNumber);
    consume(PACKET_1);
    consume(PACKET_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(25600);
  }

  @Test
  public void consume_twoDualChannelAlawPackets() {
    pcmReader =
        new RtpPcmReader(
            new RtpPayloadFormat(
                new Format.Builder()
                    .setChannelCount(2)
                    .setSampleMimeType(MimeTypes.AUDIO_ALAW)
                    .setSampleRate(16_000)
                    .build(),
                /* rtpPayloadType= */ RTP_PAYLOAD_TYPE,
                /* clockRate= */ 16_000,
                /* fmtpParameters= */ ImmutableMap.of(),
                RtpPayloadFormat.RTP_MEDIA_PCMA));

    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(PACKET_1.timestamp, PACKET_1.sequenceNumber);
    consume(PACKET_1);
    consume(PACKET_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(96000);
  }

  @Test
  public void consume_twoDualChannelMlawPackets() {
    pcmReader =
        new RtpPcmReader(
            new RtpPayloadFormat(
                new Format.Builder()
                    .setChannelCount(2)
                    .setSampleMimeType(MimeTypes.AUDIO_MLAW)
                    .setSampleRate(24_000)
                    .build(),
                /* rtpPayloadType= */ RTP_PAYLOAD_TYPE,
                /* clockRate= */ 24_000,
                /* fmtpParameters= */ ImmutableMap.of(),
                RtpPayloadFormat.RTP_MEDIA_PCMU));

    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(PACKET_1.timestamp, PACKET_1.sequenceNumber);
    consume(PACKET_1);
    consume(PACKET_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_PAYLOAD);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(64000);
  }

  private static RtpPacket createRtpPacket(long timestamp, int sequenceNumber, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp(timestamp)
        .setSequenceNumber(sequenceNumber)
        // RFC3551 Section 4.1.
        .setMarker(false)
        .setPayloadData(payloadData)
        .build();
  }

  private void consume(RtpPacket frame) {
    packetData.reset(frame.payloadData);
    pcmReader.consume(packetData, frame.timestamp, frame.sequenceNumber, frame.marker);
  }
}

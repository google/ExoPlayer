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

import androidx.media3.common.C;
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
 * Unit test for {@link RtpPcmReader}.
 */
@RunWith(AndroidJUnit4.class)
public final class RtpPcmReaderTest {

  private static final String FRAMEDATA1 = "01020304";
  private static final String FRAMEDATA2 = "05060708";

  private static final RtpPacket FRAME1 =
      createRtpPacket(2599168056L, 40289, getBytesFromHexString(FRAMEDATA1));
  private static final RtpPacket FRAME2 =
      createRtpPacket(2599169592L, 40290, getBytesFromHexString(FRAMEDATA2));

  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();
  private ParsableByteArray packetData;
  private FakeTrackOutput trackOutput;
  private RtpPcmReader pcmReader;
  @Mock
  private ExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    trackOutput = new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ true);
    when(extractorOutput.track(anyInt(), anyInt())).thenReturn(trackOutput);
  }

  @Test
  public void consume_AllPackets_8bit() {
    pcmReader = new RtpPcmReader(
        new RtpPayloadFormat(
            new Format.Builder()
                .setChannelCount(2)
                .setSampleMimeType(MimeTypes.AUDIO_WAV)
                .setPcmEncoding(C.ENCODING_PCM_8BIT)
                .setSampleRate(48_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 48_000,
            /* fmtpParameters= */ ImmutableMap.of()));
    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(FRAME1.timestamp, FRAME1.sequenceNumber);
    consume(FRAME1);
    consume(FRAME2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString(FRAMEDATA1));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString(FRAMEDATA2));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_AllPackets_16bit() {
    pcmReader = new RtpPcmReader(
        new RtpPayloadFormat(
            new Format.Builder()
                .setChannelCount(1)
                .setSampleMimeType(MimeTypes.AUDIO_WAV)
                .setPcmEncoding(C.ENCODING_PCM_16BIT_BIG_ENDIAN)
                .setSampleRate(60_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 60_000,
            /* fmtpParameters= */ ImmutableMap.of()));
    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(FRAME1.timestamp, FRAME1.sequenceNumber);
    consume(FRAME1);
    consume(FRAME2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString(FRAMEDATA1));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString(FRAMEDATA2));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(25600);
  }

  @Test
  public void consume_AllPackets_ALAW() {
    pcmReader = new RtpPcmReader(
        new RtpPayloadFormat(
            new Format.Builder()
                .setChannelCount(2)
                .setSampleMimeType(MimeTypes.AUDIO_ALAW)
                .setSampleRate(16_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 16_000,
            /* fmtpParameters= */ ImmutableMap.of()));
    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(FRAME1.timestamp, FRAME1.sequenceNumber);
    consume(FRAME1);
    consume(FRAME2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString(FRAMEDATA1));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString(FRAMEDATA2));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(96000);
  }

  @Test
  public void consume_AllPackets_MLAW() {
    pcmReader = new RtpPcmReader(
        new RtpPayloadFormat(
            new Format.Builder()
                .setChannelCount(2)
                .setSampleMimeType(MimeTypes.AUDIO_MLAW)
                .setSampleRate(24_000)
                .build(),
            /* rtpPayloadType= */ 97,
            /* clockRate= */ 24_000,
            /* fmtpParameters= */ ImmutableMap.of()));
    pcmReader.createTracks(extractorOutput, /* trackId= */ 0);
    pcmReader.onReceivingFirstPacket(FRAME1.timestamp, FRAME1.sequenceNumber);
    consume(FRAME1);
    consume(FRAME2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString(FRAMEDATA1));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString(FRAMEDATA2));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(64000);
  }

  private static RtpPacket createRtpPacket(
      long timestamp, int sequenceNumber, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp((int) timestamp)
        .setSequenceNumber(sequenceNumber)
        .setMarker(false)
        .setPayloadData(payloadData)
        .build();
  }

  private void consume(RtpPacket frame) {
    packetData.reset(frame.payloadData);
    pcmReader.consume(packetData, frame.timestamp, frame.sequenceNumber, frame.marker);
  }
}

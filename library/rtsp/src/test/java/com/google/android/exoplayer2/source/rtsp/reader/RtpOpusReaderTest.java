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
import static org.junit.Assert.assertThrows;
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

/** Unit test for {@link RtpOpusReader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpOpusReaderTest {

  private static final RtpPayloadFormat OPUS_FORMAT =
      new RtpPayloadFormat(
          new Format.Builder()
              .setChannelCount(6)
              .setSampleMimeType(MimeTypes.AUDIO_OPUS)
              .setSampleRate(48_000)
              .build(),
          /* rtpPayloadType= */ 97,
          /* clockRate= */ 48_000,
          /* fmtpParameters= */ ImmutableMap.of(),
          RtpPayloadFormat.RTP_MEDIA_OPUS);

  private static final RtpPacket OPUS_HEADER =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40289,
          /* payloadData= */ getBytesFromHexString("4F707573486561640102000000000000000000"));
  private static final RtpPacket OPUS_TAGS =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40290,
          /* payloadData= */ getBytesFromHexString("4F707573546167730000000000000000000000"));
  private static final RtpPacket OPUS_FRAME_1 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40292,
          /* payloadData= */ getBytesFromHexString("010203"));
  private static final RtpPacket OPUS_FRAME_2 =
      createRtpPacket(
          /* timestamp= */ 2599169592L,
          /* sequenceNumber= */ 40293,
          /* payloadData= */ getBytesFromHexString("04050607"));

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private ParsableByteArray packetData;
  private RtpOpusReader opusReader;
  private FakeTrackOutput trackOutput;
  @Mock private ExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    packetData = new ParsableByteArray();
    trackOutput = new FakeTrackOutput(/* deduplicateConsecutiveFormats= */ true);
    when(extractorOutput.track(anyInt(), anyInt())).thenReturn(trackOutput);
    opusReader = new RtpOpusReader(OPUS_FORMAT);
    opusReader.createTracks(extractorOutput, /* trackId= */ 0);
  }

  @Test
  public void consume_validPackets() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    consume(OPUS_HEADER);
    consume(OPUS_TAGS);
    consume(OPUS_FRAME_1);
    consume(OPUS_FRAME_2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("010203"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("04050607"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_opusHeaderWithInvalidHeader_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            consume(
                createRtpPacket(
                    /* timestamp= */ 2599168056L,
                    /* sequenceNumber= */ 40289,
                    // Modify "OpusHead" -> "OrusHead" (First 8 bytes).
                    /* payloadData= */ getBytesFromHexString(
                        "4F727573486561640102000000000000000000"))));
  }

  @Test
  public void consume_opusHeaderWithInvalidSampleSize_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            consume(
                createRtpPacket(
                    /* timestamp= */ 2599168056L,
                    /* sequenceNumber= */ 40289,
                    // Truncate the opusHeader payload data.
                    /* payloadData= */ getBytesFromHexString("4F707573486561640102"))));
  }

  @Test
  public void consume_opusHeaderWithInvalidVersionNumber_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            consume(
                createRtpPacket(
                    /* timestamp= */ 2599168056L,
                    /* sequenceNumber= */ 40289,
                    // Modify version 1 -> 2 (9th byte)
                    /* payloadData= */ getBytesFromHexString(
                        "4f707573486561640202000000000000000000"))));
  }

  @Test
  public void consume_invalidOpusTags_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    consume(OPUS_HEADER);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            consume(
                createRtpPacket(
                    /* timestamp= */ 2599168056L,
                    /* sequenceNumber= */ 40290,
                    // Modify "OpusTags" -> "OpusTggs" (First 8 bytes)
                    /* payloadData= */ getBytesFromHexString("4F70757354676773"))));
  }

  @Test
  public void consume_skipOpusTags_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    consume(OPUS_HEADER);
    assertThrows(IllegalArgumentException.class, () -> consume(OPUS_FRAME_1));
  }

  @Test
  public void consume_skipOpusHeader_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    assertThrows(IllegalArgumentException.class, () -> consume(OPUS_TAGS));
  }

  @Test
  public void consume_skipOpusHeaderAndOpusTags_throwsIllegalArgumentException() {
    opusReader.onReceivingFirstPacket(OPUS_HEADER.timestamp, OPUS_HEADER.sequenceNumber);
    assertThrows(IllegalArgumentException.class, () -> consume(OPUS_FRAME_1));
  }

  private static RtpPacket createRtpPacket(long timestamp, int sequenceNumber, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp(timestamp)
        .setSequenceNumber(sequenceNumber)
        .setMarker(false)
        .setPayloadData(payloadData)
        .build();
  }

  private void consume(RtpPacket rtpPacket) {
    packetData.reset(rtpPacket.payloadData);
    opusReader.consume(packetData, rtpPacket.timestamp, rtpPacket.sequenceNumber, rtpPacket.marker);
  }
}

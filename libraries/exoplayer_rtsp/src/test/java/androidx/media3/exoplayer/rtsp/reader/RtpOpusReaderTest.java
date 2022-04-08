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
import static org.junit.Assert.assertThrows;
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
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit test for {@link RtpOpusReader}.
 */
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
          /* fmtpParameters= */ ImmutableMap.of());

  private final RtpPacket opusHeader =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40289,
          /* payloadData= */ getBytesFromHexString("4F707573486561640102000000000000000000"));
  private final RtpPacket opusTags =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40290,
          /* payloadData= */ getBytesFromHexString("4F707573546167730000000000000000000000"));
  private final RtpPacket frame1 =
      createRtpPacket(
          /* timestamp= */ 2599168056L,
          /* sequenceNumber= */ 40292,
          /* payloadData= */ getBytesFromHexString("010203"));
  private final RtpPacket frame2 =
      createRtpPacket(
          /* timestamp= */ 2599169592L,
          /* sequenceNumber= */ 40293,
          /* payloadData= */ getBytesFromHexString("04050607"));

  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();

  private ParsableByteArray packetData;

  private RtpOpusReader opusReader;
  private FakeTrackOutput trackOutput;
  @Mock
  private ExtractorOutput extractorOutput;

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
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    consume(opusHeader);
    consume(opusTags);
    consume(frame1);
    consume(frame2);

    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(getBytesFromHexString("010203"));
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(getBytesFromHexString("04050607"));
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(32000);
  }

  @Test
  public void consume_OpusHeader_invalidHeader() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    // Modify "OpusHead" -> "OrusHead" (First 8 bytes)
    assertExceptionMessage(
        () -> consume(opusHeader, getBytesFromHexString("4F727573486561640102000000000000000000")),
        "ID Header missing");
  }

  @Test
  public void consume_OpusHeader_invalidSampleSize() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    // Truncate the opusHeader payload data
    assertExceptionMessage(
        () -> consume(opusHeader, getBytesFromHexString("4F707573486561640102")),
        "ID Header has insufficient data");
  }

  @Test
  public void consume_OpusHeader_invalidVersionNumber() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    // Modify version 1 -> 2 (9th byte)
    assertExceptionMessage(
        () -> consume(opusHeader, getBytesFromHexString("4F707573486561640202000000000000000000")),
        "version number must always be 1");
  }

  @Test
  public void consume_invalidOpusTags() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    consume(opusHeader);
    // Modify "OpusTags" -> "OpusTggs" (First 8 bytes)
    assertExceptionMessage(
        () -> consume(opusTags, getBytesFromHexString("4F70757354676773")),
        "Comment Header should follow ID Header");
  }

  @Test
  public void consume_skipOpusTags() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    consume(opusHeader);
    assertExceptionMessage(
        () -> consume(frame1),
        "Comment Header has insufficient data");
  }

  @Test
  public void consume_skipOpusHeader() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    assertExceptionMessage(
        () -> consume(opusTags),
        "ID Header missing");
  }

  @Test
  public void consume_skipOpusHeaderAndOpusTags() {
    opusReader.onReceivingFirstPacket(opusHeader.timestamp, opusHeader.sequenceNumber);
    assertExceptionMessage(
        () -> consume(frame1),
        "ID Header has insufficient data");
  }

  private static RtpPacket createRtpPacket(long timestamp, int sequenceNumber, byte[] payloadData) {
    return new RtpPacket.Builder()
        .setTimestamp((int) timestamp)
        .setSequenceNumber(sequenceNumber)
        .setMarker(false)
        .setPayloadData(payloadData)
        .build();
  }

  private void assertExceptionMessage(ThrowingRunnable runnable, String expectedExceptionMessage) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, runnable);
    assertThat(exception.getMessage()).isEqualTo(expectedExceptionMessage);
  }

  private void consume(RtpPacket rtpPacket) {
    consume(rtpPacket, rtpPacket.payloadData);
  }

  private void consume(RtpPacket rtpPacket, byte[] payloadData) {
    packetData.reset(payloadData);
    opusReader.consume(
        packetData,
        rtpPacket.timestamp,
        rtpPacket.sequenceNumber,
        /* isFrameBoundary= */ rtpPacket.marker);
  }
}

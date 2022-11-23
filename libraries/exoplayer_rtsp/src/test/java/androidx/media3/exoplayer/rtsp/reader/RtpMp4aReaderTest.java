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

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtpMp4aReader}. */
@RunWith(AndroidJUnit4.class)
public final class RtpMp4aReaderTest {
  private static final byte[] FRAME_1_FRAGMENT_1_DATA = getBytesFromHexString("0102");
  private static final RtpPacket FRAME_1_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp(2599168056L)
          .setSequenceNumber(40289)
          .setMarker(false)
          .setPayloadData(
              Bytes.concat(/* payload size */ getBytesFromHexString("02"), FRAME_1_FRAGMENT_1_DATA))
          .build();
  private static final byte[] FRAME_1_FRAGMENT_2_DATA = getBytesFromHexString("030405");
  private static final RtpPacket FRAME_1_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp(2599168056L)
          .setSequenceNumber(40290)
          .setMarker(true)
          .setPayloadData(
              Bytes.concat(/* payload size */ getBytesFromHexString("03"), FRAME_1_FRAGMENT_2_DATA))
          .build();
  private static final byte[] FRAME_1_DATA =
      Bytes.concat(FRAME_1_FRAGMENT_1_DATA, FRAME_1_FRAGMENT_2_DATA);

  private static final byte[] FRAME_2_FRAGMENT_1_DATA = getBytesFromHexString("0607");
  private static final RtpPacket FRAME_2_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp(2599168344L)
          .setSequenceNumber(40291)
          .setMarker(false)
          .setPayloadData(
              Bytes.concat(/* payload size */ getBytesFromHexString("02"), FRAME_2_FRAGMENT_1_DATA))
          .build();
  private static final byte[] FRAME_2_FRAGMENT_2_DATA = getBytesFromHexString("0809");
  private static final RtpPacket FRAME_2_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp(2599168344L)
          .setSequenceNumber(40292)
          .setMarker(true)
          .setPayloadData(
              Bytes.concat(/* payload size */ getBytesFromHexString("02"), FRAME_2_FRAGMENT_2_DATA))
          .build();
  private static final byte[] FRAME_2_DATA =
      Bytes.concat(FRAME_2_FRAGMENT_1_DATA, FRAME_2_FRAGMENT_2_DATA);

  private static final RtpPayloadFormat MP4A_LATM_FORMAT =
      new RtpPayloadFormat(
          new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(1).build(),
          /* rtpPayloadType= */ 97,
          /* clockRate= */ 44_100,
          /* fmtpParameters= */ ImmutableMap.of(),
          RtpPayloadFormat.RTP_MEDIA_MPEG4_LATM_AUDIO);

  private FakeExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    extractorOutput = new FakeExtractorOutput();
  }

  @Test
  public void consume_validPackets() throws ParserException {
    RtpMp4aReader mp4aLatmReader = new RtpMp4aReader(MP4A_LATM_FORMAT);
    mp4aLatmReader.createTracks(extractorOutput, /* trackId= */ 0);
    mp4aLatmReader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(mp4aLatmReader, FRAME_1_FRAGMENT_1);
    consume(mp4aLatmReader, FRAME_1_FRAGMENT_2);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_1);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(6530);
  }

  @Test
  public void consume_fragmentedFrameMissingFirstFragment() throws ParserException {
    RtpMp4aReader mp4aLatmReader = new RtpMp4aReader(MP4A_LATM_FORMAT);
    mp4aLatmReader.createTracks(extractorOutput, /* trackId= */ 0);
    mp4aLatmReader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(mp4aLatmReader, FRAME_1_FRAGMENT_2);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_1);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_FRAGMENT_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(6530);
  }

  @Test
  public void consume_fragmentedFrameMissingBoundaryFragment() throws ParserException {
    RtpMp4aReader mp4aLatmReader = new RtpMp4aReader(MP4A_LATM_FORMAT);
    mp4aLatmReader.createTracks(extractorOutput, /* trackId= */ 0);
    mp4aLatmReader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(mp4aLatmReader, FRAME_1_FRAGMENT_1);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_1);
    consume(mp4aLatmReader, FRAME_2_FRAGMENT_2);

    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_FRAGMENT_1_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(6530);
  }

  private static void consume(RtpMp4aReader mpeg4Reader, RtpPacket rtpPacket) {
    ParsableByteArray packetData = new ParsableByteArray();
    packetData.reset(rtpPacket.payloadData);
    mpeg4Reader.consume(
        packetData,
        rtpPacket.timestamp,
        rtpPacket.sequenceNumber,
        /* isFrameBoundary= */ rtpPacket.marker);
  }
}

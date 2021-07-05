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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RtpPacket}. */
@RunWith(AndroidJUnit4.class)
public final class RtpPacketTest {
  /*
    10.. .... = Version: RFC 1889 Version (2)
    ..0. .... = Padding: False
    ...0 .... = Extension: False
    .... 0000 = Contributing source identifiers count: 0
    1... .... = Marker: True
    Payload type: DynamicRTP-Type-96 (96)
    Sequence number: 22159
    Timestamp: 55166400
    Synchronization Source identifier: 0xd76ef1a6 (3614372262)
    Payload: 019fb174427f00006c10c4008962e33ceb5f1fde8ee2d0d9…
  */
  private final byte[] rtpData =
      getBytesFromHexString(
          "80e0568f0349c5c0d76ef1a6019fb174427f00006c10c4008962e33ceb5f1fde8ee2d0d9b169651024c83b24c3a0f274ea327e2440ae0d3e2ed194beaa2c91edaa5d1e1df7ce30d1ca3726804d2db37765cf3d174338459623bc627c15c687045390a8d702f623a8dbe49e5c7896dbd7105daecb02ce30c0eee324c0c21ed820a0e67344c7a6e10859");
  private final byte[] rtpPayloadData =
      Arrays.copyOfRange(rtpData, RtpPacket.MIN_HEADER_SIZE, rtpData.length);

  /*
   10.. .... = Version: RFC 1889 Version (2)
   ..0. .... = Padding: False
   ...0 .... = Extension: False
   .... 0000 = Contributing source identifiers count: 0
   1... .... = Marker: True
   Payload type: DynamicRTP-Type-96 (96)
   Sequence number: 29234
   Timestamp: 3688686074
   Synchronization Source identifier: 0xf5fe62a4 (4127089316)
   Payload: 419a246c43bffea996000003000003000003000003000003…
  */
  private final byte[] rtpDataWithLargeTimestamp =
      getBytesFromHexString(
          "80e07232dbdce1faf5fe62a4419a246c43bffea99600000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300000300002ce0");
  private final byte[] rtpWithLargeTimestampPayloadData =
      Arrays.copyOfRange(
          rtpDataWithLargeTimestamp, RtpPacket.MIN_HEADER_SIZE, rtpDataWithLargeTimestamp.length);

  @Test
  public void parseRtpPacket() {
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, rtpData.length));

    assertThat(packet.version).isEqualTo(RtpPacket.RTP_VERSION);
    assertThat(packet.padding).isFalse();
    assertThat(packet.extension).isFalse();
    assertThat(packet.csrcCount).isEqualTo(0);
    assertThat(packet.csrc).hasLength(0);
    assertThat(packet.marker).isTrue();
    assertThat(packet.payloadType).isEqualTo(96);
    assertThat(packet.sequenceNumber).isEqualTo(22159);
    assertThat(packet.timestamp).isEqualTo(55166400);
    assertThat(packet.ssrc).isEqualTo(0xD76EF1A6);
    assertThat(packet.payloadData).isEqualTo(rtpPayloadData);
  }

  @Test
  public void parseRtpPacketWithLargeTimestamp() {
    RtpPacket packet =
        checkNotNull(RtpPacket.parse(rtpDataWithLargeTimestamp, rtpDataWithLargeTimestamp.length));

    assertThat(packet.version).isEqualTo(RtpPacket.RTP_VERSION);
    assertThat(packet.padding).isFalse();
    assertThat(packet.extension).isFalse();
    assertThat(packet.csrcCount).isEqualTo(0);
    assertThat(packet.csrc).hasLength(0);
    assertThat(packet.marker).isTrue();
    assertThat(packet.payloadType).isEqualTo(96);
    assertThat(packet.sequenceNumber).isEqualTo(29234);
    assertThat(packet.timestamp).isEqualTo(3688686074L);
    assertThat(packet.ssrc).isEqualTo(0xf5fe62a4);
    assertThat(packet.payloadData).isEqualTo(rtpWithLargeTimestampPayloadData);
  }

  @Test
  public void writetoBuffer_withProperlySizedBuffer_writesPacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength);

    assertThat(writtenBytes).isEqualTo(packetByteLength);
    assertThat(testBuffer).isEqualTo(rtpData);
  }

  @Test
  public void writetoBuffer_withBufferTooSmall_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength / 2];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void writetoBuffer_withProperlySizedBufferButSmallLengthParameter_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes = packet.writeToBuffer(testBuffer, /* offset= */ 0, packetByteLength / 2);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void writetoBuffer_withProperlySizedBufferButNotEnoughSpaceLeft_doesNotWritePacket() {
    int packetByteLength = rtpData.length;
    RtpPacket packet = checkNotNull(RtpPacket.parse(rtpData, packetByteLength));

    byte[] testBuffer = new byte[packetByteLength];
    int writtenBytes =
        packet.writeToBuffer(testBuffer, /* offset= */ packetByteLength - 1, packetByteLength);

    assertThat(writtenBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void buildRtpPacket() {
    RtpPacket builtPacket =
        new RtpPacket.Builder()
            .setPadding(false)
            .setMarker(true)
            .setPayloadType((byte) 96)
            .setSequenceNumber(22159)
            .setTimestamp(55166400)
            .setSsrc(0xD76EF1A6)
            .setPayloadData(rtpPayloadData)
            .build();

    RtpPacket parsedPacket = checkNotNull(RtpPacket.parse(rtpData, rtpData.length));

    // Test equals function.
    assertThat(parsedPacket).isEqualTo(builtPacket);
  }

  @Test
  public void buildRtpPacketWithLargeTimestamp_matchesPacketData() {
    RtpPacket builtPacket =
        new RtpPacket.Builder()
            .setPadding(false)
            .setMarker(true)
            .setPayloadType((byte) 96)
            .setSequenceNumber(29234)
            .setTimestamp(3688686074L)
            .setSsrc(0xf5fe62a4)
            .setPayloadData(rtpWithLargeTimestampPayloadData)
            .build();

    int packetSize = RtpPacket.MIN_HEADER_SIZE + builtPacket.payloadData.length;
    byte[] builtPacketBytes = new byte[packetSize];
    builtPacket.writeToBuffer(builtPacketBytes, /* offset= */ 0, packetSize);
    assertThat(builtPacketBytes).isEqualTo(rtpDataWithLargeTimestamp);
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.rtcp;

/*
 * This class wraps a RTCP BYE packet.
 *
 *
 *          0                   1                   2                   3
 *          0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  header |V=2|P|    SC   |   PT=BYE=203  |             length            |
 *         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *         |                           SSRC/CSRC                           |
 *         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *         :                              ...                              :
 *         +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *  (opt)  |     length    |               reason for leaving            ...
 *         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class RtcpByePacket extends RtcpPacket {
    private static final int FIXED_HEADER_SIZE = 4; // 4 bytes

    private final long[] ssrcs;
    RtcpByePacket(Builder builder) {
        super(builder);
        ssrcs = builder.ssrcs;
    }

    @Override
    public byte[] getBytes() {
        // construct the first byte containing V, P and SC
        byte V_P_SC = (byte) ((RTCP_VERSION << 6) | (RTCP_PADDING << 5) |
                (byte) (ssrcs.length & 0x1F));

        // Generate the payload type byte
        byte PT[] = RtcpPacketUtils.longToBytes((long) BYE, 1);

        // Length of the packet is number of 32 byte words - 1
        byte[] length =
                RtcpPacketUtils.longToBytes(((FIXED_HEADER_SIZE + (ssrcs.length * 4))/4)-1, 2);

        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_SC;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);

        // Generate the SSRCs
        for (long ssrc : ssrcs) {
            byte ss[] = RtcpPacketUtils.longToBytes(ssrc, 4);
            packet = RtcpPacketUtils.append(packet, ss);
        }

        return packet;
    }

    /** Builder for {@link RtcpByePacket}. */
    public static final class Builder extends RtcpPacket.Builder {
        long[] ssrcs;

        public Builder() {
            super(RtcpPacket.BYE);
            ssrcs = new long[0];
        }

        public Builder setSsrcs(long[] ssrcs) {
            if (ssrcs != null) {
                this.ssrcs = ssrcs;
            }

            return this;
        }

        @Override
        public RtcpByePacket build() {
            return new RtcpByePacket(this);
        }
    }
}

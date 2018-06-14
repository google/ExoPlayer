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

import java.util.Arrays;

/**
 * This class wraps a RTCP compound packet.
 *
 */
public class RtcpCompoundPacket extends RtcpPacket  {
    private RtcpPacket packets[];

    RtcpCompoundPacket(Builder builder) {
        super(builder);
        packets = builder.packets;
    }

    public byte[] getBytes() {
        byte bytes[] = new byte[0];

        for (RtcpPacket packet : packets) {
            bytes = RtcpPacketUtils.append(bytes, packet.getBytes());
        }

        return bytes;
    }

    public RtcpPacket[] getPackets() {
        return packets;
    }

    /** Builder for {@link RtcpSrPacket}. */
    public static final class Builder extends RtcpPacket.Builder {
        private RtcpPacket packets[];

        public Builder() {
            super(COMPOUND);
            packets = new RtcpPacket[0];
        }

        public Builder addPacket(RtcpPacket packet) {
            if (packet != null) {
                int packetCount = packets.length;
                packets = Arrays.copyOf(packets, packetCount + 1);
                packets[packetCount] = packet;
                length += packet.getLength();
            }
            return this;
        }

        public RtcpCompoundPacket build() {
            return new RtcpCompoundPacket(this);
        }
    }
}



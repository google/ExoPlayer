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

import androidx.annotation.Nullable;

/*
 * This class wraps a RTCP Sender Info.
 *
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |              NTP timestamp, most significant word             |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |             NTP timestamp, least significant word             |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                         RTP timestamp                         |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                     sender's packet count                     |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                      sender's octet count                     |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public class RtcpSenderInfo {
    public static final int RTCP_SENDER_INFO_SIZE = 20; /* RFC 3550 */

    private final int packetCount;
    private final int octetCount;
    private final long rtpTimestamp;
    private final long ntpTimestampMsw;
    private final long ntpTimestampLsw;

    RtcpSenderInfo(Builder builder) {
        packetCount = builder.packetCount;
        octetCount = builder.octetCount;
        rtpTimestamp = builder.rtpTimestamp;
        ntpTimestampMsw = builder.ntpTimestampMsw;
        ntpTimestampLsw = builder.ntpTimestampLsw;
    }

    public int getPacketCount() {
        return packetCount;
    }

    public int getOctectCount() {
        return octetCount;
    }

    public long getRtpTimestamp() {
        return rtpTimestamp;
    }

    public long getNtpTimestampMsw() {
        return ntpTimestampMsw;
    }

    public long getNtpTimestampLsw() {
        return ntpTimestampLsw;
    }

    @Nullable
    public static RtcpSenderInfo parse(byte[] packet, int length) {
        if (length < RTCP_SENDER_INFO_SIZE || length > packet.length) {
            return null;
        }

        long ntpTimestampMsw = ((((long)packet[0]) & 0xff) << 24) | ((((long)packet[1]) & 0xff) << 16) |
                ((((long)packet[2]) & 0xff) << 8) | (((long)packet[3]) & 0xff);
        long ntpTimestampLsw = ((((long)packet[4]) & 0xff) << 24) | ((((long)packet[5]) & 0xff) << 16) |
                ((((long)packet[6]) & 0xff) << 8) | (((long)packet[7]) & 0xff);
        long rtpTimestamp = ((((long)packet[8]) & 0xff) << 24) | ((((long)packet[9]) & 0xff) << 16) |
                ((((long)packet[10]) & 0xff) << 8) | (((long)packet[11]) & 0xff);
        int packetCount = ((((int)packet[12]) & 0xff) << 24) | ((((int)packet[13]) & 0xff) << 16) |
                ((((int)packet[14]) & 0xff) << 8) | (((int)packet[15]) & 0xff);
        int octetCount = ((((int)packet[16]) & 0xff) << 24) | ((((int)packet[17]) & 0xff) << 16) |
                ((((int)packet[18]) & 0xff) << 8) | (((int)packet[19]) & 0xff);

        return new Builder()
                .setNtpTimestampMsw(ntpTimestampMsw)
                .setNtpTimestampLsw(ntpTimestampLsw)
                .setRtpTimestamp(rtpTimestamp)
                .setPacketCount(packetCount)
                .setOctetCount(octetCount)
                .build();
    }

    /** Builder for {@link RtcpSenderInfo}. */
    public static final class Builder {
        int packetCount;
        int octetCount;
        long rtpTimestamp;
        long ntpTimestampMsw;
        long ntpTimestampLsw;

        public Builder setPacketCount(int packetCount) {
            this.packetCount = packetCount;
            return this;
        }

        public Builder setOctetCount(int octetCount) {
            this.octetCount = octetCount;
            return this;
        }

        public Builder setRtpTimestamp(long rtpTimestamp) {
            this.rtpTimestamp = rtpTimestamp;
            return this;
        }

        public Builder setNtpTimestampMsw(long ntpTimestampMsw) {
            this.ntpTimestampMsw = ntpTimestampMsw;
            return this;
        }

        public Builder setNtpTimestampLsw(long ntpTimestampLsw) {
            this.ntpTimestampLsw = ntpTimestampLsw;
            return this;
        }

        /** Creates a {@link RtcpSenderInfo}. */
        public RtcpSenderInfo build() {
            return new RtcpSenderInfo(this);
        }
    }
}

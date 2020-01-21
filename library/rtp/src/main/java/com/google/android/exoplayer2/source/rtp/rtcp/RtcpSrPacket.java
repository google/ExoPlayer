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
 * This class wraps a RTCP Sender Report packet.
 *
 *           0                   1                   2                   3
 *           0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   header |V=2|P|    RC   |   PT=RR=200   |             length            |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                     SSRC of packet sender                     |
 *          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *   sender |              NTP timestamp, most significant word             |
 *   info   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |             NTP timestamp, least significant word             |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                         RTP timestamp                         |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                     sender's packet count                     |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                      sender's octet count                     |
 *          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *   report |                 SSRC_1 (SSRC of first source)                 |
 *   block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     1    | fraction lost |       cumulative number of packets lost       |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |           extended highest sequence number received           |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                      interarrival jitter                      |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                         last SR (LSR)                         |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                   delay since last SR (DLSR)                  |
 *          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *   report |                 SSRC_2 (SSRC of second source)                |
 *   block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     2    :                               ...                             :
 *          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *          |                  profile-specific extensions                  |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
public final class RtcpSrPacket extends RtcpPacket {
    private final long ssrc;
    private final RtcpSenderInfo senderInfo;
    private final RtcpReportBlock[] reportBlocks;

    RtcpSrPacket(Builder builder) {
        super(builder);

        ssrc = builder.ssrc;
        senderInfo = builder.senderInfo;
        reportBlocks = builder.reportBlocks;
    }

    public long getSsrc() {
        return ssrc;
    }

    public int getReportCount() {
        return reportBlocks.length;
    }

    public RtcpSenderInfo getSenderInfo() {
        return senderInfo;
    }

    public RtcpReportBlock[] getReportBlocks() {
        return reportBlocks;
    }

    @Override
    public byte[] getBytes() {
        // construct the first byte containing V, P and RC
        byte V_P_RC = (byte) ((RTCP_VERSION << 6) | (RTCP_PADDING << 5) |
                (byte) (reportBlocks.length & 0x1F));

        // SSRC of sender
        byte[] ss = RtcpPacketUtils.longToBytes(ssrc, 4);

        // Payload Type = SR
        byte[] pt = RtcpPacketUtils.longToBytes((long) SR, 1);

        // Sender Info
        byte nptTimestampMsw[] = RtcpPacketUtils.longToBytes(senderInfo.getNtpTimestampMsw(), 4);
        byte nptTimestampLsw[] = RtcpPacketUtils.longToBytes(senderInfo.getNtpTimestampLsw(), 4);
        byte rtpTimestamp[] = RtcpPacketUtils.longToBytes(senderInfo.getRtpTimestamp(), 4);
        byte packetCount[] = RtcpPacketUtils.longToBytes(senderInfo.getPacketCount(), 4);
        byte octetCount[] = RtcpPacketUtils.longToBytes(senderInfo.getOctectCount(), 4);

        // Sender Report Blocks
        byte[] senderReportBlocks = new byte [0];
        if (reportBlocks.length > 0) {
            senderReportBlocks = RtcpPacketUtils.append(senderReportBlocks,
                    assembleRTCPSenderReports());
        }

        // Length
        byte[] srLength = RtcpPacketUtils.longToBytes(((RTCP_HDR_SIZE + ss.length +
                nptTimestampMsw.length + nptTimestampLsw.length + rtpTimestamp.length +
                packetCount.length + octetCount.length + senderReportBlocks.length)/4)-1, 2);

        // Assemble all the info into a packet
        byte[] packet = new byte [1];
        packet[0] = V_P_RC;
        packet = RtcpPacketUtils.append(packet, pt);
        packet = RtcpPacketUtils.append(packet, srLength);
        packet = RtcpPacketUtils.append(packet, ss);
        packet = RtcpPacketUtils.append(packet, nptTimestampMsw);
        packet = RtcpPacketUtils.append(packet, nptTimestampLsw);
        packet = RtcpPacketUtils.append(packet, rtpTimestamp);
        packet = RtcpPacketUtils.append(packet, packetCount);
        packet = RtcpPacketUtils.append(packet, octetCount);
        packet = RtcpPacketUtils.append(packet, senderReportBlocks);

        return packet;
    }

    /**
     * assemble RTCP Sender report blocks
     * @return report data
     */
    private byte[] assembleRTCPSenderReports() {
        byte reportBlock[] = new byte[0];

        for (RtcpReportBlock rr : reportBlocks) {
            byte ssrc[] = RtcpPacketUtils.longToBytes(rr.getSsrc(), 4);
            byte fractionLost[] = RtcpPacketUtils.longToBytes((long)rr.getFractionLost(), 1);
            byte pktsLost[] = RtcpPacketUtils.longToBytes((long)rr.getCumulativeNumberOfPacketsLost(), 3);
            byte lastSeq[] = RtcpPacketUtils.longToBytes(rr.getExtendedHighestSequenceNumberReceived(), 4);
            byte jitter[] = RtcpPacketUtils.longToBytes(rr.getInterarrivalJitter(), 4);
            byte lst[] = RtcpPacketUtils.longToBytes(rr.getLastSenderReport(), 4);
            byte dlsr[] = RtcpPacketUtils.longToBytes(rr.getDelaySinceLastSenderReport(), 4);

            reportBlock = RtcpPacketUtils.append(reportBlock, ssrc);
            reportBlock = RtcpPacketUtils.append(reportBlock, fractionLost);
            reportBlock = RtcpPacketUtils.append(reportBlock, pktsLost);
            reportBlock = RtcpPacketUtils.append(reportBlock, lastSeq);
            reportBlock = RtcpPacketUtils.append(reportBlock, jitter);
            reportBlock = RtcpPacketUtils.append(reportBlock, lst);
            reportBlock = RtcpPacketUtils.append(reportBlock, dlsr);
        }

        return reportBlock;
    }

    /** Builder for {@link RtcpSrPacket}. */
    public static final class Builder extends RtcpPacket.Builder {
        long ssrc;
        RtcpSenderInfo senderInfo;
        RtcpReportBlock[] reportBlocks;

        public Builder() {
            super(RtcpPacket.SR);
            reportBlocks = new RtcpReportBlock[0];
        }

        public Builder setSsrc(long ssrc) {
            this.ssrc = ssrc;
            return this;
        }

        public Builder setSenderInfo(RtcpSenderInfo senderInfo) {
            this.senderInfo = senderInfo;
            return this;
        }

        public Builder setReportBlocks(RtcpReportBlock[] reportBlocks) {
            if (reportBlocks != null) {
                this.reportBlocks = reportBlocks;
            }

            return this;
        }

        @Override
        public RtcpSrPacket build() {
            return new RtcpSrPacket(this);
        }
    }
}

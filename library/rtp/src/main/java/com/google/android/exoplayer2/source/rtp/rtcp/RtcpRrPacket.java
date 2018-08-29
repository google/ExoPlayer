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
 * This class wraps a RTCP Receiver Report packet.
 *
 *           0                   1                   2                   3
 *           0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   header |V=2|P|    RC   |   PT=RR=201   |             length            |
 *          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *          |                     SSRC of packet sender                     |
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
public final class RtcpRrPacket extends RtcpPacket {
    private final long ssrc;
    private final RtcpReportBlock[] reportBlocks;

    RtcpRrPacket(Builder builder) {
        super(builder);
        ssrc = builder.ssrc;
        reportBlocks = builder.reportBlocks;
    }

    public long getSsrc() {
        return ssrc;
    }

    public int getReportCount() {
        return reportBlocks.length;
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

        // Payload Type = RR
        byte[] pt = RtcpPacketUtils.longToBytes((long) RR, 1);

        // Reception Report Blocks
        byte[] receptionReportBlocks = new byte [0];
        if (reportBlocks.length > 0) {
            receptionReportBlocks = RtcpPacketUtils.append(receptionReportBlocks,
                    assembleRTCPReceptionReports());
        }

        // Length
        byte[] rrLength = RtcpPacketUtils.longToBytes(((RTCP_HDR_SIZE + ss.length +
                receptionReportBlocks.length)/4)-1, 2);

        // Assemble all the info into a packet
        byte[] packet = new byte [1];
        packet[0] = V_P_RC;
        packet = RtcpPacketUtils.append(packet, pt);
        packet = RtcpPacketUtils.append(packet, rrLength);
        packet = RtcpPacketUtils.append(packet, ss);
        packet = RtcpPacketUtils.append(packet, receptionReportBlocks);

        return packet;
    }

    /**
     * assemble RTCP Reception report blocks
     * @return report data
     */
    private byte[] assembleRTCPReceptionReports() {
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

    /** Builder for {@link RtcpRrPacket}. */
    public static final class Builder extends RtcpPacket.Builder {
        long ssrc;
        RtcpReportBlock[] reportBlocks;

        public Builder() {
            super(RtcpPacket.RR);
            reportBlocks = new RtcpReportBlock[0];
        }

        public Builder setSsrc(long ssrc) {
            this.ssrc = ssrc;
            return this;
        }

        public Builder setReportBlocks(RtcpReportBlock[] reportBlocks) {
            if (reportBlocks != null) {
                this.reportBlocks = reportBlocks;
            }

            return this;
        }

        @Override
        public RtcpRrPacket build() {
            return new RtcpRrPacket(this);
        }
    }
}

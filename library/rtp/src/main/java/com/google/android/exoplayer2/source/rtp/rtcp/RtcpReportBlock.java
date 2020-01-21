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

/**
 * This class wraps a RTCP Report block providing method to convert from and to a byte array.
 *
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                      SSRC (SSRC of source)                    |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        | fraction lost |       cumulative number of packets lost       |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |           extended highest sequence number received           |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                      interarrival jitter                      |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                         last SR (LSR)                         |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                   delay since last SR (DLSR)                  |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 */

public final class RtcpReportBlock {
    public static final int RTCP_REPORT_BLOCK_SIZE = 24; /* RFC 3550 */

    /**
     * The SSRC identifier of the source to which the information in this
     * reception report block pertains.
     */
    private long ssrc;

    /**
     * The fraction of RTP data packets from source SSRC lost since the previous
     * SR or RR packet was sent.
     */
    private double fractionLost;

    /**
     * The total number of RTP data packets from source SSRC_n that have been
     * lost since the beginning of reception.
     */
    private int cumulativeNumberOfPacketsLost;

    /**
     * The low 16 bits contain the highest sequence number received in an RTP
     * data packet from source SSRC, and the most significant 16 bits extend
     * that sequence number with the corresponding count of sequence number
     * cycles.
     */
    private long extendedHighestSequenceNumberReceived;

    /**
     * An estimate of the statistical variance of the RTP data packet
     * interarrival time, measured in timestamp units and expressed as
     * anunsigned integer.
     */
    private long interarrivalJitter;

    /**
     * The middle 32 bits out of 64 in the NTP timestamp received as part of the
     * most recent RTCP sender report (SR) packet from source SSRC. If no SR has
     * been received yet, the field is set to zero.
     */
    private long lastSenderReport;

    /**
     * The delay, expressed in units of 1/65536 seconds, between receiving the
     * last SR packet from source SSRC and sending this reception report block.
     */
    private long delaySinceLastSenderReport;


    RtcpReportBlock(Builder builder) {
        this.ssrc = builder.ssrc;
        this.fractionLost = builder.fractionLost;
        this.cumulativeNumberOfPacketsLost = builder.cumulativeNumberOfPacketsLost;
        this.extendedHighestSequenceNumberReceived = builder.extendedHighestSequenceNumberReceived;
        this.interarrivalJitter = builder.interarrivalJitter;
        this.lastSenderReport = builder.lastSenderReport;
        this.delaySinceLastSenderReport = builder.delaySinceLastSenderReport;
    }

    public long getSsrc() {
        return ssrc;
    }

    public double getFractionLost() {
        return fractionLost;
    }

    public int getCumulativeNumberOfPacketsLost() {
        return cumulativeNumberOfPacketsLost;
    }

    public long getExtendedHighestSequenceNumberReceived() {
        return extendedHighestSequenceNumberReceived;
    }

    public long getInterarrivalJitter() {
        return interarrivalJitter;
    }

    public long getLastSenderReport() {
        return lastSenderReport;
    }

    public long getDelaySinceLastSenderReport() {
        return delaySinceLastSenderReport;
    }

    @Nullable
    public static RtcpReportBlock parse(byte[] packet, int length) {
        if (length < RTCP_REPORT_BLOCK_SIZE || length > packet.length) {
            return null;
        }

        long ssrc = ((((long)packet[0]) & 0xff) << 24) | ((((long)packet[1]) & 0xff) << 16) |
                ((((long)packet[2]) & 0xff) << 8) | (((long)packet[3]) & 0xff);
        double fractionLost = packet[4] & 0xff;
        int cumulativeNumberOfPacketsLost = ((packet[5] & 0xff) << 16) | ((packet[6] & 0xff) << 8) |
                (packet[7] & 0xff);
        long extendedHighestSequenceNumberReceived = ((((long)packet[8]) & 0xff) << 24) |
                ((((long)packet[9]) & 0xff) << 16) | ((((long)packet[10]) & 0xff) << 8) |
                (((long)packet[11]) & 0xff);
        long interarrivalJitter = ((((long)packet[12]) & 0xff) << 24) |
                ((((long)packet[13]) & 0xff) << 16) | ((((long)packet[14]) & 0xff) << 8) |
                (((long)packet[15]) & 0xff);
        long lastSenderReport = ((((long)packet[16]) & 0xff) << 24) |
                ((((long)packet[17]) & 0xff) << 16) | ((((long)packet[18]) & 0xff) << 8) |
                (((long)packet[19]) & 0xff);
        long delaySinceLastSenderReport = ((((long)packet[20]) & 0xff) << 24) |
                ((((long)packet[21]) & 0xff) << 16) | ((((long)packet[22]) & 0xff) << 8) |
                (((long)packet[23]) & 0xff);

        return new Builder()
                .setSsrc(ssrc)
                .setFractionLost(fractionLost)
                .setCumulativeNumberOfPacketsLost(cumulativeNumberOfPacketsLost)
                .setExtendedHighestSequenceNumberReceived(extendedHighestSequenceNumberReceived)
                .setInterarrivalJitter(interarrivalJitter)
                .setLastSenderReport(lastSenderReport)
                .setDelaySinceLastSenderReport(delaySinceLastSenderReport)
                .build();
    }

    /** Builder for {@link RtcpReportBlock}. */
    public static final class Builder {
        long ssrc;
        double fractionLost;
        int cumulativeNumberOfPacketsLost;
        long extendedHighestSequenceNumberReceived;
        long interarrivalJitter;
        long lastSenderReport;
        long delaySinceLastSenderReport;

        public Builder setSsrc(long ssrc) {
            this.ssrc = ssrc;
            return this;
        }

        public Builder setFractionLost(double fractionLost) {
            this.fractionLost = fractionLost;
            return this;
        }

        public Builder setCumulativeNumberOfPacketsLost(int cumulativeNumberOfPacketsLost) {
            this.cumulativeNumberOfPacketsLost = cumulativeNumberOfPacketsLost;
            return this;
        }

        public Builder setExtendedHighestSequenceNumberReceived(long extendedHighestSequenceNumberReceived) {
            this.extendedHighestSequenceNumberReceived = extendedHighestSequenceNumberReceived;
            return this;
        }

        public Builder setInterarrivalJitter(long interarrivalJitter) {
            this.interarrivalJitter = interarrivalJitter;
            return this;
        }

        public Builder setLastSenderReport(long lastSenderReport) {
            this.lastSenderReport = lastSenderReport;
            return this;
        }

        public Builder setDelaySinceLastSenderReport(long delaySinceLastSenderReport) {
            this.delaySinceLastSenderReport = delaySinceLastSenderReport;
            return this;
        }

        /** Creates a {@link RtcpReportBlock}. */
        public RtcpReportBlock build() {
            return new RtcpReportBlock(this);
        }
    }
}
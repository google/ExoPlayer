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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * This class wraps a RTCP packet.
 *
 * A RCTP packet is composed of an header and the subsequent payload. It has the following format:
 *
 /*
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|    NA   |       PT      |             length            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        :                           payload                             :
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The first four octets are present in every RTCP packet.
 *
 */
public abstract class RtcpPacket {
    protected static final int RTCP_VERSION = 0x02;

    protected static final int RTCP_PADDING = 0;
    protected static final int RTCP_HDR_SIZE = 4; /* RFC 3550 */

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FIR, NACK, SMPTECT, IJ, SR, RR, SDES, BYE, APP, RTPFB, PSFB, XR, TOKEN, AVB, RSI,
            COMPOUND})
    public @interface PacketType {}
    public static final int COMPOUND = -1;
    // RTP Payload Format for H.261 Video Streams (RFC 2032)
    public static final int FIR = 192;
    public static final int NACK = 193;
    // Associating Time-Codes with RTP Streams (RFC 5484)
    public static final int SMPTECT = 194;
    // Transmission Time Offsets in RTP Streams (RFC 5450)
    public static final int IJ = 195;
    // RTP: A Transport Protocol for Real-Time Applications (RFC 3550)
    public static final int SR = 200;
    public static final int RR = 201;
    public static final int SDES = 202;
    public static final int BYE = 203;
    public static final int APP = 204;
    // Extended RTP for Real-time Transport Control Protocol Based Feedback (RFC 4585)
    public static final int RTPFB = 205;
    public static final int PSFB = 206;
    // RTP Control Protocol Extended Reports (RFC 3611)
    public static final int XR = 207;
    // IEEE Standard for Layer 3 Transport Protocol for Time-Sensitive Applications in Local Area
    // Networks (IEEE 1733)
    public static final int AVB = 208;
    // RTP Control Protocol (RTCP) Extensions for Single-Source Multicast Sessions with Unicast
    // Feedback (RFC 5760)
    public static final int RSI = 209;
    // Port Mapping between Unicast and Multicast RTP Sessions (RFC 6284)
    public static final int TOKEN = 210;

    public static final int RTCP_PAYLOAD_MIN = FIR;
    public static final int RTCP_PAYLOAD_MAX = TOKEN;

    // fields that compose the RTCP header
    protected final boolean padding;
    private final @PacketType int payloadType;
    private final int length;

    RtcpPacket(Builder builder) {
        this.padding = builder.padding;
        this.payloadType = builder.payloadType;
        this.length = builder.length;
    }

    public int getVersion() {
        return RTCP_VERSION;
    }

    public boolean hasPadding() {
        return padding;
    }

    public @PacketType int getPayloadType() {
        return payloadType;
    }

    public int getLength() {
        return length;
    }

    public abstract byte[] getBytes();

    @Nullable
    public static RtcpPacket parse(byte[] packet, int length) {
        int offset = 0, padLen = 0, headLen = RTCP_HDR_SIZE, packetsCount = 0;
        RtcpCompoundPacket.Builder builder = new RtcpCompoundPacket.Builder();

        while (offset < length) {
           if ((length-offset) < RTCP_HDR_SIZE) {
                return null;
            }

            // Read the packet header
            int version = (packet[offset] & 0xC0) >> 6;

            if (RTCP_VERSION != version) {
                return null;
            }

            // Read and check the payload type
            @PacketType int payloadType = (int)((packet[offset + 1]) & 0xFF);
            if (payloadType < RtcpPacket.FIR || payloadType > RtcpPacket.TOKEN) {
                return null;
            }

            boolean padding = ((packet[offset] & 0x20) >> 5) == 1;
            int bytesLen = ((((((int)packet[offset + 2]) & 0xff) << 8) |
                    (((int)packet[offset + 3]) & 0xff)) + 1) * 4;

            if (padding) {
                padLen = packet[bytesLen - 1];
            }

            if (bytesLen < (headLen + padLen)) {
                return null;
            }

            switch (payloadType) {
                case RtcpPacket.SR:
                    long srSsrc = ((((long)packet[offset + 4]) & 0xff) << 24) |
                            ((((long)packet[offset + 5]) & 0xff) << 16) |
                            ((((long)packet[offset + 6]) & 0xff) << 8) |
                            (((long)packet[offset + 7]) & 0xff);

                    int senderReportCount = packet[offset] & 0x1F;

                    // Read the payload
                    byte[] srPayload = Arrays.copyOfRange(packet, offset + 8,
                            bytesLen-padLen);

                    RtcpSenderInfo senderInfo = RtcpSenderInfo.parse(srPayload,
                            RtcpSenderInfo.RTCP_SENDER_INFO_SIZE);

                    RtcpReportBlock[] senderReportBlocks = getReportBlocksFromPayload(srPayload,
                            RtcpSenderInfo.RTCP_SENDER_INFO_SIZE, senderReportCount);

                    if (senderReportCount == senderReportBlocks.length) {
                        builder.addPacket(new RtcpSrPacket.Builder()
                                .setSsrc(srSsrc)
                                .setSenderInfo(senderInfo)
                                .setReportBlocks(senderReportBlocks)
                                .setPadding(padLen)
                                .setLength(bytesLen - padLen)
                                .build());
                    }
                    break;

                case RtcpPacket.RR:
                    break;

                case RtcpPacket.SDES:
                    /**
                     * RTCP Header Validity Checks: The payload type field of the first RTCP packet
                     * in a compound packet must be equal to SR or RR
                     */
                    if (packetsCount == 0 && bytesLen < length) {
                        return null;
                    }

                    int sourceCount = ((int)packet[offset]) & 0x1F;

                    // Read the payload
                    byte[] sdesPayload = Arrays.copyOfRange(packet, offset, offset+bytesLen-padLen);

                    RtcpChunk[] chunks = getChunksFromPayload(sdesPayload, headLen, sourceCount);

                    if (chunks != null && sourceCount == chunks.length) {
                        builder.addPacket(new RtcpSdesPacket.Builder()
                                .setChunks(chunks)
                                .build());
                    }
                    break;

                case RtcpPacket.BYE:
                case RtcpPacket.APP:
                case RtcpPacket.RTPFB:
                case RtcpPacket.PSFB:
                case RtcpPacket.XR:
                case RtcpPacket.TOKEN:
                    /**
                     * RTCP Header Validity Checks: The payload type field of the first RTCP packet
                     * in a compound packet must be equal to SR or RR
                     */
                    if (packetsCount == 0 && bytesLen < length) {
                        return null;
                    }
                    break;
            }

            offset += bytesLen;
            packetsCount++;
        }

        if (packetsCount > 0 && (offset == length)) {
            RtcpCompoundPacket compoundPacket = builder.build();
            if (compoundPacket.getPackets().length == 1) {
                return compoundPacket.getPackets()[0];

            } else {
                return compoundPacket;
            }
        }

        return null;
    }

    @Nullable
    private static RtcpReportBlock[] getReportBlocksFromPayload(byte[] payload, int offset,
                                                                int reportCount) {
        if ((payload.length - offset) >= reportCount * RtcpReportBlock.RTCP_REPORT_BLOCK_SIZE) {
            RtcpReportBlock[] reportBlocks = new RtcpReportBlock[reportCount];

            for (int report = 0; report < reportCount; report++) {
                byte[] reportBlock = new byte[RtcpReportBlock.RTCP_REPORT_BLOCK_SIZE];
                System.arraycopy(payload, offset, reportBlock, 0,
                        RtcpReportBlock.RTCP_REPORT_BLOCK_SIZE);
                reportBlocks[report] = RtcpReportBlock.parse(reportBlock,
                        RtcpReportBlock.RTCP_REPORT_BLOCK_SIZE);

                if (reportBlocks[report] == null) {
                    return null;
                }

                offset += RtcpReportBlock.RTCP_REPORT_BLOCK_SIZE;
            }

            return reportBlocks;
        }

        return null;
    }

    @Nullable
    private static RtcpChunk[] getChunksFromPayload(byte[] payload, int offset, int sourceCount) {
        int length = payload.length - offset;
        if (length >= sourceCount * RtcpChunk.RTCP_CHUNK_FIXED_SIZE) {
            RtcpChunk[] chunks = new RtcpChunk[sourceCount];

            for (int source = 0; source < sourceCount; source++) {
                byte[] chunk = new byte[length];
                System.arraycopy(payload, offset, chunk, 0, length);
                chunks[source] = RtcpChunk.parse(chunk, length);
                if (chunks[source] == null) {
                    return null;
                }
                offset += RtcpChunk.RTCP_CHUNK_FIXED_SIZE + chunks[source].getLength();
            }

            return chunks;
        }

        return null;
    }

    /** Builder for {@link RtcpPacket}. */
    public static abstract class Builder {
        boolean padding;
        @PacketType int payloadType;
        int length;
        byte[] payload;

        protected Builder(@PacketType int payloadType) {
            this.payloadType = payloadType;
        }

        public Builder setPadding(int padLen) {
            if (padLen > 0) {
                padding = true;
            }
            return this;
        }

        public Builder setLength(int length) {
            this.length = length;
            return this;
        }

        public Builder setPayload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        /** Creates a {@link RtcpPacket}. */
        public abstract RtcpPacket build();
    }
}

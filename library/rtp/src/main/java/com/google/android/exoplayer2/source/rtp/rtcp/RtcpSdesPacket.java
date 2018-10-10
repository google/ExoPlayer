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

/**
 *  This class wraps a RTCP Source Description packet.
 *
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|    SC   |  PT=SDES=202  |             length            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        |                          SSRC/CSRC_1                          |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           SDES items                          |
 *        |                              ...                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        |                          SSRC/CSRC_2                          |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           SDES items                          |
 *        |                              ...                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 */

public final class RtcpSdesPacket extends RtcpPacket {
    private final RtcpChunk[] chunks;

    RtcpSdesPacket(Builder builder) {
        super(builder);
        chunks = builder.chunks;
    }

    public int getSourceCount() {
        return chunks.length;
    }

    public RtcpChunk[] getChunks() {
        return chunks;
    }

    @Override
    public byte[] getBytes() {
        // construct the first byte containing V, P and RC
        byte V_P_SC = (byte) ((RTCP_VERSION << 6) | (RTCP_PADDING << 5) |
                (byte) (chunks.length & 0x1F));

        // Payload Type = SR
        byte[] pt = RtcpPacketUtils.longToBytes ((long) SDES, 1);

        // Chunks
        byte[] chunkItems = new byte [0];
        if (chunks.length > 0) {
            chunkItems = RtcpPacketUtils.append(chunkItems, assembleRTCPChunks());
        }

        int padLen = RtcpPacketUtils.calculatePadLength(chunkItems.length);
        if (padLen > 0) {
            // Append necessary setPadding fields
            byte[] padBytes = new byte[padLen];
            chunkItems = RtcpPacketUtils.append(chunkItems, padBytes);
        }

        // Length
        byte[] sdesLength = RtcpPacketUtils.longToBytes (((RTCP_HDR_SIZE +
                chunkItems.length + padLen + 4)/4)-1, 2);

        // Assemble all the info into a packet
        byte[] packet = new byte[2];
        packet[0] = V_P_SC;
        packet[1] = pt[0];
        packet = RtcpPacketUtils.append(packet, sdesLength);
        packet = RtcpPacketUtils.append(packet, chunkItems);

        return packet;
    }

    /**
     * assemble RTCP Chunks
     * @return chunks data
     */
    private byte[] assembleRTCPChunks() {
        byte[] chunkItems = new byte[0];

        for (RtcpChunk chunk : chunks) {
            byte[] ss = RtcpPacketUtils.longToBytes (chunk.getSsrc(), 4);
            byte[] sdesItems = new byte[0];

            for (RtcpSdesItem sdesItem : chunk.getSdesItems()) {
                byte[] sdesItemHdr = { (byte) sdesItem.getType(), (byte) sdesItem.getLength() };
                sdesItems = RtcpPacketUtils.append(sdesItems, sdesItemHdr);
                sdesItems = RtcpPacketUtils.append(sdesItems, sdesItem.getValue());
            }

            // Append SDES Item end field (32 bit boundary)
            byte[] sdesItemEnd = new byte [1];
            sdesItems = RtcpPacketUtils.append(sdesItems, sdesItemEnd);

            int padLen = RtcpPacketUtils.calculatePadLength(sdesItems.length);
            if (padLen > 0) {
                // Append necessary setPadding fields
                byte[] padBytes = new byte[padLen];
                sdesItems = RtcpPacketUtils.append(sdesItems, padBytes);
            }

            byte[] chunkItem = new byte[0];
            chunkItem = RtcpPacketUtils.append(chunkItem, ss);
            chunkItem = RtcpPacketUtils.append(chunkItem, sdesItems);

            chunkItems = RtcpPacketUtils.append(chunkItems, chunkItem);
        }

        return chunkItems;
    }

    /** Builder for {@link RtcpSdesPacket}. */
    public static final class Builder extends RtcpPacket.Builder {
        RtcpChunk[] chunks;

        public Builder() {
            super(RtcpPacket.SDES);
            chunks = new RtcpChunk[0];
        }

        public Builder setChunks(RtcpChunk[] chunks) {
            if (chunks != null) {
                this.chunks = chunks;
            }

            return this;
        }

        @Override
        public RtcpSdesPacket build() {
            return new RtcpSdesPacket(this);
        }
    }
}

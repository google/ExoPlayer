/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.rtp.rtcp;

/**
 * This class wraps a RTCP packet providing method to convert from and to a byte array.
 *
 * A RCTP packet is composed of an header and the subsequent payload. It has the following format:
 *
 /*
 *         0                   1                   2                   3
 *         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|    RC   |       PT      |             length            |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                          SSRC of sender                       |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        :                                                               :
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The first eight octets are present in every RTCP packet.
 *
 */
abstract public class RtcpPacket {

    private static final int RTCP_VERSION = 0x02;

    private static final int RTCP_HDR_SIZE = 8; /* RFC 3550 */

    // fields that compose the RTCP header
    private int version;
    private boolean padding;
    private int receptionCount;
    private int payloadType;
    private int length;
    private long ssrc;

    private byte[] payload;

    private int packetSize;

    /**
     * Thrown when an error is encountered when trying to parse a {@link RtcpPacket}.
     */
    public static final class RtcpPacketException extends Exception {

        public RtcpPacketException(String message) {
            super(message);
        }

        public RtcpPacketException(Throwable cause) {
            super(cause);
        }

        public RtcpPacketException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public RtcpPacket() {
        // Fill default fields
        version = 2;
        padding = true;
        receptionCount = 0;
        ssrc = 0;
    }

    public int getVersion() {
        return version;
    }

    public boolean isPadding() {
        return padding;
    }

    public int getReceptionCount() {
        return receptionCount;
    }

    public int getPayloadType() {
        return payloadType;
    }

    public int getLength() {
        return length;
    }

    public long getSsrc() {
        return ssrc;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getPacketSize() {
        return packetSize;
    }

    // Decode a RTCP packet from bytes
    public void fromBytes(byte[] buffer, int length) throws RtcpPacketException {
        int padLen = 0, headLen = RTCP_HDR_SIZE;
        int frontSkip = 0, backSkip = 0;

        if (buffer.length < RTCP_HDR_SIZE) {
            throw new RtcpPacketException("Inappropriate length=[" + buffer.length + "] of RTCP packet");
        }

        // Read the packet header
        this.version = (buffer[0] & 0xC0) >> 6;

        if (RTCP_VERSION != this.version) {
            throw new RtcpPacketException("Wrong RTCP version " + this.version + ", must be " +
                    RTCP_VERSION);
        }

        this.padding = ((buffer[0] & 0x20) >> 5) == 1;
        this.receptionCount = buffer[0] & 0x1F;
        this.payloadType = buffer[1] & 0xFF;
        this.length = (buffer[2] & 0xff) * 256 + (buffer[3] & 0xff);

        this.ssrc = ((buffer[4] & 0xff) << 24) | ((buffer[5] & 0xff) << 16) |
                ((buffer[6] & 0xff) << 8) | (buffer[7] & 0xff);

        frontSkip += headLen;

        if (padding) {
            padLen = buffer[length - 1];
            backSkip += padLen;
        }

        if (length < (frontSkip + backSkip)) {
            throw new RtcpPacketException("Invalid header (skip "
                    + (frontSkip + backSkip) + " exceeds packet length " + length);
        }

        // we have already read 2 * 4 = 8 bytes
        // out of ( length + 1 ) * 4 totals
        int size = Math.min(((this.length + 1) * 4 - RTCP_HDR_SIZE), length-frontSkip);

        // Read the payload
        this.payload = new byte[size];
        System.arraycopy(buffer, frontSkip, payload, 0, size);

        decodePayload(payload, size);

        this.packetSize = length;
    }

    abstract void decodePayload(byte[] payload, int length);

    public byte[] toBytes() { return null; }
}

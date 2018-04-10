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
package com.google.android.exoplayer2.source.rtp;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.IOException;
import java.util.Arrays;

/**
 * This class wraps a RTP packet providing method to convert from a byte array
 *
 * A RTP packet is composed of an header and the subsequent payload. It has the following format:
 *
 *        0                   1                   2                   3
 *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           timestamp                           |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |           synchronization source (SSRC) identifier            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        |            contributing source (CSRC) identifiers             |
 *        |                             ....                              |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        | Profile-specific extension ID |   Extension header length     |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                       Extension header                        |
 *        |                             ....                              |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The first twelve octets are present in every RTP packet, while the list of
 * CSRC identifiers is present only when inserted by a mixer.
 *
 */
public final class RtpPacket {
    public static final int MAX_PACKET_SIZE = 65507;

    private static final int RTP_VERSION = 0x02;

    private static final int RTP_MIN_SIZE = 4;
    private static final int RTP_HDR_SIZE = 12; /* RFC 3550 */
    private static final int RTP_MAX_HDR_SIZE = 76;

    private static final int RTP_XTHDR_SIZE = 4;

    private static final int CSRC_SIZE = 4;

    /* MPEG payload-type constants */
    private static final int RTP_MPA_TYPE = 0x0E;     // MPEG-1 and MPEG-2 audio
    private static final int RTP_MPV_TYPE = 0x20;     // MPEG-1 and MPEG-2 video

    //Fields that compose the RTP header
    private int version;
    private boolean padding;
    private boolean extension;
    private int csrcCount;

    private boolean marker;
    private int sequenceNumber;
    private int payloadType;

    private long timestamp;
    private long ssrc;

    private long[] csrc;

    private byte[] hdrExtension;

    private byte[] payload;

    private int extLen;
    private int padLen;

    RtpPacket(Builder builder) {
        this.version = builder.version;
        this.padding = builder.padding;
        this.extension = builder.extension;
        this.marker = builder.marker;
        this.sequenceNumber = builder.sequenceNumber;
        this.payloadType = builder.payloadType;
        this.timestamp = builder.timestamp;
        this.ssrc = builder.ssrc;
        this.csrc = builder.csrc;
        this.payload = builder.payload;

        csrcCount = csrc != null ? csrc.length : 0;
    }

    RtpPacket(int version, int padLen, byte[] hdrExtension, boolean marker, int payloadType,
              int sequenceNumber, long timestamp, long ssrc, long[] csrc, byte[] payload) {
        this.version = version;
        this.padding = padLen > 0;
        this.padLen = padding ? padLen : 0;
        this.extension = hdrExtension != null && hdrExtension.length > 0;
        this.hdrExtension = hdrExtension;
        this.marker = marker;
        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.csrc = csrc;
        this.payload = payload;

        csrcCount = csrc != null ? csrc.length : 0;
        extLen = (extension) ? hdrExtension.length : 0;
    }

    public int version() { return version; }

    public boolean marker() { return marker; }

    public boolean hasExtension() { return extension; }

    public int payloadType() { return payloadType; }

    public int sequenceNumber() { return sequenceNumber; }

    public long timestamp() { return timestamp; }

    public long ssrc() { return ssrc; }

    public long[] csrc() { return csrc; }

    public byte[] payload() { return payload; }

    public byte[] extension() { return hdrExtension; }


    public byte[] toBytes() {
        // build the header
        int payloadLen = (payload == null) ? 0 : payload.length;
        byte[] packet = new byte[RTP_HDR_SIZE + payloadLen];

        // fill the header array of byte with RTP header fields
        packet[0] = (byte)(version << 6 | (padding ? 1 : 0) << 5 | (extension ? 1 : 0) << 4 | csrcCount);
        packet[1] = (byte)((marker ? 1 : 0) << 7 | payloadType & 0x000000FF);
        packet[2] = (byte)(sequenceNumber >> 8);
        packet[3] = (byte)(sequenceNumber & 0xFF);
        packet[4] = (byte)(timestamp >> 24);
        packet[5] = (byte)(timestamp >> 16);
        packet[6] = (byte)(timestamp >> 8);
        packet[7] = (byte)(timestamp & 0xFF);
        packet[8] = (byte)(ssrc >> 24);
        packet[9] = (byte)(ssrc >> 16);
        packet[10] = (byte)(ssrc >> 8);
        packet[11] = (byte)(ssrc & 0xFF);

        if (payloadLen > 0) {
            System.arraycopy(payload, 0, packet, RTP_HDR_SIZE, payloadLen);
        }

        return packet;
    }

    @Nullable
    public static RtpPacket parse(byte[] packet, int length) {
        int padLen = 0, headLen = 0, csrcLen = 0, extLen = 0;
        int frontSkip = 0, backSkip = 0;

        if( (length < RTP_MIN_SIZE) || (length < RTP_HDR_SIZE) ) {
            return null;
        }

        // Read the packet header
        int version = (packet[0] & 0xC0) >> 6;

        if (RTP_VERSION != version) {
            return null;
        }

        boolean padding = ((packet[0] & 0x20) >> 5) == 1;
        boolean extension = ((packet[0] & 0x10) >> 4) == 1;
        int csrcCount = packet[0] & 0x0F;

        csrcLen = CSRC_SIZE * csrcCount;
        headLen += RTP_HDR_SIZE + csrcLen;

        boolean marker = ((packet[1] & 0x80) >> 7) == 1;
        int payloadType = packet[1] & 0x7F;

        /* profile-based skip: adopted from vlc 0.8.6 code */
        if ((RtpPacket.RTP_MPA_TYPE == payloadType) || (RtpPacket.RTP_MPV_TYPE == payloadType)) {
            headLen += 4;
        }

        frontSkip += headLen;

        if (padding) {
            padLen = packet[length - 1];
            backSkip += padLen;
        }

        if (length < (frontSkip + backSkip)) {
            return null;
        }

        if (extension) {
            if (length < RTP_HDR_SIZE + RTP_XTHDR_SIZE + csrcLen) {
                return null;
            }

            extLen = RTP_XTHDR_SIZE +
                    4 * (((packet[RTP_HDR_SIZE + csrcLen + 2] & 0xFF) << 8) |
                            (packet[RTP_HDR_SIZE + csrcLen + 3] & 0xFF));

            frontSkip += extLen;
        }

        int sequenceNumber = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);

        long timestamp = ((((long)packet[4]) & 0xFF) << 24) | ((((long)packet[5]) & 0xFF) << 16) |
                ((((long)packet[6]) & 0xFF) << 8) | (((long)packet[7]) & 0xFF);

        long ssrc = (((((long)packet[8]) & 0xFF) << 24) | ((((long)packet[9]) & 0xFF) << 16) |
                ((((long)packet[10]) & 0xFF) << 8) | (((long)packet[11]) & 0xFF));

        long[] csrc;

        // CSRC list
        if (csrcCount > 0) {
            csrc = new long[csrcCount];

            for (int ndx=0, offset=RTP_HDR_SIZE; ndx < csrcCount; ndx++, offset+=4) {
                csrc[ndx] = ((packet[offset] & 0xFF) << 24) | ((packet[offset + 1] & 0xFF) << 16) |
                        ((packet[offset + 2] & 0xFF) << 8) | (packet[offset + 3] & 0xFF);
            }
        } else {
            csrc = new long[0];
        }

        byte[] hdrExtension;

        // Read the extension header if present
        if (extension) {
            hdrExtension = Arrays.copyOfRange(packet, headLen, headLen + extLen);
        } else {
            hdrExtension = new byte[0];
        }

        // Read the payload
        byte[] payload = Arrays.copyOfRange(packet, frontSkip, length);

        return new RtpPacket(version, padLen, hdrExtension, marker, payloadType,
                sequenceNumber, timestamp, ssrc, csrc, payload);
    }

    public static int sniffHeader(ExtractorInput input) throws IOException, InterruptedException {
        int headLen = 0, csrcLen, extLen;
        int frontSkip = 0;

        byte[] packet = new byte[RTP_MAX_HDR_SIZE];

        input.peek(packet, 0, RTP_MAX_HDR_SIZE);
        input.resetPeekPosition();

        // Read the packet header
        int version = (packet[0] & 0xC0) >> 6;

        if (RTP_VERSION != version) {
            return 0;
        }

        boolean extension = ((packet[0] & 0x10) >> 4) == 1;
        int csrcCount = packet[0] & 0x0F;

        csrcLen = CSRC_SIZE * csrcCount;
        headLen += RTP_HDR_SIZE + csrcLen;

        int payloadType = packet[1] & 0x7F;

        /* profile-based skip: adopted from vlc 0.8.6 code */
        if ((RtpPacket.RTP_MPA_TYPE == payloadType) || (RtpPacket.RTP_MPV_TYPE == payloadType)) {
            headLen += 4;
        }

        frontSkip += headLen;

        if (extension) {
            extLen = RTP_XTHDR_SIZE +
                    4 * (((packet[RTP_HDR_SIZE + csrcLen + 2] & 0xFF ) << 8) |
                            (packet[RTP_HDR_SIZE + csrcLen + 3] & 0xFF));

            frontSkip += extLen;
        }

        return frontSkip;
    }


    public static class Builder {
        int version;
        boolean padding;
        boolean extension;

        boolean marker;
        int sequenceNumber;
        int payloadType;

        long timestamp;
        long ssrc;

        long[] csrc;
        byte[] payload;

        public Builder() {
            // fill by default header fields
            this.version = 2;
            this.padding = false;
            this.extension = false;
            this.marker = false;
        }

        public Builder marker(boolean marker) {
            this.marker = marker;
            return this;
        }

        public Builder payloadType(int payloadType) {
            this.payloadType = payloadType;
            return this;
        }

        public Builder sequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder ssrc(long ssrc) {
            this.ssrc = ssrc;
            return this;
        }

        public Builder csrc(long[] csrc) {
            this.csrc = csrc;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public RtpPacket build() {
            if (sequenceNumber < 0 || sequenceNumber > 65535) throw new IllegalArgumentException("sequenceNumber is invalid");
            if (timestamp < 0) throw new IllegalArgumentException("timestamp is negative");

            return new RtpPacket(this);
        }
    }
}
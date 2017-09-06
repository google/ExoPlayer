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
package com.google.android.exoplayer2.util.rtp;

import android.support.annotation.Nullable;
import android.util.Log;


/**
 * This class wraps a RTP packet providing method to convert from a byte array or individual
 * setter methods.
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
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * The first twelve octets are present in every RTP packet, while the list of
 * CSRC identifiers is present only when inserted by a mixer.
 *
 */
public class RtpPacket {

    private static final String LOG_TAG = RtpPacket.class.getSimpleName();

    private static final int MPEG_TS_SIG = 0x47;
    private static final int RTP_MIN_SIZE = 4;
    private static final int RTP_HDR_SIZE = 12; /* RFC 3550 */
    private static final int RTP_VERSION = 0x02;

    /* offset to header extension and extension length,
    * as per RFC 3550 5.3.1 */
    private static final int XTLEN_OFFSET = 14;
    private static final int XTSIZE = 4;

    private static final int RTP_XTHDRLEN = XTLEN_OFFSET + XTSIZE;

    private static final int CSRC_SIZE = 4;

    /* MPEG payload-type constants */
    public static final int RTP_MPA_TYPE = 0x0E;     // MPEG-1 and MPEG-2 audio
    public static final int RTP_MPV_TYPE = 0x20;     // MPEG-1 and MPEG-2 video
    public static final int RTP_MP2TS_TYPE = 0x21;   // MPEG TS
    public static final int RTP_DYN_TYPE = 0x63;    // MPEG TS

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

    //bitstream of the RTP header extension
    private byte[] headerExtension;

    //bitstream of the RTP payload
    private byte[] payload;

    private int packetSize;

    /**
     * Thrown when an error is encountered when trying to parse from a {@link RtpPacket}.
     */
    public static final class RtpPacketException extends Exception {

        public RtpPacketException(String message) {
            super(message);
        }

        public RtpPacketException(Throwable cause) {
            super(cause);
        }

        public RtpPacketException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public RtpPacket() {
        // Fill default fields
        version = 2;
        padding = true;
        marker = true;
        ssrc = 0;
        csrcCount = 0;
        timestamp = 0;
    }

    public RtpPacket(int payloadType, int sequenceNumber, long timestamp, long ssrc,
                      byte[] payload, @Nullable byte[] headerExtension) {
        this();

        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;

        this.extension = !(headerExtension == null);
        this.headerExtension = headerExtension;
    }

    public RtpPacket(byte[] buffer, int length) throws RtpPacketException {
        fromBytes(buffer, length);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isPadding() {
        return padding;
    }

    public void setPadding(boolean padding) {
        this.padding = padding;
    }

    public boolean isExtension() {
        return extension;
    }

    public void setExtension(boolean extension) {
        this.extension = extension;
    }

    public int getCsrcCount() {
        return csrcCount;
    }

    public void setCsrcCount(int csrcCount) {
        this.csrcCount = csrcCount;
    }

    public boolean isMarker() {
        return marker;
    }

    public void setMarker(boolean marker) {
        this.marker = marker;
    }

    public int getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(int payloadType) {
        this.payloadType = payloadType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public long getTimeStamp() {return timestamp; }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSsrc() {return ssrc; }

    public void setSsrc(long ssrc) {
        this.ssrc = ssrc;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getHeaderExtension() {
        return headerExtension;
    }

    public void setHeaderExtension(byte[] headerExtension) {
        this.headerExtension = headerExtension;
    }

    public byte[] getPayload() { return payload; }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public void setPacketSize(int packetSize) {
        this.packetSize = packetSize;
    }


    // Decode a RTP packet from bytes
    public void fromBytes(byte[] buffer, int length) throws RtpPacketException {
        int padLen = 0, headLen = 0, extLen = 0;
        int frontSkip = 0, backSkip = 0;

        if( (buffer.length < RTP_MIN_SIZE) || (buffer.length < RTP_HDR_SIZE) ) {
            throw new RtpPacketException("Inappropriate length=[" + buffer.length + "] of RTP packet");
        }

        // Read the packet header
        version = (buffer[0] & 0xC0) >> 6;

        if (RTP_VERSION != version) {
            throw new RtpPacketException("Wrong RTP version " + version + ", must be " +
                    RTP_VERSION);
        }

        padding = ((buffer[0] & 0x20) >> 5) == 1;
        extension = ((buffer[0] & 0x10) >> 4) == 1;
        csrcCount = buffer[0] & 0x0F;

        headLen += RTP_HDR_SIZE + (CSRC_SIZE * csrcCount);

        marker = ((buffer[1] & 0x80) >> 7) == 1;
        payloadType = buffer[1] & 0x7F;

        /* profile-based skip: adopted from vlc 0.8.6 code */
        if ((RtpPacket.RTP_MPA_TYPE == payloadType) || (RtpPacket.RTP_MPV_TYPE == payloadType)) {
            headLen += 4;
        } else if ((RtpPacket.RTP_MP2TS_TYPE != payloadType) && (RtpPacket.RTP_DYN_TYPE != payloadType)) {
            throw new RtpPacketException("Unsupported payload type " + payloadType);
        }

        frontSkip += headLen;

        if (padding) {
            padLen = buffer[length - 1];
            backSkip += padLen;
        }

        if (length < (frontSkip + backSkip)) {
            throw new RtpPacketException("Invalid header (skip "
                    + (frontSkip + backSkip) + " exceeds packet length " + length);
        }

        if (extension) {
            if (buffer.length < RTP_XTHDRLEN) {
                throw new RtpPacketException("RTP x-header requires " + (XTLEN_OFFSET + 1) +
                        " bytes, only " + buffer.length + " provided");
            }

            extLen = XTSIZE +
                    (Integer.SIZE/Byte.SIZE) * ((buffer[XTLEN_OFFSET] << 8) + buffer[XTLEN_OFFSET + 1]);

            frontSkip += extLen;
        }

        //Payload Type: 33 MPEG 2 TS
        if (payloadType == RtpPacket.RTP_MP2TS_TYPE) {
            sequenceNumber = (buffer[2] & 0xff) * 256 + (buffer[3] & 0xff);
        } else {
            //Payload Type: DynamicRTP-Type 99 MPEG2TS with sequence number preceded.
            sequenceNumber = (buffer[frontSkip]&0xff)*256+(buffer[frontSkip+1]&0xff);
            frontSkip += 2;
        }

        timestamp = ((buffer[4] & 0xff) << 24) | ((buffer[5] & 0xff) << 16) |
                ((buffer[6] & 0xff) << 8) | (buffer[7] & 0xff);

        ssrc = ((buffer[8] & 0xff) << 24) | ((buffer[9] & 0xff) << 16) |
                ((buffer[10] & 0xff) << 8) | (buffer[11] & 0xff);

        // CSRC list
        if (csrcCount > 0) {
            csrc = new long[csrcCount];

            for (int i=0, pos=12; i < csrcCount; i++, pos+=4) {
                csrc[i] = ((buffer[pos] & 0xff) << 24) | ((buffer[pos+1] & 0xff) << 16) |
                        ((buffer[pos+2] & 0xff) << 8) | (buffer[pos+3] & 0xff);
            }
        }

        // Read the extension header if present
        if (extension) {
            headerExtension = new byte[extLen];
            System.arraycopy(buffer, headLen, headerExtension, 0, extLen);
        }

        // Read the payload
        payload = new byte[length-frontSkip];
        System.arraycopy(buffer, frontSkip, payload, 0, length-frontSkip);

        packetSize = length;
    }
}
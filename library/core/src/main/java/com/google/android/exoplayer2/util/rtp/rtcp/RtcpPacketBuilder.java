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

import android.support.annotation.Nullable;
import android.util.SparseIntArray;

import java.util.List;

/**
 * This class provides generic packet assembly and building functions for
 * RTCP packets
 */
public class RtcpPacketBuilder {

    private static final byte VERSION = 2;
    private static final byte PADDING = 0;

    // Payload Types
    public static final int RTCP_SR = (int) 200;
    public static final int RTCP_RR = (int) 201;
    public static final int RTCP_SDES =	(int) 202;
    public static final int RTCP_BYE = (int) 203;
    public static final int RTCP_APP = (int) 204;

    // Extended RTP for Real-time Transport Control Protocol Based Feedback
    public static final int RTCP_RTPFB = (int) 205;
    public static final int RTCP_PSFB = (int) 206;

    // Extended RTP for Real-time Transport Control Protocol Based Port Mapping between Unicast
    // and Multicast RTP Sessions
    public static final int RTCP_TOKEN = (int) 210;

    public static final int RTCP_SMT_PORT_MAPPING_REQ = (int) 1;
    public static final int RTCP_SMT_PORT_MAPPING_RESP = (int) 2;
    public static final int RTCP_SMT_TOKEN_VERIFY_REQ = (int) 3;
    public static final int RTCP_SMT_TOKEN_VERIFY_FAIL = (int) 4;

    public static final int RTCP_SFMT_RAMS_REQ = (int) 1;
    public static final int RTCP_SFMT_RAMS_INFO = (int) 2;
    public static final int RTCP_SFMT_RAMS_TERM = (int) 3;

    private static final int RTCP_RAMS_TLV_SSRC = (int) 1;

    private static final byte RTCP_SDES_END = (byte) 0;
    private static final byte RTCP_SDES_CNAME =	(byte) 1;
    private static final byte RTCP_SDES_NAME =	(byte) 2;
    private static final byte RTCP_SDES_EMAIL =	(byte) 3;
    private static final byte RTCP_SDES_PHONE =	(byte) 4;
    private static final byte RTCP_SDES_LOC =	(byte) 5;
    private static final byte RTCP_SDES_TOOL =	(byte) 6;
    private static final byte RTCP_SDES_NOTE =	(byte) 7;
    private static final byte RTCP_SDES_PRIV =	(byte) 8;


    public static class NackFbElement {
        private int pid;
        private int blp;

        public NackFbElement(int pid, int blp) {
            this.pid = pid;
            this.blp = blp;
        }

        public int getPid() { return pid; }

        public int getBlp() { return blp; }
    }

    /**
     * A {@link TlvElement} represents each entry in a TLV formatted byte-array.
     *
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |     Type      |   Reserved    |            Length             |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       :                             Value                             :
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    public static class TlvElement {
        /**
         * The Type (T) field of the current TLV element. Note that for LV
         * formatted byte-arrays (i.e. TLV whose Type/T size is 0) the value of
         * this field is undefined.
         */
        private long type;
        /**
         * The Length (L) field of the current TLV element.
         */
        private long length;
        /**
         * The Value (V) field - a raw byte array representing the current TLV
         * element
         */
        private byte[] value;

        public TlvElement(long type, long length, @Nullable byte[] value) {
            this.type = type;
            this.length = length;
            this.value = value;
        }

        public long getType() { return type; }

        public long getLength() { return length; }

        public byte[] getValue() { return value; }
    }


    /**
     * A {@link PrivateExtension} represents each entry in a Private Extension on TLV formatted
     * byte-array.
     *
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |     Type      |   Reserved    |            Length             |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                       Enterprise Number                       |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     :                             Value                             :
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    public static class PrivateExtension extends TlvElement {
        /**
         * The Enterprise Number (V) field of the current TLV element.
         */
        private long enterpriseNumber;

        public PrivateExtension(long type, long length, @Nullable byte[] value,
                                 long enterpriseNumber) {
            super(type, length, value);

            this.enterpriseNumber = enterpriseNumber;
        }

        public long getEnterpriseNumber() { return enterpriseNumber; }
    }

    /**
     *   Assembly a Receiver Report RTCP Packet.
     *
     *   @param   ssrc
     *   @return  byte[] The Receiver Report Packet
     */
    private static byte[] assembleRTCPReceiverReportPacket(long ssrc) {
        /*
          0                   1                   2                   3
          0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |V=2|P|    RC   |   PT=RR=201   |             length            | header
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |                         SSRC of sender                        |
          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
          |                 SSRC_1 (SSRC of first source)                 | report
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ block
          | fraction lost |       cumulative number of packets lost       |   1
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |           extended highest sequence number received           |
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |                      interarrival jitter                      |
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |                         last SR (LSR)                         |
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          |                   delay since last SR (DLSR)                  |
          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
          |                 SSRC_2 (SSRC of second source)                | report
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ block
          :                               ...                             :   2
          +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
          |                  profile-specific extensions                  |
          +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes

        // construct the first byte containing V, P and RC
        byte V_P_RC;
        V_P_RC = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x00)
                // take only the right most 5 bytes i.e.
                // 00011111 = 0x1F
        );

        // SSRC of sender
        byte[] ss = RtcpPacketUtils.longToBytes(ssrc, 4);

        // Payload Type = RR
        byte[] pt =
                RtcpPacketUtils.longToBytes((long) RTCP_RR, 1);

        byte[] receptionReportBlocks =
                new byte [0];

        /* TODO
           receptionReportBlocks =
                RtcpPacketUtils.append(receptionReportBlocks,
                        assembleRTCPReceptionReport());*/

        // Each reception report is 24 bytes, so calculate the number of
        // sources in the reception report block and update the reception
        // block count in the header
        byte receptionReports = (byte) (receptionReportBlocks.length / 24);

        // Reset the RC to reflect the number of reception report blocks
        V_P_RC = (byte) (V_P_RC | (byte) (receptionReports & 0x1F));

        byte[] length =
                RtcpPacketUtils.longToBytes(((FIXED_HEADER_SIZE + ss.length +
                        receptionReportBlocks.length)/4)-1, 2);

        byte[] packet = new byte [1];
        packet[0] = V_P_RC;
        packet = RtcpPacketUtils.append(packet, pt);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ss);

        /*
            TODO
            packet = RtcpPacketUtils.append(packet, receptionReportBlocks);
        */

        return packet;
    }

    /**
     *   Assembly an Source Description SDES RTCP Packet.
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @return  The SDES Packet
     */
    private static byte[] assembleRTCPSourceDescriptionPacket(long ssrc, String cname) {
        /*
           0                   1                   2                   3
           0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |V=2|P|    SC   |  PT=SDES=202  |             length            |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
           |                          SSRC/CSRC_1                          |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                           SDES items                          |
           |                              ...                              |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
           |                          SSRC/CSRC_2                          |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                           SDES items                          |
           |                              ...                              |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
        */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and SC
        byte v_p_sc;
        v_p_sc =    (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x01));

        byte[] pt =
                RtcpPacketUtils.longToBytes ((long) RTCP_SDES, 1);

        /////////////////////// Chunk 1 ///////////////////////////////
        byte[] ss =
                RtcpPacketUtils.longToBytes ((long) ssrc, 4);


        ////////////////////////////////////////////////
        // SDES Item #1 :CNAME
        /* 0                   1                   2                   3
           0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
	       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	       |    CNAME=1    |     length    | user and domain name         ...
	       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
	    */

        byte item = RTCP_SDES_CNAME;
        byte[] user_and_domain = new byte[cname.length()];
        user_and_domain = cname.getBytes();


        // Copy the CName item related fields
        byte[] cnameHeader = { item, (byte) user_and_domain.length };

        // Append the header and CName Information in the SDES Item Array
        byte[] sdesItem = new byte[0] ;
        sdesItem = RtcpPacketUtils.append (sdesItem, cnameHeader);
        sdesItem = RtcpPacketUtils.append (sdesItem, user_and_domain);

        int padLen = RtcpPacketUtils.calculatePadLength(sdesItem.length);

        // Determine the length of the packet (section 6.4.1 "The length of
        // the RTCP packet in 32 bit words minus one, including the header and
        // any padding")
        byte[] sdesLength = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ss.length + sdesItem.length + padLen + 4)/4)-1, 2);

        // Assemble all the info into a packet
        byte[] packet = new byte[2];

        packet[0] = v_p_sc;
        packet[1] = pt[0];
        packet = RtcpPacketUtils.append(packet, sdesLength);
        packet = RtcpPacketUtils.append(packet, ss);
        packet = RtcpPacketUtils.append(packet, sdesItem);

        if (padLen > 0) {
            // Append necessary padding fields
            byte[] padBytes = new byte[padLen];
            packet = RtcpPacketUtils.append(packet, padBytes);
        }

        // Append SDES Item end field (32 bit boundary)
        byte[] sdesItemEnd = new byte [4];
        packet = RtcpPacketUtils.append (packet, sdesItemEnd);

        return packet;
    }

    /**
     *
     *   Assembly a "BYE" packet (PT=BYE=203)
     *
     *   @param   ssrc The sincronization source
     *   @return  The BYE Packet
     *
     */
    private static byte[] assembleRTCPByePacket(long ssrc) {
        /*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |V=2|P|    SC   |   PT=BYE=203  |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                           SSRC/CSRC                           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       :                              ...                              :
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |     length    |               reason for leaving             ... (opt)
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and SC
        byte V_P_SC;
        V_P_SC =    (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x01)
        );

        // Generate the payload type byte
        byte PT[] = RtcpPacketUtils.longToBytes((long) RTCP_BYE, 1);

        // Generate the SSRC
        byte ss[] = RtcpPacketUtils.longToBytes((long) ssrc, 4);

        byte textLength [] = RtcpPacketUtils.longToBytes(0 , 1);

        // Length of the packet is number of 32 byte words - 1
        byte[] length =
                RtcpPacketUtils.longToBytes(((FIXED_HEADER_SIZE + ss.length)/4)-1, 2);

        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_SC;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ss);
        packet = RtcpPacketUtils.append(packet, textLength);

        return packet;
    }

    /**
     *
     *   Assembly a "APP" packet (PT=APP=204)
     *
     *   @param   ssrc The sincronization source
     *   @param   name The application name
     *   @param   appData The application-dependent data
     *   @return  The APP Packet
     *
     */
    private static byte[] assembleRTCPAppPacket(long ssrc, String name,
                                                byte[] appData) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P| subtype |   PT=APP=204  |             length            |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                           SSRC/CSRC                           |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                          name (ASCII)                         |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                   application-dependent data                 ...
	        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and SC
        byte V_P_SC;
        V_P_SC =    (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x00));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_APP, 1);

        // Generate the SSRC
        byte[] ss = RtcpPacketUtils.longToBytes((long) ssrc, 4);

        // Generate the application name
        byte[] appName = name.getBytes();

        int dataLen = appName.length + appData.length;

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ss.length + dataLen + 2)/4)-1, 2);

        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_SC;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ss);

        packet = RtcpPacketUtils.append(packet, appName);
        packet = RtcpPacketUtils.append(packet, appData);

        return packet;
    }


    /**
     *
     *   Assembly a Transport layer Feedback (Generic NACK) "RTPFB" packet (PT=RTPFB=205)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   ssrcSource The sincronization source
     *   @param   fbInformation The RTP sequence number array of the lost packets and the bitmask
     *                          of the lost packets immediately following the RTP packet indicated
     *                          by the pid
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPNackPacket(long ssrcSender, long ssrcSource,
                                                 List<NackFbElement> fbInformation) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  FMT=1  |     PT=205    |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of media source                         |
            +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
            |            PID                |             BLP               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and FMT
        byte V_P_FMT;
        V_P_FMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x01));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_RTPFB, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        // Generate the SSRC media source
        byte[] ssms = RtcpPacketUtils.longToBytes((long) ssrcSource, 4);

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + ssms.length + (fbInformation.size()*4) + 2)/4)-1, 2);


        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_FMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);
        packet = RtcpPacketUtils.append(packet, ssms);

        // Generate the feedback control information (FCI)
        for (int index = 0; index < fbInformation.size(); index++) {

            NackFbElement fbElement = fbInformation.get(index);

            // Generate the PID
            byte[] pid = RtcpPacketUtils.longToBytes((long) fbElement.getPid(), 2);

            // Generate the BLP
            byte[] blp = RtcpPacketUtils.longToBytes((long) fbElement.getPid(), 2);

            packet = RtcpPacketUtils.append(packet, pid);
            packet = RtcpPacketUtils.append(packet, blp);
        }

        return packet;
    }


    /**
     *
     *   Assembly a Transport layer Feedback (RAMS Request) "RTPFB" packet (PT=RTPFB=205)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   ssrcSource The sincronization source
     *   @param   extensions The optional TLV elements
     *   @param   privateExtensions The optional private extensions
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPRamsRequestPacket(long ssrcSender, long ssrcSource,
                                                        List<TlvElement> extensions,
                                                        List<PrivateExtension> privateExtensions) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  FMT=6  |     PT=205    |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of media source                         |
            +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
            |    SFMT=1     |                    Reserved                   |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            :                  Requested Media Sender SSRC(s)               :
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            :      Optional TLV-encoded Fields (and Padding, if needed)     :
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_FCI_SIZE = 8; // 8 bytes (4 + 4 bytes mandatory TLV element fixed to 0 length)

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and FMT
        byte V_P_FMT;
        V_P_FMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x06));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_RTPFB, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        // Generate the SSRC media source
        byte[] ssms = RtcpPacketUtils.longToBytes((long) ssrcSource, 4);

        // Length of the feedback control information (FCI) is composed of fixed and variable values
        int var_fci_size = 0;

        for (int index = 0; index < extensions.size(); index++) {
            var_fci_size += extensions.get(index).getLength();
        }

        for (int index = 0; index < privateExtensions.size(); index++) {
            var_fci_size += privateExtensions.get(index).getLength();
        }

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + ssms.length + (FIXED_FCI_SIZE + var_fci_size) + 2)/4)-1, 2);


        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_FMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);
        packet = RtcpPacketUtils.append(packet, ssms);

        // Generate the feedback control information (FCI)

        // Generate the sub fmt type byte
        byte[] SFMT = RtcpPacketUtils.longToBytes((long) RTCP_SFMT_RAMS_REQ, 1);

        // Generate the reserved byte
        byte[] reserved = RtcpPacketUtils.longToBytes((long) 0, 3);

        // Generate the requested media senders byte
        // (Mandatory TLV element: Length field set to 0 bytes means requesting to rapidly acquire channel)
        byte[] tlv_type = RtcpPacketUtils.longToBytes((long) RTCP_RAMS_TLV_SSRC, 1);
        byte[] tlv_reserved = RtcpPacketUtils.longToBytes((long) 0, 1);
        byte[] tlv_length = RtcpPacketUtils.longToBytes((long) 0, 2);

        packet = RtcpPacketUtils.append(packet, SFMT);
        packet = RtcpPacketUtils.append(packet, reserved);
        packet = RtcpPacketUtils.append(packet, tlv_type);
        packet = RtcpPacketUtils.append(packet, tlv_reserved);
        packet = RtcpPacketUtils.append(packet, tlv_length);

        for (int index = 0; index < extensions.size(); index++) {
            TlvElement tlvElement = extensions.get(index);

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes((long) tlvElement.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes((long) tlvElement.getLength(), 2);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, tlvElement.getValue());
        }

        for (int index = 0; index < privateExtensions.size(); index++) {
            PrivateExtension privateExtension = privateExtensions.get(index);

            /**
             * Represents a TLV formatted byte-array with Private Extensions.
             *
             0                   1                   2                   3
             0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |     Type      |   Reserved    |            Length             |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |                       Enterprise Number                       |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             :                             Value                             :
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             */

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes(privateExtension.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes(privateExtension.getLength(), 2);
            byte[] enterpriseCode = RtcpPacketUtils.longToBytes(privateExtension.getEnterpriseNumber(), 4);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, enterpriseCode);
            packet = RtcpPacketUtils.append(packet, privateExtension.getValue());
        }

        return packet;
    }


    /**
     *
     *   Assembly a Transport layer Feedback (RAMS Termination) "RTPFB" packet (PT=BYE=205)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   ssrcSource The sincronization source
     *   @param   extensions The optional TLV elements
     *   @param   privateExtensions The optional private extensions
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPRamsTerminationPacket(long ssrcSender, long ssrcSource,
                                                         List<TlvElement> extensions,
                                                         List<PrivateExtension> privateExtensions) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  FMT=6  |     PT=205    |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of media source                         |
            +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
            |    SFMT=3     |                    Reserved                   |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            :      Optional TLV-encoded Fields (and Padding, if needed)     :
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and FMT
        byte V_P_FMT;
        V_P_FMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x06));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_RTPFB, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        // Generate the SSRC media source
        byte[] ssms = RtcpPacketUtils.longToBytes((long) ssrcSource, 4);


        // Length of the feedback control information (FCI) is composed of fixed and variable values
        int var_fci_size = 0;

        for (int index = 0; index < extensions.size(); index++) {
            var_fci_size += extensions.get(index).getLength();
        }

        for (int index = 0; index < privateExtensions.size(); index++) {
            var_fci_size += privateExtensions.get(index).getLength();
        }

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + ssms.length + (var_fci_size) + 2)/4)-1, 2);


        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_FMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);
        packet = RtcpPacketUtils.append(packet, ssms);

        // Generate the feedback control information (FCI)

        // Generate the sub fmt type byte
        byte[] SFMT = RtcpPacketUtils.longToBytes((long) RTCP_SFMT_RAMS_TERM, 1);

        // Generate the reserved byte
        byte[] reserved = RtcpPacketUtils.longToBytes((long) 0, 3);

        // Generate the optional tlv elements
        byte[] tlv_reserved = RtcpPacketUtils.longToBytes((long) 0, 1);

        packet = RtcpPacketUtils.append(packet, SFMT);
        packet = RtcpPacketUtils.append(packet, reserved);

        for (int index = 0; index < extensions.size(); index++) {
            TlvElement tlvElement = extensions.get(index);

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes((long) tlvElement.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes((long) tlvElement.getLength(), 2);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, tlvElement.getValue());
        }

        for (int index = 0; index < privateExtensions.size(); index++) {
            PrivateExtension privateExtension = privateExtensions.get(index);

            /**
             * Represents a TLV formatted byte-array with Private Extensions.
             *
             0                   1                   2                   3
             0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |     Type      |   Reserved    |            Length             |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             |                       Enterprise Number                       |
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             :                             Value                             :
             +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
             */

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes(privateExtension.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes(privateExtension.getLength(), 2);
            byte[] enterpriseCode = RtcpPacketUtils.longToBytes(privateExtension.getEnterpriseNumber(), 4);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, enterpriseCode);
            packet = RtcpPacketUtils.append(packet, privateExtension.getValue());
        }

        return packet;
    }


    /**
     *
     *   Assembly a Lack of Synch Indication (LSI) "RTPFB" packet (PT=205, FMT=2)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   ssrcSource The sincronization source
     *   @param   bitrate The maximum bitrate of RTP stream it can accommodate
     *   @param   extensions The optional extended parameters encoded using TLV elements
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPLackSynchIndicationPacket(long ssrcSender, long ssrcSource,
                                                                long bitrate,
                                                                List<TlvElement> extensions) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  FMT=2  |     PT=205    |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of media source                         |
            +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
            |                          Bitrate                              |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                          Extensions                           |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */
        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and FMT
        byte V_P_FMT;
        V_P_FMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x02));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_RTPFB, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        // Generate the SSRC media source
        byte[] ssms = RtcpPacketUtils.longToBytes((long) ssrcSource, 4);

        // Generate the SSRC media source
        byte[] br = RtcpPacketUtils.longToBytes((long) bitrate, 8);

        // Length of the feedback control information (FCI) is composed of fixed and variable values
        int var_fci_size = 0;

        for (int index = 0; index < extensions.size(); index++) {
            var_fci_size += extensions.get(index).getLength();
        }

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + ssms.length + (br.length + var_fci_size) + 2)/4)-1, 2);


        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_FMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);
        packet = RtcpPacketUtils.append(packet, ssms);

        // Generate the feedback control information (FCI)

        // Generate the sub fmt type byte
        byte[] SFMT = RtcpPacketUtils.longToBytes((long) RTCP_SFMT_RAMS_REQ, 1);

        // Generate the reserved byte
        byte[] reserved = RtcpPacketUtils.longToBytes((long) 0, 3);

        // Generate the requested media senders byte
        // (Mandatory TLV element: Length field set to 0 bytes means requesting to rapidly acquire channel)

        byte[] tlv_type = RtcpPacketUtils.longToBytes((long) RTCP_RAMS_TLV_SSRC, 1);
        byte[] tlv_reserved = RtcpPacketUtils.longToBytes((long) 0, 1);
        byte[] tlv_length = RtcpPacketUtils.longToBytes((long) 0, 2);
        byte[] tlv_value = RtcpPacketUtils.longToBytes((long) 0, 4);

        packet = RtcpPacketUtils.append(packet, SFMT);
        packet = RtcpPacketUtils.append(packet, reserved);
        packet = RtcpPacketUtils.append(packet, tlv_type);
        packet = RtcpPacketUtils.append(packet, tlv_reserved);
        packet = RtcpPacketUtils.append(packet, tlv_length);
        packet = RtcpPacketUtils.append(packet, tlv_value);

        for (int index = 0; index < extensions.size(); index++) {
            TlvElement tlvElement = extensions.get(index);

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes((long) tlvElement.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes((long) tlvElement.getLength(), 2);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, tlvElement.getValue());
        }

        return packet;
    }


    /**
     *
     *   Assembly a Synch Completed Indication (SCI) "RTPFB" packet (PT=205, FMT=4)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   ssrcSource The sincronization source
     *   @param   extensions The optional extended parameters encoded using TLV elements
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPSynchCompletedIndicationPacket(long ssrcSender, long ssrcSource,
                                                                     List<TlvElement> extensions) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  FMT=4  |     PT=205    |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of media source                         |
            +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
            |                          Extensions                           |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */
        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and FMT
        byte V_P_FMT;
        V_P_FMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x04));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_RTPFB, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        // Generate the SSRC media source
        byte[] ssms = RtcpPacketUtils.longToBytes((long) ssrcSource, 4);

        // Length of the feedback control information (FCI) is only composed of variable values
        int var_fci_size = 0;

        for (int index = 0; index < extensions.size(); index++) {
            var_fci_size += extensions.get(index).getLength();
        }

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + ssms.length + var_fci_size + 2)/4)-1, 2);


        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_FMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);
        packet = RtcpPacketUtils.append(packet, ssms);

        // Generate the feedback control information (FCI)

        // Generate the sub fmt type byte
        byte[] SFMT = RtcpPacketUtils.longToBytes((long) RTCP_SFMT_RAMS_REQ, 1);

        // Generate the reserved byte
        byte[] reserved = RtcpPacketUtils.longToBytes((long) 0, 3);

        // Generate the requested media senders byte
        // (Mandatory TLV element: Length field set to 0 bytes means requesting to rapidly acquire channel)

        byte[] tlv_type = RtcpPacketUtils.longToBytes((long) RTCP_RAMS_TLV_SSRC, 1);
        byte[] tlv_reserved = RtcpPacketUtils.longToBytes((long) 0, 1);
        byte[] tlv_length = RtcpPacketUtils.longToBytes((long) 0, 2);
        byte[] tlv_value = RtcpPacketUtils.longToBytes((long) 0, 4);

        packet = RtcpPacketUtils.append(packet, SFMT);
        packet = RtcpPacketUtils.append(packet, reserved);
        packet = RtcpPacketUtils.append(packet, tlv_type);
        packet = RtcpPacketUtils.append(packet, tlv_reserved);
        packet = RtcpPacketUtils.append(packet, tlv_length);
        packet = RtcpPacketUtils.append(packet, tlv_value);

        for (int index = 0; index < extensions.size(); index++) {
            TlvElement tlvElement = extensions.get(index);

            byte[] opt_tlv_type = RtcpPacketUtils.longToBytes((long) tlvElement.getType(), 1);
            byte[] opt_tlv_length = RtcpPacketUtils.longToBytes((long) tlvElement.getLength(), 2);

            packet = RtcpPacketUtils.append(packet, opt_tlv_type);
            packet = RtcpPacketUtils.append(packet, tlv_reserved);
            packet = RtcpPacketUtils.append(packet, opt_tlv_length);
            packet = RtcpPacketUtils.append(packet, tlvElement.getValue());
        }

        return packet;
    }


    /**
     *
     *   Assembly a Port Mapping Request packet (PT=TOKEN=210)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   nonce The random nonce
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPPortMappingRequestPacket(long ssrcSender, byte[] nonce) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  SMT=1  |    PT=210     |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                            Random                             |
            |                            Nonce                              |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and SFMT
        byte V_P_SMT;
        V_P_SMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x01));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_TOKEN, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        int padLen = RtcpPacketUtils.calculate64PadLength(nonce.length);

        byte[] length = RtcpPacketUtils.longToBytes (((FIXED_HEADER_SIZE +
                ssps.length + nonce.length + padLen + 2)/4)-1, 2);

        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte [1];

        packet[0] = V_P_SMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);

        packet = RtcpPacketUtils.append(packet, nonce);

        if (padLen > 0) {
            // Append necessary padding fields
            byte[] padBytes = new byte[padLen];
            packet = RtcpPacketUtils.append(packet, padBytes);
        }

        return packet;
    }


    /**
     *
     *   Assembly a Token Verification Request packet (PT=TOKEN=210)
     *
     *   @param   ssrcSender The sincronization source of sender
     *   @param   nonce The random nonce
     *   @param   token The authentication token (received into Port Mapping Response packet)
     *   @param   expirationTime The expiration time for authentication token
     *   @return  The RTPFB Packet
     *
     */
    private static byte[] assembleRTCPTokenVerificationRequestPacket(long ssrcSender, byte[] nonce,
                                                                     byte[] token,
                                                                     long expirationTime) {
        /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |V=2|P|  SMT=3  |    PT=210     |          length               |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                  SSRC of packet sender                        |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                            Random                             |
            |                            Nonce                              |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            :                         Token Element                         :
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            |                       Associated Absolute                     |
            |                         Expiration Time                       |
            +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       */

        final int FIXED_HEADER_SIZE = 4; // 4 bytes
        // construct the first byte containing V, P and SFMT
        byte V_P_SMT;
        V_P_SMT = (byte) ((VERSION << 6) |
                (PADDING << 5) |
                (0x03));

        // Generate the payload type byte
        byte[] PT = RtcpPacketUtils.longToBytes((long) RTCP_TOKEN, 1);

        // Generate the SSRC packet sender
        byte[] ssps = RtcpPacketUtils.longToBytes((long) ssrcSender, 4);

        int noncePadLen = RtcpPacketUtils.calculate64PadLength(nonce.length);

        byte[] expiration = RtcpPacketUtils.longToBytes((long) expirationTime, 8);

        int tokenPadLen = RtcpPacketUtils.calculatePadLength(token.length);

        byte[] length = RtcpPacketUtils.longToBytes(((FIXED_HEADER_SIZE +
                ssps.length + nonce.length + noncePadLen + token.length + tokenPadLen +
                expiration.length + 2) / 4) - 1, 2);

        ///////////////////////// Packet Construction ///////////////////////////////
        byte packet[] = new byte[1];

        packet[0] = V_P_SMT;
        packet = RtcpPacketUtils.append(packet, PT);
        packet = RtcpPacketUtils.append(packet, length);
        packet = RtcpPacketUtils.append(packet, ssps);

        packet = RtcpPacketUtils.append(packet, nonce);

        if (noncePadLen > 0) {
            // Append necessary padding fields
            byte[] padBytes = new byte[noncePadLen];
            packet = RtcpPacketUtils.append(packet, padBytes);
        }

        packet = RtcpPacketUtils.append(packet, token);

        if (tokenPadLen > 0) {
            // Append necessary padding fields
            byte[] padBytes = new byte[tokenPadLen];
            packet = RtcpPacketUtils.append(packet, padBytes);
        }

        packet = RtcpPacketUtils.append(packet, expiration);

        return packet;
    }


    //************************************************************************
    // IETF RFC 3350 - RTP: A Transport Protocol for Real-Time Applications

    /**
     *
     *   Constructs a "BYE" packet (PT=BYE=203)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   cname The canonical name
     *   @return  The BYE Packet
     *
     */
    public static byte[] buildByePacket(long ssrc, String cname) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPByePacket(ssrc));

        return packet;
    }

    /**
     *
     *   Constructs a "APP" packet (PT=APP=204)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @return  The APP Packet
     *
     */
    public static byte[] buildAppPacket(long ssrc, String cname,
                                        String appName, byte[] appData) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPAppPacket(ssrc, appName, appData));

        return packet;
    }


    //************************************************************************
    // IETF RFC 4584 - Extended RTP Profile for Real-time Transport Control Protocol (RTCP) -
    // Based Feedback (RTP/AVPF)

    /**
     *
     *   Constructs a Transport layer Feedback (Generic NACK) "RTPFB" packet (PT=RTPFB=205)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   ssrcSender The sincronization source of sender
     *   @param   fbInformation The RTP sequence number array of the lost packets and the bitmask
     *                          of the lost packets immediately following the RTP packet indicated
     *                          by the pid
     *   @return  The RTPFB Packet
     *
     */

    public static byte[] buildNackPacket(long ssrc, String cname, long ssrcSender,
                                         List<NackFbElement> fbInformation) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPNackPacket(ssrc, ssrcSender,
                fbInformation));

        return packet;
    }


    //**************************************************************************
    // IETF RFC 6285 - Unicast-Based Rapid Acquisition of Multicast RTP Sessions

    /**
     *
     *   Constructs a Transport layer Feedback (RAMS request) "RTPFB" packet (PT=RTPFB=205)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   ssrcSender The sincronization source of sender
     *   @param   extensions The optional TLV elements
     *   @param   privateExtensions The optional private extensions
     *   @return  The RTPFB Packet
     *
     */
    public static byte[] buildRamsRequestPacket(long ssrc, String cname, long ssrcSender,
                                                List<TlvElement> extensions,
                                                List<PrivateExtension> privateExtensions) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPRamsRequestPacket(ssrc, ssrcSender,
                extensions, privateExtensions));

        return packet;
    }

    /**
     *
     *   Constructs a Transport layer Feedback (RAMS Termination) "RTPFB" packet (PT=RTPFB=205)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   ssrcSender The sincronization source of sender
     *   @param   extensions The optional TLV elements
     *   @param   privateExtensions The optional private extensions
     *   @return  The RTPFB Packet
     *
     */
    public static byte[] buildRamsTerminationPacket(long ssrc, String cname, long ssrcSender,
                                                    List<TlvElement> extensions,
                                                    List<PrivateExtension> privateExtensions) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPRamsTerminationPacket(ssrc, ssrcSender,
                extensions, privateExtensions));

        return packet;
    }


    //************************************************************************
    // IETF RFC 6284 - Port Mapping between Unicast and Multicast RTP Sessions

    /**
     *
     *   Constructs a Port Mapping Request packet (PT=TOKEN)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   nonce The random nonce
     *   @return  The Packet
     *
     */
    public static byte[] buildPortMappingRequestPacket(long ssrc, String cname, byte[] nonce) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPPortMappingRequestPacket(ssrc, nonce));

        return packet;
    }

    /**
     *
     *   Constructs a Token Verification Request packet (PT=TOKEN)
     *
     *   @param   ssrc The sincronization source
     *   @param   cname The canonical name
     *   @param   nonce The random nonce
     *   @param   token The token element
     *   @param   expirationTime The absoluete expiration time
     *   @return  The Packet
     *
     */
    public static byte[] buildTokenVerificationRequestPacket(long ssrc, String cname, byte[] nonce,
                                                             byte[] token, long expirationTime) {
        byte packet[] = new byte [0];

        packet = RtcpPacketUtils.append(packet, assembleRTCPReceiverReportPacket(ssrc));
        packet = RtcpPacketUtils.append(packet, assembleRTCPSourceDescriptionPacket(ssrc, cname));
        packet = RtcpPacketUtils.append(packet, assembleRTCPTokenVerificationRequestPacket(
                ssrc, nonce, token, expirationTime));

        return packet;
    }
}

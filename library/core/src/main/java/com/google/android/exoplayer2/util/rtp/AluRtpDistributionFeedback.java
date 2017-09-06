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

import android.net.Uri;
import android.util.SparseArray;

import com.google.android.exoplayer2.util.net.NetworkUtils;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpFeedbackPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpPacketBuilder;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpPacketUtils;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpTokenPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


/**
 * The RTP Distribution and Feedback Model implementation based on Alcate-Lucent architecture
 */
public final class AluRtpDistributionFeedback implements RtpDistributionFeedback {

    private final long ssrc;
    private final String cname;

    private long ssrcSender;
    private boolean ssrcSenderReceived;

    private int firstAudioSequence;
    private int firstVideoSequence;

    private int lastSequenceReceived;

    private boolean multicastSwitched;

    // Default socket time-out in milliseconds
    private static final int BURST_SOURCE_TIMEOUT = 50;
    private static final int DISTRIBUTION_SOURCE_TIMEOUT = 2000;

    private final AluRtpHeaderExtensionParser rtpHeadExtParser;
    private RtpFeedbackEventListener feedbackListener;

    public AluRtpDistributionFeedback(long ssrc, String cname) {
        this.ssrc = ssrc;
        this.cname = cname;

        ssrcSender = 0L;

        multicastSwitched = false;
        ssrcSenderReceived = false;

        firstAudioSequence = UNKNOWN_SEQ;
        firstVideoSequence = UNKNOWN_SEQ;

        lastSequenceReceived = UNKNOWN_SEQ;

        rtpHeadExtParser = new AluRtpHeaderExtensionParser();
    }

    @Override
    public RtpAuthTokenSource createAuthTokenSource(
            RtpFeedbackTargetSource.AuthTokenEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        throw new UnsupportedRtpDistributionFeedbackSourceException(
                "Authentication Token Source unsupported");
    }

    @Override
    public RtpBurstSource createBurstSource(
            RtpFeedbackTargetSource.BurstEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new AluRtpBurstSource(BURST_SOURCE_TIMEOUT, eventListener);
    }

    @Override
    public RtpRetransmissionSource createRetransmissionSource(
            RtpFeedbackTargetSource.RetransmissionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new AluRtpRetransmissionSource(eventListener);
    }

    @Override
    public RtpDistributionSource createDistributionSource(
            RtpDistributionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new AluRtpDistributionSource(DISTRIBUTION_SOURCE_TIMEOUT, eventListener);
    }

    @Override
    public void setFeedbackEventListener(
            RtpFeedbackEventListener feedbackListener) {
        this.feedbackListener = feedbackListener;
    }

    private static final boolean isAudioPacket(RtpPacket packet) {
        return ((packet.getHeaderExtension()[5] & 0x3f) >> 4) == 1;
    }

    private static final boolean isMulticastJoinSignal(RtpPacket packet) {
        return ((packet.getHeaderExtension()[5] & 0x0f) >> 3) == 1;
    }

    private static final boolean isAluExtension(RtpPacket packet) {
        return (((packet.getHeaderExtension()[0] & 0xff) == 0xbe) &&
                ((packet.getHeaderExtension()[1] & 0xff) == 0xde));
    }


    private final class AluRtpBurstSource extends RtpBurstSource {
        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 1024;

        private boolean audioSynch;
        private boolean videoSynch;

        private boolean multicastJoinSignal;

        private final RtpFeedbackTargetSource.BurstEventListener eventListener;

        public AluRtpBurstSource(int socketTimeoutMillis,
                                 RtpFeedbackTargetSource.BurstEventListener eventListener) {
            super(socketTimeoutMillis, eventListener);

            this.eventListener = eventListener;

            audioSynch = false;
            videoSynch = false;

            multicastJoinSignal = false;
        }

        @Override
        public int getMaxBufferCapacity() {
            return MAX_BUFFER_CAPACITY;
        }

        @Override
        boolean isRapidAcquisitionResponse(RtcpFeedbackPacket packet) {
            // Not supported
            return true;
        }

        @Override
        boolean isRapidAcquisitionAccepted(RtcpFeedbackPacket packet) {
            // Not supported
            return true;
        }

        @Override
        boolean isAuthTokenRejected(RtcpTokenPacket packet) {
            // Not supported
            return false;
        }

        @Override
        protected boolean processRtpPacket(RtpPacket packet) {

            if (!multicastJoinSignal && isMulticastJoinSignal(packet)) {
                eventListener.onMulticastJoinSignal();
                multicastJoinSignal = true;
            }

            if (!ssrcSenderReceived) {
                ssrcSender = packet.getSsrc();
                ssrcSenderReceived = true;
            }

            if (isAudioPacket(packet)) {

                if (audioSynch)
                    return false;

                if (firstAudioSequence == packet.getSequenceNumber()) {
                    audioSynch = true;

                    if (videoSynch) {
                        multicastSwitched = true;
                        eventListener.onBurstRapidAcquisitionCompleted();
                    }

                    return false;
                }
                else if (videoSynch) {
                    audioSynch = true;
                    multicastSwitched = true;
                    eventListener.onBurstRapidAcquisitionCompleted();
                    return false;
                }
            }
            else {

                if (videoSynch)
                    return false;

                if (firstVideoSequence == packet.getSequenceNumber()) {
                    videoSynch = true;

                    if (audioSynch) {
                        multicastSwitched = true;
                        eventListener.onBurstRapidAcquisitionCompleted();
                    }

                    return false;
                }
            }

            return true;
        }

        // BurstMessages Implementation
        @Override
        public void sendBurstRapidAcquisitionRequest(Uri uri) throws IOException {
            byte fccr_pkt[] = new byte [0];
            InetAddress srcAddr, hostAddr;

            try {

                srcAddr = InetAddress.getByName(uri.getHost());
                hostAddr = InetAddress.getByName(NetworkUtils.getLocalAddress());

            } catch (UnknownHostException ex) {
                throw new IOException(ex);
            }

            byte[] start = RtcpPacketUtils.longToBytes((long)300, 2);
            byte[] sAddr = srcAddr.getAddress();
            byte[] sPort = RtcpPacketUtils.longToBytes((long)uri.getPort(), 2);
            byte[] hAddr = hostAddr.getAddress();
            byte[] hPort = RtcpPacketUtils.longToBytes((long)0, 2);

            fccr_pkt = RtcpPacketUtils.append(fccr_pkt, start);

            fccr_pkt = RtcpPacketUtils.append(fccr_pkt, sPort);
            fccr_pkt = RtcpPacketUtils.append(fccr_pkt, sAddr);

            byte[] bounded = new byte [2];
            fccr_pkt = RtcpPacketUtils.append (fccr_pkt, bounded);
            fccr_pkt = RtcpPacketUtils.append(fccr_pkt,  RtcpPacketUtils.swapBytes(hPort));
            fccr_pkt = RtcpPacketUtils.append(fccr_pkt,  RtcpPacketUtils.swapBytes(hAddr));

            byte[] bytes = RtcpPacketBuilder.buildAppPacket(ssrc, cname,
                    "FCCR", fccr_pkt);

            sendMessageFromBytes(bytes, bytes.length);

            eventListener.onBurstRapidAcquisitionAccepted();
        }

        @Override
        public void sendBurstTerminationRequest() throws IOException {
            byte[] bytes = RtcpPacketBuilder.buildByePacket(ssrc, cname);
            sendMessageFromBytes(bytes, bytes.length);
        }
    }

    private final class AluRtpRetransmissionSource extends RtpRetransmissionSource {
        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 512;

        private static final int PACKET_LOSS_CAPACITY = 512;
        private static final double PACKET_LOSS_PERCENT = 0.7;

        // The maximum timeout delay for packet pending
        private static final int MAX_TIMEOUT_DELAY = 1000;

        private static final double PACKET_LOSS_ACCEPTABLE = PACKET_LOSS_CAPACITY * PACKET_LOSS_PERCENT;

        // The current number of lost packet pending to recovery
        private int lostPacketPending;

        private final LinkedList<Integer> keys;
        private final SparseArray<Long> timestamps;

        public AluRtpRetransmissionSource(RtpFeedbackTargetSource.RetransmissionEventListener
                                                    eventListener) {
            super(eventListener);

            timestamps = new SparseArray(PACKET_LOSS_CAPACITY);
            keys = new LinkedList<>();

            lostPacketPending = 0;
        }

        @Override
        public int getMaxBufferCapacity() {
            return MAX_BUFFER_CAPACITY;
        }

        @Override
        boolean isAuthTokenRejected(RtcpTokenPacket packet) {
            return false;
        }

        @Override
        protected boolean processRtpPacket(RtpPacket packet) {
            Long timestamp = timestamps.get(packet.getSequenceNumber());

            if (timestamp != null) {
                packet.setTimestamp(timestamp);
                timestamps.remove(packet.getSequenceNumber());
                keys.remove((Integer)packet.getSequenceNumber());

                lostPacketPending--;

                return true;
            }

            return false;
        }

        @Override
        public void resetAllPacketsRecoveryPending(long timestamp) {
            if (timestamp <= 0) {
                keys.clear();
                timestamps.clear();
                lostPacketPending = 0;
            }
            else {
                for (int index=0; index < keys.size(); index++) {
                    int seq = keys.get(index);

                    if (timestamps.get(seq) <= timestamp) {
                        keys.remove(index);
                        timestamps.remove(seq);

                        lostPacketPending--;
                    }
                    else {
                        break;
                    }
                }
            }
        }

        @Override
        public long getMaxDelayTimeForPending() {
            return MAX_TIMEOUT_DELAY;
        }

        @Override
        public int getPacketsRecoveryPending() {
            return lostPacketPending;
        }

        @Override
        public int getMaxPacketsRecoveryPending() {
            return (int) PACKET_LOSS_ACCEPTABLE;
        }

        // RetransmissionMessages implementation
        @Override
        public void sendRetransmissionPacketRequest(int lastSequenceReceived, int numLostPackets)
                throws IOException {

            List<RtcpPacketBuilder.NackFbElement> fbInformation = new ArrayList();

            int bitmaskNextSequences, bitmaskShift;
            int firstSequence, numPackets = 0;

            long currentTime = System.currentTimeMillis();

            while (numLostPackets > 0) {
                numPackets++;

                firstSequence = ((lastSequenceReceived + numPackets) < MAX_PACKET_SEQ) ?
                        (lastSequenceReceived + numPackets) :
                        ((lastSequenceReceived + numPackets) - MAX_PACKET_SEQ);

                --numLostPackets;

                timestamps.put(firstSequence, currentTime);
                keys.add(firstSequence);

                for (bitmaskShift = 0, bitmaskNextSequences = 0;
                     (bitmaskShift < BITMASK_LENGTH) && (numLostPackets > 0);
                     ++bitmaskShift, ++numPackets, --numLostPackets) {

                    bitmaskNextSequences |= ((0xffff) & (1 << bitmaskShift));

                    int sequence = ((firstSequence + bitmaskShift + 1) < MAX_PACKET_SEQ) ?
                            (firstSequence + bitmaskShift + 1) :
                            ((firstSequence + bitmaskShift + 1) - MAX_PACKET_SEQ);

                    timestamps.put(sequence, currentTime);
                    keys.add(sequence);
                }

                fbInformation.add(
                        new RtcpPacketBuilder.NackFbElement(firstSequence, bitmaskNextSequences));
            }

            if (fbInformation.size() > 0) {
                byte[] bytes = RtcpPacketBuilder.buildNackPacket(ssrc, cname,
                        ssrcSender, fbInformation);

                sendMessageFromBytes(bytes, bytes.length);
                lostPacketPending += numPackets;
            }
        }

        @Override
        public void sendRetransmissionTerminationRequest() throws IOException {
            byte[] bytes = RtcpPacketBuilder.buildByePacket(ssrc, cname);

            sendMessageFromBytes(bytes, bytes.length);
            lostPacketPending = 0;
        }
    }

    private final class AluRtpDistributionSource extends RtpDistributionSource {
        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 2048;

        private final RtpDistributionEventListener eventListener;

        public AluRtpDistributionSource(int socketTimeoutMillis,
                                          RtpDistributionEventListener eventListener) {
            super(socketTimeoutMillis, eventListener);

            this.eventListener = eventListener;
        }

        @Override
        public int getMaxBufferCapacity() {
            return MAX_BUFFER_CAPACITY;
        }

        @Override
        protected boolean processRtpPacket(RtpPacket packet) {
            if ((feedbackListener != null) && packet.isExtension() && isAluExtension(packet)) {
                if (!rtpHeadExtParser.isLoaded()) {
                    rtpHeadExtParser.parseHeader(packet.getHeaderExtension());
                }
            }

            if (!ssrcSenderReceived) {
                ssrcSender = packet.getSsrc();
                ssrcSenderReceived = true;
            }

            if (lastSequenceReceived != UNKNOWN_SEQ) {
                int nextSequence = ((lastSequenceReceived + 1) < MAX_PACKET_SEQ) ?
                        (lastSequenceReceived + 1) : (lastSequenceReceived + 1) - MAX_PACKET_SEQ;

                if (nextSequence != packet.getSequenceNumber()) {
                    int numLostPackets = (packet.getSequenceNumber() > lastSequenceReceived) ?
                            (packet.getSequenceNumber() - lastSequenceReceived) :
                            (MAX_PACKET_SEQ - lastSequenceReceived) + packet.getSequenceNumber() + 1;

                    eventListener.onRtpLostPacketDetected(lastSequenceReceived, numLostPackets);
                }
            }

            if (!multicastSwitched) {
                if (isAudioPacket(packet)) {
                    if (firstAudioSequence == UNKNOWN_SEQ) {
                        firstAudioSequence = packet.getSequenceNumber();
                    }
                } else {
                    if (firstVideoSequence == UNKNOWN_SEQ) {
                        firstVideoSequence = packet.getSequenceNumber();
                    }
                }
            }

            packet.setTimestamp(System.currentTimeMillis());

            lastSequenceReceived = packet.getSequenceNumber();

            return true;
        }
    }

    /*
        Alcatel-Lucent RTP Header Extension Format

           0                   1                   2                   3
         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |            0xbede             |    length=in 32 bits words    |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |  ID=1 | len=3 |B|E| ST|S|r|PRI| FPRI|r|   GOP end countdown   |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |  ID=4 | len=6 |  ???  -   payload row index   |               |
        +-+-+-+-+-+-+-+-+-------------------------------+               |
        |              ALU/Alu RTP ext type 4 payload row             |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |  ID=5 | len=6 |                                               |
        +-+-+-+-+-+-+-+-+       ALU/Alu RTP ext type 5 payload        |
        |                                                               |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |  ID=7 | len=2 |       TDEC_90kHz (signed - 90KHz units)       |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    private final class AluRtpHeaderExtensionParser {
        private final static int INFO_SIZE = 32;

        private int skip;
        private int state;

        private boolean begin;
        private boolean loaded;

        private final int NONE_SEEK = 0;
        private final int TYPE_SEEK = 1;
        private final int SIZE_SEEK = 2;
        private final int CHECKSUM_SEEK = 3;
        private final int INFO_SEEK = 4;

        private final byte[] info;

        public AluRtpHeaderExtensionParser() {
            info = new byte[INFO_SIZE];
            begin = loaded = false;
            reset();
        }

        public boolean isLoaded() {
            return loaded;
        }

        private void reset() {
            skip = 0;
            state = NONE_SEEK;
        }

        private String toIpAddress(byte[] bytes, int offset) {
            return (((bytes[offset] & 0xf0) >> 4) * 16 + (bytes[offset] & 0x0f)) + "." +
                    (((bytes[offset+1] & 0xf0) >> 4) * 16 + (bytes[offset+1] & 0x0f)) + "." +
                    (((bytes[offset+2] & 0xf0) >> 4) * 16 + (bytes[offset+2] & 0x0f)) + "." +
                    (((bytes[offset+3] & 0xf0) >> 4) * 16 + (bytes[offset+3] & 0x0f));
        }

        private int toPortNumber(byte[] bytes, int offset) {
            return (bytes[offset] & 0xff) * 256 + bytes[offset+1];
        }

        private void parseId5(byte[] bytes, int offset) {
            int subtype = bytes[offset] & 0x0f;

            if (begin) {

                String ipAddr = toIpAddress(bytes, offset + 1);
                int port = toPortNumber(bytes, offset + 5);

                if (subtype == 1) {
                    feedbackListener.onRtpFeedbackEvent(
                            new AluDefaultRtpRetransmissionServerEvent(ipAddr + ":" + port));

                } else if (subtype == 3) {
                    feedbackListener.onRtpFeedbackEvent(
                            new AluDefaultRtpBurstServerEvent(ipAddr + ":" + port));
                }
            }
        }

        private void parseInfo() {
            String mcastIpAddr = toIpAddress(info, 0);
            int mcastPort = toPortNumber(info, 4);

            String firstBurstIpAddr = toIpAddress(info, 6);
            int firstBurstPort = toPortNumber(info, 10);

            String secondBurstIpAddr = toIpAddress(info, 12);
            int secondBurstPort = toPortNumber(info, 16);

            String firstRetransIpAddr = toIpAddress(info, 18);
            int firstRetransPort = toPortNumber(info, 22);

            String secondRetransIpAddr = toIpAddress(info, 24);
            int secondRetransPort = toPortNumber(info, 28);

            feedbackListener.onRtpFeedbackEvent(
                    new AluRtpMulticastGroupInfoEvent(mcastIpAddr + ":" + mcastPort,
                            firstBurstIpAddr + ":" + firstBurstPort,
                            secondBurstIpAddr + ":" + secondBurstPort,
                            firstRetransIpAddr + ":" + firstRetransPort,
                            secondRetransIpAddr + ":" + secondRetransPort));
        }

        private void parseId4(byte[] bytes, int offset, int length) {
            int subtype = (bytes[offset] & 0xf0) >> 4;
            int index = ((bytes[offset] & 0x0f) * 256) + (((bytes[offset+1] & 0xf0) >> 4) * 16) +
                    (bytes[offset+1] & 0x0f);

            int seek = offset + 2; // (skip subtype and row index bytes: 2
            int total = offset + length;

            if (subtype == 1) {

                if (index == 0) {

                    if (begin) {

                        loaded = true;
                        feedbackListener.onRtpFeedbackEvent(
                                new AluRtpFeedbackConfigDiscoveryEnded());

                        return;
                    }

                    begin = true;
                    feedbackListener.onRtpFeedbackEvent(
                            new AluRtpFeedbackConfigDiscoveryStarted());
                }

                if (begin) {

                    while (seek < total) {

                        switch (state) {

                            case NONE_SEEK: {
                                if ((bytes[seek] & 0xff) == 0x02) {
                                    state = TYPE_SEEK;
                                }

                                seek++;
                            }

                            break;

                            case TYPE_SEEK: {
                                if ((bytes[seek] & 0xff) == 0x02) {
                                    state = SIZE_SEEK;
                                    skip = 2;
                                } else {
                                    reset();
                                }

                                seek++;
                            }

                            break;

                            case SIZE_SEEK: {
                                switch (skip) {
                                    case 2: {
                                        if ((bytes[seek] & 0xff) == 0x00) {
                                            skip--;
                                            seek++;
                                        } else {
                                            state = TYPE_SEEK;
                                        }
                                    }

                                    break;

                                    case 1: {
                                        if ((bytes[seek] & 0xff) == 0x20) {
                                            state = CHECKSUM_SEEK;
                                            skip = 2;
                                            seek++;
                                        } else {
                                            reset();
                                        }
                                    }

                                    break;

                                    default: {
                                        reset();
                                    }

                                    break;
                                }
                            }

                            break;

                            case CHECKSUM_SEEK: {
                                switch (skip) {
                                    case 2: {
                                        skip--;
                                        seek++;
                                    }

                                    break;

                                    case 1: {
                                        state = INFO_SEEK;
                                        skip = 0;
                                        seek++;
                                    }

                                    break;

                                    default: {
                                        reset();
                                    }

                                    break;
                                }
                            }

                            break;

                            case INFO_SEEK: {
                                if (skip + 1 < INFO_SIZE) {
                                    info[skip++] = bytes[seek++];

                                } else {
                                    info[skip++] = bytes[seek++];
                                    parseInfo();
                                    reset();
                                    return;
                                }
                            }

                            break;
                        }
                    }
                }
            }
        }

        public boolean parseHeader(byte[] header) {
            int seek=4;

            while ((seek < header.length) && !loaded) {
                int id = (header[seek] >> 4) & 0x0f;
                int length = header[seek] & 0x0f;

                seek++;

                switch (id) {
                    case 1: {
                        seek+=length;
                    }

                    break;

                    case 4: {
                        parseId4(header, seek, length + 1);
                        seek+=length+1;
                    }

                    break;

                    case 5: {
                        parseId5(header, seek);
                        seek+=length+1;
                    }

                    break;

                    default: {
                        seek+=length+1;
                    }

                    break;
                }
            }

            return true;
        }
    }

    public final class AluRtpFeedbackConfigDiscoveryStarted implements RtpFeedbackEvent {
    }

    public final class AluRtpFeedbackConfigDiscoveryEnded implements RtpFeedbackEvent {
    }

    public final class AluDefaultRtpBurstServerEvent implements RtpFeedbackEvent {

        private final String burstServer;

        public AluDefaultRtpBurstServerEvent(String burstServer) {
            this.burstServer = burstServer;
        }

        public String getBurstServer() {
            return burstServer;
        }
    }

    public final class AluDefaultRtpRetransmissionServerEvent implements RtpFeedbackEvent {

        private final String retransmissionServer;

        public AluDefaultRtpRetransmissionServerEvent(String retransmissionServer) {
            this.retransmissionServer = retransmissionServer;
        }

        public String getRetransmissionServer() {
            return retransmissionServer;
        }
    }

    public final class AluRtpMulticastGroupInfoEvent implements RtpFeedbackEvent {

        private final String multicastGroup;

        private final String firstBurstServer;
        private final String secondBurstServer;

        private final String firstRetransmissionServer;
        private final String secondRetransmissionServer;

        public AluRtpMulticastGroupInfoEvent(String multicastGroup,
                                             String firstBurstServer, String secondBurstServer,
                                             String firstRetransmissionServer,
                                             String secondRetransmissionServer)
        {
            this.multicastGroup = multicastGroup;
            this.firstBurstServer = firstBurstServer;
            this.secondBurstServer = secondBurstServer;
            this.firstRetransmissionServer = firstRetransmissionServer;
            this.secondRetransmissionServer = secondRetransmissionServer;
        }

        public String getMulticastGroup() {
            return multicastGroup;
        }

        public String getFirstBurstServer() {
            return firstBurstServer;
        }

        public String getSecondBurstServer() {
            return secondBurstServer;
        }

        public String getFirstRetransmissionServer() {
            return firstRetransmissionServer;
        }

        public String getSecondRetransmissionServer() {
            return secondRetransmissionServer;
        }
    }

}

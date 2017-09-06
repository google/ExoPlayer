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

import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpFeedbackPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpPacketBuilder;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpTokenPacket;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;


/**
 * The Default RTP Distribution and Feedback Model implementation based on IETF Standards
 */
public final class DefaultRtpDistributionFeedback implements RtpDistributionFeedback {

    private final long ssrc;
    private final String cname;

    private long ssrcSender;
    private boolean ssrcSenderReceived;

    // Default socket time-out in milliseconds
    private static final int BURST_SOURCE_TIMEOUT = 50;
    private static final int DISTRIBUTION_SOURCE_TIMEOUT = 2000;

    public DefaultRtpDistributionFeedback(long ssrc, String cname) {
        this.ssrc = ssrc;
        this.cname = cname;
    }

    @Override
    public RtpAuthTokenSource createAuthTokenSource(
            RtpFeedbackTargetSource.AuthTokenEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new DefaultRtpAuthTokenSource(0, eventListener);
    }

    @Override
    public RtpBurstSource createBurstSource(
            RtpFeedbackTargetSource.BurstEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new DefaultRtpBurstSource(BURST_SOURCE_TIMEOUT, eventListener);
    }

    @Override
    public RtpRetransmissionSource createRetransmissionSource(
            RtpFeedbackTargetSource.RetransmissionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new DefaultRtpRetransmissionSource(eventListener);
    }

    @Override
    public RtpDistributionSource createDistributionSource(
            RtpDistributionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException {
        return new DefaultRtpDistributionSource(DISTRIBUTION_SOURCE_TIMEOUT, eventListener);
    }

    @Override
    public void setFeedbackEventListener(
            RtpFeedbackEventListener feedbackListener) {
        // TODO default implementation
    }

    private final class DefaultRtpAuthTokenSource extends RtpAuthTokenSource {
        private final byte[] nonce;

        public DefaultRtpAuthTokenSource(int socketTimeoutMillis,
                                         RtpFeedbackTargetSource.AuthTokenEventListener
                                                 eventListener) {
            super(socketTimeoutMillis, eventListener);

            nonce = new byte[32];
            new SecureRandom().nextBytes(nonce);
        }

        @Override
        protected byte[] getRandomNonce() {
            return nonce;
        }

        // AuthTokenMessages standard implementation (IETF RFC 6284)
        @Override
        public synchronized void sendAuthTokenRequest() throws IOException {
            byte[] bytes = RtcpPacketBuilder.buildPortMappingRequestPacket(ssrc, cname,
                    getRandomNonce());

            sendMessageFromBytes(bytes, bytes.length);

            setAuthTokenResponsePending(true);
        }
    }

    private final class DefaultRtpBurstSource extends RtpBurstSource {

        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 1024;

        public DefaultRtpBurstSource(int socketTimeoutMillis,
                                     RtpFeedbackTargetSource.BurstEventListener eventListener) {
            super(socketTimeoutMillis, eventListener);
        }

        @Override
        public int getMaxBufferCapacity() {
            return MAX_BUFFER_CAPACITY;
        }

        @Override
        protected boolean isRapidAcquisitionResponse(RtcpFeedbackPacket packet) {
            // Rapid Acquisition response based in standard implementation (IETF RFC 6285)
            if ((packet.getPayloadType() == RtcpPacketBuilder.RTCP_RTPFB)
                    && (packet.getFmt() == RtcpPacketBuilder.RTCP_SFMT_RAMS_INFO)) {
               return true;
            }

            return false;
        }

        @Override
        protected boolean isRapidAcquisitionAccepted(RtcpFeedbackPacket packet) {
            // TODO payload decoding based in standard implementation (IETF RFC 6285)
            // ...
            // decode the feedback control information (from payload) and
            // evaluate the previous request

            return true;
        }

        @Override
        protected boolean isAuthTokenRejected(RtcpTokenPacket packet) {
            if ((packet.getPayloadType() == RtcpPacketBuilder.RTCP_TOKEN)
                    && (packet.getSmt() == RtcpPacketBuilder.RTCP_SMT_TOKEN_VERIFY_FAIL)) {

                return true;
            }

            return false;
        }

        @Override
        protected boolean processRtpPacket(RtpPacket packet) {
            // TODO

            if (!ssrcSenderReceived) {
                ssrcSender = packet.getSsrc();
                ssrcSenderReceived = true;
            }

            return true;
        }

        // BurstMessages standard implementation (IETF RFC 6285)
        @Override
        public void sendBurstRapidAcquisitionRequest(Uri uri) throws IOException {
           byte[] bytes = RtcpPacketBuilder.buildRamsRequestPacket(ssrc, cname,
                   ssrcSender, new ArrayList<RtcpPacketBuilder.TlvElement>(),
                   new ArrayList<RtcpPacketBuilder.PrivateExtension>());

           sendMessageFromBytes(bytes, bytes.length);
        }

        @Override
        public void sendBurstTerminationRequest() throws IOException {
            byte[] bytes = RtcpPacketBuilder.buildRamsTerminationPacket(ssrc, cname,
                    ssrcSender, new ArrayList<RtcpPacketBuilder.TlvElement>(),
                    new ArrayList<RtcpPacketBuilder.PrivateExtension>());

            sendMessageFromBytes(bytes, bytes.length);
        }
    }


    private final class DefaultRtpRetransmissionSource extends RtpRetransmissionSource {

        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 512;

        private final RtpFeedbackTargetSource.RetransmissionEventListener eventListener;

        public DefaultRtpRetransmissionSource(RtpFeedbackTargetSource.RetransmissionEventListener
                                                      eventListener) {
            super(eventListener);

            this.eventListener = eventListener;
        }

        @Override
        public int getMaxBufferCapacity() {
            return MAX_BUFFER_CAPACITY;
        }

        @Override
        public void resetAllPacketsRecoveryPending(long timestamp) {

        }

        @Override
        public int getPacketsRecoveryPending() {
            return 0;
        }

        @Override
        public int getMaxPacketsRecoveryPending() {
            return 0;
        }

        @Override
        protected boolean processRtpPacket(RtpPacket packet) {
            // TODO
            return true;
        }

        @Override
        public long getMaxDelayTimeForPending() {
            return 0;
        }

        // RetransmissionMessages implementation (IETF RFC 5760, IETF RFC 4585, IETF RFC 1889)
        @Override
        public void sendRetransmissionPacketRequest(int lastSequenceReceived, int numLostPackets)
                throws IOException {
            // TODO add token into Nack packet when an authentication token is used
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

                for (bitmaskShift = 0, bitmaskNextSequences = 0;
                     (bitmaskShift < BITMASK_LENGTH) && (numLostPackets > 0);
                     ++bitmaskShift, ++numPackets, --numLostPackets) {

                    bitmaskNextSequences |= ((0xffff) & (1 << bitmaskShift));

                    int sequence = ((firstSequence + bitmaskShift + 1) < MAX_PACKET_SEQ) ?
                            (firstSequence + bitmaskShift + 1) :
                            ((firstSequence + bitmaskShift + 1) - MAX_PACKET_SEQ);
                }

                fbInformation.add(
                        new RtcpPacketBuilder.NackFbElement(firstSequence, bitmaskNextSequences));
            }

            if (fbInformation.size() > 0) {
                byte[] bytes = RtcpPacketBuilder.buildNackPacket(ssrc, cname,
                        ssrcSender, fbInformation);

                sendMessageFromBytes(bytes, bytes.length);
            }
        }

        @Override
        public void sendRetransmissionTerminationRequest() throws IOException {
            // TODO add token into Nack packet when an authentication token is used
            byte[] bytes = RtcpPacketBuilder.buildByePacket(ssrc, cname);

            sendMessageFromBytes(bytes, bytes.length);

            cancelLoad();
        }

        @Override
        protected boolean isAuthTokenRejected(RtcpTokenPacket packet) {
            if ((packet.getPayloadType() == RtcpPacketBuilder.RTCP_TOKEN)
                    && (packet.getSmt() == RtcpPacketBuilder.RTCP_SMT_TOKEN_VERIFY_FAIL)) {

                return true;
            }

            return false;
        }

        // Loader.Callback implementation
        @Override
        public void onLoadCompleted(RtpRetransmissionSource loadable, long elapsedRealtimeMs,
                                    long loadDurationMs) {
            // Do nothing
        }

        @Override
        public void onLoadCanceled(RtpRetransmissionSource loadable, long elapsedRealtimeMs,
                                   long loadDurationMs, boolean released) {
            eventListener.onRtpRetransmissionSourceCanceled();
        }

        @Override
        public int onLoadError(RtpRetransmissionSource loadable, long elapsedRealtimeMs,
                               long loadDurationMs, IOException error) {
            eventListener.onRtpRetransmissionSourceError();
            return Loader.DONT_RETRY;
        }
    }

    private final class DefaultRtpDistributionSource extends RtpDistributionSource {

        // The maximum buffer capacity, in packets.
        private static final int MAX_BUFFER_CAPACITY = 2048;

        private final RtpDistributionEventListener eventListener;

        public DefaultRtpDistributionSource(int socketTimeoutMillis,
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
            // TODO

            if (!ssrcSenderReceived) {
                ssrcSender = packet.getSsrc();
                ssrcSenderReceived = true;
            }

            return true;
        }

        // Loader.Callback implementation
        @Override
        public void onLoadCompleted(RtpDistributionSource loadable, long elapsedRealtimeMs,
                                    long loadDurationMs) {
            // Do nothing
        }

        @Override
        public void onLoadCanceled(RtpDistributionSource loadable, long elapsedRealtimeMs,
                                   long loadDurationMs, boolean released) {
            eventListener.onRtpDistributionSourceCanceled();
        }

        @Override
        public int onLoadError(RtpDistributionSource loadable, long elapsedRealtimeMs,
                               long loadDurationMs, IOException error) {
            eventListener.onRtpDistributionSourceError();
            return Loader.DONT_RETRY;
        }
    }

}

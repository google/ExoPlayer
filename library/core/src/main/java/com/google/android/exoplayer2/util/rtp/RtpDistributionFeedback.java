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
import android.os.ConditionVariable;

import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.RtpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpFeedbackPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpPacketBuilder;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpSrPacket;
import com.google.android.exoplayer2.util.rtp.rtcp.RtcpTokenPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A RTP Distribution and Feedback Abstract Model interface.
 */
public interface RtpDistributionFeedback {

    int UNKNOWN_SEQ = -1; // Unknown sequence
    int MAX_PACKET_SEQ = 65536;

    /**
     * The feedback properties
     */
    interface Properties {
        int FB_SCHEME = 1;
        int FB_VENDOR = 2;
        int FB_RAMS_URI = 3;
        int FB_CONGESTION_CONTROL_URI = 4;
        int FB_PORT_MAPPING_URI = 5;
        int FB_EVENTS_CALLBACK = 6;
    }

    /**
     * The feedback schemes
     */
    interface Schemes {
        int FB_REPORT = 0x01;
        int FB_RAMS = 0x02;
        int FB_CONGESTION_CONTROL = 0x04;
        int FB_PORT_MAPPING = 0x08;
    }

    /**
     * The middleware providers (feedback vendors)
     */
    interface Providers {
        int ADTEC_DIGITAL = 1;
        int ALTICAST = 2;
        int ALU = 3;
        int BCC = 4;
        int BEE_MEDIASOFT = 5;
        int BEENIUS = 6;
        int CASCADE = 7;
        int COMIGO = 8;
        int CONKLIN_INTRACOM = 9;
        int CUBIWARE = 10;
        int DIGISOFT_TV = 11;
        int EASY_TV = 12;
        int ERICSSON = 13;
        int ESPIAL = 14;
        int HUAWEI = 15;
        int IKON = 16;
        int LEV_TV = 17;
        int MICROSOFT = 18;
        int MINERVA = 19;
        int MIRADA = 20;
        int NANGU_TV = 21;
        int NETGEM = 22;
        int NORDIJA = 23;
        int NOKIA = 24;
        int OCILION = 25;
        int QUADRILLE = 26;
        int SEACHANGE = 27;
        int SIEMENS = 28;
        int SMARTLABS = 29;
        int THOMSON = 30;
        int TIVO = 31;
        int UTSTART = 32;
        int VIANEOS = 33;
        int ZAPPWARE = 34;
        int ZENTERIO = 35;
        int ZTE = 36;
    }


    /**
     * A factory for {@link RtpDistributionFeedback} instances.
     */
    interface Factory {
        /**
         * Creates a {@link RtpDistributionFeedback} instance.
         */
        RtpDistributionFeedback createDistributionFeedback();
    }


    /**
     * A distribution and feedback event abstract
     */
    interface RtpFeedbackEvent {
    }

    /**
     * A event listener for {@link RtpFeedbackEvent} events.
     */
    interface RtpFeedbackEventListener {
        /**
         * Called when an event has been triggered from distribution and feedback architecture
         */
        void onRtpFeedbackEvent(RtpFeedbackEvent event);
    }


    /**
     * Thrown when an error is encountered when trying to create a {@link RtpFeedbackTarget} or
     * {@link RtpDistributionSource}.
     */
    final class UnsupportedRtpDistributionFeedbackSourceException extends Exception {

        public UnsupportedRtpDistributionFeedbackSourceException(String message) {
            super(message);
        }

        public UnsupportedRtpDistributionFeedbackSourceException(Throwable cause) {
            super(cause);
        }

        public UnsupportedRtpDistributionFeedbackSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    RtpAuthTokenSource createAuthTokenSource(
            RtpFeedbackTargetSource.AuthTokenEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException;

    RtpBurstSource createBurstSource(RtpFeedbackTargetSource.BurstEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException;

    RtpRetransmissionSource createRetransmissionSource(
            RtpFeedbackTargetSource.RetransmissionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException;

    RtpDistributionSource createDistributionSource(RtpDistributionEventListener eventListener)
            throws UnsupportedRtpDistributionFeedbackSourceException;

    void setFeedbackEventListener(RtpFeedbackEventListener eventListener);


    final class RtpFeedbackProperties {

        private final Map<Integer, Object> properties;
        private Map<Integer, Object> propertiesSnapshot;

        public RtpFeedbackProperties() {
            properties = new HashMap<>();
        }

        /**
         * Sets the specified property {@code value} for the specified {@code name}. If a property for
         * this name previously existed, the old value is replaced by the specified value.
         *
         * @param id The identifier of the request property.
         * @param value The value of the request property.
         */
        public synchronized void set(Integer id, Object value) {
            propertiesSnapshot = null;
            properties.put(id, value);
        }

        /**
         * Sets the keys and values contained in the map. If a property previously existed, the old
         * value is replaced by the specified value. If a property previously existed and is not in the
         * map, the property is left unchanged.
         *
         * @param properties The request properties.
         */
        public synchronized void set(Map<Integer, Object> properties) {
            propertiesSnapshot = null;
            properties.putAll(properties);
        }

        /**
         * Removes all properties previously existing and sets the keys and values of the map.
         *
         * @param properties The request properties.
         */
        public synchronized void clearAndSet(Map<Integer, Object> properties) {
            propertiesSnapshot = null;
            properties.clear();
            properties.putAll(properties);
        }

        /**
         * Removes a request property by name.
         *
         * @param identifier The identifier of the request property to remove.
         */
        public synchronized void remove(Integer identifier) {
            propertiesSnapshot = null;
            properties.remove(identifier);
        }

        /**
         * Clears all request properties.
         */
        public synchronized void clear() {
            propertiesSnapshot = null;
            properties.clear();
        }

        /**
         * Gets a snapshot of the request properties.
         *
         * @return A snapshot of the request properties.
         */
        public synchronized Map<Integer, Object> getSnapshot() {
            if (propertiesSnapshot == null) {
                propertiesSnapshot = Collections.unmodifiableMap(new HashMap<>(properties));
            }

            return propertiesSnapshot;
        }

    }


    interface RtpFeedbackTarget {
        /**
         * Thrown when an error is encountered when trying to open/read/write from/to a
         * {@link RtpFeedbackTarget}.
         */
        final class RtpFeedbackTargetException extends IOException {

            public RtpFeedbackTargetException(IOException cause) {
                super(cause);
            }

        }

        void open(Uri uri) throws RtpFeedbackTargetException;

        void close();

        Uri getUri();

        /**
         * Interface for messages to be sent into authentication source.
         */
        interface AuthTokenMessages {
            /**
             * send a Port Mapping Request packet to Retransmission Server to initialize
             * the port mapping setup procedure. (sendPortMappingRequest)
             */
            void sendAuthTokenRequest() throws IOException;
        }

        /**
         * Interface for messages to be sent into burst source.
         */
        interface BurstMessages {
            /**
             * send a RAMS Request packet to Retransmission Server to initialize
             * the unicast-based rapid acquisition of multicast procedure. (sendRamsRequest)
             */
            void sendBurstRapidAcquisitionRequest(Uri uri) throws IOException;

            /**
             * send a RAMS Terminate packet to Retransmission Server to finalize the unicast-based
             * rapid acquisition of multicast procedure. (sendRamsTerminate)
             *
             *  or send a BYE packet to Retransmission Server to finalize the rtcp feedback.
             */
            void sendBurstTerminationRequest() throws IOException;
        }

        /**
         * Interface for messages to be sent into retransmission source.
         */
        interface RetransmissionMessages {
            /**
             * send a Generic Negative Acknowledgement packet to Retransmission Server to notify a
             * packet loss was detected. (sendNack)
             */
            void sendRetransmissionPacketRequest(int lastSequenceReceived,
                                                 int numLostPackets) throws IOException;

            /**
             * send a BYE packet to Retransmission Server to finalize the rtcp feedback.
             */
            void sendRetransmissionTerminationRequest() throws IOException;
        }
    }

    interface RtpFeedbackTargetSource {

        /**
         * Interface for callbacks to be notified from Authentication Token Source.
         */
        interface AuthTokenEventListener {
            /**
             * Called when a Port Mapping Response packet has been received from Retransmission
             * Server. (onPortMappingResponse)
             */
            void onAuthTokenResponse();

            /**
             * Called when a Port Mapping response packet has not received from Retransmission
             * Server within a specified timeframe. (onPortMappingResponseBeforeTimeout)
             */
            void onAuthTokenResponseBeforeTimeout();

            /**
             * Called when an error occurs decoding Port Mapping response packet received from
             * Retransmission Server (onPortMappingResponseBeforeError)
             */
            void onAuthTokenResponseBeforeError();

            /**
             * Called when the Port Mapping response packet received from
             * Retransmission Server (onPortMappingResponseBeforeError) is unexpected
             */
            void onAuthTokenResponseUnexpected();

            /**
             * Called when a RTP Authentication Token source encounters an error.
             */
            void onRtpAuthTokenSourceError();

            /**
             * Called when a RTP Authentication Token source has been canceled.
             */
            void onRtpAuthTokenSourceCanceled();
        }

        /**
         * Interface for callbacks to be notified from Burst Source.
         */
        interface BurstEventListener {

            /**
             * Called when a RAMS Information packet has been accepted by Retransmission Server.
             */
            void onBurstRapidAcquisitionAccepted();

            /**
             * Called when a RAMS Information packet has been rejected by Retransmission Server.
             */
            void onBurstRapidAcquisitionRejected();


            /**
             * Called when an multicast join signaling is detected that requires the RTP_Rx
             * to be notified to send SFGMP Join message.
             */
            void onMulticastJoinSignal();

            /**
             * Called when the rapid acquisition has been completed.
             */
            void onBurstRapidAcquisitionCompleted();

            /**
             * Called when a Rams Information packet has not received from Retransmission Server
             * within a specified timeframe.
             */
            void oBurstRapidAcquisitionResponseBeforeTimeout();

            /**
             * Called when the Retransmission Server has detected that token is invalid or has expired.
             * It is only applied when the por mapping mechanism is supported
             */
            void onInvalidToken();

            /**
             * Called when a RTP packet burst has been received.
             */
            void onRtpPacketBurstReceived(RtpPacket packet);

            /**
             * Called when a RTP Burst Source encounters an error.
             */
            void onRtpBurstSourceError();

            /**
             * Called when a RTP Burst source has been canceled.
             */
            void onRtpBurstSourceCanceled();
        }

        /**
         * Interface for callbacks to be notified from Retransmission Source.
         */
        interface RetransmissionEventListener {

            /**
             * Called when the Retransmission Server has detected that token is invalid or has expired.
             * It is only applied when the por mapping mechanism is supported
             */
            void onInvalidToken();

            /**
             * Called when a RTP packet loss has been received.
             */
            void onRtpPacketLossReceived(RtpPacket packet);

            /**
             * Called when a RTP Retransmission Source encounters an error.
             */
            void onRtpRetransmissionSourceError();

            /**
             * Called when a RTP Retransmission source has been canceled.
             */
            void onRtpRetransmissionSourceCanceled();
        }
    }

    /**
     * Interface for callbacks to be notified from Distribution Source.
     */
    interface RtpDistributionEventListener {
        /**
         * Called when a RTP packet has been received.
         */
        void onRtpPacketReceived(RtpPacket packet);

        /**
         * Called when a RTP lost packet has been detected.
         */
        void onRtpLostPacketDetected(int lastSequenceReceived, int numLostPackets);

        /**
         * Called when a RTP Distribution source encounters an error.
         */
        void onRtpDistributionSourceError();

        /**
         * Called when a RTP Distribution source has been canceled.
         */
        void onRtpDistributionSourceCanceled();
    }

    abstract class RtpAuthTokenSource implements RtpFeedbackTarget,
            RtpFeedbackTarget.AuthTokenMessages,
            RtcpCompoundPacket.RtcpCompoundPacketEventListener,
            Loader.Loadable,
            Loader.Callback<RtpAuthTokenSource> {

        private Uri uri;

        private byte[] inBuffer;
        private byte[] outBuffer;

        protected DatagramSocket socket;
        private final DatagramPacket inPacket;
        protected final DatagramPacket outPacket;

        private InetAddress address;
        private InetSocketAddress socketAddress;

        private final int socketTimeoutMillis;

        private final ConditionVariable loadCondition;
        private volatile boolean loadCanceled = false;

        private RtpFeedbackTargetSource.AuthTokenEventListener eventListener;

        private byte[] token;
        private long expirationTime;

        private boolean authReponsePending = false;

        public RtpAuthTokenSource(int socketTimeoutMillis,
                                  RtpFeedbackTargetSource.AuthTokenEventListener eventListener) {

            this.eventListener = eventListener;
            this.socketTimeoutMillis = socketTimeoutMillis;

            this.loadCondition = new ConditionVariable(false);

            this.inBuffer = new byte[RtpDataSource.MTU_SIZE];
            this.outBuffer = new byte[RtpDataSource.MTU_SIZE];

            this.inPacket = new DatagramPacket(this.inBuffer, 0, RtpDataSource.MTU_SIZE);
            this.outPacket = new DatagramPacket(this.outBuffer, 0, RtpDataSource.MTU_SIZE);
        }

        public final byte[] getToken() { return token; }

        public final long getExpirationTime() { return expirationTime; }

        abstract byte[] getRandomNonce();

        protected void sendMessageFromBytes(byte[] bytes, int length) throws IOException {
            outPacket.setData(bytes, 0, length);
            socket.send(outPacket);

            loadCondition.open();
        }

        public final void setAuthTokenResponsePending(boolean state) {
            authReponsePending = state;
        }


        // RtpFeedbackTarget implementation
        @Override
        public void open(Uri uri) throws RtpFeedbackTargetException {
            this.uri = Assertions.checkNotNull(uri);

            String host = uri.getHost();
            int port = uri.getPort();

            try {

                address = InetAddress.getByName(host);
                socketAddress = new InetSocketAddress(address, port);

                socket = new DatagramSocket();
                socket.connect(socketAddress);

            } catch (IOException e) {
                throw new RtpFeedbackTargetException(e);
            }

            try {

                socket.setSoTimeout(socketTimeoutMillis);

            } catch (SocketException e) {
                throw new RtpFeedbackTargetException(e);
            }
        }

        @Override
        public Uri getUri() {
            return uri;
        }


        // RtcpCompoundPacketEventListener implementation
        @Override
        public void onSenderReportPacket(RtcpSrPacket packet) {
            // Do nothing
        }

        @Override
        public void onSourceDescriptionPacket(RtcpSdesPacket packet) {
            // Do nothing
        }

        @Override
        public void onRtpFeedbackPacket(RtcpFeedbackPacket packet) {
            // Do nothing
        }

        @Override
        public void onTokenPacket(RtcpTokenPacket packet) {
            if ((packet.getPayloadType() == RtcpPacketBuilder.RTCP_TOKEN)
                    && (packet.getSmt() == RtcpPacketBuilder.RTCP_SMT_PORT_MAPPING_RESP)) {
                this.token = packet.getTokenElement();
                this.expirationTime = packet.getRelativeExpirationTime();

                authReponsePending = false;
                eventListener.onAuthTokenResponse();

            } else {
                eventListener.onAuthTokenResponseUnexpected();
            }
        }

        private void handlePacket(byte[] buffer, int length)
                throws RtcpCompoundPacket.RtcpCompoundPacketException {
            RtcpCompoundPacket compoundPacket = new RtcpCompoundPacket(this);
            compoundPacket.fromBytes(buffer, length);
        }


        // Loader.Loadable implementation
        @Override
        public void cancelLoad() {
            loadCanceled = true;
        }

        @Override
        public boolean isLoadCanceled() {
            return loadCanceled;
        }

        @Override
        public void load() throws IOException, InterruptedException {
            while (!loadCanceled) {

                try {

                    loadCondition.block();

                    if (authReponsePending) {
                        socket.receive(inPacket);
                        handlePacket(inPacket.getData(), inPacket.getLength());
                    }

                } catch (RtcpCompoundPacket.RtcpCompoundPacketException e) {

                    if (authReponsePending) {
                        authReponsePending = false;
                        eventListener.onAuthTokenResponseBeforeError();
                    }

                } catch (SocketTimeoutException e) {
                    authReponsePending = false;
                    eventListener.onAuthTokenResponseBeforeTimeout();
                }

                if (authReponsePending) {
                    authReponsePending = false;
                    eventListener.onAuthTokenResponseBeforeError();
                }
            }
        }

        @Override
        public void close() {
            try {

                if (socket != null) {
                    socket.close();
                    socket = null;
                }

                loadCondition.close();

            } catch (Exception e) { }

            address = null;
            socketAddress = null;
        }

        // Loader.Callback implementation
        @Override
        public void onLoadCompleted(RtpAuthTokenSource loadable, long elapsedRealtimeMs,
                                    long loadDurationMs) {
            // Do nothing
        }

        @Override
        public void onLoadCanceled(RtpAuthTokenSource loadable, long elapsedRealtimeMs,
                                   long loadDurationMs, boolean released) {
            eventListener.onRtpAuthTokenSourceCanceled();
        }

        @Override
        public int onLoadError(RtpAuthTokenSource loadable, long elapsedRealtimeMs,
                               long loadDurationMs, IOException error) {
            eventListener.onRtpAuthTokenSourceError();
            return Loader.DONT_RETRY;
        }
    }

    abstract class RtpBurstSource implements RtpFeedbackTarget,
        RtpFeedbackTarget.BurstMessages,
        RtcpCompoundPacket.RtcpCompoundPacketEventListener,
        Loader.Loadable,
        Loader.Callback<RtpBurstSource> {

        private Uri uri;

        private byte[] inBuffer;
        private byte[] outBuffer;

        private DatagramSocket socket;
        private final DatagramPacket inPacket;
        private final DatagramPacket outPacket;

        private InetAddress address;
        private InetSocketAddress socketAddress;

        private final int socketTimeoutMillis;

        private volatile boolean loadCanceled = false;

        private final RtpFeedbackTargetSource.BurstEventListener eventListener;

        public RtpBurstSource(int socketTimeoutMillis,
                              RtpFeedbackTargetSource.BurstEventListener eventListener) {

            this.eventListener = eventListener;
            this.socketTimeoutMillis = socketTimeoutMillis;

            this.inBuffer = new byte[RtpDataSource.MTU_SIZE];
            this.outBuffer = new byte[RtpDataSource.MTU_SIZE];

            this.inPacket = new DatagramPacket(this.inBuffer, 0, RtpDataSource.MTU_SIZE);
            this.outPacket = new DatagramPacket(this.outBuffer, 0, RtpDataSource.MTU_SIZE);
        }

        protected void sendMessageFromBytes(byte[] bytes, int length) throws IOException {
            outPacket.setData(bytes, 0, length);
            socket.send(outPacket);
        }

        abstract public int getMaxBufferCapacity();

        abstract boolean isRapidAcquisitionResponse(RtcpFeedbackPacket packet);

        abstract boolean isRapidAcquisitionAccepted(RtcpFeedbackPacket packet);

        abstract boolean isAuthTokenRejected(RtcpTokenPacket packet);

        abstract protected boolean processRtpPacket(RtpPacket packet);

        @Override
        public void open(Uri uri) throws RtpFeedbackTargetException {
            this.uri = Assertions.checkNotNull(uri);

            String host = uri.getHost();
            int port = uri.getPort();

            try {

                address = InetAddress.getByName(host);
                socketAddress = new InetSocketAddress(address, port);

                socket = new DatagramSocket();
                socket.connect(socketAddress);

            } catch (IOException e) {
                throw new RtpFeedbackTargetException(e);
            }

            try {

                socket.setSoTimeout(socketTimeoutMillis);

            } catch (SocketException e) {
                throw new RtpFeedbackTargetException(e);
            }
        }

        @Override
        public Uri getUri() {
            return uri;
        }


        // RtcpCompoundPacketEventListener implementation
        @Override
        public void onSenderReportPacket(RtcpSrPacket packet) {
            // Do nothing
        }

        @Override
        public void onSourceDescriptionPacket(RtcpSdesPacket packet) {
            // Do nothing
        }

        @Override
        public void onRtpFeedbackPacket(RtcpFeedbackPacket packet) {
            if (isRapidAcquisitionResponse(packet)) {
                if (isRapidAcquisitionAccepted(packet)) {
                    eventListener.onBurstRapidAcquisitionAccepted();
                } else {
                    eventListener.onBurstRapidAcquisitionRejected();
                }
            }
        }

        @Override
        public void onTokenPacket(RtcpTokenPacket packet) {
            if (isAuthTokenRejected(packet)) {
                eventListener.onInvalidToken();
            }
        }

        private void handlePacket(byte[] buffer, int length)
                throws RtcpCompoundPacket.RtcpCompoundPacketException {
            try {

                RtpPacket rtpPacket = new RtpPacket();
                rtpPacket.fromBytes(buffer, length);

                if (processRtpPacket(rtpPacket)) {
                    eventListener.onRtpPacketBurstReceived(rtpPacket);
                }

            } catch (RtpPacket.RtpPacketException ex) {

                RtcpCompoundPacket packet = new RtcpCompoundPacket(this);
                packet.fromBytes(buffer, length);
            }
        }

        // Loader.Loadable implementation
        @Override
        public void cancelLoad() {
            loadCanceled = true;
        }

        @Override
        public boolean isLoadCanceled() {
            return loadCanceled;
        }

        @Override
        public void load() throws IOException, InterruptedException {
            while (!loadCanceled) {

                try {

                    if (!loadCanceled) {
                        socket.receive(inPacket);
                        handlePacket(inPacket.getData(), inPacket.getLength());
                    }

                } catch (SocketTimeoutException se) {
                    throw new SocketTimeoutException(se.getMessage());

                } catch (RtcpCompoundPacket.RtcpCompoundPacketException pe) {
                    // Do nothing
                }
            }
        }

        @Override
        public void close() {
            try {

                if (socket != null) {
                    socket.close();
                    socket = null;
                }

            } catch (Exception e) { }

            address = null;
            socketAddress = null;
        }

        // Loader.Callback implementation
        @Override
        public void onLoadCompleted(RtpBurstSource loadable, long elapsedRealtimeMs,
                                    long loadDurationMs) {
            // Do nothing
        }

        @Override
        public void onLoadCanceled(RtpBurstSource loadable, long elapsedRealtimeMs,
                                   long loadDurationMs, boolean released) {

            eventListener.onRtpBurstSourceCanceled();
        }

        @Override
        public int onLoadError(RtpBurstSource loadable, long elapsedRealtimeMs,
                               long loadDurationMs, IOException error) {

            eventListener.onRtpBurstSourceError();
            return Loader.DONT_RETRY;
        }
    }

    abstract class RtpRetransmissionSource implements RtpFeedbackTarget,
            RtpFeedbackTarget.RetransmissionMessages,
            RtcpCompoundPacket.RtcpCompoundPacketEventListener,
            Loader.Loadable,
            Loader.Callback<RtpRetransmissionSource> {

        private Uri uri;

        private byte[] inBuffer;
        private byte[] outBuffer;

        private DatagramSocket socket;
        private final DatagramPacket inPacket;
        private final DatagramPacket outPacket;

        private InetAddress address;
        private InetSocketAddress socketAddress;

        private volatile boolean loadCanceled = false;

        protected static final int BITMASK_LENGTH = 16;

        private final RtpFeedbackTargetSource.RetransmissionEventListener eventListener;

        public RtpRetransmissionSource(RtpFeedbackTargetSource.RetransmissionEventListener
                                               eventListener) {
            this.eventListener = eventListener;

            this.inBuffer = new byte[RtpDataSource.MTU_SIZE];
            this.outBuffer = new byte[RtpDataSource.MTU_SIZE];

            this.inPacket = new DatagramPacket(this.inBuffer, 0, RtpDataSource.MTU_SIZE);
            this.outPacket = new DatagramPacket(this.outBuffer, 0, RtpDataSource.MTU_SIZE);
        }

        @Override
        public void open(Uri uri) throws RtpFeedbackTargetException {
            this.uri = Assertions.checkNotNull(uri);

            String host = uri.getHost();
            int port = uri.getPort();

            try {

                address = InetAddress.getByName(host);
                socketAddress = new InetSocketAddress(address, port);

                socket = new DatagramSocket();
                socket.connect(socketAddress);

            } catch (IOException e) {
                throw new RtpFeedbackTargetException(e);
            }
        }

        @Override
        public Uri getUri() {
            return uri;
        }

        abstract public int getMaxBufferCapacity();

        abstract public void resetAllPacketsRecoveryPending(long timestamp);

        abstract public int getPacketsRecoveryPending();

        abstract public int getMaxPacketsRecoveryPending();

        abstract protected boolean processRtpPacket(RtpPacket packet);

        abstract boolean isAuthTokenRejected(RtcpTokenPacket packet);

        abstract public long getMaxDelayTimeForPending();

        protected void sendMessageFromBytes(byte[] bytes, int length) throws IOException {
            outPacket.setData(bytes, 0, length);
            socket.send(outPacket);
        }

        // RtcpCompoundPacketEventListener implementation
        @Override
        public void onSenderReportPacket(RtcpSrPacket packet) {
            // Do nothing
        }

        @Override
        public void onSourceDescriptionPacket(RtcpSdesPacket packet) {
            // Do nothing
        }

        @Override
        public void onRtpFeedbackPacket(RtcpFeedbackPacket packet) {
            // Do nothing
        }

        @Override
        public void onTokenPacket(RtcpTokenPacket packet) {
            if (isAuthTokenRejected(packet)) {
                eventListener.onInvalidToken();
            }
        }

        private void handlePacket(byte[] buffer, int length)
                throws RtcpCompoundPacket.RtcpCompoundPacketException {
            try {

                RtpPacket rtpPacket = new RtpPacket();
                rtpPacket.fromBytes(buffer, length);

                if (processRtpPacket(rtpPacket)) {
                    eventListener.onRtpPacketLossReceived(rtpPacket);
                }

            } catch (RtpPacket.RtpPacketException ex) {

                RtcpCompoundPacket packet = new RtcpCompoundPacket(this);
                packet.fromBytes(buffer, length);
            }
        }

        // Loader.Loadable implementation
        @Override
        public void cancelLoad() {
            loadCanceled = true;
        }

        @Override
        public boolean isLoadCanceled() {
            return loadCanceled;
        }

        @Override
        public void load() throws IOException, InterruptedException {
            while (!loadCanceled) {

                try {

                    if (!loadCanceled) {
                        socket.receive(inPacket);
                        handlePacket(inPacket.getData(), inPacket.getLength());
                    }

                } catch (RtcpCompoundPacket.RtcpCompoundPacketException e) {
                    // Do nothing
                }
            }
        }

        @Override
        public void close() {
            try {

                if (socket != null) {
                    socket.close();
                    socket = null;
                }

            } catch (Exception e) { }

            address = null;
            socketAddress = null;
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

    abstract class RtpDistributionSource implements Loader.Loadable,
            Loader.Callback<RtpDistributionSource> {
        private Uri uri;

        private byte[] inBuffer;

        private DatagramSocket socket;
        private MulticastSocket mcastSocket;
        private final DatagramPacket inPacket;

        private InetAddress address;
        private InetSocketAddress socketAddress;

        private volatile boolean loadCanceled = false;

        private final int socketTimeoutMillis;
        private final RtpDistributionEventListener eventListener;

        /**
         * Thrown when an error is encountered when trying to open/read from a
         * {@link RtpDistributionSource}.
         */
        final class RtpDistributionSourceException extends IOException {

            public RtpDistributionSourceException(IOException cause) {
                super(cause);
            }

        }

        public RtpDistributionSource(int socketTimeoutMillis,
                                     RtpDistributionEventListener eventListener) {
            this.eventListener = eventListener;
            this.socketTimeoutMillis = socketTimeoutMillis;

            this.inBuffer = new byte[RtpDataSource.MTU_SIZE];
            this.inPacket = new DatagramPacket(this.inBuffer, 0, RtpDataSource.MTU_SIZE);
        }

        public void open(Uri uri) throws RtpDistributionSourceException {
            this.uri = Assertions.checkNotNull(uri);

            String host = uri.getHost();
            int port = uri.getPort();

            try {

                address = InetAddress.getByName(host);
                socketAddress = new InetSocketAddress(address, port);

                if (address.isMulticastAddress()) {
                    mcastSocket = new MulticastSocket(socketAddress);
                    mcastSocket.joinGroup(address);
                    socket = mcastSocket;
                } else {
                    socket = new DatagramSocket();
                    socket.connect(socketAddress);
                }

            } catch (IOException e) {
                throw new RtpDistributionSourceException(e);
            }

            try {

                socket.setSoTimeout(socketTimeoutMillis);

            } catch (SocketException e) {
                throw new RtpDistributionSourceException(e);
            }
        }

        abstract public int getMaxBufferCapacity();

        abstract protected boolean processRtpPacket(RtpPacket packet);

        public Uri getUri() {
            return uri;
        }

        private void handlePacket(byte[] buffer, int length) throws RtpPacket.RtpPacketException {
            RtpPacket rtpPacket = new RtpPacket();
            rtpPacket.fromBytes(buffer, length);

            if (processRtpPacket(rtpPacket)) {
                eventListener.onRtpPacketReceived(rtpPacket);
            }
        }

        // Loader.Loadable implementation
        @Override
        public final void cancelLoad() {
            loadCanceled = true;
        }

        @Override
        public final boolean isLoadCanceled() {
            return loadCanceled;
        }

        @Override
        public final void load() throws IOException, InterruptedException {
            while (!loadCanceled) {

                try {

                    if (!loadCanceled) {
                        socket.receive(inPacket);
                        handlePacket(inPacket.getData(), inPacket.getLength());
                    }

                } catch (SocketTimeoutException se) {
                    throw new SocketTimeoutException(se.getMessage());

                } catch (RtpPacket.RtpPacketException e) {
                    // Do nothing
                }
            }
        }

        public void close() {
            try {

                if (socket != null) {
                    socket.close();
                    socket = null;
                }

            } catch (Exception e) { }

            address = null;
            socketAddress = null;
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
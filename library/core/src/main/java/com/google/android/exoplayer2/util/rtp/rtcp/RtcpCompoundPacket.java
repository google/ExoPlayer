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
 * This class wraps a RTCP compound packet providing method to convert from a byte array and obtain
 * the RTCP packets are composed
 *
 */
public class RtcpCompoundPacket {

    public interface RtcpCompoundPacketEventListener {
        /**
         * Called when a Sender Report packet has been found while parsing.
         */
        void onSenderReportPacket(RtcpSrPacket packet);

        /**
         * Called when a Source Description packet has been found while parsing.
         */
        void onSourceDescriptionPacket(RtcpSdesPacket packet);

        /**
         * Called when a Generic RTP Feedback packet has been found while parsing.
         */
        void onRtpFeedbackPacket(RtcpFeedbackPacket packet);

        /**
         * Called when a TOKEN packet has been found while parsing.
         */
        void onTokenPacket(RtcpTokenPacket packet);
    }

    private RtcpPacket packets[];

    private final RtcpCompoundPacketEventListener eventListener;

    /**
     * Thrown when an error is encountered when trying to decode a {@link RtcpCompoundPacket}.
     */
    public static final class RtcpCompoundPacketException extends Exception {

        public RtcpCompoundPacketException(String message) {
            super(message);
        }

        public RtcpCompoundPacketException(Throwable cause) {
            super(cause);
        }

        public RtcpCompoundPacketException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public RtcpCompoundPacket(RtcpCompoundPacketEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public RtcpPacket[] getPackets() {
        return packets;
    }

    // Decode a RTCP compound packet from bytes
    public void fromBytes(byte[] buffer, int length) throws RtcpCompoundPacketException {
        // TODO the implementation (invoking listener for each rtcp compound event
    }
}


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
package com.google.android.exoplayer2.source.rtp.upstream;

import com.google.android.exoplayer2.source.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSrPacket;

import java.util.concurrent.CopyOnWriteArraySet;

public final class RtcpInputReportDispatcher {

    private final CopyOnWriteArraySet<RtcpReportReceiver.EventListener> listeners;

    private boolean opened;

    public RtcpInputReportDispatcher() {
        listeners = new CopyOnWriteArraySet<>();
    }

    public void open() {
        opened = true;
    }

    public void dispatch(RtcpPacket rtcpPacket) {
        if (opened) {
            if (rtcpPacket != null) {
                @RtcpPacket.PacketType int packetType = rtcpPacket.getPayloadType();

                switch (packetType) {
                    case RtcpPacket.SR:
                        handleSenderReport((RtcpSrPacket) rtcpPacket);
                        break;

                    case RtcpPacket.COMPOUND:
                        RtcpCompoundPacket compoundPacket = (RtcpCompoundPacket) rtcpPacket;

                        for (RtcpPacket simpleRtcpPacket : compoundPacket.getPackets()) {
                            switch (simpleRtcpPacket.getPayloadType()) {
                                case RtcpPacket.SR:
                                    handleSenderReport((RtcpSrPacket) simpleRtcpPacket);
                                    break;

                                case RtcpPacket.SDES:
                                    handleSourceDescription((RtcpSdesPacket) simpleRtcpPacket);
                                    break;
                            }
                        }

                        break;
                }
            }
        }
    }

    public void close() {
        if (opened) {
            opened = false;
        }
    }

    public void addListener(RtcpReportReceiver.EventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RtcpReportReceiver.EventListener listener) {
        listeners.remove(listener);
    }

    private void handleSenderReport(RtcpSrPacket packet) {
        for (RtcpReportReceiver.EventListener listener : listeners) {
            listener.onSenderReport(packet);
        }
    }

    private void handleSourceDescription(RtcpSdesPacket packet) {
        for (RtcpReportReceiver.EventListener listener : listeners) {
            listener.onSourceDescription(packet);
        }
    }
}

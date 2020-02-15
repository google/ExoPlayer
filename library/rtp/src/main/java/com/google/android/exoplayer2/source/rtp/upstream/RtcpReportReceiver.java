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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.google.android.exoplayer2.source.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSrPacket;
import com.google.android.exoplayer2.upstream.UdpDataSource;
import java.io.IOException;

/* package */ final class RtcpReportReceiver {

    public interface EventListener {
        void onSenderReport(RtcpSrPacket srPacket);
        void onSourceDescription(RtcpSdesPacket sdesPacket);
    }

    private boolean enabled;
    private boolean canceled;

    private final Handler handler;
    private final HandlerThread thread;

    private final byte[] packetBuffer;
    private final EventListener listener;
    private final UdpDataSource dataSource;

    public RtcpReportReceiver(UdpDataSource dataSource, EventListener listener) {
        this.dataSource = dataSource;
        this.listener = listener;

        packetBuffer = new byte[UdpDataSource.DEFAULT_PACKET_SIZE];

        thread = new HandlerThread("RtcpReportReceiver:HandlerThread",
                Process.THREAD_PRIORITY_AUDIO);
        thread.start();

        handler = new Handler(thread.getLooper());
    }

    public void start() {
        if (!enabled && !canceled) {
            enabled = true;
            handler.post(loader);
        }
    }

    public void stop() {
        if (!canceled) {
            thread.quit();
            canceled = true;

            if (enabled) {
                enabled = false;
            }
        }
    }

    private Runnable loader = new Runnable() {
        @Override
        public void run() {
            try {

                while (!Thread.currentThread().isInterrupted() && enabled) {
                    try {
                        int bytesRead = dataSource.read(packetBuffer, 0,
                                UdpDataSource.DEFAULT_PACKET_SIZE);

                        RtcpPacket rtcpPacket = RtcpPacket.parse(packetBuffer, bytesRead);

                        if (rtcpPacket != null) {
                            @RtcpPacket.PacketType int packetType = rtcpPacket.getPayloadType();

                            switch (packetType) {
                                case RtcpPacket.SR:
                                    listener.onSenderReport((RtcpSrPacket) rtcpPacket);
                                    break;

                                case RtcpPacket.COMPOUND:
                                    RtcpCompoundPacket compoundPacket = (RtcpCompoundPacket)rtcpPacket;

                                    for (RtcpPacket packet : compoundPacket.getPackets()) {
                                        switch (packet.getPayloadType()) {
                                            case RtcpPacket.SR:
                                                listener.onSenderReport((RtcpSrPacket) packet);
                                                break;

                                            case RtcpPacket.SDES:
                                                listener.onSourceDescription((RtcpSdesPacket) packet);
                                                break;
                                        }
                                    }

                                    break;
                            }
                        }

                    } catch (IOException ex) {
                        enabled = false;
                    }
                }

            } finally {
                handler.removeCallbacksAndMessages(this);
            }
        }
    };
}

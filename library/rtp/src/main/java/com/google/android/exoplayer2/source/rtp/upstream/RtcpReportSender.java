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

import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.upstream.UdpDataSinkSource;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/* package */ class RtcpReportSender {
    public interface EventListener {
        void onLastReportSent();
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final UdpDataSinkSource dataSink;
    private final EventListener listener;

    public RtcpReportSender(UdpDataSinkSource dataSink, EventListener listener) {
        this.dataSink = dataSink;
        this.listener = listener;
    }

    public void cancel() {
        executorService.shutdown();
    }

    public synchronized void send(final RtcpPacket packet, final boolean isLast) {
        try {
            executorService.execute(new Runnable() {
                @Override
                public void run() {

                    try {

                        if (packet != null && dataSink != null) {
                            byte[] bytes = packet.getBytes();
                            dataSink.write(bytes, 0, bytes.length);
                        }

                    } catch (IOException ex) {

                    }
                }
            });

        } catch (RejectedExecutionException ex) {

        } finally {
            if (isLast) {
                listener.onLastReportSent();
            }
        }
    }

    public synchronized void sendTo(final RtcpPacket packet, final InetAddress address,
                                     final int port) {
        try {
            executorService.execute(new Runnable() {
                @Override
                public void run() {

                    try {

                        if (packet != null && dataSink != null) {
                            byte[] bytes = packet.getBytes();
                            dataSink.writeTo(bytes, 0, bytes.length, address, port);
                        }

                    } catch (IOException ex) {
                    }
                }
            });

        } catch (RejectedExecutionException ex) {

        }
    }
}

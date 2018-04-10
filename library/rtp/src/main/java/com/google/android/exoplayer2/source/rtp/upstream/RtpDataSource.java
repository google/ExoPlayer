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

import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSinkSource;
import com.google.android.exoplayer2.upstream.UdpDataSource;

public final class RtpDataSource extends UdpDataSinkSource {
    private final byte[] packetBuffer;
    private final RtpSamplesQueue samplesQueue;

    public RtpDataSource(int clockrate, TransferListener<? super UdpDataSource> listener) {
        this(clockrate, listener, RtpPacket.MAX_PACKET_SIZE);
    }

    public RtpDataSource(int clockrate, TransferListener<? super UdpDataSource> listener,
                         int maxPacketSize) {
        super(listener, maxPacketSize);

        packetBuffer = new byte[maxPacketSize];
        samplesQueue = new RtpSamplesQueue(clockrate);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws UdpDataSourceException {
        int bytesRead = super.read(packetBuffer, 0, RtpPacket.MAX_PACKET_SIZE);

        if (bytesRead > 0) {

            RtpPacket packet = RtpPacket.parse(packetBuffer, bytesRead);

            if (packet != null) {
                samplesQueue.offer(packet);
                packet = samplesQueue.pop();

                if (packet != null) {
                    byte[] bytes = packet.toBytes();
                    System.arraycopy(bytes, 0, buffer, offset, bytes.length);
                    return bytes.length;
                }

                return 0;

            } else {
                System.arraycopy(packetBuffer, 0, buffer, offset, bytesRead);
            }
        }

        return bytesRead;
    }

    @Override
    public void close() {
        super.close();
        samplesQueue.clear();
    }
}

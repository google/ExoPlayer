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

import android.support.annotation.IntDef;

import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSrPacket;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSinkSource;
import com.google.android.exoplayer2.upstream.UdpDataSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;

public final class RtpDataSource extends UdpDataSinkSource {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {FLAG_ENABLE_RTCP_FEEDBACK, FLAG_FORCE_RTCP_MULTIPLEXING})
    public @interface Flags {}
    public static final int FLAG_ENABLE_RTCP_FEEDBACK = 1;
    public static final int FLAG_FORCE_RTCP_MULTIPLEXING = 1 << 1;

    private final @Flags int flags;
    private final byte[] packetBuffer;

    private final RtpSamplesQueue samplesQueue;

    private RtpStatistics statistics;
    private RtcpFeedbackSource feedbackSource;

    public RtpDataSource(int clockrate) {
        this(clockrate, 0, null, RtpPacket.MAX_PACKET_SIZE);
    }

    public RtpDataSource(int clockrate, @Flags int flags) {
        this(clockrate, flags, null, RtpPacket.MAX_PACKET_SIZE);
    }

    public RtpDataSource(int clockrate, @Flags int flags,
                         TransferListener<? super UdpDataSource> listener) {
        this(clockrate, flags, listener, RtpPacket.MAX_PACKET_SIZE);
    }

    public RtpDataSource(int clockrate, @Flags int flags,
                         TransferListener<? super UdpDataSource> listener, int maxPacketSize) {
        super(listener, maxPacketSize);

        this.flags = flags;

        packetBuffer = new byte[maxPacketSize];
        samplesQueue = new RtpSamplesQueue(clockrate);

        if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
            statistics = new RtpStatistics();

            if (isSet(FLAG_FORCE_RTCP_MULTIPLEXING)) {
                feedbackSource = new RtcpFeedbackSource.Builder(statistics)
                        .setExternalSource(this)
                        .build();
            } else {
                feedbackSource = new RtcpFeedbackSource.Builder(statistics)
                        .build();
            }
        }
    }

    public void setSsrc(long ssrc) {
        if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
            feedbackSource.setRemoteSsrc(ssrc);
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws UdpDataSourceException {
        long nbytes = super.open(dataSpec);

        if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
            if (isSet(FLAG_FORCE_RTCP_MULTIPLEXING)) {
                feedbackSource.open();
            } else {
                feedbackSource.open(dataSpec.uri.getHost(), getLocalPort() + 1, dataSpec.flags);
            }
        }

        return nbytes;
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

                    if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
                        if (feedbackSource.getRemoteSsrc() == Long.MIN_VALUE) {
                            feedbackSource.setRemoteSsrc(packet.ssrc());
                        }

                        statistics.update(samplesQueue.getStatsInfo());
                    }

                    byte[] bytes = packet.getBytes();
                    System.arraycopy(bytes, 0, buffer, offset, bytes.length);
                    return bytes.length;
                }

                return 0;

            } else {

                if (isSet(FLAG_ENABLE_RTCP_FEEDBACK | FLAG_FORCE_RTCP_MULTIPLEXING)) {
                    RtcpPacket rtcpPacket = RtcpPacket.parse(packetBuffer, bytesRead);

                    if (rtcpPacket != null) {
                        @RtcpPacket.PacketType int packetType = rtcpPacket.getPayloadType();

                        switch (packetType) {
                            case RtcpPacket.SR:
                                feedbackSource.onSenderReport((RtcpSrPacket) rtcpPacket);
                                break;

                            case RtcpPacket.COMPOUND:
                                RtcpCompoundPacket compoundPacket = (RtcpCompoundPacket)rtcpPacket;

                                for (RtcpPacket simpleRtcpPacket : compoundPacket.getPackets()) {
                                    switch (simpleRtcpPacket.getPayloadType()) {
                                        case RtcpPacket.SR:
                                            feedbackSource.onSenderReport(
                                                    (RtcpSrPacket) simpleRtcpPacket);
                                            break;

                                        case RtcpPacket.SDES:
                                            feedbackSource.onSourceDescription(
                                                    (RtcpSdesPacket) simpleRtcpPacket);
                                            break;
                                    }
                                }

                                break;
                        }
                    }
                }

                return 0;
            }
        }

        return bytesRead;
    }

    public void writeTo(RtpPacket packet, InetAddress address, int port)
            throws UdpDataSourceException {
        byte[] bytes = packet.getBytes();
        super.writeTo(bytes, 0, bytes.length, address, port);
    }

    public void writeTo(RtcpPacket packet, InetAddress address, int port)
            throws UdpDataSourceException {
        if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
            feedbackSource.sendTo(packet, address, port);
        }
    }

    @Override
    public void close() {
        if (isSet(FLAG_ENABLE_RTCP_FEEDBACK)) {
            feedbackSource.close();
            statistics.clear();
        }

        samplesQueue.clear();
        super.close();
    }

    private boolean isSet(@Flags int flag) {
        return (flags & flag) != 0;
    }
}

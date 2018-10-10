package com.google.android.exoplayer2.source.rtp.upstream;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSrPacket;

import java.util.concurrent.CopyOnWriteArraySet;

public final class RtcpIncomingReportSink {

    private final byte[] packetBuffer;
    private final CopyOnWriteArraySet<RtcpReportReceiver.EventListener> listeners;

    private boolean opened;

    public RtcpIncomingReportSink() {
        listeners = new CopyOnWriteArraySet<>();
        packetBuffer = new byte[RtpPacket.MAX_PACKET_SIZE];
    }

    public void open() {
        opened = true;
    }

    public int write(byte[] buffer, int offset, int length) {
        if (opened) {
            System.arraycopy(buffer, offset, packetBuffer, 0, length);

            RtcpPacket rtcpPacket = RtcpPacket.parse(packetBuffer, length);

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

                return length;
            }

            return 0;
        }

        return C.LENGTH_UNSET;
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

package com.google.android.exoplayer2.source.rtp.upstream;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;

import java.util.concurrent.CopyOnWriteArraySet;

public final class RtcpOutgoingReportSink {

    public interface EventListener {
        void onOutgoingReport(RtcpPacket packet);
    }

    private final byte[] packetBuffer;
    private final CopyOnWriteArraySet<EventListener> listeners;

    private boolean opened;

    public RtcpOutgoingReportSink() {
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
                handleOutgoingReport(rtcpPacket);
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

    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    private void handleOutgoingReport(RtcpPacket packet) {
        for (EventListener listener : listeners) {
            listener.onOutgoingReport(packet);
        }
    }

}

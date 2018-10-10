package com.google.android.exoplayer2.source.rtp.upstream;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;

import java.io.IOException;

public final class RtpInternalSamplesSink {

    private final byte[] packetBuffer;
    private RtpSamplesQueue samplesQueue;

    private boolean opened;

    public RtpInternalSamplesSink() {
        packetBuffer = new byte[RtpPacket.MAX_PACKET_SIZE];
    }

    public void open(int clockrate) throws IOException {
        samplesQueue = new RtpSamplesQueue(clockrate);
        opened = true;
    }

    public int write(byte[] buffer, int offset, int length) {
        if (opened) {
            System.arraycopy(buffer, offset, packetBuffer, 0, length);

            RtpPacket packet = RtpPacket.parse(packetBuffer, length);

            if (packet != null) {
                samplesQueue.offer(packet);
                return length;
            }

            return 0;
        }

        return C.LENGTH_UNSET;
    }

    public void close() {
        if (opened) {
            samplesQueue.clear();
            opened = false;
        }
    }

    public RtpSamplesQueue samples() {
        return samplesQueue;
    }
}

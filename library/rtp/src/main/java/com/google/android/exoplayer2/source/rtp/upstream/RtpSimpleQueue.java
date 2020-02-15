package com.google.android.exoplayer2.source.rtp.upstream;

import com.google.android.exoplayer2.source.rtp.RtpPacket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/* package */ final class RtpSimpleQueue extends RtpQueue {

    private final Queue<RtpPacket> packets;

    public RtpSimpleQueue(int clockrate) {
        super(clockrate);

        packets = new ConcurrentLinkedQueue<>();
    }

    @Override
    public synchronized void offer(RtpPacket packet) {
        int sequence = packet.sequenceNumber();

        calculateJitter(packet.timestamp());

        if (!isStarted) {
            stats.maxSequence = sequence - 1;
            stats.baseSequence = sequence;

            isStarted = true;
        }

        int expected = (stats.maxSequence + 1) % RTP_SEQ_MOD;
        int sequenceDelta = sequence - expected;

        if (sequenceDelta >= 0 && sequenceDelta < MAX_DROPOUT) {
            packets.offer(packet);
            stats.maxSequence = sequence;
        }

        if (expected < stats.maxSequence) {
            stats.cycles++;
        }

        stats.received++;
    }

    @Override
    public synchronized RtpPacket pop() {
        if (packets.size() > 1) {
            RtpPacket packet = packets.poll();
            stats.baseSequence = packet.sequenceNumber();
            return packet;
        }

        return null;
    }

    @Override
    public synchronized void clear() {
        packets.clear();
    }

}

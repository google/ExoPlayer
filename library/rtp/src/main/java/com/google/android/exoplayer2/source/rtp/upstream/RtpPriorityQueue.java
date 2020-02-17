/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;

import java.util.Iterator;
import java.util.TreeSet;

/* package */ final class RtpPriorityQueue extends RtpQueue {

    class RtpPriorityPacket implements Comparable<RtpPriorityPacket> {
        private RtpPacket packet;
        private long timestamp;

        public RtpPriorityPacket(RtpPacket packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }

        public RtpPacket packet() {
            return packet;
        }

        public long timestamp() {
            return timestamp;
        }

        public int compareTo(RtpPriorityPacket sample) {
            final int LESS = -1;
            final int EQUAL = 0;
            final int GREATER = 1;

            int sequenceSample = sample.packet.sequenceNumber();
            int sequenceDelta = packet.sequenceNumber() - sequenceSample;

            if (sequenceDelta >= 0 && sequenceDelta < MAX_DROPOUT) {
                if (sequenceDelta == 0) {
                    return EQUAL;
                } else {
                    return GREATER;
                }
            } else if (sequenceDelta < -MAX_MISORDER || sequenceDelta >= MAX_DROPOUT) {
                if (-sequenceDelta == sequenceSample) {
                    return GREATER;
                } else if (sequenceDelta < 0 && -sequenceDelta < sequenceSample) {
                    return GREATER;
                }
            }

            return LESS;
        }
    }

    private static final int MAX_MISORDER = 100;

    private volatile int lastSequence; // last sequence number pop

    private final long maxDelay;
    private final TreeSet<RtpPriorityPacket> packets;

    public RtpPriorityQueue(int clockrate, long maxDelayMs) {
        super(clockrate);
        maxDelay = maxDelayMs;

        packets = new TreeSet<>();
    }

    @Override
    public synchronized void offer(RtpPacket packet) {
        int sequence = packet.sequenceNumber();

        calculateJitter(packet.timestamp());

        if (!isStarted) {
            stats.baseSequence = sequence;
            stats.maxSequence = sequence - 1;
            lastSequence = stats.maxSequence;

            isStarted = true;
        }

        int expected = (stats.maxSequence + 1) % RTP_SEQ_MOD;
        int sequenceDelta = sequence - expected;

        if (sequenceDelta >= 0 && sequenceDelta < MAX_DROPOUT) {
            stats.maxSequence = sequence;
            packets.add(new RtpPriorityPacket(packet, arrivalTimestamp));

        } else if (sequenceDelta < -MAX_MISORDER || sequenceDelta >= MAX_DROPOUT) {
            /* the sequence number made a very large jump */
            if (sequence < stats.baseSequence) {
                packets.add(new RtpPriorityPacket(packet, arrivalTimestamp));

            } else if (-sequenceDelta >= expected) {
                if (expected < stats.maxSequence) {
                    stats.cycles++;
                }
                stats.maxSequence = sequence;
                packets.add(new RtpPriorityPacket(packet, arrivalTimestamp));

            } else {
                if (sequenceDelta >= MAX_DROPOUT) {
                    packets.clear();
                    stats.maxSequence = sequence;
                    stats.baseSequence = sequence;
                    lastSequence = sequence;
                }
                packets.add(new RtpPriorityPacket(packet, arrivalTimestamp));
            }

        } else { /* delta < 0 && delta >= -MAX_MISORDER */
            if (sequence > lastSequence) {
                stats.maxSequence = sequence;
                packets.add(new RtpPriorityPacket(packet, arrivalTimestamp));

            } else {
                // Do nothing.
                return;
            }
        }

        stats.received++;
    }

    @Override
    public synchronized RtpPacket pop() {
        if (isStarted && packets.size() > 1) {
            long nowTimestamp = System.currentTimeMillis();

            Iterator<RtpPriorityPacket> iteratorSamples = packets.iterator();

            RtpPriorityPacket sample = iteratorSamples.next();
            RtpPacket packet = sample.packet();

            RtpPriorityPacket higher = iteratorSamples.next();
            RtpPacket expected = higher.packet();

            int sequenceDelta = ((packet.sequenceNumber() + 1) % RTP_SEQ_MOD) -
                    expected.sequenceNumber();

            if (sequenceDelta == 0) {
                stats.baseSequence = packet.sequenceNumber();
                lastSequence = stats.baseSequence;
                packets.pollFirst();
                return packet;
            }

            long deadlineTimestamp = 0;

            if (stats.jitter > 0 && clockrate > 0) {
                deadlineTimestamp = (C.MICROS_PER_SECOND * 3 * stats.jitter) / clockrate;
            }

            // Make sure we wait at least for min delay established
            if (deadlineTimestamp < maxDelay) {
                deadlineTimestamp = maxDelay;
            }

            deadlineTimestamp /= 1000;
            deadlineTimestamp += sample.timestamp();

            if (nowTimestamp >= deadlineTimestamp) {
                packets.pollFirst();
                // Discontinuity detection
                int deltaSequence = packet.sequenceNumber() - ((stats.baseSequence + 1) % RTP_SEQ_MOD);
                if (deltaSequence != 0 && deltaSequence >= 0x8000) {
                    // Trash too late packets
                    stats.baseSequence = expected.sequenceNumber();
                    lastSequence = stats.baseSequence;
                    return null;
                }

                stats.baseSequence = packet.sequenceNumber();
                lastSequence = stats.baseSequence;
                return packet;
            }
        }

        return null;
    }

    @Override
    public synchronized void clear() {
        packets.clear();
    }
}

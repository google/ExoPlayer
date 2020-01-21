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

/* package */ final class RtpSamplesPriorityQueue {

    class RtpSample implements Comparable<RtpSample> {
        private RtpPacket packet;
        private long timestamp;

        public RtpSample(RtpPacket packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }

        public RtpPacket packet() {
            return packet;
        }

        public long timestamp() {
            return timestamp;
        }

        public int compareTo(RtpSample sample) {
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

    private static final int MAX_DROPOUT = 3000;
    private static final int MAX_MISORDER = 100;
    private static final int RTP_SEQ_MOD = 1<<16;

    private volatile int lastSequence; // last sequence number pop

    private long lastArrivalTimestamp;
    private long lastSentTimestamp;

    private boolean isStarted;

    private final int clockrate;
    private final TreeSet<RtpSample> samples;
    private final RtpStatistics.RtpStatsInfo statsInfo;

    public RtpSamplesPriorityQueue(int clockrate) {
        this.clockrate = clockrate;

        samples = new TreeSet<>();
        statsInfo = new RtpStatistics.RtpStatsInfo();
    }

    public synchronized void offer(RtpPacket packet) {
        int sequence = packet.sequenceNumber();
        long sentTimestamp = packet.timestamp();
        long arrivalTimestamp = System.currentTimeMillis();

        if (!isStarted) {
            statsInfo.baseSequence = sequence;
            statsInfo.maxSequence = sequence - 1;
            lastSequence = statsInfo.maxSequence;

            isStarted = true;

        } else {
            long timestampDelta = ((arrivalTimestamp - lastArrivalTimestamp) * clockrate) / C.MICROS_PER_SECOND;
            timestampDelta -= sentTimestamp - lastSentTimestamp;

            if (timestampDelta < 0) {
                timestampDelta = -timestampDelta;
            }

            statsInfo.jitter += ((timestampDelta - statsInfo.jitter) + 8) >> 4;
        }

        lastSentTimestamp = sentTimestamp;
        lastArrivalTimestamp = arrivalTimestamp;

        int expected = (statsInfo.maxSequence + 1) % RTP_SEQ_MOD;
        int sequenceDelta = sequence - expected;

        if (sequenceDelta >= 0 && sequenceDelta < MAX_DROPOUT) {
            statsInfo.maxSequence = sequence;
            samples.add(new RtpSample(packet, arrivalTimestamp));

        } else if (sequenceDelta < -MAX_MISORDER || sequenceDelta >= MAX_DROPOUT) {
			/* the sequence number made a very large jump */
            if (sequence < statsInfo.baseSequence) {
                samples.add(new RtpSample(packet, arrivalTimestamp));

            } else if (-sequenceDelta >= expected) {
                if (expected < statsInfo.maxSequence) {
                    statsInfo.cycles++;
                }
                statsInfo.maxSequence = sequence;
                samples.add(new RtpSample(packet, arrivalTimestamp));

            } else {
                if (sequenceDelta >= MAX_DROPOUT) {
                    samples.clear();
                    statsInfo.maxSequence = sequence;
                    statsInfo.baseSequence = sequence;
                    lastSequence = sequence;
                }
                samples.add(new RtpSample(packet, arrivalTimestamp));
            }

        } else { /* delta < 0 && delta >= -MAX_MISORDER */
            if (sequence > lastSequence) {
                statsInfo.maxSequence = sequence;
                samples.add(new RtpSample(packet, arrivalTimestamp));

            } else {
                // Do nothing.
                return;
            }
        }

        statsInfo.received++;
    }

    public synchronized RtpPacket pop() {
        if (isStarted && samples.size() > 1) {
            long nowTimestamp = System.currentTimeMillis();

            Iterator<RtpSample> iteratorSamples = samples.iterator();

            RtpSample sample = iteratorSamples.next();
            RtpPacket packet = sample.packet();

            RtpSample higher = iteratorSamples.next();
            RtpPacket expected = higher.packet();

            int sequenceDelta = ((packet.sequenceNumber() + 1) % RTP_SEQ_MOD) - expected.sequenceNumber();

            if (sequenceDelta == 0) {
                statsInfo.baseSequence = packet.sequenceNumber();
                lastSequence = statsInfo.baseSequence;
                samples.pollFirst();
                return packet;
            }

            long deadlineTimestamp = 0;

            if (statsInfo.jitter > 0 && clockrate > 0) {
                deadlineTimestamp = (C.MICROS_PER_SECOND * 3 * statsInfo.jitter) / clockrate;
            }

            // Make sure we wait at least for 25 msec
            if (deadlineTimestamp < (C.MICROS_PER_SECOND / 40)) {
                deadlineTimestamp = C.MICROS_PER_SECOND / 40;
            }

            deadlineTimestamp /= 1000;
            deadlineTimestamp += sample.timestamp();

            if (nowTimestamp >= deadlineTimestamp) {
                samples.pollFirst();
                // Discontinuity detection
                int deltaSequence = packet.sequenceNumber() - ((statsInfo.baseSequence + 1) % RTP_SEQ_MOD);
                if (deltaSequence != 0 && deltaSequence >= 0x8000) {
                    // Trash too late packets
                    statsInfo.baseSequence = expected.sequenceNumber();
                    lastSequence = statsInfo.baseSequence;
                    return null;
                }

                statsInfo.baseSequence = packet.sequenceNumber();
                lastSequence = statsInfo.baseSequence;
                return packet;
            }
        }

        return null;
    }

    public synchronized void clear() {
        samples.clear();
    }

    public synchronized RtpStatistics.RtpStatsInfo getStatsInfo() {
        return new RtpStatistics.RtpStatsInfo(statsInfo);
    }
}

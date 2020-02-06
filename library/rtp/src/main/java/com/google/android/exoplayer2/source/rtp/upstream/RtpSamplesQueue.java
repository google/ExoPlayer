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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/* package */ final class RtpSamplesQueue {
    private static final int RTP_SEQ_MOD = 1<<16;

    private long lastArrivalTimestamp;
    private long lastSentTimestamp;

    private boolean isStarted;

    private final int clockrate;
    private final Queue<RtpPacket> samples;
    private final RtpStatistics.RtpStatsInfo statsInfo;

    public RtpSamplesQueue(int clockrate) {
        this.clockrate = clockrate;

        samples = new ConcurrentLinkedQueue<>();
        statsInfo = new RtpStatistics.RtpStatsInfo();
    }

    public synchronized void offer(RtpPacket packet) {
        int sequence = packet.sequenceNumber();
        long sentTimestamp = packet.timestamp();
        long arrivalTimestamp = System.currentTimeMillis();

        if (!isStarted) {
            statsInfo.baseSequence = sequence;
            statsInfo.maxSequence = sequence - 1;

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
        if (expected < statsInfo.maxSequence) {
            statsInfo.cycles++;
        }

        statsInfo.maxSequence = sequence;
        samples.add(packet);

        statsInfo.received++;
    }

    public synchronized RtpPacket pop() {
        if (isStarted && samples.size() > 0) {
            long nowTimestamp = System.currentTimeMillis();

            RtpPacket packet = samples.poll();
            statsInfo.baseSequence = packet.sequenceNumber();

            long deadlineTimestamp = 0;

            if (statsInfo.jitter > 0 && clockrate > 0) {
                deadlineTimestamp = (C.MICROS_PER_SECOND * 3 * statsInfo.jitter) / clockrate;
            }

            // Make sure we wait at least for 25 msec
            if (deadlineTimestamp < (C.MICROS_PER_SECOND / 40)) {
                deadlineTimestamp = C.MICROS_PER_SECOND / 40;
            }

            deadlineTimestamp /= 1000;
            deadlineTimestamp += packet.timestamp();

            if (nowTimestamp >= deadlineTimestamp) {
                //samples.pollFirst();
                // Discontinuity detection
                int deltaSequence = packet.sequenceNumber() - ((statsInfo.baseSequence + 1) % RTP_SEQ_MOD);
                if (deltaSequence != 0 && deltaSequence >= 0x8000) {
                    // Trash too late packets
                    return null;
                }

                statsInfo.baseSequence = packet.sequenceNumber();
                return packet;
            }


            return packet;
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

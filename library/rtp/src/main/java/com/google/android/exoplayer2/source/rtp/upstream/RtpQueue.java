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

/* package */ abstract class RtpQueue {

    static final int MAX_DROPOUT = 3000;
    static final int RTP_SEQ_MOD = 1<<16;

    boolean isStarted;
    long arrivalTimestamp;

    private long lastSentTimestamp;
    private long lastArrivalTimestamp;

    final int clockrate;
    final RtpStats stats;

    RtpQueue(int clockrate) {
        this.clockrate = clockrate;

        stats = new RtpStats();
    }

    void calculateJitter(long sentTimestamp) {
        long arrivalTimestamp = System.currentTimeMillis();

        if (isStarted) {
            long timestampDelta = ((arrivalTimestamp - lastArrivalTimestamp) * clockrate) /
                C.MICROS_PER_SECOND;
            timestampDelta -= sentTimestamp - lastSentTimestamp;

            if (timestampDelta < 0) {
                timestampDelta = -timestampDelta;
            }

            stats.jitter += ((timestampDelta - stats.jitter) + 8) >> 4;
        }

        lastSentTimestamp = sentTimestamp;
        lastArrivalTimestamp = arrivalTimestamp;
    }

    public abstract void offer(RtpPacket packet);

    public abstract RtpPacket pop();

    public abstract void clear();

    final synchronized RtpStats getStats() {
        return stats.clone();
    }
}

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


import androidx.annotation.Nullable;

/* package */ final class RtpStatistics {

    public static final class RtpStatsInfo {
        /**
         * The base sequence number.
         */
        public int baseSequence;

        /**
         * The highest sequence number seen.
         */
        public int maxSequence;

        /**
         * The shifted count of sequence number cycles.
         */
        public int cycles;

        /**
         * The packets received.
         */
        public int received;

        /**
         * The estimated jitter.
         */
        public int jitter;


        public RtpStatsInfo() { }

        public RtpStatsInfo(RtpStatsInfo statsInfo) {
            this.baseSequence = statsInfo.baseSequence;
            this.maxSequence = statsInfo.maxSequence;
            this.cycles = statsInfo.cycles;
            this.jitter = statsInfo.jitter;
            this.received = statsInfo.received;
        }
    }

    private RtpStatsInfo statsInfo;

    public synchronized void clear() {
        statsInfo = null;
    }

    public synchronized void update(RtpStatsInfo statsInfo) {
        this.statsInfo = statsInfo;
    }

    @Nullable
    public synchronized RtpStatsInfo getStatsInfo() {
        if (statsInfo != null) {
            return new RtpStatsInfo(statsInfo);
        }

        return null;
    }

}

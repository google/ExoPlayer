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


/* package */ final class RtpStats {

    /**
     * The base sequence number.
     */
    int baseSequence;

    /**
     * The highest sequence number seen.
     */
    int maxSequence;

    /**
     * The shifted count of sequence number cycles.
     */
    int cycles;

    /**
     * The packets received.
     */
    int received;

    /**
     * The estimated jitter.
     */
    int jitter;

    RtpStats() {

    }

    private RtpStats(RtpStats stats) {
        this.cycles = stats.cycles;
        this.jitter = stats.jitter;
        this.received = stats.received;
        this.maxSequence = stats.maxSequence;
        this.baseSequence = stats.baseSequence;
    }

    void update(RtpStats stats) {
        this.cycles = stats.cycles;
        this.jitter = stats.jitter;
        this.received = stats.received;
        this.maxSequence = stats.maxSequence;
        this.baseSequence = stats.baseSequence;
    }

    public RtpStats clone() {
        return new RtpStats(this);
    }
}

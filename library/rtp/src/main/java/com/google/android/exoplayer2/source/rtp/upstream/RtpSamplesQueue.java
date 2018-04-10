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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;

import java.util.Iterator;
import java.util.TreeSet;

/* package */ final class RtpSamplesQueue {

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
            int sequenceDelta = this.packet.sequenceNumber() - sequenceSample;

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

    private int jitter; // estimated jitter

    private int baseSequence; // base sequence number
    private volatile int lastSequence; // last sequence number pop
    private int maxSequence; // highest sequence number seen

    private long lastRxTimestamp;
    private long lastRtpTimestamp;

    private boolean isStarted;

    private final int clockrate;
    private final TreeSet<RtpSample> samples;

    public RtpSamplesQueue(int clockrate) {
        this.clockrate = clockrate;
        samples = new TreeSet<>();
    }

    public void offer(RtpPacket packet) {
        int sequence = packet.sequenceNumber();
        long rtpTimestamp = packet.timestamp();
        long nowTimestamp = System.currentTimeMillis();

        if (!isStarted) {
            baseSequence = sequence;
            maxSequence = sequence - 1;
            lastSequence = maxSequence;
            isStarted = true;

        } else {
            long timestampDelta = ((nowTimestamp - lastRxTimestamp) * clockrate) / C.MICROS_PER_SECOND;
            timestampDelta -= rtpTimestamp - lastRtpTimestamp;

            if (timestampDelta < 0) {
                timestampDelta = -timestampDelta;
            }

            jitter += ((timestampDelta - jitter) + 8) >> 4;
        }

        lastRtpTimestamp = rtpTimestamp;
        lastRxTimestamp = nowTimestamp;

        int expected = (maxSequence + 1) % 65536;
        int sequenceDelta = sequence - expected;

        if (sequenceDelta >= 0 && sequenceDelta < MAX_DROPOUT) {
            maxSequence = sequence;
            samples.add(new RtpSample(packet, nowTimestamp));

            //Log.v("RtpSamplesQueue","RTP packet enqueue[0]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
              //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");

        } else if (sequenceDelta < -MAX_MISORDER || sequenceDelta >= MAX_DROPOUT) {
			/* the sequence number made a very large jump */
            if (sequence < baseSequence) {
                samples.add(new RtpSample(packet, nowTimestamp));

                //Log.v("RtpSamplesQueue","RTP packet enqueue[1]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");

            } else if (-sequenceDelta >= expected) {
                maxSequence = sequence;
                samples.add(new RtpSample(packet, nowTimestamp));

                //Log.v("RtpSamplesQueue","RTP packet enqueue[2]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");
            } else {
                if (sequence < baseSequence) {
                    samples.clear();
                    maxSequence = sequence;
                    baseSequence = sequence;
                    lastSequence = sequence;
                }
                samples.add(new RtpSample(packet, nowTimestamp));

                //Log.v("RtpSamplesQueue","RTP packet enqueue[3]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");
            }

        } else { /* delta < 0 && delta >= -MAX_MISORDER */
            if (sequence > lastSequence) {
                maxSequence = sequence;
                samples.add(new RtpSample(packet, nowTimestamp));

                //Log.v("RtpSamplesQueue","RTP packet enqueue[4]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");
            } else {
                //Log.v("RtpSamplesQueue","RTP packet rejected: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");
            }
        }
    }

    public RtpPacket pop() {
        if (isStarted && samples.size() > 1) {
            long nowTimestamp = System.currentTimeMillis();

            Iterator<RtpSample> iteratorSamples = samples.iterator();

            RtpSample sample = iteratorSamples.next();
            RtpPacket packet = sample.packet();

            RtpSample higher = iteratorSamples.next();
            RtpPacket expected = higher.packet();

            int sequenceDelta = ((packet.sequenceNumber() + 1) % 65536) - expected.sequenceNumber();

            if (sequenceDelta == 0) {
                baseSequence = packet.sequenceNumber();
                lastSequence = baseSequence;

                //Log.v("RtpSamplesQueue","RTP packet dequeue[0]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], timestamp=[" + packet.timestamp() + "], payloadType=[" + packet.payloadType() + "]");

                samples.pollFirst();
                return packet;
            }

            long deadlineTimestamp = 0;

            if (jitter > 0 && clockrate > 0) {
                deadlineTimestamp = (C.MICROS_PER_SECOND * 3 * jitter) / clockrate;
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
                int deltaSequence = packet.sequenceNumber() - ((baseSequence + 1) % 65536);
                if (deltaSequence != 0 && deltaSequence >= 0x8000) {
                    // Trash too late packets
                    baseSequence = expected.sequenceNumber();
                    lastSequence = baseSequence;

                    //Log.v("RtpSamplesQueue","RTP packet discarded [" + deltaSequence + "]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                      //      "], millisInQueue=[" + (nowTimestamp - sample.timestamp()) + "], payloadType=[" + packet.payloadType() + "]");

                    return null;
                }

                baseSequence = packet.sequenceNumber();
                lastSequence = baseSequence;

                //Log.v("RtpSamplesQueue", "RTP packet dequeue[1]: base=[" + baseSequence + "], max=[" + maxSequence + "], last=[" + lastSequence + "], seq=[" + packet.sequenceNumber() +
                  //      "], millisInQueue=[" + (nowTimestamp - sample.timestamp()) + "], payloadType=[" + packet.payloadType() + "]");

                return packet;
            }
        }

        return null;
    }

    public void clear() {
        samples.clear();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }
}

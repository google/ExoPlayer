/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.rtp;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A circular buffer queue for RTP packets
 */
public class RtpPacketQueue {

    private final static int MAX_SEGMENT_SIZE = 1316;

    private final ByteBuffer buffer;
    private final RtpPacketQueueItem[] packets;

    private final int capacity;

    private int front;
    private int back;

    private long total;

    private class RtpPacketQueueItem {

        public RtpPacket packet;
        public int length;
        public int offset;

        public RtpPacketQueueItem() {
            this.packet = null;
            this.length = 0;
            this.offset = 0;
        }

        public void reset() {
            this.packet = null;
            length = 0;
            offset = 0;
        }
    }

    public RtpPacketQueue(int capacity) {

        this.capacity = capacity;

        total = front = back = 0;

        buffer = ByteBuffer.allocate(MAX_SEGMENT_SIZE * capacity);

        packets = new RtpPacketQueueItem[capacity];

        for (int i = 0; i < capacity; i++)
            packets[i] = new RtpPacketQueueItem();
    }

    public synchronized void reset() {
        front = back = 0;
        total = 0;

        buffer.rewind();

        for (int i = 0; i < capacity; i++)
            packets[i].reset();
    }

    public synchronized int push(RtpPacket packet) throws BufferUnderflowException {
        if ((packet != null) && (packets[back].length == 0)) {

            int length = packet.getPayload().length;

            packets[back].packet = packet;
            packets[back].length = length;
            packets[back].offset = 0;

            buffer.position(back * MAX_SEGMENT_SIZE);
            buffer.put(packet.getPayload());

            total+=length;

            back = (back + 1) % capacity;

            return length;
        }

        return -1;
    }

    public synchronized int get(byte[] data, int offset, int length) throws BufferUnderflowException {
        int size = 0;

        if ((length > 0) && (packets[front].length > 0)) {

            size = Math.min(length, packets[front].length);

            if (packets[front].length >= size) {

                buffer.position((front * MAX_SEGMENT_SIZE) + packets[front].offset);
                buffer.get(data, offset, size);

                packets[front].length -= size;

                if (packets[front].length == 0) {
                    packets[front].reset();
                    front = (front + 1) % capacity;

                } else {
                    packets[front].offset += size;
                }

                total-=size;
            }
        }

        return size;
    }

    public synchronized RtpPacket front() {
        return packets[front].packet;
    }

    public synchronized boolean isDataAvailable() { return (total > 0); }
}

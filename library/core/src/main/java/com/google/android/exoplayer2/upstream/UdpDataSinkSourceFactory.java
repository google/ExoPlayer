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
package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.upstream.DataSource.Factory;

import static com.google.android.exoplayer2.upstream.UdpDataSource.DEFAULT_MAX_PACKET_SIZE;

/**
 * A {@link Factory} that produces {@link UdpDataSinkSourceFactory} for UDP data sink sources.
 */
public final class UdpDataSinkSourceFactory implements Factory {

    private final TransferListener<? super DataSource> listener;
    private final int maxPacketSize;

    public UdpDataSinkSourceFactory() {
        this(null, DEFAULT_MAX_PACKET_SIZE);
    }

    /**
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     */
    public UdpDataSinkSourceFactory(int maxPacketSize) {
        this(null, maxPacketSize);
    }

    /**
     * @param listener An optional listener.
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     */
    public UdpDataSinkSourceFactory(TransferListener<? super DataSource> listener, int maxPacketSize) {
        this.listener = listener;
        this.maxPacketSize = maxPacketSize;
    }

    @Override
    public DataSource createDataSource() {
        return new UdpDataSinkSource(listener, maxPacketSize);
    }

}

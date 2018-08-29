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

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSource;

/**
 * A {@link Factory} that produces {@link RtpDataSourceFactory} for RTP data sources.
 */
public final class RtpDataSourceFactory implements Factory {

    private final TransferListener<? super UdpDataSource> listener;
    private final @RtpDataSource.Flags int flags;
    private final int clockrate;

    /**
     * @param clockrate The clock rate.
     */
    public RtpDataSourceFactory(int clockrate) {
        this(clockrate, 0);
    }

    /**
     * @param clockrate The clock rate.
     * @param flags The optional flags.
     */
    public RtpDataSourceFactory(int clockrate, @RtpDataSource.Flags int flags) {
        this(clockrate, flags, null);
    }

    /**
     * @param clockrate The clock rate.
     * @param flags The optional flags.
     * @param listener An optional listener.
     */
    public RtpDataSourceFactory(int clockrate, @RtpDataSource.Flags int flags,
                                TransferListener<? super UdpDataSource> listener) {
        this.flags = flags;
        this.clockrate = clockrate;
        this.listener = listener;
    }

    @Override
    public RtpDataSource createDataSource() {
        return new RtpDataSource(clockrate, flags, listener);
    }

}

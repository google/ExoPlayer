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

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtp.RtpPacket;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.UdpDataSource;

import java.io.IOException;

public final class RtpInternalDataSource extends UdpDataSource {

    private Uri uri;
    private long length;
    private boolean opened;

    private RtcpIncomingReportSink reportSink;
    private final RtpInternalSamplesSink samplesSink;

    private RtpStatistics statistics;
    private RtcpStatsFeedback feedbackSource;

    public RtpInternalDataSource(RtpInternalSamplesSink samplesSink) {
        this.samplesSink = samplesSink;
    }

    public RtpInternalDataSource(RtpInternalSamplesSink samplesSink, RtcpIncomingReportSink reportSink,
                                 RtcpOutgoingReportSink outgoingReportSink) {
        this.samplesSink = samplesSink;

        if (reportSink != null && outgoingReportSink != null) {
            this.reportSink = reportSink;

            statistics = new RtpStatistics();
            feedbackSource = new RtcpStatsFeedback(statistics, outgoingReportSink);

            reportSink.addListener(feedbackSource);
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        length = dataSpec.length;

        transferInitializing(dataSpec);

        if (feedbackSource != null) {
            feedbackSource.open();
        }

        opened = true;
        transferStarted(dataSpec);
        return length;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        RtpSamplesQueue samples = samplesSink.samples();

        RtpPacket packet = samples.pop();
        if (packet != null) {

            if (statistics != null) {
                if (feedbackSource.getRemoteSsrc() == Long.MIN_VALUE) {
                    feedbackSource.setRemoteSsrc(packet.ssrc());
                }

                statistics.update(samples.getStatsInfo());
            }

            byte[] bytes = packet.getBytes();
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);

            bytesTransferred(bytes.length);
            return bytes.length;
        }

        return 0;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        if (opened) {
            opened = false;

            if (statistics != null) {
                reportSink.removeListener(feedbackSource);
                feedbackSource.close();
                statistics.clear();
            }

            transferEnded();
        }
    }

    public int getLocalPort() {
        return C.PORT_UNSET;
    }
}

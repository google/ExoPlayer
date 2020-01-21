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
import java.util.Timer;
import java.util.TimerTask;

public final class RtpBufferedDataSource extends UdpDataSource {

    private final class TimeoutMonitor {
        private Timer timer;
        private TimerTask timerTask;

        private boolean started;

        public void start() {
            if (!started) {
                started = true;
                timer = new Timer();
                buildAndScheduleTask();
            }
        }

        public void reset() {
            if (started) {
                timerTask.cancel();
                timer.cancel();
                timer.purge();

            } else {
                started = true;
            }

            timer = new Timer();
            buildAndScheduleTask();
        }

        public void stop() {
            if (started) {
                timer.cancel();
                timer.purge();
                started = false;
            }
        }

        private void buildAndScheduleTask() {
            timerTask = new TimerTask() {
                public void run() {
                    if (bytesRead > 0) {
                        bytesRead = 0;
                    } else {
                        canceled = true;
                    }
                }
            };

            try {
                timer.schedule(timerTask, 5000);
            } catch (IllegalStateException ex) {

            }
        }
    }

    private Uri uri;
    private long length;
    private boolean opened;

    private volatile boolean canceled;
    private volatile long bytesRead;
    private TimeoutMonitor timeoutMonitor;

    private RtcpInputReportDispatcher reportDispatcher;
    private final RtpSamplesHolder samplesHolder;

    private RtpStatistics statistics;
    private RtcpStatsFeedback statsFeedback;

    public RtpBufferedDataSource(RtpSamplesHolder samplesHolder) {
        this(samplesHolder, null, null);
    }

    public RtpBufferedDataSource(RtpSamplesHolder samplesHolder,
                                 RtcpInputReportDispatcher incomingReportDispatcher,
                                 RtcpOutputReportDispatcher outgoingReportDispatcher) {
        this.samplesHolder = samplesHolder;
        timeoutMonitor = new TimeoutMonitor();

        if (incomingReportDispatcher != null && outgoingReportDispatcher != null) {
            statistics = new RtpStatistics();
            statsFeedback = new RtcpStatsFeedback(statistics, outgoingReportDispatcher);

            reportDispatcher = incomingReportDispatcher;
            reportDispatcher.addListener(statsFeedback);
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        length = dataSpec.length;

        transferInitializing(dataSpec);

        if (statsFeedback != null) {
            statsFeedback.open();
        }

        opened = true;
        transferStarted(dataSpec);
        timeoutMonitor.start();
        return length;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (opened && !canceled) {
            RtpSamplesQueue samples = samplesHolder.samples();

            /* There is no reordering on stream sockets */
            RtpPacket packet = samples.pop();
            if (packet != null) {

                if (statistics != null) {
                    if (statsFeedback.getRemoteSsrc() == Long.MIN_VALUE) {
                        statsFeedback.setRemoteSsrc(packet.ssrc());
                    }

                    statistics.update(samples.getStatsInfo());
                }

                byte[] bytes = packet.getBytes();
                /*Log.v("RtpBufferedDataSource", "packet=[" + packet.sequenceNumber() +
                        "], marker=[" + packet.marker() + "], elapsedTime=[" +
                        SystemClock.elapsedRealtime() + "]");*/
                System.arraycopy(bytes, 0, buffer, offset, bytes.length);

                bytesTransferred(bytes.length);
                bytesRead += bytes.length;

                timeoutMonitor.reset();

                return bytes.length;
            }

            return 0;
        }

        return C.RESULT_END_OF_INPUT;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        if (opened) {
            opened = false;
            timeoutMonitor.stop();

            if (statistics != null) {
                reportDispatcher.removeListener(statsFeedback);
                statsFeedback.close();
                statistics.clear();
            }

            transferEnded();
        }
    }
}

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

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpByePacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpChunk;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpCompoundPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpReportBlock;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpRrPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesItem;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSdesPacket;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSenderInfo;
import com.google.android.exoplayer2.source.rtp.rtcp.RtcpSrPacket;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.UdpDataSink;
import com.google.android.exoplayer2.upstream.UdpDataSinkSource;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/* package */ final class RtcpStatsFeedback implements RtcpReportReceiver.EventListener,
        RtcpReportSender.EventListener {

    private static final long REPORT_INTERVAL = 5000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final long MIDDLE_32_BITS_OUT_OF_64_BITS = 0x0000ffffffff0000L;

    private boolean opened;

    private volatile long lastSrTimestamp;
    private volatile long arrivalSrTimestamp;

    private final RtpStats statistics;

    /**
     * The packets expected in last interval.
     */
    private int expectedPrior;

    /**
     * The packets received in last interval.
     */
    private int receivedPrior;

    private long localSsrc;
    private long remoteSsrc;

    private boolean isLastReport;

    private RtcpReportReceiver receiver;
    private RtcpReportSender sender;

    private UdpDataSinkSource dataSinkSource;
    private RtcpOutputReportDispatcher reportDispatcher;

    public RtcpStatsFeedback(RtpStats statistics) {
        this.statistics = statistics;

        dataSinkSource = new UdpDataSinkSource();

        sender = new RtcpReportSender(dataSinkSource, this);
        receiver = new RtcpReportReceiver(dataSinkSource, this);

        localSsrc = new Random().nextLong();
        remoteSsrc = Long.MIN_VALUE;
    }

    public RtcpStatsFeedback(RtpStats statistics, UdpDataSink dataSink) {
        this.statistics = statistics;

        sender = new RtcpReportSender(dataSink, this);

        localSsrc = new Random().nextLong();
        remoteSsrc = Long.MIN_VALUE;
    }

    public RtcpStatsFeedback(RtpStats statistics,
                             RtcpOutputReportDispatcher outgoingReportDispatcher) {
        this.statistics = statistics;
        this.reportDispatcher = outgoingReportDispatcher;

        localSsrc = new Random().nextLong();
        remoteSsrc = Long.MIN_VALUE;
    }

    public long getRemoteSsrc() {
        return remoteSsrc;
    }

    public void setRemoteSsrc(long remoteSsrc) {
        this.remoteSsrc = remoteSsrc;
    }

    public void open() throws IllegalStateException {
        if (reportDispatcher == null && sender == null) {
            throw new IllegalStateException(
                    "None internal outgoing or data sink was found");
        }

        if (!opened) {
            opened = true;
            executor.execute(scheduler);
        }
    }

    public void open(String host, int port, @DataSpec.Flags int flags)
            throws IOException, IllegalStateException {
        if (dataSinkSource == null) {
            throw new IllegalStateException("None data and sink source was found");
        }

        if (!opened) {
            dataSinkSource.open(new DataSpec(Uri.parse("rtcp://" + host + ":" + port), flags));
            opened = true;

            receiver.start();
            executor.execute(scheduler);
        }
    }

    public void close() {
        if (opened) {
            opened = false;
            sendLastReport();
        }
    }

    // RtcpReportReceiver.EventListener implementation

    @Override
    public void onSenderReport(RtcpSrPacket srPacket) {
        RtcpSenderInfo senderInfo = srPacket.getSenderInfo();
        long ntpTimestampMsw = senderInfo.getNtpTimestampMsw();
        long ntpTimestampLsw = senderInfo.getNtpTimestampLsw();

        if (remoteSsrc == Long.MIN_VALUE) {
            remoteSsrc = srPacket.getSsrc();
        }

        lastSrTimestamp = (((ntpTimestampMsw << 32) | ntpTimestampLsw) &
                MIDDLE_32_BITS_OUT_OF_64_BITS) >>> 16;

        arrivalSrTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onSourceDescription(RtcpSdesPacket sdesPacket) {
        // Do nothing
    }

    private void release() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }

        if (sender != null) {
            sender.cancel();
        }

        if (receiver != null) {
            receiver.stop();
            dataSinkSource.close();
        }
    }


    // RtcpReportSender.EventListener implementation

    @Override
    public void onReportSent() {
        if (isLastReport) {
            release();
        }
    }

    // Internal methods

    private void sendLastReport() {
        isLastReport = true;
        notifyFeedbackReport();
    }

    private void notifyFeedbackReport() {
        RtcpPacket packet = buildReportPacket();
        if (packet != null && reportDispatcher != null) {
            reportDispatcher.dispatch(packet);

            if (isLastReport) {
                release();
            }

        } else if (sender != null) {
            sender.send(packet);
        }
    }

    @Nullable
    private RtcpPacket buildReportPacket() {
        RtpStats statsInfo = statistics.clone();
        if (statsInfo != null) {
            int extendedMax = statsInfo.cycles + statsInfo.maxSequence;
            int expected = extendedMax - (statsInfo.baseSequence + 1);
            int lost = Math.min(expected - statsInfo.received, 0xFFFFFF);
            int expectedInterval = expected - expectedPrior;
            expectedPrior = expected;
            int receivedInterval = statsInfo.received - receivedPrior;
            receivedPrior = statsInfo.received;
            int lostInterval = expectedInterval - receivedInterval;
            int fractionLost = (expectedInterval == 0 || lostInterval <= 0) ? 0
                    : (lostInterval << 8) / expectedInterval;

            long delaySinceLastSr = 0;
            if (lastSrTimestamp > 0) {
                long nowTimestamp = System.currentTimeMillis();
                delaySinceLastSr =  ((nowTimestamp - arrivalSrTimestamp) * 65536) / 1000;
            }

            RtcpReportBlock reportBlock = new RtcpReportBlock.Builder()
                    .setSsrc(remoteSsrc)
                    .setFractionLost(fractionLost)
                    .setCumulativeNumberOfPacketsLost(lost)
                    .setExtendedHighestSequenceNumberReceived(extendedMax)
                    .setInterarrivalJitter(statsInfo.jitter)
                    .setLastSenderReport(lastSrTimestamp)
                    .setDelaySinceLastSenderReport(delaySinceLastSr)
                    .build();

            RtcpRrPacket rrPacket = new RtcpRrPacket.Builder()
                    .setSsrc(localSsrc)
                    .setReportBlocks((reportBlock != null) ? new RtcpReportBlock[]{reportBlock} : null)
                    .build();

            RtcpCompoundPacket.Builder builder = new RtcpCompoundPacket.Builder()
                    .addPacket(rrPacket);

            if (isLastReport) {
                RtcpByePacket byePacket = new RtcpByePacket.Builder()
                        .setSsrcs(new long[] {remoteSsrc})
                        .build();

                builder.addPacket(byePacket);

            } else {
                RtcpSdesPacket sdesPacket = new RtcpSdesPacket.Builder()
                        .setChunks(new RtcpChunk[]{new RtcpChunk.Builder()
                                .setSsrc(localSsrc)
                                .setSdesItems(new RtcpSdesItem[]{new RtcpSdesItem
                                        .Builder()
                                        .setType(RtcpSdesItem.CNAME)
                                        .setValue(ExoPlayerLibraryInfo.VERSION_SLASHY.getBytes())
                                        .build()})
                                .build()})
                        .build();

                builder.addPacket(sdesPacket);
            }

            return builder.build();
        }

        return null;
    }

    private void onReceivedDelaySinceLastReport() {
        notifyFeedbackReport();
    }

    private long delayToSendNextRtcpReport() {
        int random = new Random().nextInt (999);
        long delayToNext = (REPORT_INTERVAL / 2) + (REPORT_INTERVAL * random / 1000);
        return delayToNext;
    }

    private Runnable scheduler = new Runnable() {
        @Override
        public void run() {
            try {
                try {

                    while (!Thread.currentThread().isInterrupted() && opened) {
                        Thread.sleep(delayToSendNextRtcpReport());

                        if (opened) {
                            onReceivedDelaySinceLastReport();
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            } catch (RejectedExecutionException e) {

            } finally {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                }
            }
        }
    };
}

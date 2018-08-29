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
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

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
import com.google.android.exoplayer2.upstream.UdpDataSinkSource;
import com.google.android.exoplayer2.upstream.UdpDataSinkSourceFactory;
import com.google.android.exoplayer2.upstream.UdpDataSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.util.Random;

/* package */ final class RtcpFeedbackSource implements RtcpReportReceiver.EventListener,
        RtcpReportScheduler.EventListener, RtcpReportSender.EventListener {

    public interface FeedbackListener {
        void onFeedbackReport(RtcpPacket packet);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {FLAG_FORCE_EXTERNAL_RECEIVER, FLAG_FORCE_EXTERNAL_FEEDBACK})
    private @interface Flags {}
    private static final int FLAG_FORCE_EXTERNAL_RECEIVER = 1;
    public static final int FLAG_FORCE_EXTERNAL_FEEDBACK = 1 << 1;

    private static final long MIDDLE_32_BITS_OUT_OF_64_BITS = 0x0000ffffffff0000L;

    private boolean opened;

    private volatile long lastSrTimestamp;
    private volatile long arrivalSrTimestamp;

    private final RtpStatistics statistics;

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

    private @Flags int flags;
    private FeedbackListener listener;

    private RtcpReportReceiver receiver;
    private RtcpReportSender sender;
    private RtcpReportScheduler scheduler;

    private final UdpDataSinkSource dataSinkSource;

    public RtcpFeedbackSource(Builder builder) {
        flags = builder.flags;
        listener = builder.listener;
        statistics = builder.statistics;

        dataSinkSource = (builder.dataSinkSource != null) ? builder.dataSinkSource :
                new UdpDataSinkSourceFactory().createDataSource();

        scheduler = new RtcpReportScheduler(this);

        if (!isSet(FLAG_FORCE_EXTERNAL_FEEDBACK)) {
            sender = new RtcpReportSender(dataSinkSource, this);
        }

        if (!isSet(FLAG_FORCE_EXTERNAL_RECEIVER)) {
            receiver = new RtcpReportReceiver(dataSinkSource, this);
        }

        localSsrc = new Random().nextLong();
        remoteSsrc = Long.MIN_VALUE;
    }

    public long getRemoteSsrc() {
        return remoteSsrc;
    }

    public void setRemoteSsrc(long remoteSsrc) {
        this.remoteSsrc = remoteSsrc;
    }

    public void open() throws UdpDataSource.UdpDataSourceException {
        if (!isSet(FLAG_FORCE_EXTERNAL_RECEIVER)) {
            throw new UdpDataSource.UdpDataSourceException("None external source was found");
        }

        if (!opened) {
            scheduler.start();
            opened = true;
        }
    }

    public void open(String host, int port, @DataSpec.Flags int flags)
            throws UdpDataSource.UdpDataSourceException {
        if (isSet(FLAG_FORCE_EXTERNAL_RECEIVER)) {
            throw new UdpDataSource.UdpDataSourceException("An external source was found");
        }

        if (!opened) {
            dataSinkSource.open(new DataSpec(Uri.parse("rtcp://" + host + ":" + port), flags));

            scheduler.start();
            receiver.start();
            opened = true;
        }
    }

    public void sendTo(RtcpPacket packet, InetAddress address, int port) {
        if (!isSet(FLAG_FORCE_EXTERNAL_FEEDBACK)) {
            sender.sendTo(packet, address, port);
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

    // RtcpReportScheduler.EventListener implementation

    @Override
    public void onReceivedDelaySinceLastReport() {
        notifyFeedbackReport(false);
    }

    // RtcpReportSender.EventListener implementation

    @Override
    public void onLastReportSent() {
        scheduler.stop();
        sender.cancel();

        if (receiver != null) {
            receiver.stop();
            dataSinkSource.close();
        }
    }

    // Internal methods

    private void sendLastReport() {
        notifyFeedbackReport(true);
    }

    private void notifyFeedbackReport (boolean isLastReport) {
        RtcpPacket packet = buildReportPacket(isLastReport);
        if (isSet(FLAG_FORCE_EXTERNAL_FEEDBACK)) {
            listener.onFeedbackReport(packet);
        } else {
            sender.send(packet, isLastReport);
        }
    }

    @Nullable
    private RtcpPacket buildReportPacket(boolean isLastReport) {
        RtpStatistics.RtpStatsInfo statsInfo = statistics.getStatsInfo();
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

    private boolean isSet(@Flags int flag) {
        return (flags & flag) != 0;
    }

    public static class Builder {
        private @Flags int flags;
        private FeedbackListener listener;
        private RtpStatistics statistics;
        private UdpDataSinkSource dataSinkSource;

        public Builder(RtpStatistics statistics) {
            this.statistics = statistics;
        }

        public Builder setFeedbackListener(FeedbackListener listener) {
            if (listener == null) throw new NullPointerException("listener cannot be null");

            this.listener = listener;
            this.flags = flags | FLAG_FORCE_EXTERNAL_FEEDBACK;
            return this;
        }

        public Builder setExternalSource(UdpDataSinkSource dataSinkSource) {
            if (dataSinkSource == null) throw new NullPointerException("dataSinkSource cannot be null");

            this.dataSinkSource = dataSinkSource;
            this.flags = flags | FLAG_FORCE_EXTERNAL_RECEIVER;
            return this;
        }

        public RtcpFeedbackSource build() {
            return new RtcpFeedbackSource(this);
        }
    }
}

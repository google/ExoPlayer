package com.google.android.exoplayer2.video;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;

import java.util.concurrent.ArrayBlockingQueue;

public class MonitorMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    PtsHistory ptsHistory = new PtsHistory();
    PtsExpectedQueue ptsQueue = new PtsExpectedQueue();

    public MonitorMediaCodecVideoRenderer(
            Context context,
            MediaCodecSelector mediaCodecSelector,
            long allowedJoiningTimeMs,
            boolean enableDecoderFallback,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            int maxDroppedFramesToNotify) {
        super(
                context,
                mediaCodecSelector,
                allowedJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                maxDroppedFramesToNotify);
    }

    @Override
    public String getName() {
        return "MonVidRenderer";
    }

    @Override
    protected void resetCodecStateForFlush() {
        super.resetCodecStateForFlush();
        if (ptsQueue != null) {// We need this. It can be the following stack
            ptsQueue.clearTimestamps();
        }
    }

    @Override
    protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
        super.onInputFormatChanged(formatHolder);
        // TODO: figure out why!
        // Ensure timestamps of buffers queued after this format change are never inserted into the
        // queue of expected output timestamps before those of buffers that have already been queued.
        ptsQueue.freeze();
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
        super.onQueueInputBuffer(buffer);
        ptsQueue.insertTimestamp(buffer.timeUs);
        ptsQueue.maybeShiftTimestampsList();
    }

    @Override
    protected void onProcessedOutputBuffer(long presentationTimeUs) {
        super.onProcessedOutputBuffer(presentationTimeUs);
        ptsHistory.record(presentationTimeUs);
        ptsQueue.increaseBufferCount();

        int dropCount = 0;

        long expectedTimestampUs = ptsQueue.dequeueTimestamp();
        long nextExpected = expectedTimestampUs;
        while (presentationTimeUs > nextExpected) {
            dropCount++;
            nextExpected = ptsQueue.dequeueTimestamp();
        }

        if (dropCount == 0) return; // No drop happened, continue.

        String warning = "Expected to dequeue video buffer with presentation "
                + "timestamp: " + expectedTimestampUs + ". Instead got: " + presentationTimeUs
                + " (Processed buffers since last flush: " + ptsQueue.bufferCount + "), dropCount = " + dropCount;

        if (nextExpected == presentationTimeUs) {
            Log.w("DROP-MON", warning);
        } else {
            throw new IllegalStateException("PTS does not exist in the queue, I don't know what to do!");
        }
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        Thread loggerThread = new Thread("AuditorThread") {
            @Override
            public void run() {
                try {
                    while (true) {
                        long[] times = ptsHistory.fpsQueue.take();
                        StringBuilder sb = new StringBuilder();
                        float currentFps = getCurrentOutputFormat().frameRate;
                        sb.append("===>currentFps = " + currentFps);
                        // Use awk to handle delta
                        // adb logcat com.canaldigital.ngp:I  *:S FPS:V |grep -v "===>" > sample.log
                        // awk '/pts/{print $0 "\tdelta = " $10-t} !/pts/{print $0} {t=$10}' sample.log >sample_with_delta.log
                        for (int i = 0; i < ptsHistory.RECORD_CNT; i++) {
                            sb.append(
                                    "\npts = " + times[i + ptsHistory.RECORD_CNT] +
                                            // "\t" + sdf.format(new Date(times[i + RECORD_CNT])) +
                                            "\tcb = " + times[i]);
                        }
                        Log.d("FPS", sb.toString());

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        loggerThread.setPriority(Thread.MIN_PRIORITY);
        loggerThread.start();

    }

    private static class PtsHistory {
        public static final int RECORD_CNT = 100; //
        private int idx = 0;
        private long[] timeRecords = new long[RECORD_CNT * 2];
        private long[] timeRecordsBackup = new long[RECORD_CNT * 2];
        public ArrayBlockingQueue<long[]> fpsQueue = new ArrayBlockingQueue<>(1);

        public void record(long presentationTimeUs) {
            timeRecords[idx] = System.currentTimeMillis();
            timeRecords[idx + RECORD_CNT] = presentationTimeUs/1000;
            idx++;
            if (idx == RECORD_CNT) {
                System.arraycopy(timeRecords, 0, timeRecordsBackup, 0, RECORD_CNT * 2);
                idx = 0;
                // The AuditorThread may experience starvation, this method may return false
                fpsQueue.offer(timeRecordsBackup);
            }
        }
    }

    private static class PtsExpectedQueue {
        private int startIndex;
        private int queueSize;
        private int bufferCount;
        private int minimumInsertIndex;

        private static final int ARRAY_SIZE = 1000;
        private final long[] timestampsList = new long[ARRAY_SIZE];

        public void clearTimestamps() {
            startIndex = 0;
            queueSize = 0;
            bufferCount = 0;
            minimumInsertIndex = 0;
        }

        public void increaseBufferCount() {
            bufferCount ++;
        }

        public void insertTimestamp(long presentationTimeUs) {
            for (int i = startIndex + queueSize - 1; i >= minimumInsertIndex; i--) {
                if (presentationTimeUs >= timestampsList[i]) {
                    timestampsList[i + 1] = presentationTimeUs;
                    queueSize++;
                    return;
                }
                timestampsList[i + 1] = timestampsList[i];
            }
            timestampsList[minimumInsertIndex] = presentationTimeUs;
            queueSize++;
        }

        public void maybeShiftTimestampsList() {
            if (startIndex + queueSize == ARRAY_SIZE) {
                System.arraycopy(timestampsList, startIndex, timestampsList, 0, queueSize);
                minimumInsertIndex -= startIndex;
                startIndex = 0;
            }
        }

        public long dequeueTimestamp() {
            queueSize--;
            startIndex++;
            minimumInsertIndex = Math.max(minimumInsertIndex, startIndex);
            return timestampsList[startIndex - 1];
        }

        public void freeze() {
            minimumInsertIndex = startIndex + queueSize;
        }
    }
}

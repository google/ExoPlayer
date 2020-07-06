package com.google.android.exoplayer2.video;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;

import java.util.concurrent.ArrayBlockingQueue;

public class MonitorMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    private static final int RECORD_CNT = 100; //
    private int idx = 0;
    private long[] timeRecords = new long[RECORD_CNT * 2];
    private long[] timeRecordsBackup = new long[RECORD_CNT * 2];
    private ArrayBlockingQueue<long[]> fpsQueue = new ArrayBlockingQueue<>(1);

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
    protected void onStarted() {
        super.onStarted();
        Thread loggerThread = new Thread("AuditorThread") {
            @Override
            public void run() {
                try {
                    while (true) {
                        long[] times = fpsQueue.take();
                        StringBuilder sb = new StringBuilder();
                        float currentFps = getCurrentOutputFormat().frameRate;
                        sb.append("===>currentFps = " + currentFps);
                        // Use awk to handle delta
                        // adb logcat com.canaldigital.ngp:I  *:S FPS:V |grep -v "===>" > sample.log
                        // awk '/pts/{print $0 "\tdelta = " $10-t} !/pts/{print $0} {t=$10}' sample.log >sample_with_delta.log
                        for (int i = 0; i < RECORD_CNT; i++) {
                            sb.append(
                                    "\npts = " + times[i + RECORD_CNT] +
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

    @Override
    protected void onProcessedOutputBuffer(long presentationTimeUs) {
        super.onProcessedOutputBuffer(presentationTimeUs);
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

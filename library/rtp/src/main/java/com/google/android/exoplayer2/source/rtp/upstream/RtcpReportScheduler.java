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

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/* package */ final class RtcpReportScheduler {

    public interface EventListener {
        void onReceivedDelaySinceLastReport();
    }

    private static final long REPORT_INTERVAL = 5000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean enabled;

    private final EventListener listener;

    public RtcpReportScheduler(EventListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (!enabled) {
            enabled = true;
            executor.execute(scheduler);
        }
    }

    public void stop() {
        if (enabled) {
            enabled = false;
            executor.shutdown();
        }
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

                while (enabled) {
                    try {

                        while (!Thread.currentThread().isInterrupted() && enabled) {
                            Thread.sleep(delayToSendNextRtcpReport());
                            listener.onReceivedDelaySinceLastReport();
                        }

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();

                    } catch (RejectedExecutionException e) {
                        e.printStackTrace();
                    }
                }

            } finally {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                }
            }
        }
    };
}

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
package com.google.android.exoplayer2.source.rtsp.core;

import android.util.Log;

import com.google.android.exoplayer2.source.rtsp.message.InterleavedFrame;
import com.google.android.exoplayer2.source.rtsp.message.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;


/* package */ final class Sender {

    public interface EventListener {
        void onSendSuccess(Message message);
        void onSendFailure(Message message);
        void onSendSuccess(InterleavedFrame message);
        void onSendFailure(InterleavedFrame message);
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final OutputStream outputStream;
    private final EventListener eventListener;

    private boolean canceled;

    public Sender(OutputStream outputStream, EventListener eventListener) {
        this.outputStream = outputStream;
        this.eventListener = eventListener;
    }

    public void cancel() {
        if (!canceled) {
            canceled = true;

            if (!executorService.isShutdown()) {
                executorService.shutdown();
            }
        }
    }

    public synchronized void send(final InterleavedFrame message) {
        if (!canceled) {
            try {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            if (message != null) {
                                byte[] bytes = message.getBytes();
                                outputStream.write(bytes, 0, bytes.length);

                                eventListener.onSendSuccess(message);
                            }

                        } catch (IOException ex) {
                            eventListener.onSendFailure(message);
                        }
                    }
                });

            } catch (RejectedExecutionException ex) {

            }
        }
    }

    public synchronized void send(final Message message) {
        if (!canceled) {
            try {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            if (message != null) {
                                Log.v("Sender", message.toString());
                                byte[] bytes = message.toString().getBytes();
                                outputStream.write(bytes, 0, bytes.length);

                                eventListener.onSendSuccess(message);
                            }

                        } catch (IOException ex) {
                            if (!canceled) {
                                eventListener.onSendFailure(message);
                            }
                        }
                    }
                });

            } catch (RejectedExecutionException ex) {
            }
        }
    }
}


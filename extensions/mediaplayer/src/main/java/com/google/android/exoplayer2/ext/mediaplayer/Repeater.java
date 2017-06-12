package com.google.android.exoplayer2.ext.mediaplayer;

/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 * @author michalliu@tencent.com
 */

import android.os.Handler;

public class Repeater {
    private volatile boolean repeaterRunning = false;
    private int repeatDelay = 1000;

    private Handler delayedHandler;

    private RepeatListener listener;
    private PollRunnable pollRunnable = new PollRunnable();

    interface RepeatListener {
        void onUpdate();
    }

    Repeater(Handler handler) {
        delayedHandler = handler;
    }

    void setRepeaterDelay(int milliSeconds) {
        repeatDelay = milliSeconds;
    }

    void start() {
        if (!repeaterRunning) {
            repeaterRunning = true;
            pollRunnable.performPoll();
        }
    }

    void stop() {
        repeaterRunning = false;
    }

    void setRepeatListener(RepeatListener listener) {
        this.listener = listener;
    }

    private class PollRunnable implements Runnable {
        @Override
        public void run() {
            if (listener != null) {
                listener.onUpdate();
            }

            if (repeaterRunning) {
                performPoll();
            }
        }

        void performPoll() {
            delayedHandler.postDelayed(pollRunnable, repeatDelay);
        }
    }
}
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

import com.google.android.exoplayer2.source.rtp.rtcp.RtcpPacket;

import java.util.concurrent.CopyOnWriteArraySet;

public final class RtcpOutputReportDispatcher {

    public interface EventListener {
        void onOutputReport(RtcpPacket packet);
    }

    private final CopyOnWriteArraySet<EventListener> listeners;

    private boolean opened;

    public RtcpOutputReportDispatcher() {
        listeners = new CopyOnWriteArraySet<>();
    }

    public void open() {
        opened = true;
    }

    public void dispatch(RtcpPacket rtcpPacket) {
        if (opened) {
            if (rtcpPacket != null) {
                handleOutgoingReport(rtcpPacket);
            }
        }
    }

    public void close() {
        if (opened) {
            opened = false;
        }
    }

    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    private void handleOutgoingReport(RtcpPacket packet) {
        for (EventListener listener : listeners) {
            listener.onOutputReport(packet);
        }
    }

}

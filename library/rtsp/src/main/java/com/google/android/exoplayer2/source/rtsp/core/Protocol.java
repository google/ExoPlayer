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

import android.support.annotation.Nullable;

import java.io.IOException;

/**
 * Protocols that RTSP implements.
 *
 */
public enum Protocol {
    RTSP_1_0("RTSP/1.0"),
    RTSP_2_0("RTSP/2.0");

    private final String protocol;

    Protocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Returns the protocol identified by {@code protocol}.
     *
     * @throws IOException if {@code protocol} is unknown.
     */

    @Nullable
    public static Protocol parse(String protocol) {
        if (protocol.equalsIgnoreCase(RTSP_1_0.protocol)) return RTSP_1_0;
        if (protocol.equalsIgnoreCase(RTSP_2_0.protocol)) return RTSP_2_0;

        return null;
    }

    /**
     * Returns the string used to identify this protocol
     */
    @Override public String toString() {
        return protocol;
    }
}


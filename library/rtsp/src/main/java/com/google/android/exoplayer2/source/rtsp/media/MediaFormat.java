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
package com.google.android.exoplayer2.source.rtsp.media;

import android.support.annotation.IntDef;

import com.google.android.exoplayer2.source.rtp.format.RtpPayloadFormat;
import com.google.android.exoplayer2.source.rtsp.core.Transport;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class MediaFormat {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO, VIDEO, DATA, TEXT, APPLICATION})
    public @interface MediaType {}
    public static final int AUDIO = 1;
    public static final int VIDEO = 2;
    public static final int DATA = 3;
    public static final int TEXT = 4;
    public static final int APPLICATION = 5;

    private Transport transport;
    private final @MediaType int type;
    private final int bitrate;
    private final RtpPayloadFormat format;

    MediaFormat(Builder builder) {
        this.type = builder.type;
        this.transport = builder.transport;
        this.bitrate = builder.bitrate;
        this.format = builder.format;
    }

    public @MediaType int type() { return type; }

    public Transport transport() { return transport; }

    public void transport(Transport transport) { this.transport = transport; }

    public int bitrate() { return bitrate; }

    public RtpPayloadFormat format() { return format; }


    public static class Builder {
        final @MediaType int type;

        int bitrate = -1;
        Transport transport;
        RtpPayloadFormat format;

        public Builder(@MediaType int type) {
            this.type = type;
        }

        public final Builder bitrate(int bitrate) {
            if (bitrate <= 0) throw new NullPointerException("bitrate is invalid");

            this.bitrate = bitrate;
            return this;
        }

        public final Builder transport(Transport transport) {
            if (transport == null) throw new NullPointerException("transport is null");

            this.transport = transport;
            return this;
        }

        public final Builder format(RtpPayloadFormat format) {
            this.format = format;
            return this;
        }

        public MediaFormat build() {
            if (transport == null) throw new IllegalStateException("transport is null");

            if (Transport.AVP_PROFILE.equals(transport.profile())) {
                if (format == null) throw new IllegalStateException("format is null");
            }

            return new MediaFormat(this);
        }
    }
}

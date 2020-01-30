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
package com.google.android.exoplayer2.source.rtp.format;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public abstract class RtpPayloadFormat {

    /**
     * Thrown when the format is not supported.
     */
    public static final class UnsupportedFormatException extends IOException {

        public UnsupportedFormatException(String msg) {
            super(msg);
        }

    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO, VIDEO, DATA, TEXT, APPLICATION})
    public @interface MediaType {}
    public static final int AUDIO = 1;
    public static final int VIDEO = 2;
    public static final int DATA = 3;
    public static final int TEXT = 4;
    public static final int APPLICATION = 5;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({AC3, AMR, G722, EAC3, JPEG, GSM, H261, H263, H264, H265, L16, MP2T,
            MP4ALATM, MPEG4GENERIC, MP4V_ES, MPA, MPV, PCMA, PCMU, PCMA_WB, PCMU_WB, OPUS, VP8, VP9})
    public @interface MediaCodec {}
    public static final String AC3 = "AC3";
    public static final String AMR = "AMR";
    public static final String G722 = "G722";
    public static final String GSM = "GSM";
    public static final String EAC3 = "EAC3";
    public static final String JPEG = "JPEG";
    public static final String H261 = "H261";
    public static final String H263 = "H263";
    public static final String H264 = "H264";
    public static final String H265 = "H265";
    public static final String L16 = "L16";
    public static final String MP2T = "MP2T";
    public static final String MP4ALATM = "MP4A-LATM";
    public static final String MPEG4GENERIC = "MPEG4-GENERIC";
    public static final String MP4V_ES = "MP4V-ES";
    public static final String MPA = "MPA";
    public static final String MPV = "MPV";
    public static final String PCMU = "PCMU";
    public static final String PCMA = "PCMA";
    public static final String PCMA_WB = "PCMA-WB";
    public static final String PCMU_WB = "PCMU-WB";
    public static final String OPUS = "OPUS";
    public static final String VP8 = "VP8";
    public static final String VP9 = "VP9";

    private final @MediaType int type;
    private final int payload;
    protected int bitrate;
    protected int clockrate;

    protected String sampleMimeType;
    private @MediaCodec String encoding;

    protected final FormatSpecificParameters parameters;

    RtpPayloadFormat(Builder builder) {
        this.type = builder.type;
        this.payload = builder.payload;
        this.bitrate = builder.bitrate;
        this.clockrate = builder.clockrate;
        this.parameters = builder.parameters;

        buildCodecData(builder.encoding);
        buildSampleMimeType();
        buildCodecProfileLevel();
    }

    public @MediaType int type() { return type; }

    public long payload() { return payload; }

    public @MediaCodec String encoding() { return encoding; }

    public int clockrate() { return clockrate; }

    public int bitrate() { return bitrate; }

    public String sampleMimeType() { return sampleMimeType; }

    public FormatSpecificParameters parameters() { return parameters; }

    private void buildCodecData(@MediaCodec String encoding) {
        switch (payload) {
            case 0:
                this.encoding = PCMU;
                clockrate = (clockrate == -1) ? 8000 : clockrate;
                bitrate = (bitrate == -1) ? (clockrate == 8000 ? 64 : 128) : bitrate;
                ((RtpAudioPayload)this).setChannels(1);
                break;

            case 3:
                this.encoding = GSM;
                clockrate = 8000;
                ((RtpAudioPayload)this).setChannels(1);
                break;

            case 8:
                this.encoding = PCMA;
                clockrate = (clockrate == -1) ? 8000 : clockrate;
                bitrate = (bitrate == -1) ? (clockrate == 8000 ? 64 : 128) : bitrate;
                ((RtpAudioPayload)this).setChannels(1);
                break;

            case 9:
                this.encoding = G722;
                clockrate = 8000;
                ((RtpAudioPayload)this).setChannels(1);
                break;

            case 10:
                this.encoding = L16;
                clockrate = 44100;
                ((RtpAudioPayload)this).setChannels(2);
                break;

            case 11:
                this.encoding = L16;
                clockrate = 44100;
                ((RtpAudioPayload)this).setChannels(1);
                break;

            case 14:
                this.encoding = MPA;
                clockrate = 90000;
                if (((RtpAudioPayload)this).channels() == 0) {
                    ((RtpAudioPayload)this).setChannels(1);
                }
                break;

            case 26:
                this.encoding = JPEG;
                clockrate = 90000;
                break;

            case 31:
                this.encoding = H261;
                clockrate = 90000;
                break;

            case 32:
                this.encoding = MPV;
                clockrate = 90000;
                break;

            case 33:
                this.encoding = MP2T;
                clockrate = 90000;
                break;

            case 34:
                this.encoding = H263;
                clockrate = 90000;
                break;

            default: // rtp payload type dynamic
                if (payload > 95) {
                    if (encoding.equals(AMR)) {
                        clockrate = 8000;
                        this.encoding = encoding;
                    } else if (encoding.equals(H264) || encoding.equals(H265)) {
                        clockrate = 90000;
                        this.encoding = encoding;
                    } else if (encoding.equals(MP4V_ES)) {
                        clockrate = 90000;
                        this.encoding = encoding;
                    } else if (encoding.equals(OPUS)) {
                        clockrate = 48000;
                        this.encoding = encoding;
                        ((RtpAudioPayload)this).setChannels(2);
                    } else if (encoding.equals(PCMA_WB) || encoding.equals(PCMU_WB)) {
                        clockrate = 16000;
                        this.encoding = encoding;
                        ((RtpAudioPayload)this).setChannels(1);
                    } else if (encoding.equals(VP8) || encoding.equals(VP9)) {
                        clockrate = 90000;
                        this.encoding = encoding;
                    } else {
                        this.encoding = encoding;
                    }

                } else {
                    this.encoding = encoding;
                }
        }
    }

    @Nullable
    private void buildSampleMimeType() {
        switch (type) {
            case AUDIO:
                switch (encoding) {
                    case PCMA:
                    case PCMA_WB:
                        this.sampleMimeType = MimeTypes.AUDIO_ALAW;
                        break;
                    case PCMU:
                    case PCMU_WB:
                        this.sampleMimeType = MimeTypes.AUDIO_MLAW;
                        break;
                    case AC3:
                        this.sampleMimeType = MimeTypes.AUDIO_AC3;
                        break;
                    case EAC3:
                        this.sampleMimeType = MimeTypes.AUDIO_E_AC3;
                        break;
                    case MPEG4GENERIC:
                        this.sampleMimeType = MimeTypes.AUDIO_AAC;
                        break;
                    case L16:
                        this.sampleMimeType = MimeTypes.AUDIO_L16;
                        break;
                    case MPA:
                    case MP4ALATM:
                        this.sampleMimeType = MimeTypes.AUDIO_MP4;
                        break;
                    case OPUS:
                        this.sampleMimeType = MimeTypes.AUDIO_OPUS;
                        break;
                }
                break;

            case VIDEO:
                switch (encoding) {
                    case H263:
                        this.sampleMimeType = MimeTypes.VIDEO_H263;
                        break;
                    case H264:
                        this.sampleMimeType = MimeTypes.VIDEO_H264;
                        break;
                    case H265:
                        this.sampleMimeType = MimeTypes.VIDEO_H265;
                        break;
                    case JPEG:
                        this.sampleMimeType = MimeTypes.VIDEO_MJPEG;
                        break;
                    case MP2T:
                        this.sampleMimeType = MimeTypes.VIDEO_MP2T;
                        break;
                    case MPV:
                        this.sampleMimeType = MimeTypes.VIDEO_MP4;
                        break;
                    case MP4V_ES:
                        this.sampleMimeType = MimeTypes.VIDEO_MP4V;
                        break;
                    case VP8:
                        this.sampleMimeType = MimeTypes.VIDEO_VP8;
                        break;
                    case VP9:
                        this.sampleMimeType = MimeTypes.VIDEO_VP9;
                        break;
                }
                break;
        }
    }

    public abstract void buildCodecProfileLevel();

    public abstract List<byte[]> buildCodecSpecificData();


    public static abstract class Builder {
        @MediaType int type;
        int payload;

        @MediaCodec String encoding;
        int clockrate = -1;
        int bitrate = -1;

        FormatSpecificParameters parameters = new FormatSpecificParameters();

        Builder(@MediaType int type) {
            if (type < 0) throw new NullPointerException("type is wrong");

            this.type = type;
        }

        public final Builder bitrate(int bitrate) {
            if (bitrate <= 0) throw new NullPointerException("bitrate is invalid");

            this.bitrate = bitrate;
            return this;
        }

        public final Builder payload(int payload) {
            if ((payload < 0) || (payload > 127) ) throw new NullPointerException("payload is out of range");

            this.payload = payload;
            return this;
        }

        public final Builder encoding(@MediaCodec String encoding) {
            if (encoding == null) throw new NullPointerException("encoding is null");

            this.encoding = encoding;
            return this;
        }

        public final Builder clockrate(int clockrate) {
            if (clockrate < 0) throw new NullPointerException("clockrate is negative");

            this.clockrate = clockrate;
            return this;
        }

        public final Builder addEncodingParameter(FormatSpecificParameter attribute) {
            if (attribute != null) {
                parameters.add(attribute);
            }

            return this;
        }

        public abstract RtpPayloadFormat build();
    }
}

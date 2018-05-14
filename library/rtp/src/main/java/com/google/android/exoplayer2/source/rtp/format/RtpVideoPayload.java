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

import android.util.Base64;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;

import java.util.ArrayList;
import java.util.List;

public final class RtpVideoPayload extends RtpPayloadFormat {
    private int width;
    private int height;
    private float framerate;
    private final int quality;
    private String codecs;

    private float pixelWidthAspectRatio;
    private List<byte[]> codecSpecificData = null;

    RtpVideoPayload(Builder builder) {
        super(builder);

        this.width = builder.width;
        this.height = builder.height;
        this.framerate = builder.framerate;
        this.quality = builder.quality;

        pixelWidthAspectRatio = 1.0f;
    }

    public int width() { return width; }

    public int height() { return height; }

    public float framerate() { return framerate; }

    public int quality() { return quality; }

    public String codecs() { return codecs; }

    public float pixelWidthAspectRatio() { return pixelWidthAspectRatio; }

    @Override
    public void buildCodecProfileLevel() {
        if (MimeTypes.VIDEO_H264.equals(sampleMimeType)) {
            if (parameters.contains(FormatSpecificParameter.PROFILE_LEVEL_ID)) {
                //  42:  Baseline
                //  4d:  Main
                //  58:  Extended
                //  64:  High
                //  6e:  High 10
                //  7a:  High 4:2:2
                //  f4:  High 4:4:4
                //  2c:  CAVLC 4:4:4
                codecs = "avc1." + parameters.value(FormatSpecificParameter.PROFILE_LEVEL_ID);
            }
        }
    }

    @Override
    public List<byte[]> buildCodecSpecificData() {
        if (codecSpecificData == null) {
            if (MimeTypes.VIDEO_H264.equals(sampleMimeType)) {
                if (parameters.contains(FormatSpecificParameter.SPROP_PARAMETER_SETS)) {
                    /*For H.264 MPEG4 Part15, the CodecPrivateData field must contain SPS and PPS
                    in the following form, base16-encoded: [start code][SPS][start code][PPS],
                    where [start code] is the following four bytes: 0x00, 0x00, 0x00, 0x01.
                    */
                    String[] paramSets = parameters.value(FormatSpecificParameter.SPROP_PARAMETER_SETS).
                            split(",");
                    if (paramSets.length == 2) {
                        codecSpecificData = new ArrayList<>();
                        for (String s : paramSets) {
                            if ((s != null) && (s.length() != 0)) {
                                byte[] nal = Base64.decode(s, Base64.DEFAULT);

                                if ((nal != null) && (nal.length != 0)) {
                                    codecSpecificData.add(CodecSpecificDataUtil.buildNalUnit
                                            (nal, 0, nal.length));
                                }
                            }
                        }

                        byte[] sps = codecSpecificData.get(0);
                        NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(
                                sps, 4, sps.length);

                        pixelWidthAspectRatio = spsData.pixelWidthAspectRatio;

                        if (width == Format.NO_VALUE && height == Format.NO_VALUE) {
                            width = spsData.width;
                            height = spsData.height;

                        } else if ((width != Format.NO_VALUE && spsData.width != width) ||
                                    (height != Format.NO_VALUE && spsData.height != height)) {
                            codecSpecificData.clear();
                            codecSpecificData = null;
                        }
                    }
                }
            }
        }

        return codecSpecificData;
    }

    public static final class Builder extends RtpPayloadFormat.Builder {
        int width;
        int height;
        float framerate;
        int quality;

        public Builder() {
            super(VIDEO);
            width = Format.NO_VALUE;
            height = Format.NO_VALUE;
            framerate = Format.NO_VALUE;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder framerate(float framerate) {
            this.framerate = framerate;
            return this;
        }

        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        @Override
        public RtpPayloadFormat build() {
            return new RtpVideoPayload(this);
        }
    }
}

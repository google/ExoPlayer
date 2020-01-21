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
package com.google.android.exoplayer2.source.sdp.core;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Attribute {
    private static final Pattern regexSDPAttribute = Pattern.compile("([a-zA-Z_-]*):\\s*(.+)|(\\w+)",
            Pattern.CASE_INSENSITIVE);

    public final static String CHARSET = "charset";
    public final static String CONTROL = "control";
    public final static String CLIPRECT = "cliprect";
    public final static String ETAG = "etag";
    public final static String FMTP = "fmtp";
    public final static String FRAMESIZE = "framesize";
    public final static String X_DIMENSIONS = "x-dimensions";
    public final static String X_FRAMERATE = "x-framerate";
    public final static String X_RTP_TS = "x-rtp-ts";
    public final static String X_QT_TEXT_NAM = "x-qt-text-nam";
    public final static String X_QT_TEXT_INF = "x-qt-text-inf";
    public final static String X_VENDORID = "x-vendor-id";
    public final static String FRAMERATE = "framerate";
    public final static String INACTIVE = "inactive";
    public final static String LENGTH = "length";
    public final static String MAXPTIME = "maxptime";
    public final static String MPEG4_ESID = "mpeg4-esid";
    public final static String RANGE = "range";
    public final static String RTCP_MUX = "rtcp-mux";
    public final static String RTPMAP = "rtpmap";
    public final static String PTIME = "ptime";
    public final static String QUALITY = "quality";
    public final static String SDPLANG = "sdplang";
    public final static String TS_REFCLK = "ts-refclk";
    public final static String MEDIACLK = "mediaclk";

    private String name;
    private String value;

    Attribute(String name) {
        this.name = name;
    }

    Attribute(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    @Nullable
    public static Attribute parse(String line) {
        try {

            Matcher matcher = regexSDPAttribute.matcher(line);

            if (matcher.find()) {
                return (matcher.group(3) == null) ?
                        new Attribute(matcher.group(1).trim(), matcher.group(2).trim()) :
                        new Attribute(matcher.group(3).trim());
            }

        } catch (Exception ex) {
        }

        return null;
    }
}

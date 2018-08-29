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

import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bandwidth {
    private static final Pattern regexSDPBandwidth = Pattern.compile("(\\S+)\\s*:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({AS, RS, RR, TIAS})
    public @interface BwType {}
    public static final String AS = "AS";
    public static final String RS = "RS";
    public static final String RR = "RR";
    public static final String TIAS = "TIAS";

    private @BwType String bwtype;
    private int bandwidth;

    Bandwidth(@BwType String bwtype, int bandwidth) {
        this.bwtype = bwtype;
        this.bandwidth = bandwidth;
    }

    public @BwType String bwtype() {
        return bwtype;
    }

    public int bandwidth() {
        return bandwidth;
    }

    @Nullable
    public static Bandwidth parse(String line) {
        try {

            Matcher matcher = regexSDPBandwidth.matcher(line);

            if (matcher.find()) {
                return new Bandwidth(matcher.group(1).trim(),
                        Integer.parseInt(matcher.group(2).trim()));
            }

        } catch (Exception ex) {
        }

        return null;
    }
}

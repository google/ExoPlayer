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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Time {
    private static final Pattern regexSDPTime = Pattern.compile("(\\d+[d|h|m|s]?)\\s*(\\d+[d|h|m|s]?)",
            Pattern.CASE_INSENSITIVE);

    private long startTime;
    private long stopTime;

    Time(long startTime, long stopTime) {
        this.startTime = startTime;
        this.stopTime = stopTime;
    }

    public long startTime() {
        return startTime;
    }

    public long stopTime() {
        return stopTime;
    }

    /** Returns whether the start and stop times were set to zero (in NTP).
     * @return boolean
     */
    public boolean isZero() {
        return (startTime == 0) && (stopTime == 0);
    }

    private static long getSecondsFromTypedTime(String typedTime) {
        if (typedTime.endsWith("d")) {
            return Integer.parseInt(typedTime.replace('d', ' ').trim()) * 86400;
        } else if (typedTime.endsWith("h")) {
            return Integer.parseInt(typedTime.replace('h', ' ').trim()) * 3600;
        } else if (typedTime.endsWith("m")) {
            return Integer.parseInt(typedTime.replace('m', ' ').trim()) * 60;
        } else {
            return Integer.parseInt(typedTime.replace('s', ' ').trim());
        }
    }

    @Nullable
    public static Time parse(String line) {
        try {

            Matcher matcher = regexSDPTime.matcher(line);

            if (matcher.find()) {
                return new Time(getSecondsFromTypedTime(matcher.group(1).trim()),
                        getSecondsFromTypedTime(matcher.group(2).trim()));
            }

        } catch (Exception ex) {

        }

        return null;
    }
}

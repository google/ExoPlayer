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

import com.google.android.exoplayer2.C;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Range {
    // We only support the Normal Play Time (npt)
    private static final Pattern regexRange =
            Pattern.compile("npt=\\s*(\\d+:[0-5][0-9]:[0-5][0-9]|now|\\d+.\\d+|\\d*)?\\s*-\\s*" +
                    "(\\d+:[0-5][0-9]:[0-5][0-9]|end|\\d+.\\d+|\\d*)?\\s*");

    private final String range;
    private final double startTime;
    private final double endTime;
    private final long duration;

    Range(String range, double startTime, double endTime) {
        this.range = range;
        this.startTime = startTime;
        this.endTime = endTime;

        if ((startTime != C.TIME_UNSET) && (endTime != C.TIME_UNSET)) {
            duration = (long)(endTime - startTime) * C.MICROS_PER_SECOND;
        } else {
            duration = C.TIME_UNSET;
        }
    }

    public double startTime() { return startTime; }

    public double endTime() { return endTime; }

    public long duration() {
        return duration;
    }

    @Nullable
    public static Range parse(String rangeNpt) {
        try {

            Matcher matcher = regexRange.matcher(rangeNpt);

            if (matcher.find()) {

                double startTime = C.TIME_UNSET;
                double endTime = C.TIME_UNSET;

                if (matcher.group(1) != null) {

                    try {
                        startTime = Double.parseDouble(matcher.group(1));

                    } catch (NumberFormatException ex) {
                        String start = matcher.group(1);

                        if (!start.equals("now") && !start.equals("")) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                                startTime = sdf.parse(start).getTime();

                            } catch (ParseException e) {
                                return null;
                            }
                        }
                    }

                    try {
                        endTime = Double.parseDouble(matcher.group(2));

                    } catch (NumberFormatException ex) {
                        String end = matcher.group(2);

                        if (!end.equals("end") && !end.equals("")) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                                endTime = sdf.parse(end).getTime();

                            } catch (ParseException e) {
                                return null;
                            }
                        }
                    }

                    String range = matcher.group(1) + "-" + matcher.group(2);
                    return new Range(range, startTime, endTime);
                }
            }
        }
        catch (Exception ex) {

        }

        return null;
    }

    /**
     * Returns the string used to identify this range
     */
    @Override public String toString() {
        StringBuilder str = new StringBuilder().append("npt=").append(range);
        return str.toString();
    }
}

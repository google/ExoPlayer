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

public final class Information {

    private static final Pattern regexSDPInformation = Pattern.compile("(\\w+)?",
            Pattern.CASE_INSENSITIVE);

    private String information;

    Information(String information) {
        this.information = information;
    }

    public String info() {
        return information;
    }

    @Nullable
    public static Information parse(String line) {
        try {

            Matcher matcher = regexSDPInformation.matcher(line);

            if (matcher.find()) {
                return new Information(matcher.group(1).trim());
            }
        }
        catch (Exception ex) {

        }

        return null;
    }
}

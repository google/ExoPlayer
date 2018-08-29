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

public final class Media {
    private static final Pattern regexSDPMedia =
            Pattern.compile("(\\S+)\\s+(\\d+|\\d+\\/\\d+)\\s+(\\S+)\\s+(.+)",
                    Pattern.CASE_INSENSITIVE);

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({audio, video, text, application, message})
    public @interface MediaType {}
    public static final String audio = "audio";
    public static final String video = "video";
    public static final String text = "text";
    public static final String application = "application";
    public static final String message = "message";

    private @MediaType String type;
    private String port;
    private String proto;
    private String fmt;

    Media(@MediaType String type, String port, String proto, String fmt) {
        this.type = type;
        this.port = port;
        this.proto = proto;
        this.fmt = fmt;
    }

    public @MediaType String type() {
        return type;
    }

    public String port() {
        return port;
    }

    public String proto() {
        return proto;
    }

    public String fmt() {
        return fmt;
    }

    @Nullable
    public static Media parse(String line) {
        try {

            Matcher matcher = regexSDPMedia.matcher(line);

            if (matcher.find()) {

                @MediaType String media = matcher.group(1).trim();

                if (media != null) {
                    return new Media(media, matcher.group(2).trim(),
                            matcher.group(3).trim(), matcher.group(4).trim());
                }
            }
        }
        catch (Exception ex) {

        }

        return null;
    }
}

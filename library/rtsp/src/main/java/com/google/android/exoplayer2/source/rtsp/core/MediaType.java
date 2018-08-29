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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Media Type, appropriate to describe the content type of an RTSP request or response body.
 */
public final class MediaType {
    private static final Pattern regexMediaType = Pattern.compile("(\\w+)/(\\w+)");

    public static final MediaType APPLICATION_SDP = MediaType.parse("application/sdp");
    public static final MediaType TEXT_PARAMETERS = MediaType.parse("text/parameters");

    private final String mediaType;
    private final String type;
    private final String subtype;

    private MediaType(String mediaType, String type, String subtype) {
        this.mediaType = mediaType;
        this.type = type;
        this.subtype = subtype;
    }

    /**
     * Returns a media type for {@code string}, or null if {@code string} is not a well-formed media
     * type.
     */
    public static @Nullable
    MediaType parse(String string) {
        Matcher typeSubtype = regexMediaType.matcher(string);

        if (!typeSubtype.lookingAt()) return null;

        String type = typeSubtype.group(1).toLowerCase(Locale.US);
        String subtype = typeSubtype.group(2).toLowerCase(Locale.US);

        return new MediaType(string, type, subtype);
    }

    /**
     * Returns the high-level media type, such as "application" or "text".
     */
    public String type() {
        return type;
    }

    /**
     * Returns a specific media subtype, such as "sdp", "rtsl", "mheg" or "parameters".
     */
    public String subtype() {
        return subtype;
    }

    /**
     * Returns the encoded media type, like "application/sdp", "application/rtsl", "application/mheg"
     * or "text/parameters", appropriate for use in a Content-Type header.
     */
    @Override public String toString() {
        return mediaType;
    }

    @Override public boolean equals(@Nullable Object other) {
        return other instanceof MediaType && ((MediaType) other).mediaType.equals(mediaType);
    }
}


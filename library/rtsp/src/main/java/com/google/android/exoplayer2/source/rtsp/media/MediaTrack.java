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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaTrack {
    private static final Pattern rexegUrl = Pattern.compile("trackID=(\\S+)",
            Pattern.CASE_INSENSITIVE);

    private final String url;
    private final String trackId;
    private final String language;
    private final MediaFormat format;

    MediaTrack(Builder builder) {
        this.url = builder.url;
        this.trackId = builder.trackId;
        this.language = builder.language;
        this.format = builder.format;
    }

    public String url() { return url; }

    public String trackId() { return trackId; }

    public String language() { return language; }

    public MediaFormat format() { return format; }


    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MediaTrack) ? ((MediaTrack)obj).url().equals(url) : false;
    }

    public static class Builder {
        String url;
        String trackId;
        String language;
        MediaFormat format;

        public final Builder url(String url) {
            if (url == null) throw new NullPointerException("url is null");

            Matcher matcher = rexegUrl.matcher(url);
            if (matcher.find()) {
                trackId = matcher.group(1);
            }

            this.url = url;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public final Builder format(MediaFormat format) {
            if (format == null) throw new NullPointerException("format is null");

            this.format = format;
            return this;
        }

        public MediaTrack build() {
            if (url == null) throw new IllegalStateException("url is null");
            if (format == null) throw new IllegalStateException("format is null");

            return new MediaTrack(this);
        }
    }
}

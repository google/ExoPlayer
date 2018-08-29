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

public final class Key {

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({base64, clear, prompt, uri})
    public @interface KeyType {}
    private static final String base64 = "base64";
    private static final String clear = "clear";
    private static final String prompt = "prompt";
    private static final String uri = "uri";

    private static final Pattern regexSDPKey = Pattern.compile("(prompt)|(clear|base64|uri):\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE);

    private @KeyType String type;
    private String value;

    Key(@KeyType String type) {
        this.type = type;
    }

    Key(@KeyType String type, String value) {
        this.type = type;
        this.value = value;
    }

    @KeyType
    public String type() {
        return type;
    }

    public String value() {
        return value;
    }

    @Nullable
    public static Key parse(String line) {
        try {

            Matcher matcher = regexSDPKey.matcher(line);

            if (matcher.find()) {

                @KeyType String keyType = matcher.group(1);

                switch (keyType) {

                    case prompt:
                        return new Key(keyType);

                    case uri:
                    case clear:
                    case base64:
                        return new Key(keyType, matcher.group(2).trim());
                }
            }

        } catch (Exception ex) {
        }

        return null;
    }
}

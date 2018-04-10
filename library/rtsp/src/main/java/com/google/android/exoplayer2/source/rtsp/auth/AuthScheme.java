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
package com.google.android.exoplayer2.source.rtsp.auth;

import android.support.annotation.Nullable;

import java.io.IOException;

public enum AuthScheme {
    BASIC("Basic"),
    DIGEST("Digest");

    private final String scheme;

    AuthScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Returns the protocol identified by {@code scheme}.
     *
     * @throws IOException if {@code scheme} is unknown.
     */
    @Nullable
    public static AuthScheme parse(String scheme) throws IOException {
        for (AuthScheme authScheme : AuthScheme.values()) {
            if (authScheme.scheme.equalsIgnoreCase(scheme)) {
                return authScheme;
            }
        }

        return null;
    }

    /**
     * Returns the string used to identify this protocol
     */
    @Override public String toString() {
        return scheme;
    }
}
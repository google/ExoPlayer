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

public enum Method {
    ANNOUNCE("ANNOUNCE"),
    DESCRIBE("DESCRIBE"),
    GET_PARAMETER("GET_PARAMETER"),
    OPTIONS("OPTIONS"),
    PAUSE("PAUSE"),
    PLAY("PLAY"),
    RECORD("RECORD"),
    REDIRECT("REDIRECT"),
    SET_PARAMETER("SET_PARAMETER"),
    SETUP("SETUP"),
    TEARDOWN("TEARDOWN");

    private final String name;

    Method(String name) {
        this.name = name;
    }

    /**
     * Returns the method identified by {@code method}.
     * Returns null if the {@code method} is unknown.
     */
    @Nullable
    public static Method parse(String name) {
        for (Method method : Method.values()) {
            if (method.name.equalsIgnoreCase(name)) {
                return method;
            }
        }

        return null;
    }

    /**
     * Returns the string used to identify this method
     */
    @Override public String toString() {
        return name;
    }
}

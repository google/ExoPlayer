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

import com.google.android.exoplayer2.source.rtsp.core.Request;

import java.util.Map;

public abstract class Credentials {
    protected Map<String, String> params;

    Credentials(Map params) {
        this.params = params;
    }

    public final Map<String, String> params() { return params; }

    public abstract String username();
    public abstract String password();

    public abstract void applyToRequest(Request request);

    public interface Builder {
        Builder username(String username);
        Builder password(String username);
        Builder setParam(String param, String value);

        Credentials build();
    }
}

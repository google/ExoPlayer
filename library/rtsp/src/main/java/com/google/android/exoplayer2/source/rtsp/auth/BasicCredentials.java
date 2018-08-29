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

import com.google.android.exoplayer2.source.rtsp.core.Header;
import com.google.android.exoplayer2.source.rtsp.core.Request;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BasicCredentials extends Credentials {

    public static final String REALM = "realm";

    private final String username;
    private final String password;

    BasicCredentials(BasicCredentials.Builder builder) {
        super(builder.params);

        username = builder.username;
        password = builder.password;
    }

    public final String realm() { return params.get(REALM); }

    public final String username() { return username; }

    public final String password() { return password; }

    private String generate(String token) {
        StringBuilder str = new StringBuilder();
        str.append(AuthScheme.BASIC).append(' ').append(token);

        return str.toString();
    }

    public final void applyToRequest(Request request) {
        BasicAuthCipher basicAuthCipher = new BasicAuthCipher.Builder().
                username(username()).password(password()).build();

        request.getHeaders().add(Header.Authorization.toString(),
                generate(basicAuthCipher.token()));
    }

    public static BasicCredentials parse(String credentials) {
        if (credentials == null) throw new NullPointerException("credentials is null");

        BasicCredentials.Builder builder = new BasicCredentials.Builder();

        String[] attrs = credentials.split(",");

        for (String attr : attrs) {
            String[] params = attr.split(",");
            builder.setParam(params[0], params[1]);
        }

        return (BasicCredentials) builder.build();
    }

    public static class Builder implements Credentials.Builder {
        private final Map<String, String> params;

        private String username;
        private String password;

        public Builder() {
            params = new LinkedHashMap<>();
        }

        public Builder(String credentials) {
            params = BasicCredentials.parse(credentials).params();
        }

        public Builder(BasicCredentials credentials) {
            if (credentials == null) {
                params = new LinkedHashMap<>();
            }
            else {
                params = credentials.params;
                username = credentials.username;
                password = credentials.password;
            }
        }

        @Override
        public Credentials.Builder username(String username) {
            if (username == null) throw new NullPointerException("username is null");

            this.username = username;
            return this;
        }

        @Override
        public Credentials.Builder password(String password) {
            if (password == null) throw new NullPointerException("password is null");

            this.password = password;
            return this;
        }

        @Override
        public Credentials.Builder setParam(String name, String value) {
            if (name == null) throw new NullPointerException("param name is null");
            if (value == null) throw new NullPointerException("param value is null");

            if (name.equalsIgnoreCase(REALM)) {
                params.put(name, value);
            }
            else
                throw new IllegalStateException("param is unknown");

            return this;
        }

        @Override
        public Credentials build() {
            if (username == null) throw new NullPointerException("username is null");
            if (password == null) throw new NullPointerException("password is null");
            if (params.get(REALM) == null) throw new NullPointerException("realm is null");

            return new BasicCredentials(this);
        }
    }
}

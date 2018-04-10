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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DigestCredentials extends Credentials {
    public static final String REALM = "realm";
    public static final String NONCE = "nonce";
    public static final String CNONCE = "cnonce";
    public static final String QOP = "qop";
    public static final String NC = "nc";
    public static final String OPAQUE = "opaque";
    public static final String RESPONSE = "response";
    public static final String URI = "uri";
    public static final String USERNAME = "username";
    public static final String DOMAIN = "domain";
    public static final String ALGORITHM = "algorithm";
    public static final String STALE = "stale";

    private final String username;
    private final String password;

    private int nonceCount;
    private String lastNonce;

    DigestCredentials(DigestCredentials.Builder builder) {
        super(builder.params);

        username = builder.username;
        password = builder.password;
    }

    public final String realm() { return params.get(REALM); }

    public final String nonce() { return params.get(NONCE); }

    public final String cnonce() { return params.get(CNONCE); }

    public final String qop() { return params.get(QOP); }

    public final String nc() { return params.get(NC); }

    public final String opaque() { return params.get(OPAQUE); }

    public final String uri() { return params.get(URI); }

    public final String username() { return username; }

    public final String password() { return password; }

    public final String domain() { return params.get(DOMAIN); }

    public final String algorithm() { return params.get(ALGORITHM); }

    public final String stale() { return params.get(STALE); }

    public final String response() { return params.get(RESPONSE); }

    private String generate() {
        StringBuilder str = new StringBuilder();
        str.append(AuthScheme.DIGEST).append(' ');

        if (username() != null) {
            str.append(USERNAME).append('=').append('\"').append(username()).append('\"').append(", ");
        }

        str.append(REALM).append('=').append('\"').append(realm()).append('\"');
        str.append(", ").append(NONCE).append('=').append('\"').append(nonce()).append('\"');

        if (qop() != null) {
            str.append(", ").append(QOP).append('=').append('\"').append(qop()).append('\"');
        }

        if (uri() != null) {
            str.append(", ").append(URI).append('=').append('\"').append(uri()).append('\"');
        }

        if (nc() != null) {
            str.append(", ").append(NC).append('=').append('\"').append(nc()).append('\"');
        }

        if (cnonce() != null) {
            str.append(", ").append(CNONCE).append('=').append('\"').append(cnonce()).append('\"');
        }

        if (opaque() != null) {
            str.append(", ").append(OPAQUE).append('=').append('\"').append(opaque()).append('\"');
        }

        if (domain() != null) {
            str.append(", ").append(DOMAIN).append('=').append('\"').append(domain()).append('\"');
        }

        if (algorithm() != null) {
            str.append(", ").append(ALGORITHM).append('=').append('\"').append(algorithm()).append('\"');
        }

        if (stale() != null) {
            str.append(", ").append(STALE).append('=').append('\"').append(stale()).append('\"');
        }

        if (response() != null) {
            str.append(", ").append(RESPONSE).append('=').append('\"').append(response()).append('\"');
        }

        return str.toString();
    }

    private String getNonceCount(String nonce) {
        nonceCount = nonce.equals(lastNonce) ? nonceCount + 1 : 1;
        lastNonce = nonce;

        NumberFormat nf;
        nf = NumberFormat.getIntegerInstance();
        ((DecimalFormat) nf).applyPattern("#00000000");

        return nf.format(nonceCount);
    }

    private int getRandom() {
        return 10 + (int) (Math.random() * ((Integer.MAX_VALUE - 10) + 1));
    }

    private String generateClientNonce(String nonce, String nc) {
        return MD5.hash(nc + nonce + System.currentTimeMillis() + getRandom());
    }

    public final void applyToRequest(Request request) {

        String nc = getNonceCount(nonce());
        String cNonce = generateClientNonce(nonce(), nc);

        DigestCredentials.Builder builder = new DigestCredentials.
                Builder(this);

        if ("auth".equals(qop()) ||
                "auth-int".equals(qop())) {
            builder.setParam(DigestCredentials.CNONCE, cNonce);
            builder.setParam(DigestCredentials.NC, nc);
        }

        DigestCredentials newCredentials = (DigestCredentials) builder.build();

        DigestAuthCipher digestAuthCipher = new DigestAuthCipher.Builder().
                credentials(newCredentials).username(username()).password(password()).
                method(request.getMethod()).uri(request.getUrl()).
                body(request.getMessageBody())
                .build();

        Credentials credentials = new DigestCredentials.
                Builder(newCredentials).
                setParam(DigestCredentials.URI, request.getUrl()).
                setParam(DigestCredentials.USERNAME, username()).
                setParam(DigestCredentials.RESPONSE, digestAuthCipher.token()).build();

        this.lastNonce = nonce();
        this.params = credentials.params;

        request.getHeaders().add(Header.Authorization.toString(),
                generate());
    }

    public static DigestCredentials parse(String credentials) {
        if (credentials == null) throw new NullPointerException("credentials is null");

        DigestCredentials.Builder builder = new DigestCredentials.Builder();

        String[] attrs = credentials.split(",");

        for (String attr : attrs) {
            String[] params = attr.split("=");

            String name = params[0].trim();
            String value = params[1].trim();

            builder.setParam(name, value.substring(1, value.length()-1));
        }

        return (DigestCredentials) builder.build();
    }

    public static class Builder implements Credentials.Builder {
        private final Map<String, String> params;
        private String username;
        private String password;

        public Builder() {
              params = new LinkedHashMap<>();
        }

        public Builder(String credentials) {
            params = new LinkedHashMap<>();

            String[] attrs = credentials.split(",");

            for (String attr : attrs) {
                String[] params = attr.split("=");

                String name = params[0].trim();
                String value = params[1].trim();

                setParam(name, value.substring(1, value.length()-1));
            }
        }

        Builder(DigestCredentials credentials) {
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
            if (name == null) throw new NullPointerException("name is null");
            if (value == null) throw new NullPointerException("value is null");

            if (name.equalsIgnoreCase(REALM) || name.equalsIgnoreCase(NONCE) ||
                    name.equalsIgnoreCase(CNONCE) || name.equalsIgnoreCase(NC) ||
                    name.equalsIgnoreCase(OPAQUE) || name.equalsIgnoreCase(RESPONSE) ||
                    name.equalsIgnoreCase(URI) || name.equalsIgnoreCase(USERNAME) ||
                    name.equalsIgnoreCase(DOMAIN) || name.equalsIgnoreCase(ALGORITHM) ||
                    name.equalsIgnoreCase(STALE)) {
                params.put(name, value);
            }
            else if (name.equalsIgnoreCase(QOP)) {
                if (value.equalsIgnoreCase("auth") || value.equalsIgnoreCase("auth-int")) {
                    params.put(name, value);
                }
                else
                    throw new IllegalStateException("value is invalid");
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
            if (params.get(NONCE) == null) throw new NullPointerException("nonce is null");
            if (params.get(URI) == null) throw new NullPointerException("uri is null");

            return new DigestCredentials(this);
        }
    }
}

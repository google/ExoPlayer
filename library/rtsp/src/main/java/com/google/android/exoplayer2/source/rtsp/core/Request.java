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

import java.util.Map;

/**
 * An RTSP request
 */
public final class Request extends Message {
    String url;
    Method method;

    Request(Builder builder) {
        super(Message.REQUEST);

        this.url = builder.url;
        this.protocol = builder.protocol;
        this.method = builder.method;
        this.headers = builder.headers;
        this.body = builder.body;
    }

    public String getUrl() {
        return url;
    }

    @Nullable
    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(method).append(' ').append(url).append(' ').append(protocol).append("\r\n");

        for (Map.Entry<String, String> entry : headers.getSnapshot().entrySet()) {
            String header = entry.getKey();
            String value = entry.getValue();
            str.append(header).append(": ").append(value).append("\r\n");
        }

        if (body != null) {
            str.append(Header.ContentType).append(": ").append(body.getContentType()).append("\r\n");
            str.append(Header.ContentLength).append(": ").append(body.getContentLength()).append("\r\n");
            str.append("\r\n").append(body.getContent());
        } else {
            str.append("\r\n");
        }

        return str.toString();
    }


    public static class Builder implements Message.Builder {
        String url;
        Method method;
        Protocol protocol;
        Headers headers;
        MessageBody body;

        public Builder() {
            this.protocol = Protocol.RTSP_1_0;
            this.headers = new Headers();
        }

        public Builder(Request request) {
            this.url = request.url;
            this.protocol = request.protocol;
            this.method = request.method;
            this.headers = request.headers;
            this.body = request.body;
        }

        public Builder url(String url) {
            if (url == null) throw new NullPointerException("url is null");

            if (url.regionMatches(true, 0, "rtsp:", 0, 5) || url.equals("*")) {
                this.url = url;
            } else {
                if (url.contains("://")) {
                    throw new IllegalArgumentException("scheme is invalid");
                }
                this.url = "rtsp://" + url;
            }

            return this;
        }

        public Builder method(Method method) {
            if (method == null) throw new NullPointerException("method is null");

            if (method.equals(Method.OPTIONS) ||
                    method.equals(Method.DESCRIBE) ||
                    method.equals(Method.SETUP) ||
                    method.equals(Method.PLAY) ||
                    method.equals(Method.PAUSE) ||
                    method.equals(Method.ANNOUNCE) ||
                    method.equals(Method.GET_PARAMETER) ||
                    method.equals(Method.TEARDOWN)) {
                this.method = method;

            } else {
                throw new IllegalArgumentException("method is invalid");
            }

            return this;
        }

        @Override
        public Builder protocol(Protocol protocol) {
            if (protocol != null) {
                this.protocol = protocol;
            }
            return this;
        }

        /**
         * Adds a header with {@code name} and {@code value}.
         *
         */
        @Override
        public Builder header(Header header, Object value) {
            if (header == null) throw new NullPointerException("header is null");
            if (value == null) throw new NullPointerException("value is null");

            headers.add(header.toString(), value.toString());
            return this;
        }

        @Override
        public Builder body(MessageBody body) {
            if (body == null) throw new NullPointerException("body is null");

            this.body = body;
            return this;
        }

        public Builder options() { return method(Method.OPTIONS); }

        public Builder describe() { return method(Method.DESCRIBE); }

        public Builder setup() { return method(Method.SETUP); }

        public Builder play() { return method(Method.PLAY); }

        public Builder pause() { return method(Method.PAUSE); }

        public Builder announce() { return method(Method.ANNOUNCE); }

        public Builder get_parameter() { return method(Method.GET_PARAMETER); }

        public Builder set_parameter() { return method(Method.SET_PARAMETER); }

        public Builder teardown() { return method(Method.TEARDOWN); }

        public Builder record() { return method(Method.RECORD); }

        @Override
        public Request build() {
            if (url == null) throw new IllegalStateException("url is null");
            if (method == null) throw new IllegalStateException("method is null");
            if (protocol == null) throw new IllegalStateException("protocol is null");
            if (headers.size() == 0) throw new IllegalStateException("headers are empty");

            return new Request(this);
        }
    }
}

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

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public abstract class Message {
    @Retention(SOURCE)
    @IntDef({NONE, REQUEST, RESPONSE})
    public @interface MessageType {
    }

    public static final int NONE = 0;
    public static final int REQUEST = 1;
    public static final int RESPONSE = 2;

    protected final @MessageType int type;

    protected Protocol protocol;
    protected Headers headers;
    protected MessageBody body;

    Message(@MessageType int type) {
        this.type = type;
        headers = new Headers();
    }

    public abstract String toString();

    public
    @MessageType
    int getType() {
        return type;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Headers getHeaders() {
        return headers;
    }

    public MessageBody getMessageBody() {
        return body;
    }


    public interface Builder {
        Builder protocol(Protocol protocol);
        Builder header(Header header, Object value);
        Builder body(MessageBody messageBody);

        Message build();
    }
}

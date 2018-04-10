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

/**
 * Headers that RTSP implements.
 *
 */
public enum Header {
    Accept("Accept"),
    AcceptEncoding("Accept-Encoding"),
    AcceptLanguage("Accept-Language"),
    Allow("Allow"),
    Authorization("Authorization"),
    Bandwidth("Bandwidth"),
    Blocksize("Blocksize"),
    CacheControl("Cache-Control"),
    Conference("Conference"),
    Connection("Connection"),
    ContentBase("Content-Base"),
    ContentEncoding("Content-Encoding"),
    ContentLanguage("Content-Language"),
    ContentLength("Content-Length"),
    ContentLocation("Content-Location"),
    ContentType("Content-Type"),
    CSeq("CSeq"),
    Date("Date"),
    EventType("Event-Type"),
    Expires("Expires"),
    From("From"),
    Host("Host"),
    IfMatch("If-Match"),
    IfModifiedSince("If-Modified-Since"),
    LastModified("Last-Modified"),
    Location("Location"),
    Notice("Notice"),
    ProxyAuthenticate("Proxy-Authenticate"),
    ProxyRequire("Proxy-Require"),
    Public("Public"),
    Range("Range"),
    Reason("Reason"),
    Referer("Referer"),
    RetryAfter("Retry-After"),
    Require("Require"),
    RTPInfo("RTP-Info"),
    Scale("Scale"),
    Session("Session"),
    Server("Server"),
    Speed("Speed"),
    Supported("Supported"),
    Timestamp("Timestamp"),
    Transport("Transport"),
    Unsupported("Unsupported"),
    UserAgent("User-Agent"),
    Vary("Vary"),
    Via("Via"),
    W3Authenticate("WWW-Authenticate"),
    xAcceptRetransmit("x-Accept-Retransmit"),
    xAcceptDynamicRate("x-Accept-Dynamic-Rate"),
    XmayNotify("x-mayNotify"),
    XplayNow("x-playNow"),
    XnoFlush("x-noFlush");

    private final String name;

    Header(String name) {
        this.name = name;
    }

    /**
     * Returns the header identified by {@code header}.
     * Returns null if the {@code header} is unknown.
     */
    @Nullable
    public static Header parse(String name) {
        for (Header header : Header.values()) {
            if (header.name.equalsIgnoreCase(name)) {
                return header;
            }
        }

        return null;
    }

    /**
     * Returns the string used to identify this header
     */
    @Override public String toString() {
        return name;
    }
}

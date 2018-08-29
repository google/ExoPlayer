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

import java.io.IOException;

public enum Status {
    Continue(100),

    OK(200), Created(201), LowOnStorageSpace(250, "Low on Storage Space"),

    MultipleChoices(300, "Multiple Choices"), MovedPermanently(301, "Multiple Choices"),
    MovedTemporarily(302, "Moved Temporarily"), SeeOther(303, "See Other"),
    NotModified(304, "Not Modified"), UseProxy(305, "Use Proxy"),

    BadRequest(400, "Bad Request"), Unauthorized(401), PaymentRequired(402, "Payment Required"),
    Forbidden(403), NotFound(404, "Not Found"), MethodNotAllowed(405, "Method Not Allowed"),
    NotAcceptable(406, "Not Acceptable"),
    ProxyAuthenticationRequired(407, "Proxy Authentication Required"),
    RequestTimeOut(408, "Request Time-out"), Gone(410), LengthRequired(411, "Length Required"),
    PreconditionFailed(412, "Precondition Failed"),
    RequestEntityTooLarge(413, "Request Entity Too Large"),
    RequestUriTooLarge(414, "Request-URI Too Large"),
    UnsupportedMediaType(415, "Unsupported Media Type"),ExpectationFailed(417, "Expectation Failed"),
    ParameterNotUnderstood(451, "Parameter Not Understood"),
    ConferenceNotFound(452, "Conference Not Found"), NotEnoughBandwidth(453, "Not Enough Bandwidth"),
    SessionNotFound(454, "Session Not Found"),
    MethodNotValidInThisState(455, "Method Not Valid in This State"),
    HeaderFieldNotValidForResource(456, "Header Field Not Valid for Resource"),
    InvalidRange(457, "Invalid Range"), ParameterIsReadOnly(458, "Parameter Is Read-Only"),
    AggregateOperationNotAllowed(459, "Aggregate Operation Not Allowed"),
    OnlyAggregateOperationAllowed(460, "Only Aggregate Operation Allowed"),
    UnsupportedTransport(461, "Unsupported Transport"),
    DestinationUnreachable(462, "Destination Unreachable"), NoSuchContent(499, "No Such Content"),

    InternalServerError(500, "Internal Server Error"),
    NotImplemented(501, "Not Implemented"), BadGateway(502, "Bad Gateway"),
    ServiceUnavailable(503, "Service Unavailable"), GatewayTimeOut(504, "Gateway Time-out"),
    RtspVersionNotSupported(505, "RTSP Version not supported"),
    OptionNotSupported(551, "Option not supported");

    private final int value;
    private final String reason;

    Status(int value, String reason) {
        this.value = value;
        this.reason = reason;
    }

    Status(int value) {
        this.value = value;
        this.reason = null;
    }

    /**
     * @return the numeric value of the RTSP code
     */
    public int code() {
        return this.value;
    }

    /**
     * @return the human-readable description of the RTSP code
     */
    public String reason() {
        if (reason != null)
            return reason;
        else
            return name();
    }

    /**
     * Returns the status identified by {@code code}.
     *
     * @throws IOException if {@code code} is unknown.
     */
    @Nullable
    public static Status parse(int code) throws IOException {
        for (Status status : Status.values()) {
            if (status.code() == code) return status;
        }

        return null;
    }

    /**
     * Returns the string used to identify this status
     */
    @Override public String toString() {
        return reason();
    }
}

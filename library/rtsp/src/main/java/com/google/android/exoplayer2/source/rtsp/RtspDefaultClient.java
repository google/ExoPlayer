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
package com.google.android.exoplayer2.source.rtsp;

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.source.rtsp.api.Client;
import com.google.android.exoplayer2.source.rtsp.core.Header;
import com.google.android.exoplayer2.source.rtsp.core.MediaType;
import com.google.android.exoplayer2.source.rtsp.core.Range;
import com.google.android.exoplayer2.source.rtsp.core.Request;
import com.google.android.exoplayer2.source.rtsp.core.Transport;

public final class RtspDefaultClient extends Client {

    private static final String USER_AGENT = ExoPlayerLibraryInfo.VERSION_SLASHY +
            " (Media Player for Android)";

    public static Client.Factory<RtspDefaultClient> factory() {
        return new Client.Factory<RtspDefaultClient>() {
            public RtspDefaultClient create(Client.Builder builder) {
                return new RtspDefaultClient(builder);
            }
        };
    }


    RtspDefaultClient(Builder builder) {
        super(builder);
    }

    @Override
    protected String userAgent() {
        return USER_AGENT;
    }

    @Override
    protected void sendOptionsRequest() {
        Request.Builder builder = new Request.Builder().options().url("*");
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);

        dispatch(builder.build());
    }

    @Override
    protected void sendDescribeRequest() {
        Request.Builder builder = new Request.Builder().describe().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Accept, MediaType.APPLICATION_SDP);

        dispatch(builder.build());
    }

    @Override
    public void sendSetupRequest(String trackId, Transport transport, int localPort) {
        Request.Builder builder = new Request.Builder().setup().url(trackId);
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);

        builder.header(Header.Transport, transport + ";client_port=" + localPort);

        dispatch(builder.build());
    }

    @Override
    public void sendPlayRequest(Range range) {
        Request.Builder builder = new Request.Builder().play().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());
        builder.header(Header.Range, range);

        dispatch(builder.build());
    }

    @Override
    public void sendPlayRequest(Range range, float scale) {
        Request.Builder builder = new Request.Builder().play().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());
        builder.header(Header.Range, range);
        builder.header(Header.Scale, scale);

        dispatch(builder.build());
    }

    @Override
    public void sendPauseRequest() {
        Request.Builder builder = new Request.Builder().pause().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());

        dispatch(builder.build());
    }

    @Override
    protected void sendRecordRequest() {
        // Not Implemented
    }

    @Override
    protected void sendGetParameterRequest() {
        Request.Builder builder = new Request.Builder().get_parameter().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());

        dispatch(builder.build());
    }

    @Override
    protected void sendSetParameterRequest(String name, String value) {
        // Not Implemented
    }

    @Override
    public void sendTeardownRequest() {
        Request.Builder builder = new Request.Builder().teardown().url(session.uri().toString());
        builder.header(Header.CSeq, session.nexCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());

        dispatch(builder.build());
    }
}

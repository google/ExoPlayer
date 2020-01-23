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
import com.google.android.exoplayer2.source.rtsp.core.Client;
import com.google.android.exoplayer2.source.rtsp.message.Header;
import com.google.android.exoplayer2.source.rtsp.media.MediaType;
import com.google.android.exoplayer2.source.rtsp.message.Range;
import com.google.android.exoplayer2.source.rtsp.message.Request;
import com.google.android.exoplayer2.source.rtsp.message.Transport;
import com.google.android.exoplayer2.source.rtsp.media.MediaTrack;

public final class RtspDefaultClient extends Client {

    private static final String USER_AGENT = ExoPlayerLibraryInfo.VERSION_SLASHY +
            " (Media Player for Android)";

    public static Factory<RtspDefaultClient> factory() {
        return new Factory<RtspDefaultClient>() {
            private @Flags int flags;
            private @Mode int mode;
            private @NatMethod int natMethod;

            public Factory<RtspDefaultClient> setFlags(@Flags int flags) {
                this.flags = flags;
                return this;
            }

            public Factory<RtspDefaultClient> setMode(@Mode int mode) {
                this.mode = mode;
                return this;
            }

            public Factory<RtspDefaultClient> setNatMethod(@NatMethod int natMethod) {
                this.natMethod = natMethod;
                return this;
            }

            public RtspDefaultClient create(Builder builder) {
                return new RtspDefaultClient(builder
                        .setUserAgent(USER_AGENT)
                        .setFlags(flags)
                        .setMode((mode < builder.mode) ? builder.mode : mode)
                        .setNatMethod(natMethod));
            }
        };
    }


    RtspDefaultClient(Builder builder) {
        super(builder);
    }

    @Override
    protected void sendOptionsRequest() {
        Request.Builder builder = new Request.Builder().options().url(session.uri().toString());
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);

        if (session.getId() != null) {
            builder.header(Header.Session, session.getId());
        }

        dispatch(builder.build());
    }

    @Override
    protected void sendDescribeRequest() {
        Request.Builder builder = new Request.Builder().describe().url(session.uri().toString());
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Accept, MediaType.APPLICATION_SDP);

        dispatch(builder.build());
    }

    @Override
    public void sendSetupRequest(MediaTrack track, int localPort) {
        Request.Builder builder = new Request.Builder().setup().url(track.url());
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);

        if (session.getId() != null) {
            builder.header(Header.Session, session.getId());
        }

        Transport transport = track.format().transport();

        if (isFlagSet(FLAG_ENABLE_RTCP_SUPPORT)) {
            if (isFlagSet(FLAG_FORCE_RTCP_MUXED)) {
                builder.header(Header.Transport, transport + ";client_port=" + localPort +
                        "-" + localPort);
            } else {
                builder.header(Header.Transport, transport + ";client_port=" + localPort +
                        "-" + (localPort + 1));
            }
        } else {
            builder.header(Header.Transport, transport + ";client_port=" + localPort);
        }

        dispatch(builder.build());
    }

    @Override
    public void sendSetupRequest(String trackId, Transport transport) {
        Request.Builder builder = new Request.Builder().setup().url(trackId);
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);

        if (session.getId() != null) {
            builder.header(Header.Session, session.getId());
        }

        builder.header(Header.Transport, transport);

        dispatch(builder.build());
    }

    @Override
    public void sendPlayRequest(Range range) {
        Request.Builder builder = new Request.Builder().play().url(getPlayUrl());
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());
        builder.header(Header.Range, range);

        dispatch(builder.build());
    }

    @Override
    public void sendPlayRequest(Range range, float scale) {
        Request.Builder builder = new Request.Builder().play().url(getPlayUrl());
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());
        builder.header(Header.Range, range);
        builder.header(Header.Scale, scale);

        dispatch(builder.build());
    }

    @Override
    public void sendPauseRequest() {
        Request.Builder builder = new Request.Builder().pause().url(session.uri().toString());
        builder.header(Header.CSeq, session.nextCSeq());
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
        builder.header(Header.CSeq, session.nextCSeq());
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
        builder.header(Header.CSeq, session.nextCSeq());
        builder.header(Header.UserAgent, USER_AGENT);
        builder.header(Header.Session, session.getId());

        dispatch(builder.build());
    }
}

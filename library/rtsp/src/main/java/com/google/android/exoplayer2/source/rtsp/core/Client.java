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

import android.net.Uri;
import android.os.Handler;

import androidx.annotation.IntDef;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.rtp.format.FormatSpecificParameter;
import com.google.android.exoplayer2.source.rtp.format.RtpAudioPayload;
import com.google.android.exoplayer2.source.rtp.format.RtpPayloadFormat;
import com.google.android.exoplayer2.source.rtp.format.RtpVideoPayload;
import com.google.android.exoplayer2.source.rtsp.auth.AuthScheme;
import com.google.android.exoplayer2.source.rtsp.auth.BasicCredentials;
import com.google.android.exoplayer2.source.rtsp.auth.Credentials;
import com.google.android.exoplayer2.source.rtsp.auth.DigestCredentials;
import com.google.android.exoplayer2.source.rtsp.message.InterleavedFrame;
import com.google.android.exoplayer2.source.rtsp.message.Header;
import com.google.android.exoplayer2.source.rtsp.message.Headers;
import com.google.android.exoplayer2.source.rtsp.media.MediaType;
import com.google.android.exoplayer2.source.rtsp.message.MessageBody;
import com.google.android.exoplayer2.source.rtsp.message.Method;
import com.google.android.exoplayer2.source.rtsp.message.Range;
import com.google.android.exoplayer2.source.rtsp.message.Request;
import com.google.android.exoplayer2.source.rtsp.message.Response;
import com.google.android.exoplayer2.source.rtsp.message.Status;
import com.google.android.exoplayer2.source.rtsp.message.Transport;
import com.google.android.exoplayer2.source.rtsp.media.MediaFormat;
import com.google.android.exoplayer2.source.rtsp.media.MediaSession;
import com.google.android.exoplayer2.source.rtsp.media.MediaTrack;
import com.google.android.exoplayer2.source.sdp.MediaDescription;
import com.google.android.exoplayer2.source.sdp.SessionDescription;
import com.google.android.exoplayer2.source.sdp.core.Attribute;
import com.google.android.exoplayer2.source.sdp.core.Bandwidth;
import com.google.android.exoplayer2.source.sdp.core.Media;

import com.google.android.exoplayer2.upstream.UdpDataSource;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Client implements Dispatcher.EventListener {

    public interface Factory<T> {
        Factory<T> setMode(@Mode int mode);
        Factory<T> setMaxDelay(long delayMs);
        Factory<T> setFlags(@Flags int flags);
        Factory<T> setBufferSize(int bufferSize);
        Factory<T> setAVOptions(@AVOptions int avOptions);
        Factory<T> setNatMethod(@NatMethod int natMethod);

        long getMaxDelay();
        int getBufferSize();
        @Mode int getMode();
        @Flags int getFlags();
        @AVOptions int getAVOptions();
        @NatMethod int getNatMethod();

        T create(Builder builder);
    }

    public interface EventListener {
        /**
         * Called when the rtsp media session is established and prepared.
         *
         */
        void onMediaDescriptionInfoRefreshed(long durationUs);

        /**
         * Called when the rtsp media description type is not supported.
         *
         */
        void onMediaDescriptionTypeUnSupported(MediaType mediaType);

        /**
         * Called when the transport protocol changed.
         *
         */
        void onTransportProtocolChanged(@C.TransportProtocol int protocol);

        /**
         * Called when an error occurs on rtsp client.
         *
         */
        void onClientError(Throwable throwable);
    }

    private static final Pattern regexRtpMap = Pattern.compile(
            "\\d+\\s+([a-zA-Z0-9-]*)/(\\d+){1}(/(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern regexFrameSize = Pattern.compile("(\\d+)\\s+(\\d+)-(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern regexXDimensions = Pattern.compile("(\\d+),\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern regexFmtp = Pattern.compile("\\d+\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern regexNumber = Pattern.compile("([\\d\\.]+)\\b");

    private static final Pattern regexAuth = Pattern.compile("(\\S+)\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final int DEFAULT_PORT = 554;
    protected static final int MIN_RECEIVE_BUFFER_SIZE = UdpDataSource.DEFAULT_RECEIVE_BUFFER_SIZE / 2;
    protected static final int MAX_RECEIVE_BUFFER_SIZE = 500 * 1024;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {AV_OPT_FLAG_DISABLE_AUDIO, AV_OPT_FLAG_DISABLE_VIDEO})
    public @interface AVOptions {}
    public static final int AV_OPT_FLAG_DISABLE_AUDIO = 1;
    public static final int AV_OPT_FLAG_DISABLE_VIDEO = 1 << 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {FLAG_ENABLE_RTCP_SUPPORT, FLAG_FORCE_RTCP_MUXED})
    public @interface Flags {}
    public static final int FLAG_ENABLE_RTCP_SUPPORT = 1;
    public static final int FLAG_FORCE_RTCP_MUXED = 1 << 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {RTSP_AUTO_DETECT, RTSP_INTERLEAVED})
    public @interface Mode {}
    public static final int RTSP_AUTO_DETECT = 0;
    public static final int RTSP_INTERLEAVED = 1;
    //public static final int RTSP_TUNNELING = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {RTSP_NAT_NONE, RTSP_NAT_DUMMY})
    public @interface NatMethod {}
    public static final int RTSP_NAT_NONE = 0;
    public static final int RTSP_NAT_DUMMY = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {IDLE, INIT, READY, PLAYING, RECORDING})
    @interface ClientState {}
    final static int IDLE = 0;
    final static int INIT = 1;
    final static int READY = 2;
    final static int PLAYING = 3;
    final static int RECORDING = 4;

    private final List<Method> serverMethods;

    private final String userAgent;

    private final Dispatcher dispatcher;
    protected final MediaSession session;

    private @ClientState int state;

    private final Uri uri;
    private ExoPlayer player;
    private @Flags int flags;
    private final long delayMs;
    private final int bufferSize;
    private final EventListener listener;
    private final @NatMethod int natMethod;
    private @Mode int mode;
    private final @AVOptions int avOptions;

    private Credentials credentials;

    private boolean opened;
    private boolean released;

    public Client(Builder builder) {
        uri = builder.uri;
        player = builder.player;
        listener = builder.listener;
        userAgent = builder.userAgent;

        mode = builder.factory.getMode();
        flags = builder.factory.getFlags();
        delayMs = builder.factory.getMaxDelay();
        avOptions = builder.factory.getAVOptions();
        natMethod = builder.factory.getNatMethod();
        bufferSize = builder.factory.getBufferSize();

        serverMethods = new ArrayList<>();

        dispatcher = new Dispatcher.Builder(this)
                .setUri(uri)
                .setUserAgent(userAgent)
                .build();

        session = new MediaSession.Builder(this)
                .build();
    }

    public final MediaSession session() { return session; }

    public final ExoPlayer player() { return player; }

    public final int getBufferSize() { return bufferSize; }

    public final long getMaxDelay() { return delayMs; }

    protected final @ClientState int state() { return state; }

    public final void open() throws IOException, NullPointerException {
        if (!opened) {
            dispatcher.connect();
            sendOptionsRequest();

            player.getVideoComponent().addVideoListener(session);
            opened = true;
        }
    }

    public final void retry() throws IOException, NullPointerException {
        if (!opened) {
            mode = RTSP_INTERLEAVED;
            dispatcher.connect();
            sendOptionsRequest();

            player.getVideoComponent().addVideoListener(session);
            opened = true;
        }
    }

    public final void close() {
        if (opened || !released) {
            opened = false;
            released = true;

            player.getVideoComponent().removeVideoListener(session);

            session.release();
            dispatcher.close();
            serverMethods.clear();

            state = IDLE;
        }
    }

    public final void release() {
        close();
    }

    public final Uri uri() {
        return uri;
    }

    public boolean isFlagSet(@Flags int flag) {
        return (flags & flag) == flag;
    }

    private boolean isAVOptionSet(@AVOptions int option) {
        return (avOptions & option) == option;
    }

    public boolean isInterleavedMode() {
        return RTSP_INTERLEAVED == mode;
    }

    public boolean isNatSet(@NatMethod int method) {
        return (natMethod & method) != 0;
    }

    public final void dispatch(InterleavedFrame interleavedFrame) {
        dispatcher.execute(interleavedFrame);
    }

    public final void dispatch(Request request) {
        if (credentials != null) {
            if (!request.getHeaders().contains(Header.Authorization)) {
                credentials.applyToRequest(request);
            }
        }

        switch (state) {
            case IDLE:
                if (request.getMethod().equals(Method.DESCRIBE) ||
                        request.getMethod().equals(Method.OPTIONS)) {
                    dispatcher.execute(request);

                } else if (request.getMethod().equals(Method.SETUP)) {
                    state = INIT;
                    dispatcher.execute(request);
                }
                break;

            case INIT:
                if (request.getMethod().equals(Method.SETUP) ||
                        request.getMethod().equals(Method.TEARDOWN)) {
                    dispatcher.execute(request);
                }
                break;

            case READY:
                if (request.getMethod().equals(Method.PAUSE) ||
                        request.getMethod().equals(Method.PLAY) ||
                        request.getMethod().equals(Method.GET_PARAMETER) ||
                        request.getMethod().equals(Method.RECORD) ||
                        request.getMethod().equals(Method.SETUP) ||
                        request.getMethod().equals(Method.TEARDOWN)) {
                    dispatcher.execute(request);
                }
                break;

            case PLAYING:
            case RECORDING:
                if (request.getMethod().equals(Method.ANNOUNCE) ||
                        request.getMethod().equals(Method.GET_PARAMETER) ||
                        request.getMethod().equals(Method.OPTIONS) ||
                        request.getMethod().equals(Method.PLAY) ||
                        request.getMethod().equals(Method.PAUSE) ||
                        request.getMethod().equals(Method.SETUP) ||
                        request.getMethod().equals(Method.TEARDOWN)) {
                    dispatcher.execute(request);
                }
        }
    }

    // Dispatcher.EventListener implementation
    @Override
    public final void onAnnounceRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.OK);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent);

        dispatcher.execute(builder.build());
    }

    @Override
    public final void onRedirectRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent);

        dispatcher.execute(builder.build());
    }

    @Override
    public final void onOptionsRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent);

        dispatcher.execute(builder.build());
    }

    @Override
    public final void onGetParameterRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent);

        dispatcher.execute(builder.build());
    }

    @Override
    public final void onSetParameterRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent);

        dispatcher.execute(builder.build());
    }

    @Override
    public final void onAnnounceResponse(Response response) {
        // Not Applicable
    }

    @Override
    public final void onOptionsResponse(Response response) {
        if (serverMethods.size() == 0) {
            if (response.getHeaders().contains(Header.Public)) {
                String publicHeader = response.getHeaders().value(Header.Public);
                String[] names = publicHeader.split((publicHeader.indexOf(',') != -1) ? "," : " ");

                for (String name : names) {
                    serverMethods.add(Method.parse(name.trim()));
                }
            }

            if (state == IDLE) {
                sendDescribeRequest();
            }
        }

        if (response.getHeaders().contains(Header.Server)) {
            session.setServer(response.getHeaders().value(Header.Server));
        }
    }

    @Override
    public final void onDescribeResponse(Response response) {
        MessageBody body = response.getMessageBody();
        Headers headers = response.getHeaders();
        String baseUrl = null;

        if (headers.contains(Header.ContentBase)) {
            baseUrl = headers.value(Header.ContentBase);
        } else if (headers.contains(Header.ContentLocation)) {
            baseUrl = headers.value(Header.ContentLocation);
        }

        session.setBaseUri(Uri.parse(baseUrl));

        if (body != null) {
            MediaType mediaType = body.getContentType();

            if (MediaType.APPLICATION_SDP.equals(mediaType)) {
                String content = body.getContent();

                if ((content != null) && (content.length() > 0)) {
                    SessionDescription sessionDescription = SessionDescription.parse(content);

                    if (sessionDescription.sessionName() != null) {
                        session.setName(sessionDescription.sessionName().name());
                    }

                    if (sessionDescription.information() != null) {
                        session.setDescription(sessionDescription.information().info());
                    }

                    for (Attribute attribute : sessionDescription.attributes()) {
                        String attrName = attribute.name();
                        if (Attribute.RANGE.equalsIgnoreCase(attrName)) {
                            session.setDuration(Range.parse(attribute.value()).duration());

                        } else if (Attribute.LENGTH.equalsIgnoreCase(attrName)) {
                            session.setDuration((long)Double.parseDouble(attribute.value()));

                        } else if (Attribute.SDPLANG.equalsIgnoreCase(attrName)) {
                            session.setLanguage(attribute.value());
                        }
                    }

                    // Only support permanent sessions
                    if (sessionDescription.time() == null || sessionDescription.time().isZero()) {

                        for (MediaDescription mediaDescription :
                                sessionDescription.mediaDescriptions()) {

                            Media media = mediaDescription.media();

                            // We only support audio o video
                            if ((Media.audio.equals(media.type()) && !isAVOptionSet(
                                    AV_OPT_FLAG_DISABLE_AUDIO)) ||
                                    (Media.video.equals(media.type())) & !isAVOptionSet(
                                            AV_OPT_FLAG_DISABLE_VIDEO)) {

                                RtpPayloadFormat.Builder payloadBuilder = null;
                                MediaTrack.Builder trackBuilder = new MediaTrack.Builder();

                                @MediaFormat.MediaType int type = media.type().equals(Media.audio) ?
                                        MediaFormat.AUDIO : MediaFormat.VIDEO;

                                MediaFormat.Builder formatBuilder = new MediaFormat.Builder(type);

                                Transport transport = Transport.parse(media.proto(), media.fmt());
                                if (Transport.AVP_PROFILE.equals(transport.profile())) {
                                    payloadBuilder = MediaFormat.AUDIO == type ?
                                            new RtpAudioPayload.Builder() :
                                            new RtpVideoPayload.Builder();

                                    if (isNumeric(media.fmt())) {
                                        payloadBuilder.payload(Integer.parseInt(media.fmt()));
                                    }
                                }

                                formatBuilder.transport(transport);

                                Bandwidth bandwidth = mediaDescription.bandwidth();
                                if (bandwidth != null && Bandwidth.AS.equals(bandwidth.bwtype())) {
                                    formatBuilder.bitrate(bandwidth.bandwidth());
                                    payloadBuilder.bitrate(bandwidth.bandwidth());
                                }

                                for (Attribute attribute : mediaDescription.attributes()) {
                                    String attrName = attribute.name();
                                    String attrValue = attribute.value();

                                    if (Attribute.RANGE.equalsIgnoreCase(attrName)) {
                                        session.setDuration(Range.parse(attrValue).duration());

                                    } else if (Attribute.CONTROL.equalsIgnoreCase(attrName)) {
                                        if (baseUrl != null && attrValue.startsWith(baseUrl)) {
                                            trackBuilder.url(attrValue);
                                        } else {
                                            if (attrValue.toLowerCase().startsWith("rtsp://")) {
                                                trackBuilder.url(attrValue);

                                            } else {
                                                Uri uri = session.uri();
                                                String url = uri.getScheme() + "://" + uri.getHost()
                                                        + ((uri.getPort() > 0) ? ":" + uri.getPort()
                                                        : ":" + DEFAULT_PORT) + uri.getPath();

                                                if (baseUrl != null) {
                                                    Uri uriBaseUrl = Uri.parse(baseUrl);
                                                    String scheme = uriBaseUrl.getScheme();
                                                    if (scheme != null &&
                                                            "rtsp".equalsIgnoreCase(scheme)) {
                                                        url = baseUrl;
                                                    }
                                                }

                                                if (url.lastIndexOf('/') == url.length() - 1) {
                                                    trackBuilder.url(url + attrValue);
                                                } else {
                                                    trackBuilder.url(url + "/" + attrValue);
                                                }
                                            }
                                        }

                                    } else if (Attribute.RTCP_MUX.equalsIgnoreCase(attrName)) {
                                        trackBuilder.muxed(true);
                                        flags |= FLAG_FORCE_RTCP_MUXED;

                                    } else if (Attribute.SDPLANG.equalsIgnoreCase(attrName)) {
                                        trackBuilder.language(attrValue);

                                    } else if (payloadBuilder != null) {
                                        if (Attribute.RTPMAP.equalsIgnoreCase(attrName)) {
                                            Matcher matcher = regexRtpMap.matcher(attrValue);
                                            if (matcher.find()) {
                                                @RtpPayloadFormat.MediaCodec String encoding =
                                                        matcher.group(1).toUpperCase();

                                                payloadBuilder.encoding(encoding);

                                                if (matcher.group(2) != null) {
                                                    if (isNumeric(matcher.group(2))) {
                                                        payloadBuilder.clockrate(
                                                                Integer.parseInt(matcher.group(2)));
                                                    }
                                                }

                                                if (matcher.group(3) != null) {
                                                    if (isNumeric(matcher.group(4))) {
                                                        ((RtpAudioPayload.Builder) payloadBuilder).
                                                                channels(Integer.parseInt(matcher.group(4)));
                                                    }
                                                }
                                            }
                                            /* NOTE: fmtp is only supported AFTER the 'a=rtpmap:xxx' tag */
                                        } else if (Attribute.FMTP.equalsIgnoreCase(attrName)) {
                                            Matcher matcher = regexFmtp.matcher(attrValue);
                                            if (matcher.find()) {
                                                String[] encodingParameters = matcher.group(1).
                                                        split(";");
                                                for (String parameter : encodingParameters) {
                                                    payloadBuilder.addEncodingParameter(
                                                            FormatSpecificParameter.parse(parameter));
                                                }
                                            }
                                        } else if (Attribute.FRAMERATE.equalsIgnoreCase(attrName)) {
                                            if (isNumeric(attrValue)) {
                                                ((RtpVideoPayload.Builder) payloadBuilder).framerate(
                                                        Float.parseFloat(attrValue));
                                            }

                                        } else if (Attribute.FRAMESIZE.equalsIgnoreCase(attrName)) {
                                            Matcher matcher = regexFrameSize.matcher(attrValue);
                                            if (matcher.find()) {
                                                if (isNumeric(matcher.group(2)) &&
                                                        isNumeric(matcher.group(3))) {
                                                    ((RtpVideoPayload.Builder) payloadBuilder).width(
                                                            Integer.parseInt(matcher.group(2)));

                                                    ((RtpVideoPayload.Builder) payloadBuilder).height(
                                                            Integer.parseInt(matcher.group(3)));
                                                }
                                            }
                                        } else if (Attribute.X_FRAMERATE.equalsIgnoreCase(attrName)) {
                                            if (isNumeric(attrValue)) {
                                                ((RtpVideoPayload.Builder) payloadBuilder).framerate(
                                                        Float.parseFloat(attrValue));
                                            }

                                        } else if (Attribute.X_DIMENSIONS.equalsIgnoreCase(attrName)) {
                                            Matcher matcher = regexXDimensions.matcher(attrValue);
                                            if (matcher.find()) {
                                                if (isNumeric(matcher.group(2)) &&
                                                        isNumeric(matcher.group(3))) {
                                                    ((RtpVideoPayload.Builder) payloadBuilder).width(
                                                            Integer.parseInt(matcher.group(2)));

                                                    ((RtpVideoPayload.Builder) payloadBuilder).height(
                                                            Integer.parseInt(matcher.group(3)));
                                                }
                                            }

                                        } else if (Attribute.PTIME.equalsIgnoreCase(attrName)) {
                                            if (isNumeric(attrValue)) {
                                                ((RtpAudioPayload.Builder) payloadBuilder).
                                                        ptime(Long.parseLong(attrValue));
                                            }

                                        } else if (Attribute.MAXPTIME.equalsIgnoreCase(attrName)) {
                                            if (isNumeric(attrValue)) {
                                                ((RtpAudioPayload.Builder) payloadBuilder).
                                                        maxptime(Long.parseLong(attrValue));
                                            }

                                        } else if (Attribute.QUALITY.equalsIgnoreCase(attrName)) {
                                            if (isNumeric(attrValue)) {
                                                ((RtpVideoPayload.Builder) payloadBuilder).
                                                        quality(Integer.parseInt(attrValue));
                                            }
                                        }
                                    }
                                }

                                if (payloadBuilder != null) {
                                    formatBuilder.format(payloadBuilder.build());
                                }

                                try {

                                    MediaFormat format = formatBuilder.build();
                                    MediaTrack track = trackBuilder.format(format).build();
                                    session.addMediaTrack(track);

                                } catch (IllegalStateException ex) {

                                }
                            }
                        }

                        listener.onMediaDescriptionInfoRefreshed(session.getDuration());
                    }
                }
            } else {

                Response.Builder builder = new Response.Builder().status(Status.UnsupportedMediaType);
                builder.header(Header.CSeq, Integer.toString(session.nextCSeq()));
                builder.header(Header.UserAgent, userAgent);
                builder.header(Header.Unsupported, mediaType.toString());

                dispatcher.execute(builder.build());

                close();
                listener.onMediaDescriptionTypeUnSupported(mediaType);
            }
        }
    }

    @Override
    public final void onSetupResponse(Response response) {
        if (session.getId() == null) {
            Pattern rexegSession = Pattern.compile("(\\S+);timeout=(\\S+)|(\\S+)",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = rexegSession.matcher(response.getHeaders().value(Header.Session));

            if (matcher.find()) {
                if (matcher.group(1) != null) {
                    session.setId(matcher.group(1));
                    // timeout in milliseconds
                    session.setTimeout(Integer.parseInt(matcher.group(2))*1000);

                } else {
                    session.setId(matcher.group(3));
                }
            }

            if (state == INIT) {
                state = READY;
            }
        }

        Transport transport = Transport.parse(response.getHeaders().value(Header.Transport));
        session.configureTransport(transport);
        session.continuePreparing();
    }

    @Override
    public final void onPlayResponse(Response response) {
        state = PLAYING;
        session.onPlaySuccess();

        if (session.isInterleaved()) {
            listener.onTransportProtocolChanged(C.TCP);
        } else {
            listener.onTransportProtocolChanged(C.UDP);
        }
    }

    @Override
    public final void onPauseResponse(Response response) {
        state = READY;
        session.onPauseSuccess();
    }

    @Override
    public final void onGetParameterResponse(Response response) {
    }

    @Override
    public final void onRecordResponse(Response response) {
        state = RECORDING;
        // Not Supported
    }

    @Override
    public final void onSetParameterResponse(Response response) {
        // Not Supported
    }

    @Override
    public final void onTeardownResponse(Response response) {
        state = IDLE;
    }

    @Override
    public final void onEmbeddedBinaryData(InterleavedFrame frame) {
        session.onIncomingInterleavedFrame(frame);
    }

    @Override
    public final void onUnauthorized(Request request, Response response) {
        List<String> w3AuthenticateList = response.getHeaders().values(Header.W3Authenticate);

        for (String w3Authenticate : w3AuthenticateList) {
            Matcher matcher = regexAuth.matcher(w3Authenticate);

            if (matcher.find()) {
                try {

                    switch (AuthScheme.parse(matcher.group(1))) {
                        case BASIC:
                            if (session.username() != null) {
                                credentials = new BasicCredentials.Builder(matcher.group(2)).
                                        username(session.username()).
                                        password(session.password()).
                                        build();
                                credentials.applyToRequest(request);

                                request.getHeaders().add(Header.CSeq.toString(),
                                        String.valueOf(session.nextCSeq()));

                                dispatcher.execute(request);
                            }

                            return;

                        case DIGEST:
                            if (session.username() != null) {
                                credentials = new DigestCredentials.Builder(matcher.group(2)).
                                        username(session.username()).
                                        password(session.password()).
                                        setParam(DigestCredentials.URI, session.uri().toString()).
                                        build();
                                credentials.applyToRequest(request);

                                request.getHeaders().add(Header.CSeq.toString(),
                                        String.valueOf(session.nextCSeq()));

                                dispatcher.execute(request);
                            }
                            return;
                    }

                } catch (IOException ex) {
                    close();
                    listener.onClientError(ex);
                }
            }
        }
    }

    @Override
    public final void onUnSuccess(Request request, Response response) {
        // when options method isn't supported from server a describe method is sent
        if ((Method.OPTIONS.equals(request.getMethod())) &&
                (Status.NotImplemented.equals(response.getStatus()))) {

            if (state == IDLE) {
                sendDescribeRequest();
            }

        } else if ((Method.SETUP.equals(request.getMethod())) &&
                (Status.UnsupportedTransport.equals(response.getStatus()))) {
            sendSetupRequest(request.getUrl(), Transport.parse("RTP/AVP/TCP;interleaved=" +
                    session.nextTcpChannel()));

        } else {
            // any other unsuccessful response
            if (state >= READY) {
                if (serverMethods.contains(Method.TEARDOWN)) {
                    sendTeardownRequest();
                }
            }

            close();
            listener.onClientError(null);
        }
    }

    @Override
    public final void onMalformedResponse(Response response) {
        close();
        listener.onClientError(null);
    }

    @Override
    public final void onIOError() {
        close();
        listener.onClientError(null);
    }

    @Override
    public final void onRequestTimeOut() {
        close();
        listener.onClientError(null);
    }

    @Override
    public final void onNoResponse(Request request) {
        Method method = request.getMethod();
        if (Method.OPTIONS.equals(method) || Method.GET_PARAMETER.equals(method)) {
            if (session.isInterleaved()) {
                return;
            }
        }

        close();
        listener.onClientError(null);
    }

    protected abstract void sendOptionsRequest();
    protected abstract void sendDescribeRequest();
    public abstract void sendSetupRequest(MediaTrack track, int localPort);
    public abstract void sendSetupRequest(String trackId, Transport transport);
    public abstract void sendPlayRequest(Range range);
    public abstract void sendPlayRequest(Range range, float scale);
    public abstract void sendPauseRequest();
    protected abstract void sendRecordRequest();
    protected abstract void sendGetParameterRequest();
    protected abstract void sendSetParameterRequest(String name, String value);
    public abstract void sendTeardownRequest();

    public void sendKeepAlive() {
        if (state >= READY) {
            if (serverMethods.contains(Method.GET_PARAMETER)) {
                sendGetParameterRequest();

            } else {
                sendOptionsRequest();
            }
        }
    }

    private boolean isNumeric(String number) {
        Matcher matcher = regexNumber.matcher(number);

        if (matcher.find()) {
            return true;
        }

        return false;
    }

    protected final String getPlayUrl() {
        // determine the URL to use for PLAY requests
        Uri baseUri = session.getBaseUri();
        if (baseUri != null) {
            return baseUri.toString();
        }

        // remove the user info from the URL if it is present
        Uri uri = session.uri();
        String url = uri.toString();
        String uriUserInfo = uri.getUserInfo();
        if (uriUserInfo != null && !uriUserInfo.isEmpty()) {
            uriUserInfo += "@";
            url = url.replace(uriUserInfo, "");
        }

        return url;
    }

    public static final class Builder {
        private Uri uri;
        private String userAgent;
        private ExoPlayer player;
        private EventListener listener;

        private final Factory<? extends Client> factory;

        private Handler eventHandler;
        private MediaSourceEventListener eventListener;

        public Builder(Factory<? extends Client> factory) {
            this.factory = factory;
        }

        public Builder setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder setListener(EventListener listener) {
            if (listener == null) throw new IllegalArgumentException("listener == null");

            this.listener = listener;
            return this;
        }

        /**
         * Sets the listener to respond to adaptive {@link MediaSource} events and the handler to
         * deliver these events.
         *
         * @param eventHandler A handler for events.
         * @param eventListener A listener of events.
         * @return This builder.
         */
        public Builder setEventListener(Handler eventHandler, MediaSourceEventListener eventListener) {
            this.eventHandler = eventHandler;
            this.eventListener = eventListener;
            return this;
        }

        public Builder setPlayer(ExoPlayer player) {
            if (player == null) throw new IllegalArgumentException("player is null");

            this.player = player;
            return this;
        }

        public Builder setUri(Uri uri) {
            if (uri == null) throw new NullPointerException("uri == null");

            if (uri.getPort() == C.PORT_UNSET) {
                this.uri = Uri.parse(uri.getScheme() + "://" + ((uri.getUserInfo() != null) ?
                        uri.getUserInfo() + "@" : "") + uri.getHost() +
                        ((uri.getPort() > 0) ? ":" + uri.getPort() : ":" + DEFAULT_PORT) +
                        uri.getPath() + ((uri.getQuery() != null) ? "?" + uri.getQuery() : ""));
            } else {
                this.uri = uri;
            }

            return this;
        }

        public Client build() {
            if (factory == null) throw new IllegalStateException("factory is null");
            if (listener == null) throw new IllegalStateException("listener is null");
            if (uri == null) throw new IllegalStateException("uri is null");

            return factory.create(this);
        }
    }
}

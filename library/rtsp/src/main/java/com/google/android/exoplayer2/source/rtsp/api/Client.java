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
package com.google.android.exoplayer2.source.rtsp.api;

import android.net.Uri;
import android.os.Handler;
import android.support.annotation.IntDef;

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
import com.google.android.exoplayer2.source.rtsp.core.Header;
import com.google.android.exoplayer2.source.rtsp.core.Headers;
import com.google.android.exoplayer2.source.rtsp.core.MediaType;
import com.google.android.exoplayer2.source.rtsp.core.MessageBody;
import com.google.android.exoplayer2.source.rtsp.core.Method;
import com.google.android.exoplayer2.source.rtsp.core.Protocol;
import com.google.android.exoplayer2.source.rtsp.core.Range;
import com.google.android.exoplayer2.source.rtsp.core.Request;
import com.google.android.exoplayer2.source.rtsp.core.Response;
import com.google.android.exoplayer2.source.rtsp.core.Status;
import com.google.android.exoplayer2.source.rtsp.core.Transport;
import com.google.android.exoplayer2.source.rtsp.media.MediaFormat;
import com.google.android.exoplayer2.source.rtsp.media.MediaSession;
import com.google.android.exoplayer2.source.rtsp.media.MediaTrack;
import com.google.android.exoplayer2.source.sdp.MediaDescription;
import com.google.android.exoplayer2.source.sdp.SessionDescription;
import com.google.android.exoplayer2.source.sdp.core.Attribute;
import com.google.android.exoplayer2.source.sdp.core.Bandwidth;
import com.google.android.exoplayer2.source.sdp.core.Media;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Client {

    public interface Factory<T> {
        T create(Client.Builder builder);
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
         * Called when an error occurs on rtsp client.
         *
         */
        void onClientError(Throwable throwable);

    }

    private static final Pattern rexegRtpMap = Pattern.compile("\\d+\\s+([a-zA-Z0-9-]*)/(\\d+){1}(/(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern rexegFrameSize = Pattern.compile("(\\d+)\\s+(\\d+)-(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern rexegFmtp = Pattern.compile("\\d+\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    static final List<Method> METHODS = Collections.unmodifiableList(Arrays.asList(
            Method.ANNOUNCE, Method.OPTIONS, Method.TEARDOWN));

    static final List<Protocol> DEFAULT_PROTOCOLS = Collections.unmodifiableList(Arrays.asList(
            Protocol.RTSP_1_0));

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {IDLE, INIT, READY, PLAYING, RECORDING})
    protected @interface ClientState {}

    protected final static int IDLE = 0;
    protected final static int INIT = 1;
    protected final static int READY = 2;
    protected final static int PLAYING = 3;
    protected final static int RECORDING = 4;

    private final List<Method> serverMethods = new ArrayList();

    private final Dispatcher dispatcher;
    protected final MediaSession session;

    private @ClientState int state;

    private final Uri uri;
    private final int retries;
    private final EventListener listener;

    private Credentials credentials;

    private boolean opened;

    public Client(Builder builder) {
        uri = builder.uri;
        retries = builder.retries;
        listener = builder.listener;

        dispatcher = new Dispatcher.Builder().client(this).build();
        session = new MediaSession.Builder().client(this).build();
    }

    public final MediaSession session() { return session; }

    protected final @ClientState int state() { return state; }

    public final void open() throws IOException {
        if (!opened) {
            dispatcher.connect();
            sendOptionsRequest();

            opened = true;
        }
    }

    public final void close() {
        if (opened) {
            session.release();
            dispatcher.close();
            serverMethods.clear();

            state = IDLE;
            opened = false;
        }
    }

    public final Uri uri() {
        return uri;
    }

    public final boolean isMethodSupported(Method method) {
        return METHODS.contains(method);
    }

    public final boolean isProtocolSupported(Protocol protocol) {
        return DEFAULT_PROTOCOLS.contains(protocol);
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

    // Default callbacks for incomming request messages
    public final void onAnnounceRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.OK);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent());

        dispatcher.execute(builder.build());

        // TODO evaluate the announce code received
    }

    public final void onRedirectRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent());

        dispatcher.execute(builder.build());
    }

    public final void onOptionsRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent());

        dispatcher.execute(builder.build());
    }

    public final void onGetParameterRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent());

        dispatcher.execute(builder.build());
    }

    public final void onSetParameterRequest(Request request) {
        Response.Builder builder = new Response.Builder().status(Status.MethodNotAllowed);
        builder.header(Header.CSeq, request.getHeaders().value(Header.CSeq));
        builder.header(Header.UserAgent, userAgent());

        dispatcher.execute(builder.build());
    }


    // Default callbacks for incomming response messages
    public final void onAnnounceResponse(Response response) {
        // Not Applicable
    }

    public final void onOptionsResponse(Response response) {
        if (serverMethods.size() == 0) {
            String publicHeader = response.getHeaders().value(Header.Public);
            String[] names = publicHeader.split((publicHeader.indexOf(',') != -1) ? "," : " ");

            for (String name : names) {
                serverMethods.add(Method.parse(name.trim()));
            }

            if (state == IDLE) {
                sendDescribeRequest();
            }
        }

        if (response.getHeaders().contains(Header.Server)) {
            session.setServer(response.getHeaders().value(Header.Server));
        }
    }

    public final void onDescribeResponse(Response response) {
        MessageBody body = response.getMessageBody();
        Headers headers = response.getHeaders();
        String baseUrl = null;

        if (headers.contains(Header.ContentBase)) {
            baseUrl = headers.value(Header.ContentBase);
        } else if (headers.contains(Header.ContentLocation)) {
            baseUrl = headers.value(Header.ContentLocation);
        }

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
                        if (Attribute.RANGE.equalsIgnoreCase(attribute.name())) {
                            session.setDuration(Range.parse(attribute.value()).duration());

                        } else if (Attribute.LENGTH.equalsIgnoreCase(attribute.name())) {
                            session.setDuration((long)Double.parseDouble(attribute.value()));

                        } else if (Attribute.SDPLANG.equalsIgnoreCase(attribute.name())) {
                            session.setLanguage(attribute.value());
                        }
                    }

                    // We only support permanent sessions
                    if (sessionDescription.time() == null || sessionDescription.time().isZero()) {

                        for (MediaDescription mediaDescription :
                                sessionDescription.mediaDescriptions()) {

                            Media media = mediaDescription.media();

                            // We only support audio o video
                            if (Media.audio.equals(media.type()) ||
                                    Media.video.equals(media.type())) {

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

                                    payloadBuilder.payload(Integer.parseInt(media.fmt()));
                                }

                                formatBuilder.transport(transport);

                                Bandwidth bandwidth = mediaDescription.bandwidth();
                                if (bandwidth != null && Bandwidth.AS.equals(bandwidth.bwtype())) {
                                    formatBuilder.bitrate(bandwidth.bandwidth());
                                }

                                for (Attribute attribute : mediaDescription.attributes()) {
                                    if (Attribute.RANGE.equalsIgnoreCase(attribute.name())) {
                                        session.setDuration(Range.parse(attribute.value()).duration());

                                    } else if (Attribute.CONTROL.equalsIgnoreCase(attribute.name())) {
                                        if (baseUrl != null && attribute.value().startsWith(baseUrl)) {
                                            trackBuilder.url(attribute.value());
                                        } else {
                                            if (attribute.value().toLowerCase().startsWith("rtsp://")) {
                                                trackBuilder.url(attribute.value());

                                            } else {

                                                Uri uri = session.uri();
                                                String url = uri.getScheme() + "://" + uri.getHost()
                                                        + ":" + uri.getPort() + uri.getPath();

                                                if (baseUrl != null) {
                                                    String scheme = Uri.parse(baseUrl).getScheme();
                                                    if (scheme != null &&
                                                            "rtsp".equalsIgnoreCase(scheme)) {
                                                        url = baseUrl;
                                                        }
                                                }

                                                if (url.lastIndexOf('/') == url.length() - 1) {
                                                    trackBuilder.url(url + attribute.value());
                                                } else {
                                                    trackBuilder.url(url + "/" + attribute.value());
                                                }
                                            }
                                        }

                                    } else if (Attribute.SDPLANG.equalsIgnoreCase(attribute.name())) {
                                        trackBuilder.language(attribute.value());

                                    } else if (payloadBuilder != null) {
                                        if (Attribute.RTPMAP.equalsIgnoreCase(attribute.name())) {
                                            Matcher matcher = rexegRtpMap.matcher(attribute.value());
                                            if (matcher.find()) {
                                                @RtpPayloadFormat.MediaCodec String encoding = matcher.group(1).toUpperCase();

                                                payloadBuilder.encoding(encoding);

                                                if (matcher.group(2) != null) {
                                                    payloadBuilder.clockrate(Integer.parseInt(matcher.group(2)));
                                                }

                                                if (matcher.group(3) != null) {
                                                    ((RtpAudioPayload.Builder) payloadBuilder).
                                                            channels(Integer.parseInt(matcher.group(4)));
                                                }
                                            }
                                        /* NOTE: fmtp is only supported AFTER the 'a=rtpmap:xxx' tag */
                                        } else if (Attribute.FMTP.equalsIgnoreCase(attribute.name())) {
                                            Matcher matcher = rexegFmtp.matcher(attribute.value());

                                            if (matcher.find()) {
                                                String[] encodingParameters = matcher.group(1).split(";");
                                                for (String parameter : encodingParameters) {
                                                    payloadBuilder.addEncodingParameter(FormatSpecificParameter.parse(parameter));
                                                }
                                            }

                                        } else if (Attribute.FRAMERATE.equalsIgnoreCase(attribute.name())) {
                                            ((RtpVideoPayload.Builder) payloadBuilder).framerate(
                                                    Float.parseFloat(attribute.value()));

                                        } else if (Attribute.FRAMESIZE.equalsIgnoreCase(attribute.name())) {
                                            Matcher matcher = rexegFrameSize.matcher(attribute.value());

                                            if (matcher.find()) {
                                                ((RtpVideoPayload.Builder) payloadBuilder).width(
                                                        Integer.parseInt(matcher.group(2)));

                                                ((RtpVideoPayload.Builder) payloadBuilder).height(
                                                        Integer.parseInt(matcher.group(3)));
                                            }
                                        } else if (Attribute.PTIME.equalsIgnoreCase(attribute.name())) {
                                            ((RtpAudioPayload.Builder) payloadBuilder).
                                                    ptime(Long.parseLong(attribute.value()));

                                        } else if (Attribute.MAXPTIME.equalsIgnoreCase(attribute.name())) {
                                            ((RtpAudioPayload.Builder) payloadBuilder).
                                                    maxptime(Long.parseLong(attribute.value()));

                                        } else if (Attribute.QUALITY.equalsIgnoreCase(attribute.name())) {
                                            ((RtpVideoPayload.Builder) payloadBuilder).
                                                    quality(Integer.parseInt(attribute.value()));
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
                builder.header(Header.CSeq, Integer.toString(session().nexCSeq()));
                builder.header(Header.UserAgent, userAgent());
                builder.header(Header.Unsupported, mediaType.toString());

                dispatcher.execute(builder.build());

                listener.onMediaDescriptionTypeUnSupported(mediaType);
            }
        }
    }

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
        session.configTransport(transport);
        session.continuePreparing();
    }

    public final void onPlayResponse(Response response) {
        state = PLAYING;
        session.onPlaySuccess();
    }

    public final void onPauseResponse(Response response) {
        state = READY;
        session.onPauseSuccess();
    }

    public final void onGetParameterResponse(Response response) {
    }

    public final void onRecordResponse(Response response) {
        state = RECORDING;
        // Not Supported
    }

    public final void onSetParameterResponse(Response response) {
        // Not Supported
    }

    public final void onTeardownResponse(Response response) {
        state = INIT;
    }

    public final void onUnauthorized(Request request, Response response) {
        Pattern regexAuth = Pattern.compile("(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        String w3Authenticate = response.getHeaders().value(Header.W3Authenticate);
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
                                    String.valueOf(session.nexCSeq()));

                            dispatcher.execute(request);
                        }

                        break;

                    case DIGEST:
                        if (session.username() != null) {
                            credentials = new DigestCredentials.Builder(matcher.group(2)).
                                    username(session.username()).
                                    password(session.password()).
                                    setParam(DigestCredentials.URI, session.uri().toString()).
                                    build();
                            credentials.applyToRequest(request);

                            request.getHeaders().add(Header.CSeq.toString(),
                                    String.valueOf(session.nexCSeq()));

                            dispatcher.execute(request);
                        }
                        break;
                }

            } catch (IOException ex) {
                listener.onClientError(ex);
            }
        }
    }

    public final void onUnSuccess(Request request, Response response) {
        // when options method isn't supported from server a describe method is sent
        if ((Method.OPTIONS.equals(request.getMethod())) &&
                (Status.NotImplemented.equals(response.getStatus()))) {

            if (state == IDLE) {
                sendDescribeRequest();
            }

        } else {
            // any other unsuccessful response
            if (state >= READY) {
                if (serverMethods.contains(Method.TEARDOWN)) {
                    sendTeardownRequest();
                }
            }

            listener.onClientError(null);
        }
    }

    public final void onIOError() {
        listener.onClientError(null);
    }

    public final void onTimeOut() {
        listener.onClientError(null);
    }

    public final void onBadRequest() {
        listener.onClientError(null);
    }

    protected abstract String userAgent();

    protected abstract void sendOptionsRequest();
    protected abstract void sendDescribeRequest();
    public abstract void sendSetupRequest(String trackId, Transport transport, int localPort);
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

    public static final class Builder {
        Uri uri;
        int retries;
        EventListener listener;
        final Factory<? extends Client> factory;

        Handler eventHandler;
        MediaSourceEventListener eventListener;

        public Builder(Factory<? extends Client> factory) {
            this.factory = factory;
        }

        public Client.Builder listener(EventListener listener) {
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
        public Client.Builder setEventListener(Handler eventHandler, MediaSourceEventListener eventListener) {
            this.eventHandler = eventHandler;
            this.eventListener = eventListener;
            return this;
        }

        public Client.Builder retries(int retries) {
            if (retries < 0) throw new IllegalArgumentException("retries is wrong");

            this.retries = retries;
            return this;
        }

        public Client.Builder uri(Uri uri) {
            if (uri == null) throw new NullPointerException("uri == null");

            this.uri = uri;
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

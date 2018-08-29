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
package com.google.android.exoplayer2.source.sdp;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.sdp.core.Attribute;
import com.google.android.exoplayer2.source.sdp.core.Bandwidth;
import com.google.android.exoplayer2.source.sdp.core.Connection;
import com.google.android.exoplayer2.source.sdp.core.Information;
import com.google.android.exoplayer2.source.sdp.core.Key;
import com.google.android.exoplayer2.source.sdp.core.Media;
import com.google.android.exoplayer2.source.sdp.core.Origin;
import com.google.android.exoplayer2.source.sdp.core.ProtoVersion;
import com.google.android.exoplayer2.source.sdp.core.SessionName;
import com.google.android.exoplayer2.source.sdp.core.Time;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Represents the data defined by the MediaSession Description Protocol (see IETF RFC 4566) and holds
 * information about the originator of a session, the media types that a client can support and
 * the host and port on which the client will listen for that media.
 *
 * The SessionDescription also holds timing information for the session (e.g. start, end,
 * repeat, time zone) and bandwidth supported for the session.
 *
 * Please refer to IETF RFC 4566 for a description of SDP.
 *
 */
public final class SessionDescription {
    private static final Pattern regexSdpLine = Pattern.compile("([a-z])=\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private ProtoVersion version;
    private Origin origin;
    private SessionName name;
    private Information info;
    private Connection connection;
    private Bandwidth bandwidth;
    private Time time;
    private Key key;

    private Vector<Attribute> attributes;
    private Vector<MediaDescription> mediaDescriptions;

    SessionDescription(Builder builder) {
        this.version = builder.version;
        this.origin = builder.origin;
        this.name = builder.name;
        this.info = builder.info;
        this.connection = builder.connection;
        this.bandwidth = builder.bandwidth;
        this.time = builder.time;
        this.key = builder.key;

        this.attributes = builder.attributes;
        this.mediaDescriptions = builder.mediaDescriptions;
    }

    public ProtoVersion version() {
        return version;
    }

    public SessionName sessionName() {
        return name;
    }

    public Origin origin() { return origin; }

    public Information information() { return info; }

    public Connection connection() { return connection; }

    public Bandwidth bandwidth() { return bandwidth; }

    public Time time() { return time; }

    public Key key() { return key; }

    public Vector<Attribute> attributes() {
        return attributes;
    }

    public Vector<MediaDescription> mediaDescriptions() {
        return mediaDescriptions;
    }


    @Nullable
    public static SessionDescription parse(String sdpContent) {
        if (sdpContent == null) throw new IllegalArgumentException("sdpContent == null");

        try {

            String line;

            MediaDescription.Builder mediaBuilder = null;
            SessionDescription.Builder sesionBuilder = new SessionDescription.Builder();

            BufferedReader reader = new BufferedReader(new StringReader(sdpContent));

            while ((line = reader.readLine()) != null) {
                Matcher matcher = regexSdpLine.matcher(line);

                if (matcher.find()) {
                    switch (matcher.group(1).charAt(0)) {
                        case 'v':
                            sesionBuilder.version(ProtoVersion.parse(matcher.group(2).trim()));
                            break;

                        case 'o':
                            sesionBuilder.origin(Origin.parse(matcher.group(2).trim()));
                            break;

                        case 's':
                            sesionBuilder.sessionName(SessionName.parse(matcher.group(2).trim()));
                            break;

                        case 'i':
                            if (mediaBuilder == null) {
                                sesionBuilder.info(Information.parse(matcher.group(2).trim()));
                            }
                            else {
                                mediaBuilder.info(Information.parse(matcher.group(2).trim()));
                            }

                            break;

                        case 'c':
                            if (mediaBuilder == null) {
                                sesionBuilder.connection(Connection.parse(matcher.group(2).trim()));
                            }
                            else {
                                mediaBuilder.connection(Connection.parse(matcher.group(2).trim()));
                            }

                            break;

                        case 'b':
                            if (mediaBuilder == null) {
                                sesionBuilder.bandwidth(Bandwidth.parse(matcher.group(2).trim()));
                            }
                            else {
                                mediaBuilder.bandwidth(Bandwidth.parse(matcher.group(2).trim()));
                            }

                            break;

                        case 't':
                            sesionBuilder.time(Time.parse(matcher.group(2).trim()));
                            break;

                        case 'k':
                            if (mediaBuilder == null) {
                                sesionBuilder.key(Key.parse(matcher.group(2).trim()));
                            }
                            else {
                                mediaBuilder.key(Key.parse(matcher.group(2).trim()));
                            }

                            break;

                        case 'a':
                            if (mediaBuilder == null) {
                                sesionBuilder.attribute(Attribute.parse(matcher.group(2).trim()));
                            }
                            else {
                                mediaBuilder.attribute(Attribute.parse(matcher.group(2).trim()));
                            }
                            break;

                        case 'm':

                            if (mediaBuilder != null) {
                                sesionBuilder.media(mediaBuilder.build());
                            }

                            mediaBuilder = new MediaDescription.Builder();
                            mediaBuilder.media(Media.parse(matcher.group(2).trim()));
                            break;
                    }
                }
            }

            reader.close();

            if (mediaBuilder != null) {
                sesionBuilder.media(mediaBuilder.build());
            }

            return sesionBuilder.build();

        } catch (Exception exc) {
            // quit
        }

        return null;
    }

    public static class Builder {
        ProtoVersion version;
        Origin origin;
        SessionName name;
        Information info;
        Connection connection;
        Bandwidth bandwidth;
        Time time;
        Key key;

        Vector<Attribute> attributes;
        Vector<MediaDescription> mediaDescriptions;

        Builder() {
            attributes = new Vector();
            mediaDescriptions = new Vector();
        }

        public Builder version(ProtoVersion version) {
            if (version == null) throw new IllegalArgumentException("version == null");

            this.version = version;
            return this;
        }

        public Builder origin(Origin origin) {
            if (origin == null) throw new IllegalArgumentException("origin == null");

            this.origin = origin;
            return this;
        }

        public Builder sessionName(SessionName name) {
            if (name == null) throw new IllegalArgumentException("name == null");

            this.name = name;
            return this;
        }

        public Builder info(Information info) {
            if (info == null) throw new IllegalArgumentException("info == null");

            this.info = info;
            return this;
        }

        public Builder connection(Connection connection) {
            if (connection == null) throw new IllegalArgumentException("connection == null");

            this.connection = connection;
            return this;
        }

        public Builder bandwidth(Bandwidth bandwidth) {
            if (bandwidth == null) throw new IllegalArgumentException("bandwidth == null");

            this.bandwidth = bandwidth;
            return this;
        }

        public Builder time(Time time) {
            if (time == null) throw new IllegalArgumentException("time == null");

            this.time = time;
            return this;
        }

        public Builder key(Key key) {
            if (key == null) throw new IllegalArgumentException("key == null");

            this.key = key;
            return this;
        }

        public Builder attribute(Attribute attribute) {
            if (attribute == null) throw new IllegalArgumentException("attribute == null");

            attributes.add(attribute);
            return this;
        }

        public Builder media(MediaDescription media) {
            if (media == null) throw new IllegalArgumentException("media == null");

            mediaDescriptions.add(media);
            return this;
        }

        public SessionDescription build() throws IOException {
            if (version == null) throw new NullPointerException("version == null");

            return new SessionDescription(this);
        }
    }

}

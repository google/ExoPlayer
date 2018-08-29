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

import com.google.android.exoplayer2.source.sdp.core.Attribute;
import com.google.android.exoplayer2.source.sdp.core.Bandwidth;
import com.google.android.exoplayer2.source.sdp.core.Connection;
import com.google.android.exoplayer2.source.sdp.core.Information;
import com.google.android.exoplayer2.source.sdp.core.Key;
import com.google.android.exoplayer2.source.sdp.core.Media;

import java.io.IOException;
import java.util.Vector;

/**
 * Identifies the set of medias that may be received on a specific port or set of ports. It includes:
 *
 * a mediaType (e.g., audio, video, etc.)
 * a port number (or set of ports)
 * a protocol to be used (e.g., RTP/AVP)
 * a set of media formats which correspond to attributes associated with the media description.
 *
 * Please refer to IETF RFC 4566 for a description of SDP.
*/

public final class MediaDescription {
    private Media media;
    private Information info;
    private Connection connection;
    private Bandwidth bandwidth;
    private Key key;

    private Vector<Attribute> attributes;

    MediaDescription(Builder builder) {
        this.media = builder.media;
        this.info = builder.info;
        this.connection = builder.connection;
        this.bandwidth = builder.bandwidth;
        this.key = builder.key;

        this.attributes = builder.attributes;
    }

    public Media media() {
        return media;
    }

    public Information information() {
        return info;
    }

    public Connection connection() {
        return connection;
    }

    public Bandwidth bandwidth() { return bandwidth; }

    public Key key() {
        return key;
    }

    public Vector<Attribute> attributes() { return attributes; }


    public static class Builder {
        Media media;
        Information info;
        Connection connection;
        Bandwidth bandwidth;
        Key key;

        Vector<Attribute> attributes;

        Builder() {
            attributes = new Vector();
        }

        public MediaDescription.Builder media(Media media) {
            if (media == null) throw new IllegalArgumentException("media == null");

            this.media = media;
            return this;
        }

        public MediaDescription.Builder info(Information info) {
            if (info == null) throw new IllegalArgumentException("info == null");

            this.info = info;
            return this;
        }

        public MediaDescription.Builder connection(Connection connection) {
            if (connection == null) throw new IllegalArgumentException("connection == null");

            this.connection = connection;
            return this;
        }

        public MediaDescription.Builder bandwidth(Bandwidth bandwidth) {
            if (bandwidth == null) throw new IllegalArgumentException("bandwidth == null");

            this.bandwidth = bandwidth;
            return this;
        }

        public MediaDescription.Builder key(Key key) {
            if (key == null) throw new IllegalArgumentException("key == null");

            this.key = key;
            return this;
        }

        public MediaDescription.Builder attribute(Attribute attribute) {
            if (attribute == null) throw new IllegalArgumentException("attribute == null");

            attributes.add(attribute);
            return this;
        }

        public MediaDescription build() throws IOException {
            if (media == null) throw new IllegalArgumentException("media == null");

            return new MediaDescription(this);
        }
    }
}

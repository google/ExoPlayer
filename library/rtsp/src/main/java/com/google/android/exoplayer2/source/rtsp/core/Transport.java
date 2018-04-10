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
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Transport {

    private static final Pattern regexTransport =
            Pattern.compile("(\\w+)/(\\w+)/(\\w+)|(\\w+)/(\\w+)|(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern regexClientPort =
            Pattern.compile(".+;client_port=(\\d+)-(\\d+)|.+;client_port=(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern regexServerPort =
            Pattern.compile(".+;server_port=(\\d+)-(\\d+)|.+;server_port=(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern regexSource =
            Pattern.compile("source=(\\w|\\.\\w)+", Pattern.CASE_INSENSITIVE);

    private static final Pattern regexDestination =
            Pattern.compile("destination=(\\w|\\.\\w)+", Pattern.CASE_INSENSITIVE);

    public static final Transport SPEC_MP2T = Transport.parse("MP2T/H2221");
    public static final Transport SPEC_RAW = Transport.parse("RAW/RAW");
    public static final Transport SPEC_RTP = Transport.parse("RTP/AVP");

    /**
     * Protocols for RTSP Transport.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({RTP_PROTOCOL, MP2T_PROTOCOL, RAW_PROTOCOL})
    public @interface TransportProtocol {
    }

    /**
     * Real Time Transport Protocol.
     */
    public static final String RTP_PROTOCOL = "RTP";
    /**
     * MPEG-2 Transport Protocol.
     */
    public static final String MP2T_PROTOCOL = "MP2T";
    /**
     * RAW Transport Protocol.
     */
    public static final String RAW_PROTOCOL = "RAW";


    /**
     * Profiles for RTSP Transport.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({AVP_PROFILE, H2221_PROFILE, RAW_PROFILE})
    public @interface Profile {
    }

    /**
     * Audio Video Profile.
     */
    public static final String AVP_PROFILE = "AVP";
    /**
     * ITU H.222.1 Profile.
     */
    public static final String H2221_PROFILE = "H2221";
    /**
     * RAW Profile.
     */
    public static final String RAW_PROFILE = "RAW";


    /**
     * Lower Transport for RTSP Transport.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({TCP, UDP})
    public @interface LowerTransport {
    }

    /**
     * TCP Lower Transport.
     */
    public static final String TCP = "TCP";
    /**
     * UDP Lower Transport.
     */
    public static final String UDP = "UDP";


    /**
     * Delivery Type for RTSP Transport.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({unicast, multicast})
    public @interface DeliveryType {
    }

    /**
     * Unicast Delivery.
     */
    public static final String unicast = "unicast";
    /**
     * Multicast Delivery.
     */
    public static final String multicast = "multicast";


    /**
     * General parameters for RTSP Transport
     */
    @TransportProtocol
    private String transportProtocol;

    @Profile
    private String profile;

    @LowerTransport
    private String lowerTransport;

    @DeliveryType
    private String deliveryType;

    /**
     * RTP Specific parameters for RTSP Transport
     */
    private String[] clientPort;

    private String[] serverPort;

    private String source;
    private String destination;

    Transport(Transport transport) {
        this.deliveryType = transport.deliveryType;
        this.transportProtocol = transport.transportProtocol;
        this.profile = transport.profile;
        this.lowerTransport = transport.lowerTransport;
        this.clientPort = transport.clientPort;
        this.serverPort = transport.serverPort;
        this.source = transport.source;
        this.destination = transport.destination;
    }

    Transport(@TransportProtocol String transportProtocol, @Profile String profile,
              @LowerTransport String lowerTransport, String[] clientPort, String[] serverPort,
              String source, String destination) {
        this.deliveryType = unicast;
        this.transportProtocol = transportProtocol;
        this.profile = profile;
        this.lowerTransport = lowerTransport;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        this.source = source;
        this.destination = destination;
    }

    public String transportProtocol() {
        return transportProtocol;
    }

    public String profile() {
        return profile;
    }

    public String lowerTransport() {
        return lowerTransport;
    }

    public String deliveryType() {
        return deliveryType;
    }

    public String[] clientPort() {
        return clientPort;
    }

    public String[] serverPort() {
        return serverPort;
    }

    public String source() {
        return source;
    }

    public String destination() {
        return destination;
    }

    @Nullable
    public static Transport parse(String mediaProtocol) {
        return parse(mediaProtocol, null);
    }

    @Nullable
    public static Transport parse(String mediaProtocol, String mediaFormat) {
        String source = null;
        String destination = null;
        String[] clientPort = null;
        String[] serverPort = null;
        @Profile String profile = RAW_PROFILE;
        @LowerTransport String lowerTransport = UDP;
        @TransportProtocol String transportProtocol = RAW_PROTOCOL;

        try {

            if (UDP.equalsIgnoreCase(mediaProtocol) && MP2T_PROTOCOL.equalsIgnoreCase(mediaFormat)) {
                profile = H2221_PROFILE;
                transportProtocol = MP2T_PROTOCOL;

            } else {

                Matcher matcher = regexTransport.matcher(mediaProtocol);
                if (matcher.find()) {
                    String protocol = matcher.group(1);

                    if (protocol == null) {
                        if (matcher.group(4) == null) {
                            lowerTransport = matcher.group(6);
                        } else {
                            transportProtocol = matcher.group(4);
                            profile = matcher.group(5);
                        }
                    } else {
                        transportProtocol = protocol;
                        profile = matcher.group(2);
                        lowerTransport = matcher.group(3);
                    }

                } else {
                    return null;
                }
            }

            if (mediaProtocol.contains("client_port")) {
                Matcher matcher = regexClientPort.matcher(mediaProtocol);
                if (matcher.find()) {
                    if (matcher.group(3) != null) {
                        clientPort = new String[]{matcher.group(3)};
                    } else {
                        clientPort = new String[]{matcher.group(1), matcher.group(2)};
                    }
                }

                if (mediaProtocol.contains("server_port")) {
                    matcher = regexServerPort.matcher(mediaProtocol);
                    if (matcher.find()) {
                        if (matcher.group(3) != null) {
                            serverPort = new String[]{matcher.group(3)};
                        } else {
                            serverPort = new String[]{matcher.group(1), matcher.group(2)};
                        }
                    }

                    matcher = regexSource.matcher(mediaProtocol);
                    if (matcher.find()) {
                        source = matcher.group(0).split("=")[1];
                    }

                    matcher = regexDestination.matcher(mediaProtocol);
                    if (matcher.find()) {
                        destination = matcher.group(0).split("=")[1];
                    }
                }
            }

            return new Transport(transportProtocol, profile, lowerTransport,
                    clientPort, serverPort, source, destination);
        }
        catch (Exception ex) {

        }

        return null;
    }

    /**
     * Returns the string used to identify this transport
     */
    @Override public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(transportProtocol).append('/').append(profile);

        if (lowerTransport.equals(TCP)) {
            str.append('/').append(TCP);
        } else if (profile.equals(H2221_PROFILE) ) {
            str.append('/').append(UDP);
        }

        str.append(';').append(deliveryType);

        if (clientPort != null && clientPort.length > 0) {
            str.append(";client_port=").append(clientPort[0]);

            if (clientPort.length == 2) {
                str.append('-').append(clientPort[1]);
            }
        }

        if (source != null) {
            str.append(";source=").append(source);
        }

        if (destination != null) {
            str.append(";destination=").append(destination);
        }

        if (serverPort != null && serverPort.length > 0) {
            str.append(";server_port=").append(serverPort[0]);

            if (serverPort.length == 2) {
                str.append('-').append(serverPort[1]);
            }
        }

        return str.toString();
    }

    @Override public boolean equals(@Nullable Object other) {
        return other instanceof Transport && other.toString().equals(toString());
    }
}

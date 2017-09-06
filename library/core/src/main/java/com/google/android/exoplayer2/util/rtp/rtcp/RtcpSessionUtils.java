/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.rtp.rtcp;

import com.google.android.exoplayer2.util.net.Connectivity;
import com.google.android.exoplayer2.util.net.NetworkUtils;

/**
 * This class provides generic utility functions for
 * RTCP sessions
 */
public class RtcpSessionUtils {

    public static long SSRC() {
        return (long)((65535L * Math.random()) + 1L);
    }

    public static String CNAME() {
        String iface = "eth0";

        try {

            iface = Connectivity.isConnectedEthernet() ? "eth0" : "wlan0";
        } catch (Exception e) {

        }

        StringBuilder cname = new StringBuilder();
        String macAddress = NetworkUtils.getMACAddress(iface);
        String[] tokensMacAddr = macAddress.split(":");

        //TODO append urn:uuid:

        cname.append(tokensMacAddr[1]);
        cname.append(tokensMacAddr[2]);
        cname.append(tokensMacAddr[3]);
        cname.append('@');
        cname.append(tokensMacAddr[4]);

        return cname.toString();
    }
}

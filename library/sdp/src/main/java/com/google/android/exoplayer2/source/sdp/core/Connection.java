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
package com.google.android.exoplayer2.source.sdp.core;

import android.support.annotation.Nullable;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class Connection {
    private static final Pattern regexSDPConnection = Pattern.compile("IN\\s+(\\S+)\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({IN})
    public @interface NetType {}
    private static final String IN = "IN";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({IP4, IP6})
    public @interface AddrType {}
    private static final String IP4 = "IP4";
    private static final String IP6 = "IP6";

    private @NetType String nettype;
    private @AddrType String addrtype;
    private String connectionAddress;

    Connection(@NetType String nettype, @AddrType String addrtype, String connectionAddress) {
        this.nettype = nettype;
        this.addrtype = addrtype;
        this.connectionAddress = connectionAddress;
    }

    @NetType
    public String nettype() {
        return nettype;
    }

    @AddrType
    public String addrtype() {
        return addrtype;
    }

    public String connectionAddress() {
        return connectionAddress;
    }

    @Nullable
    public static Connection parse(String line) {
        try {

            Matcher matcher = regexSDPConnection.matcher(line);

            if (matcher.find()) {
                if (matcher.group(1).trim().equals(IP4)) {
                    return new Connection(IN, IP4, matcher.group(2).trim());
                }
                else if (matcher.group(1).trim().equals(IP6)) {
                    return new Connection(IN, IP6, matcher.group(2).trim());
                }
            }
        }
        catch (Exception ex) {
            // Do nothing
        }

        return null;
    }
}

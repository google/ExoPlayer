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

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Origin {
    private static final Pattern regexSDPOrigin =
            Pattern.compile("(\\S+)\\s(\\S+)\\s(\\S+)\\sIN\\s(\\w+)(?:\\s+(\\S+))?",
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

    private String username;
    private String sessId;
    private String sessVersion;

    private @NetType String nettype;
    private @AddrType String addrtype;
    private String unicastAddress;

    public Origin(String userName, String unicastAddress) {
        this(userName, IN, IP4, unicastAddress);
    }

    public Origin(String userName,
        @AddrType String addrtype, String unicastAddress) {
        this(userName, IN, addrtype, unicastAddress);
    }

    public Origin(String userName, @NetType String nettype,
        @AddrType String addrtype, String unicastAddress) {
        this(userName, generateId(), generateId(), nettype, addrtype, unicastAddress);
    }

    Origin(String username, String sessId, String sessVersion, @NetType String nettype,
           @AddrType String addrtype, String unicastAddress) {
        this.username = username;
        this.sessId = sessId;
        this.sessVersion = sessVersion;
        this.nettype = nettype;
        this.addrtype = addrtype;

        if (unicastAddress != null) {
            this.unicastAddress = unicastAddress.trim();
        }
    }

    public String username() {
        return username;
    }

    public String sessId() {
        return sessId;
    }

    public String sessVersion() {
        return sessVersion;
    }

    @NetType
    public String nettype() {
        return nettype;
    }

    @AddrType
    public String addrtype() {
        return addrtype;
    }

    public String unicastAddress() {
        return unicastAddress;
    }

    private static String generateId() {
        Random rand = new Random(System.nanoTime());
        long id = System.nanoTime() + System.currentTimeMillis() + rand.nextLong();
        return new String(Long.toString(Math.abs(id)));
    }

    @Nullable
    public static Origin parse(String line) {
        try {
            Matcher matcher = regexSDPOrigin.matcher(line);

            if (matcher.find()) {
                if (matcher.group(4).trim().equals(IP4)) {
                    return new Origin(matcher.group(1).trim(),
                            matcher.group(2).trim(),
                            matcher.group(3).trim(),
                            IN, IP4, matcher.group(5));
                }
                else if (matcher.group(4).trim().equals(IP6)) {
                    return new Origin(matcher.group(1).trim(),
                            matcher.group(2).trim(),
                            matcher.group(3).trim(),
                            IN, IP6, matcher.group(5));
                }
            }

        }
        catch (Exception ex) {

        }

        return null;
    }
}

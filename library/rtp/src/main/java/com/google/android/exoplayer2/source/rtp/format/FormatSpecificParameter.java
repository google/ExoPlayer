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
package com.google.android.exoplayer2.source.rtp.format;

import android.support.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatSpecificParameter {
    private static final Pattern regexMediaAttribute = Pattern.compile("([a-zA-Z-]+)=\\s*(.+)|(\\w+)",
            Pattern.CASE_INSENSITIVE);

    public final static String LEVER_ASYMMETRY_ALLOWED = "level-asymmetry-allowed";
    public final static String IN_BAND_PARAMETER_SETS = "in-band-parameter-sets";
    public final static String PACKETIZATION_MODE = "packetization-mode";
    public final static String PROFILE_LEVEL_ID = "profile-level-id";
    public final static String SPROP_PARAMETER_SETS = "sprop-parameter-sets";
    public final static String SPROP_INTERLEAVING_DEPTH = "sprop-interleaving-depth";
    public final static String SPROP_DEINT_BUF_REQ = "sprop-deint-buf-req";
    public final static String DEINT_BUF_CAP = "deint-buf-cap";
    public final static String SPROP_INIT_BUF_TIME = "sprop-init-buf-time";
    public final static String SPROP_MAX_DON_DIFF = "sprop-max-don-diff";
    public final static String MAX_RCMD_NALU_SIZE = "max-rcmd-nalu-size";
    public final static String SAR_UNDERSTOOD = "sar-understood";
    public final static String SAR_SUPPORTED = "sar-supported";
    public final static String EMPHASIS = "emphasis";
    public final static String CHANNEL_ORDER = "channel-order";
    public final static String CONFIG = "config";
    public final static String CPRESENT = "cpresent";
    public final static String OBJECT = "object";
    public final static String SBR_ENABLED = "sbr-enabled";
    public final static String MPS_ASC = "mps-asc";

    public final static String TYPE = "type";
    public final static String LAYER = "layer";
    public final static String MODE = "mode";

    public final static String MODE_SET = "mode-set";
    public final static String MODE_CHANGE_PERIOD = "mode-change-period";
    public final static String MODE_CHANGE_NEIGHBOR = "mode-change-neighbor";
    public final static String MAXFRAMES = "maxframes";

    public final static String MAX_MBPS = "max‐mbps";
    public final static String MAX_FR = "max‐fr";
    public final static String MAX_FS = "max‐fs";
    public final static String MAX_CPB = "max‐cpb";
    public final static String MAX_DPB = "max‐dpb";
    public final static String MAX_BR = "max‐br";
    public final static String MAX_SMPBS = "max‐smbps";

    public final static String CIF = "cif";
    public final static String QCIF = "qcif";

    public final static String ANNEXB = "annexb";
    public final static String RTCP_FB = "rtcp-fb";

    public final static String MAXAVERAGEBITRATE = "maxaveragebitrate";
    public final static String MAXPLAYBACKRATE = "maxplaybackrate";
    public final static String MAXDISPLACEMENT = "maxdisplacement";
    public final static String CTSDELTALENGTH = "ctsdeltalength";
    public final static String DTSDELTALENGTH = "dtsdeltalength";
    public final static String RANDOMACCESSINDICATION = "randomaccessindication";
    public final static String STREAMSTATEINDICATION = "streamstateindication";
    public final static String AUXILIARYDATASIZELENGTH = "auxiliarydatasizelength";

    public final static String MINPTIME = "minptime";
    public final static String STEREO = "stereo";
    public final static String CBR = "cbr";
    public final static String USEINBANDFEC = "useinbandfec";
    public final static String USEDTX = "usedtx";
    public final static String SPROP_MAXCAPTURERATE = "sprop-maxcapturerate";
    public final static String SPROP_STEREO = "sprop-stereo";

    public final static String STREAMTYPE = "streamtype";
    public final static String SIZELENGTH = "sizelength";
    public final static String INDEXLENGTH = "indexlength";
    public final static String INDEXDELTALENGTH = "indexdeltaLength";
    public final static String CONSTANTDURATION = "constantduration";
    public final static String PROFILE = "profile";
    public final static String MPS_PROFILE_LEVEL_ID = "mps-profile-level-id";
    public final static String MPS_CONFIG = "mps-config";

    public final static String SAMPLING = "sampling";
    public final static String WIDTH = "width";
    public final static String HEIGHT = "height";
    public final static String DEPTH = "depth";
    public final static String COLORIMETRY = "colorimetry";
    public final static String CHROMA_POSITION = "chroma-position";

    public final static String COMPLAW = "complaw";

    private String name;
    private String value;

    FormatSpecificParameter(String name) {
        this.name = name;
    }

    FormatSpecificParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    @Nullable
    public static FormatSpecificParameter parse(String line) {
        try {

            Matcher matcher = regexMediaAttribute.matcher(line);

            if (matcher.find()) {
                return (matcher.group(3) == null) ?
                        new FormatSpecificParameter(matcher.group(1).trim(), matcher.group(2).trim()) :
                        new FormatSpecificParameter(matcher.group(3).trim());
            }

        } catch (Exception ex) {
        }

        return null;
    }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.util;

import java.lang.String;

import android.os.Build;

import com.google.android.exoplayer.util.Util;
import com.google.android.exoplayer.util.MimeTypes;

 public class AmazonQuirks {
    private static final String deviceModel = Build.MODEL;
    private static final String manufacturer = Build.MANUFACTURER;

    public static boolean isAdaptive(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        // Fire TV and tablets till now support adaptive codecs by default for video
        if (manufacturer.equalsIgnoreCase("Amazon") &&
            (mimeType.equalsIgnoreCase(MimeTypes.VIDEO_H264) ||
                mimeType.equalsIgnoreCase(MimeTypes.VIDEO_MP4)) ) {
            return true;
        }
        // non-amazon devices or other video decoders
        return false;
    }
 }

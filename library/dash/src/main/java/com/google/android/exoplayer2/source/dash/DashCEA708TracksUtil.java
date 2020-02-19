/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.Descriptor;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DashCEA708TracksUtil {
  private static final Pattern CEA708_SERVICE_DESCRIPTOR_REGEX = Pattern.compile("([1-63])=lang:(.+)");

  //Check and return CEA-708 text tracks. Add by fred.
  public static Format[] getCea708TrackFormats(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      AdaptationSet adaptationSet = adaptationSets.get(i);
      List<Descriptor> descriptors = adaptationSets.get(i).accessibilityDescriptors;
      for (int j = 0; j < descriptors.size(); j++) {
        Descriptor descriptor = descriptors.get(j);
        if ("urn:scte:dash:cc:cea-708:2015".equals(descriptor.schemeIdUri)) {
          String value = descriptor.value;
          if (value == null) {
            // There are embedded CEA-708 tracks, but service information is not declared.
            return new Format[] {buildCea708TrackFormat(adaptationSet.id)};
          }
          String[] services = Util.split(value, ";");
          Format[] formats = new Format[services.length];
          for (int k = 0; k < services.length; k++) {
            Matcher matcher = CEA708_SERVICE_DESCRIPTOR_REGEX.matcher(services[k]);
            if (!matcher.matches()) {
              // If we can't parse service information for all services, assume a single track.
              return new Format[] {buildCea708TrackFormat(adaptationSet.id)};
            }
            formats[k] =
                buildCea708TrackFormat(
                    adaptationSet.id,
                    /* language= */ matcher.group(2),
                    /* accessibilityService= */ Integer.parseInt(matcher.group(1)));
          }
          return formats;
        }
      }
    }
    return new Format[0];
  }

  private static Format buildCea708TrackFormat(int adaptationSetId) {
    return buildCea708TrackFormat(
        adaptationSetId, /* language= */ null, /* accessibilityChannel= */ Format.NO_VALUE);
  }

  private static Format buildCea708TrackFormat(
      int adaptationSetId, String language, int accessibilityService) {
    return Format.createTextSampleFormat(
        adaptationSetId
            + ":cea708"
            + (accessibilityService != Format.NO_VALUE ? ":" + accessibilityService : ""),
        MimeTypes.APPLICATION_CEA708,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* selectionFlags= */ 0,
        language,
        accessibilityService,
        /* drmInitData= */ null,
        Format.OFFSET_SAMPLE_RELATIVE,
        /* initializationData= */ null);
  }

}


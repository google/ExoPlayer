/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer.mh.analysis;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;

import android.media.MediaCodecInfo;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.transformer.EncoderUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** An analysis test to log encoder capabilities on a device. */
@RunWith(AndroidJUnit4.class)
public class EncoderCapabilityAnalysisTest {

  // TODO(b/228167357): Remove after bumping compileApiVersion to 33.
  /** Re-definition of {@code MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing} in API33. */
  private static final String FEATURE_HdrEditing = "hdr-editing";
  /**
   * Re-definition of {@code MediaCodecInfo.CodecCapabilities.FEATURE_EncodingStatistics} in API33.
   */
  private static final String FEATURE_EncodingStatistics = "encoding-statistics";

  @Test
  public void logEncoderCapabilities() throws Exception {
    ImmutableSet<String> supportedVideoMimeTypes = EncoderUtil.getSupportedVideoMimeTypes();

    // Map from MIME type to a list of maps from capability name to value.
    LinkedHashMap<String, List<Map<String, Object>>> mimeTypeToEncoderInfo = new LinkedHashMap<>();

    for (String mimeType : supportedVideoMimeTypes) {
      ImmutableList<MediaCodecInfo> encoderInfos = EncoderUtil.getSupportedEncoders(mimeType);
      ArrayList<Map<String, Object>> encoderCapabilitiesForMimeType = new ArrayList<>();
      for (MediaCodecInfo encoderInfo : encoderInfos) {
        LinkedHashMap<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("encoder_name", encoderInfo.getName());

        capabilities.put(
            "is_software_encoder", !EncoderUtil.isHardwareAccelerated(encoderInfo, mimeType));

        // Bitrate modes.
        capabilities.put(
            "supports_vbr",
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, BITRATE_MODE_VBR));
        capabilities.put(
            "supports_cbr",
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, BITRATE_MODE_CBR));
        capabilities.put(
            "supports_cq",
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, BITRATE_MODE_CQ));
        capabilities.put(
            "supports_cbr_fd",
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, BITRATE_MODE_CBR_FD));

        capabilities.put(
            "supported_bitrate_range",
            rangeToString(EncoderUtil.getSupportedBitrateRange(encoderInfo, mimeType)));

        // Resolution support.
        Pair<Range<Integer>, Range<Integer>> supportedResolutionRanges =
            EncoderUtil.getSupportedResolutionRanges(encoderInfo, mimeType);
        capabilities.put("supported_widths_range", rangeToString(supportedResolutionRanges.first));
        capabilities.put(
            "supported_heights_range", rangeToString(supportedResolutionRanges.second));

        checkResolutionSupport(
            encoderInfo, mimeType, capabilities, /* width= */ 1280, /* height= */ 720);
        checkResolutionSupport(
            encoderInfo, mimeType, capabilities, /* width= */ 1920, /* height= */ 1080);
        checkResolutionSupport(
            encoderInfo, mimeType, capabilities, /* width= */ 2560, /* height= */ 1440);
        checkResolutionSupport(
            encoderInfo, mimeType, capabilities, /* width= */ 3840, /* height= */ 2160);

        checkProfileLevelSupport(encoderInfo, mimeType, capabilities);

        capabilities.put(
            "supported_color_profiles",
            EncoderUtil.getSupportedColorFormats(encoderInfo, mimeType));

        capabilities.put(
            "max_supported_instances",
            Util.SDK_INT >= 23 ? EncoderUtil.getMaxSupportedInstances(encoderInfo, mimeType) : -1);

        capabilities.put(
            "supports_qp_bounds",
            Util.SDK_INT >= 31
                && EncoderUtil.isFeatureSupported(
                    encoderInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_QpBounds));

        capabilities.put(
            "supports_hdr_editing",
            Util.SDK_INT >= 33
                && EncoderUtil.isFeatureSupported(encoderInfo, mimeType, FEATURE_HdrEditing));

        capabilities.put(
            "supports_encoding_statistics",
            Util.SDK_INT >= 33
                && EncoderUtil.isFeatureSupported(
                    encoderInfo, mimeType, FEATURE_EncodingStatistics));

        encoderCapabilitiesForMimeType.add(capabilities);
      }
      mimeTypeToEncoderInfo.put(mimeType, encoderCapabilitiesForMimeType);
    }

    JSONObject resultJson = new JSONObject();
    resultJson.put("encoder_capabilities", JSONObject.wrap(mimeTypeToEncoderInfo));
    AndroidTestUtil.writeTestSummaryToFile(
        ApplicationProvider.getApplicationContext(),
        /* testId= */ "encoderCapabilityAnalysisTest",
        resultJson);
  }

  private static void checkResolutionSupport(
      MediaCodecInfo encoder,
      String mimeType,
      Map<String, Object> capabilities,
      int width,
      int height) {
    Range<Integer> supportedWidths =
        EncoderUtil.getSupportedResolutionRanges(encoder, mimeType).first;
    @Nullable Range<Integer> supportedHeights = null;
    if (supportedWidths.contains(width)) {
      supportedHeights = EncoderUtil.getSupportedHeights(encoder, mimeType, width);
    }
    capabilities.put(
        Util.formatInvariant("supported_heights_for_%d", width), rangeToString(supportedHeights));

    @Nullable
    Size supportedResolution = EncoderUtil.getSupportedResolution(encoder, mimeType, width, height);
    if (supportedResolution == null) {
      supportedResolution = new Size(/* width= */ 0, /* height= */ 0);
    }
    capabilities.put(
        Util.formatInvariant("supports_%dx%d", width, height),
        (supportedResolution.getWidth() == width && supportedResolution.getHeight() == height));
    capabilities.put(
        Util.formatInvariant("fallback_%dx%d", width, height), sizeToString(supportedResolution));
  }

  private static void checkProfileLevelSupport(
      MediaCodecInfo encoder, String mimeType, Map<String, Object> capabilities) {
    LinkedHashMap<String, String> profileToHighestSupportedLevel = new LinkedHashMap<>();
    ImmutableSet<Integer> supportedEncodingProfiles =
        EncoderUtil.findSupportedEncodingProfiles(encoder, mimeType);
    for (int profile : supportedEncodingProfiles) {
      profileToHighestSupportedLevel.put(
          String.valueOf(profile),
          String.valueOf(
              EncoderUtil.findHighestSupportedEncodingLevel(encoder, mimeType, profile)));
    }
    capabilities.put("supported_profile_levels", profileToHighestSupportedLevel);
  }

  private static String rangeToString(@Nullable Range<Integer> range) {
    return range == null
        ? "0-0"
        : Util.formatInvariant("%d-%d", range.getLower(), range.getUpper());
  }

  private static String sizeToString(@Nullable Size size) {
    return size == null ? "0x0" : Util.formatInvariant("%dx%d", size.getWidth(), size.getHeight());
  }
}

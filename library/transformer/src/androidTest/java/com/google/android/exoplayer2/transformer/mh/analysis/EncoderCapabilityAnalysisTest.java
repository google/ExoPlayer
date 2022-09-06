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

import android.media.CamcorderProfile;
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

  private static final String CAMCORDER_FORMAT_STRING = "%dx%d@%dfps:%dkbps";
  private static final String CAMCORDER_TIMELAPSE_FORMAT_STRING = "timelapse_%dx%d@%dfps:%dkbps";
  private static final String CAMCORDER_HIGH_SPEED_FORMAT_STRING = "highspeed_%dx%d@%dfps:%dkbps";

  private static final ImmutableList<Integer> DEFINED_CAMCORDER_PROFILES =
      ImmutableList.of(
          CamcorderProfile.QUALITY_QCIF,
          CamcorderProfile.QUALITY_CIF,
          CamcorderProfile.QUALITY_480P,
          CamcorderProfile.QUALITY_720P,
          CamcorderProfile.QUALITY_1080P,
          CamcorderProfile.QUALITY_QVGA,
          CamcorderProfile.QUALITY_2160P,
          CamcorderProfile.QUALITY_VGA,
          CamcorderProfile.QUALITY_4KDCI,
          CamcorderProfile.QUALITY_QHD,
          CamcorderProfile.QUALITY_2K,
          CamcorderProfile.QUALITY_8KUHD,
          CamcorderProfile.QUALITY_TIME_LAPSE_QCIF,
          CamcorderProfile.QUALITY_TIME_LAPSE_CIF,
          CamcorderProfile.QUALITY_TIME_LAPSE_480P,
          CamcorderProfile.QUALITY_TIME_LAPSE_720P,
          CamcorderProfile.QUALITY_TIME_LAPSE_1080P,
          CamcorderProfile.QUALITY_TIME_LAPSE_QVGA,
          CamcorderProfile.QUALITY_TIME_LAPSE_2160P,
          CamcorderProfile.QUALITY_TIME_LAPSE_VGA,
          CamcorderProfile.QUALITY_TIME_LAPSE_4KDCI,
          CamcorderProfile.QUALITY_TIME_LAPSE_QHD,
          CamcorderProfile.QUALITY_TIME_LAPSE_2K,
          CamcorderProfile.QUALITY_TIME_LAPSE_8KUHD,
          CamcorderProfile.QUALITY_HIGH_SPEED_480P,
          CamcorderProfile.QUALITY_HIGH_SPEED_720P,
          CamcorderProfile.QUALITY_HIGH_SPEED_1080P,
          CamcorderProfile.QUALITY_HIGH_SPEED_2160P,
          CamcorderProfile.QUALITY_HIGH_SPEED_CIF,
          CamcorderProfile.QUALITY_HIGH_SPEED_VGA,
          CamcorderProfile.QUALITY_HIGH_SPEED_4KDCI);

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
                && EncoderUtil.isFeatureSupported(
                    encoderInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing));

        capabilities.put(
            "supports_encoding_statistics",
            Util.SDK_INT >= 33
                && EncoderUtil.isFeatureSupported(
                    encoderInfo,
                    mimeType,
                    MediaCodecInfo.CodecCapabilities.FEATURE_EncodingStatistics));

        encoderCapabilitiesForMimeType.add(capabilities);
      }
      mimeTypeToEncoderInfo.put(mimeType, encoderCapabilitiesForMimeType);
    }

    JSONObject resultJson = new JSONObject();
    resultJson.put("encoder_capabilities", JSONObject.wrap(mimeTypeToEncoderInfo));
    resultJson.put("camcorder_profiles_supported", getSupportedCamcorderProfileConfigurations());
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

  private static ImmutableList<String> getSupportedCamcorderProfileConfigurations() {
    ImmutableList.Builder<String> supportedConfigurations = new ImmutableList.Builder<>();
    for (int profileIndex : DEFINED_CAMCORDER_PROFILES) {
      if (CamcorderProfile.hasProfile(profileIndex)) {
        CamcorderProfile profile = CamcorderProfile.get(profileIndex);
        supportedConfigurations.add(
            Util.formatInvariant(
                profileIndex > CamcorderProfile.QUALITY_HIGH_SPEED_LOW
                    ? CAMCORDER_HIGH_SPEED_FORMAT_STRING
                    : profileIndex > CamcorderProfile.QUALITY_TIME_LAPSE_LOW
                        ? CAMCORDER_TIMELAPSE_FORMAT_STRING
                        : CAMCORDER_FORMAT_STRING,
                profile.videoFrameWidth,
                profile.videoFrameHeight,
                profile.videoFrameRate,
                // Converts bps to kbps.
                profile.videoBitRate / 1000));
      }
    }
    return supportedConfigurations.build();
  }
}

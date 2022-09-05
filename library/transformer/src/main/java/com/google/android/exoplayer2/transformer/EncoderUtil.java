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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.round;

import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ColorTransfer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.base.Ascii;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

/** Utility methods for {@link MediaCodec} encoders. */
public final class EncoderUtil {

  /** A value to indicate the encoding level is not set. */
  public static final int LEVEL_UNSET = Format.NO_VALUE;

  private static final Supplier<ImmutableListMultimap<String, MediaCodecInfo>>
      MIME_TYPE_TO_ENCODERS = Suppliers.memoize(EncoderUtil::populateEncoderInfos);

  /**
   * Returns a list of {@linkplain MediaCodecInfo encoders} that support the given {@code mimeType},
   * or an empty list if there is none.
   */
  public static ImmutableList<MediaCodecInfo> getSupportedEncoders(String mimeType) {
    return checkNotNull(MIME_TYPE_TO_ENCODERS.get()).get(Ascii.toLowerCase(mimeType));
  }

  /** Returns a list of video {@linkplain MimeTypes MIME types} that can be encoded. */
  public static ImmutableSet<String> getSupportedVideoMimeTypes() {
    return checkNotNull(MIME_TYPE_TO_ENCODERS.get()).keySet();
  }

  /**
   * Returns the names of encoders that support HDR editing for the given format, or an empty list
   * if the format is unknown or not supported for HDR encoding.
   */
  public static ImmutableList<String> getSupportedEncoderNamesForHdrEditing(
      String mimeType, @Nullable ColorInfo colorInfo) {
    if (Util.SDK_INT < 31 || colorInfo == null) {
      return ImmutableList.of();
    }

    @ColorTransfer int colorTransfer = colorInfo.colorTransfer;
    ImmutableList<Integer> profiles = getCodecProfilesForHdrFormat(mimeType, colorTransfer);
    ImmutableList.Builder<String> resultBuilder = ImmutableList.builder();
    ImmutableList<MediaCodecInfo> mediaCodecInfos =
        EncoderSelector.DEFAULT.selectEncoderInfos(mimeType);
    for (int i = 0; i < mediaCodecInfos.size(); i++) {
      MediaCodecInfo mediaCodecInfo = mediaCodecInfos.get(i);
      if (mediaCodecInfo.isAlias()
          || !isFeatureSupported(
              mediaCodecInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing)) {
        continue;
      }
      for (MediaCodecInfo.CodecProfileLevel codecProfileLevel :
          mediaCodecInfo.getCapabilitiesForType(mimeType).profileLevels) {
        if (profiles.contains(codecProfileLevel.profile)) {
          resultBuilder.add(mediaCodecInfo.getName());
        }
      }
    }
    return resultBuilder.build();
  }

  /**
   * Returns the {@linkplain MediaCodecInfo.CodecProfileLevel#profile profile} constants that can be
   * used to encode the given HDR format, if supported by the device (this method does not check
   * device capabilities). If multiple profiles are returned, they are ordered by expected level of
   * compatibility, with the most widely compatible profile first.
   */
  @SuppressWarnings("InlinedApi") // Safe use of inlined constants from newer API versions.
  public static ImmutableList<Integer> getCodecProfilesForHdrFormat(
      String mimeType, @ColorTransfer int colorTransfer) {
    // TODO(b/239174610): Add a way to determine profiles for DV and HDR10+.
    switch (mimeType) {
      case MimeTypes.VIDEO_VP9:
        if (colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084) {
          // Profiles support both HLG and PQ.
          return ImmutableList.of(
              MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
              MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR);
        }
        break;
      case MimeTypes.VIDEO_H264:
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10);
        }
        // CodecProfileLevel does not support PQ/HDR10 for H264.
        break;
      case MimeTypes.VIDEO_H265:
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
        } else if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10);
        }
        break;
      case MimeTypes.VIDEO_AV1:
        if (colorTransfer == C.COLOR_TRANSFER_HLG) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10);
        } else if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
          return ImmutableList.of(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10);
        }
        break;
      default:
        break;
    }
    // There are no profiles defined for the HDR format, or it's invalid.
    return ImmutableList.of();
  }

  /** Returns whether the {@linkplain MediaCodecInfo encoder} supports the given resolution. */
  public static boolean isSizeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {
    if (encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .isSizeSupported(width, height)) {
      return true;
    }

    // Some devices (Samsung, Huawei, and Pixel 6. See b/222095724) under-report their encoding
    // capabilities. The supported height reported for H265@3840x2160 is 2144, and
    // H264@1920x1080 is 1072. See b/229825948.
    // Cross reference with CamcorderProfile to ensure a resolution is supported.
    if (width == 1920 && height == 1080) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P);
    }
    if (width == 3840 && height == 2160) {
      return CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P);
    }
    return false;
  }

  /**
   * Returns a {@link Range} of supported heights for the given {@link MediaCodecInfo encoder},
   * {@linkplain MimeTypes MIME type} and {@code width}.
   *
   * @throws IllegalArgumentException When the width is not in the range of {@linkplain
   *     #getSupportedResolutionRanges supported widths}.
   */
  public static Range<Integer> getSupportedHeights(
      MediaCodecInfo encoderInfo, String mimeType, int width) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .getSupportedHeightsFor(width);
  }

  /**
   * Returns a {@link Pair} of supported width and height {@link Range ranges} for the given {@link
   * MediaCodecInfo encoder} and {@linkplain MimeTypes MIME type}.
   */
  public static Pair<Range<Integer>, Range<Integer>> getSupportedResolutionRanges(
      MediaCodecInfo encoderInfo, String mimeType) {
    MediaCodecInfo.VideoCapabilities videoCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
    return Pair.create(
        videoCapabilities.getSupportedWidths(), videoCapabilities.getSupportedHeights());
  }

  /**
   * Finds an {@linkplain MediaCodecInfo encoder}'s supported resolution from a given resolution.
   *
   * <p>The input resolution is returned, if it (after aligning to the encoder's requirement) is
   * supported by the {@linkplain MediaCodecInfo encoder}.
   *
   * <p>The resolution will be adjusted to be within the {@linkplain MediaCodecInfo encoder}'s range
   * of supported resolutions, and will be aligned to the {@linkplain MediaCodecInfo encoder}'s
   * alignment requirement. The adjustment process takes into account the original aspect ratio. But
   * the fixed resolution may not preserve the original aspect ratio, depending on the encoder's
   * required size alignment.
   *
   * @param encoderInfo The {@link MediaCodecInfo} of the encoder.
   * @param mimeType The output MIME type.
   * @param width The original width.
   * @param height The original height.
   * @return A {@linkplain Size supported resolution}, or {@code null} if unable to find a fallback.
   */
  @Nullable
  public static Size getSupportedResolution(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {
    MediaCodecInfo.VideoCapabilities videoEncoderCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
    int widthAlignment = videoEncoderCapabilities.getWidthAlignment();
    int heightAlignment = videoEncoderCapabilities.getHeightAlignment();

    // Fix size alignment.
    width = alignResolution(width, widthAlignment);
    height = alignResolution(height, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, width, height)) {
      return new Size(width, height);
    }

    // Try three-fourths (e.g. 1440 -> 1080).
    int newWidth = alignResolution(width * 3 / 4, widthAlignment);
    int newHeight = alignResolution(height * 3 / 4, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try two-thirds (e.g. 4k -> 1440).
    newWidth = alignResolution(width * 2 / 3, widthAlignment);
    newHeight = alignResolution(height * 2 / 3, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, width, height)) {
      return new Size(newWidth, newHeight);
    }

    // Try half (e.g. 4k -> 1080).
    newWidth = alignResolution(width / 2, widthAlignment);
    newHeight = alignResolution(height / 2, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try one-third (e.g. 4k -> 720).
    newWidth = alignResolution(width / 3, widthAlignment);
    newHeight = alignResolution(height / 3, heightAlignment);
    if (isSizeSupported(encoderInfo, mimeType, newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Fix frame being too wide or too tall.
    width = videoEncoderCapabilities.getSupportedWidths().clamp(width);
    int adjustedHeight = videoEncoderCapabilities.getSupportedHeightsFor(width).clamp(height);
    if (adjustedHeight != height) {
      width =
          alignResolution((int) round((double) width * adjustedHeight / height), widthAlignment);
      height = alignResolution(adjustedHeight, heightAlignment);
    }

    return isSizeSupported(encoderInfo, mimeType, width, height) ? new Size(width, height) : null;
  }

  /**
   * Returns a {@link ImmutableSet set} of supported {@linkplain MediaCodecInfo.CodecProfileLevel
   * encoding profiles} for the given {@linkplain MediaCodecInfo encoder} and {@linkplain MimeTypes
   * MIME type}.
   */
  public static ImmutableSet<Integer> findSupportedEncodingProfiles(
      MediaCodecInfo encoderInfo, String mimeType) {
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;
    ImmutableSet.Builder<Integer> supportedProfilesBuilder = new ImmutableSet.Builder<>();
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      supportedProfilesBuilder.add(profileLevel.profile);
    }
    return supportedProfilesBuilder.build();
  }

  /**
   * Finds the highest supported encoding level given a profile.
   *
   * @param encoderInfo The {@link MediaCodecInfo encoderInfo}.
   * @param mimeType The {@linkplain MimeTypes MIME type}.
   * @param profile The encoding profile.
   * @return The highest supported encoding level, as documented in {@link
   *     MediaCodecInfo.CodecProfileLevel}, or {@link #LEVEL_UNSET} if the profile is not supported.
   */
  public static int findHighestSupportedEncodingLevel(
      MediaCodecInfo encoderInfo, String mimeType, int profile) {
    // TODO(b/214964116): Merge into MediaCodecUtil.
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;

    int maxSupportedLevel = LEVEL_UNSET;
    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      if (profileLevel.profile == profile) {
        maxSupportedLevel = max(maxSupportedLevel, profileLevel.level);
      }
    }
    return maxSupportedLevel;
  }

  /**
   * Finds a {@link MediaCodec} that supports the {@link MediaFormat}, or {@code null} if none is
   * found.
   */
  @Nullable
  public static String findCodecForFormat(MediaFormat format, boolean isDecoder) {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    // Format must not include KEY_FRAME_RATE on API21.
    // https://developer.android.com/reference/android/media/MediaCodecList#findDecoderForFormat(android.media.MediaFormat)
    float frameRate = Format.NO_VALUE;
    if (Util.SDK_INT == 21 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      try {
        frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE);
      } catch (ClassCastException e) {
        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
      }
      // Clears the frame rate field.
      format.setString(MediaFormat.KEY_FRAME_RATE, null);
    }

    String mediaCodecName =
        isDecoder
            ? mediaCodecList.findDecoderForFormat(format)
            : mediaCodecList.findEncoderForFormat(format);

    if (Util.SDK_INT == 21) {
      MediaFormatUtil.maybeSetInteger(format, MediaFormat.KEY_FRAME_RATE, round(frameRate));
    }
    return mediaCodecName;
  }

  /** Returns the range of supported bitrates for the given {@linkplain MimeTypes MIME type}. */
  public static Range<Integer> getSupportedBitrateRange(
      MediaCodecInfo encoderInfo, String mimeType) {
    return encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities().getBitrateRange();
  }

  /** Returns whether the bitrate mode is supported by the encoder. */
  public static boolean isBitrateModeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int bitrateMode) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getEncoderCapabilities()
        .isBitrateModeSupported(bitrateMode);
  }

  /**
   * Returns a {@link ImmutableList list} of supported {@linkplain
   * MediaCodecInfo.CodecCapabilities#colorFormats color formats} for the given {@linkplain
   * MediaCodecInfo encoder} and {@linkplain MimeTypes MIME type}.
   */
  public static ImmutableList<Integer> getSupportedColorFormats(
      MediaCodecInfo encoderInfo, String mimeType) {
    return ImmutableList.copyOf(
        Ints.asList(encoderInfo.getCapabilitiesForType(mimeType).colorFormats));
  }

  /** Checks if a {@linkplain MediaCodecInfo codec} is hardware-accelerated. */
  public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo, String mimeType) {
    // TODO(b/214964116): Merge into MediaCodecUtil.
    if (Util.SDK_INT >= 29) {
      return Api29.isHardwareAccelerated(encoderInfo);
    }
    // codecInfo.isHardwareAccelerated() == !codecInfo.isSoftwareOnly() is not necessarily true.
    // However, we assume this to be true as an approximation.
    return !isSoftwareOnly(encoderInfo, mimeType);
  }

  /** Returns whether a given feature is supported. */
  public static boolean isFeatureSupported(
      MediaCodecInfo encoderInfo, String mimeType, String featureName) {
    return encoderInfo.getCapabilitiesForType(mimeType).isFeatureSupported(featureName);
  }

  /** Returns the number of max number of the supported concurrent codec instances. */
  @RequiresApi(23)
  public static int getMaxSupportedInstances(MediaCodecInfo encoderInfo, String mimeType) {
    return encoderInfo.getCapabilitiesForType(mimeType).getMaxSupportedInstances();
  }

  private static boolean isSoftwareOnly(MediaCodecInfo encoderInfo, String mimeType) {
    if (Util.SDK_INT >= 29) {
      return Api29.isSoftwareOnly(encoderInfo);
    }

    if (MimeTypes.isAudio(mimeType)) {
      // Assume audio decoders are software only.
      return true;
    }
    String codecName = Ascii.toLowerCase(encoderInfo.getName());
    if (codecName.startsWith("arc.")) {
      // App Runtime for Chrome (ARC) codecs
      return false;
    }

    // Estimate whether a codec is software-only, to emulate isSoftwareOnly on API < 29.
    return codecName.startsWith("omx.google.")
        || codecName.startsWith("omx.ffmpeg.")
        || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
        || codecName.equals("omx.qcom.video.decoder.hevcswvdec")
        || codecName.startsWith("c2.android.")
        || codecName.startsWith("c2.google.")
        || (!codecName.startsWith("omx.") && !codecName.startsWith("c2."));
  }

  /**
   * Align to the closest resolution that respects the encoder's supported alignment.
   *
   * <p>For example, size 35 will be aligned to 32 if the alignment is 16, and size 45 will be
   * aligned to 48.
   */
  private static int alignResolution(int size, int alignment) {
    // Aligning to resolutions that are multiples of 10, like from 1081 to 1080, assuming alignment
    // is 2 in most encoders.
    boolean shouldRoundDown = false;
    if (size % 10 == 1) {
      shouldRoundDown = true;
    }
    return shouldRoundDown
        ? (int) (alignment * Math.floor((float) size / alignment))
        : alignment * Math.round((float) size / alignment);
  }

  private static ImmutableListMultimap<String, MediaCodecInfo> populateEncoderInfos() {
    ImmutableListMultimap.Builder<String, MediaCodecInfo> encoderInfosBuilder =
        new ImmutableListMultimap.Builder<>();

    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    MediaCodecInfo[] allCodecInfos = mediaCodecList.getCodecInfos();

    for (MediaCodecInfo mediaCodecInfo : allCodecInfos) {
      if (!mediaCodecInfo.isEncoder()) {
        continue;
      }
      String[] supportedMimeTypes = mediaCodecInfo.getSupportedTypes();
      for (String mimeType : supportedMimeTypes) {
        if (MimeTypes.isVideo(mimeType)) {
          encoderInfosBuilder.put(Ascii.toLowerCase(mimeType), mediaCodecInfo);
        }
      }
    }
    return encoderInfosBuilder.build();
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo) {
      return encoderInfo.isHardwareAccelerated();
    }

    @DoNotInline
    public static boolean isSoftwareOnly(MediaCodecInfo encoderInfo) {
      return encoderInfo.isSoftwareOnly();
    }
  }

  private EncoderUtil() {}
}

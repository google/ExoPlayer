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

package androidx.media3.transformer;

import static java.lang.Math.max;
import static java.lang.Math.round;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for {@link MediaCodec} encoders. */
@UnstableApi
public final class EncoderUtil {

  /** A value to indicate the encoding level is not set. */
  public static final int LEVEL_UNSET = Format.NO_VALUE;

  private static final List<MediaCodecInfo> encoders = new ArrayList<>();

  /**
   * Returns a list of {@link MediaCodecInfo encoders} that support the given {@code mimeType}, or
   * an empty list if there is none.
   */
  public static ImmutableList<MediaCodecInfo> getSupportedEncoders(String mimeType) {
    maybePopulateEncoderInfos();

    ImmutableList.Builder<MediaCodecInfo> availableEncoders = new ImmutableList.Builder<>();
    for (int i = 0; i < encoders.size(); i++) {
      MediaCodecInfo encoderInfo = encoders.get(i);
      String[] supportedMimeTypes = encoderInfo.getSupportedTypes();
      for (String supportedMimeType : supportedMimeTypes) {
        if (Ascii.equalsIgnoreCase(supportedMimeType, mimeType)) {
          availableEncoders.add(encoderInfo);
        }
      }
    }
    return availableEncoders.build();
  }

  /**
   * Finds a {@link MediaCodecInfo encoder}'s supported resolution from a given resolution.
   *
   * <p>The input resolution is returned, if it (after aligning to the encoders requirement) is
   * supported by the {@link MediaCodecInfo encoder}.
   *
   * <p>The resolution will be adjusted to be within the {@link MediaCodecInfo encoder}'s range of
   * supported resolutions, and will be aligned to the {@link MediaCodecInfo encoder}'s alignment
   * requirement. The adjustment process takes into account the original aspect ratio. But the fixed
   * resolution may not preserve the original aspect ratio, depending on the encoder's required size
   * alignment.
   *
   * @param encoderInfo The {@link MediaCodecInfo} of the encoder.
   * @param mimeType The output MIME type.
   * @param width The original width.
   * @param height The original height.
   * @return A {@link Size supported resolution}, or {@code null} if unable to find a fallback.
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
    if (videoEncoderCapabilities.isSizeSupported(width, height)) {
      return new Size(width, height);
    }

    // Try three-fourths (e.g. 1440 -> 1080).
    int newWidth = alignResolution(width * 3 / 4, widthAlignment);
    int newHeight = alignResolution(height * 3 / 4, heightAlignment);
    if (videoEncoderCapabilities.isSizeSupported(newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try two-thirds (e.g. 4k -> 1440).
    newWidth = alignResolution(width * 2 / 3, widthAlignment);
    newHeight = alignResolution(height * 2 / 3, heightAlignment);
    if (videoEncoderCapabilities.isSizeSupported(newWidth, newHeight)) {
      return new Size(newWidth, newHeight);
    }

    // Try half (e.g. 4k -> 1080).
    newWidth = alignResolution(width / 2, widthAlignment);
    newHeight = alignResolution(height / 2, heightAlignment);
    if (videoEncoderCapabilities.isSizeSupported(newWidth, newHeight)) {
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

    return videoEncoderCapabilities.isSizeSupported(width, height) ? new Size(width, height) : null;
  }

  /**
   * Finds the highest supported encoding level given a profile.
   *
   * @param encoderInfo The {@link MediaCodecInfo encoderInfo}.
   * @param mimeType The {@link MimeTypes MIME type}.
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
   * Finds a {@link MediaCodec codec} that supports the {@link MediaFormat}, or {@code null} if none
   * is found.
   */
  @Nullable
  public static String findCodecForFormat(MediaFormat format, boolean isDecoder) {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
    // Format must not include KEY_FRAME_RATE on API21.
    // https://developer.android.com/reference/android/media/MediaCodecList#findDecoderForFormat(android.media.MediaFormat)
    @Nullable String frameRate = null;
    if (Util.SDK_INT == 21 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      frameRate = format.getString(MediaFormat.KEY_FRAME_RATE);
      format.setString(MediaFormat.KEY_FRAME_RATE, null);
    }

    String mediaCodecName =
        isDecoder
            ? mediaCodecList.findDecoderForFormat(format)
            : mediaCodecList.findEncoderForFormat(format);

    if (Util.SDK_INT == 21) {
      MediaFormatUtil.maybeSetString(format, MediaFormat.KEY_FRAME_RATE, frameRate);
    }
    return mediaCodecName;
  }

  /**
   * Finds the {@link MediaCodecInfo encoder}'s closest supported bitrate from the given bitrate.
   */
  public static int getClosestSupportedBitrate(
      MediaCodecInfo encoderInfo, String mimeType, int bitrate) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getVideoCapabilities()
        .getBitrateRange()
        .clamp(bitrate);
  }

  /** Returns whether the bitrate mode is supported by the encoder. */
  public static boolean isBitrateModeSupported(
      MediaCodecInfo encoderInfo, String mimeType, int bitrateMode) {
    return encoderInfo
        .getCapabilitiesForType(mimeType)
        .getEncoderCapabilities()
        .isBitrateModeSupported(bitrateMode);
  }

  /** Checks if a {@link MediaCodecInfo codec} is hardware-accelerated. */
  public static boolean isHardwareAccelerated(MediaCodecInfo encoderInfo, String mimeType) {
    // TODO(b/214964116): Merge into MediaCodecUtil.
    if (Util.SDK_INT >= 29) {
      return Api29.isHardwareAccelerated(encoderInfo);
    }
    // codecInfo.isHardwareAccelerated() == !codecInfo.isSoftwareOnly() is not necessarily true.
    // However, we assume this to be true as an approximation.
    return !isSoftwareOnly(encoderInfo, mimeType);
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

  private static synchronized void maybePopulateEncoderInfos() {
    if (encoders.isEmpty()) {
      MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
      MediaCodecInfo[] allCodecInfos = mediaCodecList.getCodecInfos();

      for (MediaCodecInfo mediaCodecInfo : allCodecInfos) {
        if (!mediaCodecInfo.isEncoder()) {
          continue;
        }
        encoders.add(mediaCodecInfo);
      }
    }
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

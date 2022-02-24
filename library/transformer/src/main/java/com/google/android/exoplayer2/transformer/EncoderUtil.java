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

import static java.lang.Math.round;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for {@link MediaCodec} encoders. */
public final class EncoderUtil {

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
   * Finds the {@link MediaCodecInfo encoder}'s closest supported resolution from the given
   * resolution.
   *
   * <p>The input resolution is returned, if it is supported by the {@link MediaCodecInfo encoder}.
   *
   * <p>The resolution will be clamped to the {@link MediaCodecInfo encoder}'s range of supported
   * resolutions, and adjusted to the {@link MediaCodecInfo encoder}'s size alignment. The
   * adjustment process takes into account the original aspect ratio. But the fixed resolution may
   * not preserve the original aspect ratio, depending on the encoder's required size alignment.
   *
   * @param encoderInfo The {@link MediaCodecInfo} of the encoder.
   * @param mimeType The output MIME type.
   * @param width The original width.
   * @param height The original height.
   * @return A {@link Pair} of width and height, or {@code null} if unable to find a fix.
   */
  @Nullable
  public static Pair<Integer, Integer> getClosestSupportedResolution(
      MediaCodecInfo encoderInfo, String mimeType, int width, int height) {
    MediaCodecInfo.VideoCapabilities videoEncoderCapabilities =
        encoderInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();

    if (videoEncoderCapabilities.isSizeSupported(width, height)) {
      return Pair.create(width, height);
    }

    // Fix frame being too wide or too tall.
    int adjustedHeight = videoEncoderCapabilities.getSupportedHeights().clamp(height);
    if (adjustedHeight != height) {
      width = (int) round((double) width * adjustedHeight / height);
      height = adjustedHeight;
    }

    int adjustedWidth = videoEncoderCapabilities.getSupportedWidths().clamp(width);
    if (adjustedWidth != width) {
      height = (int) round((double) height * adjustedWidth / width);
      width = adjustedWidth;
    }

    // Fix pixel alignment.
    width = alignResolution(width, videoEncoderCapabilities.getWidthAlignment());
    height = alignResolution(height, videoEncoderCapabilities.getHeightAlignment());

    return videoEncoderCapabilities.isSizeSupported(width, height)
        ? Pair.create(width, height)
        : null;
  }

  /** Returns whether the {@link MediaCodecInfo encoder} supports the given profile and level. */
  public static boolean isProfileLevelSupported(
      MediaCodecInfo encoderInfo, String mimeType, int profile, int level) {
    MediaCodecInfo.CodecProfileLevel[] profileLevels =
        encoderInfo.getCapabilitiesForType(mimeType).profileLevels;

    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
      if (profileLevel.profile == profile && profileLevel.level == level) {
        return true;
      }
    }
    return false;
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

  /**
   * Align to the closest resolution that respects the encoder's supported alignment.
   *
   * <p>For example, size 35 will be aligned to 32 if the alignment is 16, and size 45 will be
   * aligned to 48.
   */
  private static int alignResolution(int size, int alignment) {
    return alignment * Math.round((float) size / alignment);
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

  private EncoderUtil() {}
}

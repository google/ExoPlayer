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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.util.Pair;

/**
 * Contains information about a media decoder.
 */
@TargetApi(16)
public final class DecoderInfo {

  /**
   * The name of the decoder.
   * <p>
   * May be passed to {@link android.media.MediaCodec#createByCodecName(String)} to create an
   * instance of the decoder.
   */
  public final String name;

  /**
   * Whether the decoder supports seamless resolution switches.
   *
   * @see android.media.MediaCodecInfo.CodecCapabilities#isFeatureSupported(String)
   * @see android.media.MediaCodecInfo.CodecCapabilities#FEATURE_AdaptivePlayback
   */
  public final boolean adaptive;

  private final String mimeType;
  private final CodecCapabilities capabilities;

  public static DecoderInfo newPassthroughInstance(String name) {
    return new DecoderInfo(name, null, null);
  }

  public static DecoderInfo newInstance(String name, String mimeType,
      CodecCapabilities capabilities) {
    return new DecoderInfo(name, mimeType, capabilities);
  }

  /**
   * @param name The name of the decoder.
   * @param capabilities The capabilities of the decoder.
   */
  private DecoderInfo(String name, String mimeType, CodecCapabilities capabilities) {
    this.name = Assertions.checkNotNull(name);
    this.mimeType = mimeType;
    this.capabilities = capabilities;
    adaptive = capabilities != null && isAdaptive(capabilities);
  }

  /**
   * The profile levels supported by the decoder.
   *
   * @return The profile levels supported by the decoder.
   */
  public CodecProfileLevel[] getProfileLevels() {
    return capabilities == null || capabilities.profileLevels == null ? new CodecProfileLevel[0]
        : capabilities.profileLevels;
  }

  /**
   * Whether the decoder supports the given {@code codec}. If there is insufficient information to
   * decide, returns true.
   *
   * @param codec Codec string as defined in RFC 6381.
   * @return True if the given codec is supported by the decoder.
   */
  public boolean isCodecSupported(String codec) {
    if (codec == null || mimeType == null) {
      return true;
    }
    String codecMimeType = MimeTypes.getMediaMimeType(codec);
    if (codecMimeType == null) {
      return true;
    }
    if (!mimeType.equals(codecMimeType)) {
      return false;
    }
    if (!codecMimeType.equals(MimeTypes.VIDEO_H265)) {
      return true;
    }
    Pair<Integer, Integer> codecProfileAndLevel =
        MediaCodecUtil.getHevcProfileAndLevel(codec);
    if (codecProfileAndLevel == null) {
      // If we don't know any better, we assume that the profile and level are supported.
      return true;
    }
    for (MediaCodecInfo.CodecProfileLevel capabilities : getProfileLevels()) {
      if (capabilities.profile == codecProfileAndLevel.first
          && capabilities.level >= codecProfileAndLevel.second) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the decoder supports video with a specified width and height.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @return Whether the decoder supports video with the given width and height.
   */
  @TargetApi(21)
  public boolean isVideoSizeSupportedV21(int width, int height) {
    if (capabilities == null) {
      return false;
    }
    MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
    return videoCapabilities != null && videoCapabilities.isSizeSupported(width, height);
  }

  /**
   * Whether the decoder supports video with a given width, height and frame rate.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @param frameRate Frame rate in frames per second.
   * @return Whether the decoder supports video with the given width, height and frame rate.
   */
  @TargetApi(21)
  public boolean isVideoSizeAndRateSupportedV21(int width, int height, double frameRate) {
    if (capabilities == null) {
      return false;
    }
    MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
    return videoCapabilities != null && videoCapabilities.areSizeAndRateSupported(width, height,
        frameRate);
  }

  /**
   * Whether the decoder supports audio with a given sample rate.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param sampleRate The sample rate in Hz.
   * @return Whether the decoder supports audio with the given sample rate.
   */
  @TargetApi(21)
  public boolean isAudioSampleRateSupportedV21(int sampleRate) {
    if (capabilities == null) {
      return false;
    }
    MediaCodecInfo.AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities();
    return audioCapabilities != null && audioCapabilities.isSampleRateSupported(sampleRate);
  }

  /**
   * Whether the decoder supports audio with a given channel count.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param channelCount The channel count.
   * @return Whether the decoder supports audio with the given channel count.
   */
  @TargetApi(21)
  public boolean isAudioChannelCountSupportedV21(int channelCount) {
    if (capabilities == null) {
      return false;
    }
    MediaCodecInfo.AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities();
    return audioCapabilities != null && audioCapabilities.getMaxInputChannelCount() >= channelCount;
  }

  private static boolean isAdaptive(CodecCapabilities capabilities) {
    return Util.SDK_INT >= 19 && isAdaptiveV19(capabilities);
  }

  @TargetApi(19)
  private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
  }

}

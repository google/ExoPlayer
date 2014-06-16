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

import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.util.Pair;

import java.util.HashMap;

/**
 * A utility class for querying the available codecs.
 */
@TargetApi(16)
public class MediaCodecUtil {

  private static final HashMap<String, Pair<MediaCodecInfo, CodecCapabilities>> codecs =
      new HashMap<String, Pair<MediaCodecInfo, CodecCapabilities>>();

  /**
   * Get information about the decoder that will be used for a given mime type. If no decoder
   * exists for the mime type then null is returned.
   *
   * @param mimeType The mime type.
   * @return Information about the decoder that will be used, or null if no decoder exists.
   */
  public static DecoderInfo getDecoderInfo(String mimeType) {
    Pair<MediaCodecInfo, CodecCapabilities> info = getMediaCodecInfo(mimeType);
    if (info == null) {
      return null;
    }
    return new DecoderInfo(info.first.getName(), isAdaptive(info.second));
  }

  /**
   * Optional call to warm the codec cache.  Call from any appropriate
   * place to hide latency.
   */
  public static synchronized void warmCodecs(String[] mimeTypes) {
    for (int i = 0; i < mimeTypes.length; i++) {
      getMediaCodecInfo(mimeTypes[i]);
    }
  }

  /**
   * Returns the best decoder and its capabilities for the given mimeType. If there's no decoder
   * returns null.
   */
  private static synchronized Pair<MediaCodecInfo, CodecCapabilities> getMediaCodecInfo(
      String mimeType) {
    Pair<MediaCodecInfo, CodecCapabilities> result = codecs.get(mimeType);
    if (result != null) {
      return result;
    }
    int numberOfCodecs = MediaCodecList.getCodecCount();
    // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
    for (int i = 0; i < numberOfCodecs; i++) {
      MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
      String codecName = info.getName();
      if (!info.isEncoder() && isOmxCodec(codecName)) {
        String[] supportedTypes = info.getSupportedTypes();
        for (int j = 0; j < supportedTypes.length; j++) {
          String supportedType = supportedTypes[j];
          if (supportedType.equalsIgnoreCase(mimeType)) {
            result = Pair.create(info, info.getCapabilitiesForType(supportedType));
            codecs.put(mimeType, result);
            return result;
          }
        }
      }
    }
    return null;
  }

  private static boolean isOmxCodec(String name) {
    return name.startsWith("OMX.");
  }

  private static boolean isAdaptive(CodecCapabilities capabilities) {
    if (Util.SDK_INT >= 19) {
      return isAdaptiveV19(capabilities);
    } else {
      return false;
    }
  }

  @TargetApi(19)
  private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
  }

  /**
   * @param profile An AVC profile constant from {@link CodecProfileLevel}.
   * @param level An AVC profile level from {@link CodecProfileLevel}.
   * @return Whether the specified profile is supported at the specified level.
   */
  public static boolean isH264ProfileSupported(int profile, int level) {
    Pair<MediaCodecInfo, CodecCapabilities> info = getMediaCodecInfo(MimeTypes.VIDEO_H264);
    if (info == null) {
      return false;
    }

    CodecCapabilities capabilities = info.second;
    for (int i = 0; i < capabilities.profileLevels.length; i++) {
      CodecProfileLevel profileLevel = capabilities.profileLevels[i];
      if (profileLevel.profile == profile && profileLevel.level >= level) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return the maximum frame size for an H264 stream that can be decoded on the device.
   */
  public static int maxH264DecodableFrameSize() {
    Pair<MediaCodecInfo, CodecCapabilities> info = getMediaCodecInfo(MimeTypes.VIDEO_H264);
    if (info == null) {
      return 0;
    }

    int maxH264DecodableFrameSize = 0;
    CodecCapabilities capabilities = info.second;
    for (int i = 0; i < capabilities.profileLevels.length; i++) {
      CodecProfileLevel profileLevel = capabilities.profileLevels[i];
      maxH264DecodableFrameSize = Math.max(
          avcLevelToMaxFrameSize(profileLevel.level), maxH264DecodableFrameSize);
    }

    return maxH264DecodableFrameSize;
  }

  /**
   * Conversion values taken from: https://en.wikipedia.org/wiki/H.264/MPEG-4_AVC.
   *
   * @param avcLevel one of CodecProfileLevel.AVCLevel* constants.
   * @return maximum frame size that can be decoded by a decoder with the specified avc level
   *      (or {@code -1} if the level is not recognized)
   */
  private static int avcLevelToMaxFrameSize(int avcLevel) {
    switch (avcLevel) {
      case CodecProfileLevel.AVCLevel1: return 25344;
      case CodecProfileLevel.AVCLevel1b: return 25344;
      case CodecProfileLevel.AVCLevel12: return 101376;
      case CodecProfileLevel.AVCLevel13: return 101376;
      case CodecProfileLevel.AVCLevel2: return 101376;
      case CodecProfileLevel.AVCLevel21: return 202752;
      case CodecProfileLevel.AVCLevel22: return 414720;
      case CodecProfileLevel.AVCLevel3: return 414720;
      case CodecProfileLevel.AVCLevel31: return 921600;
      case CodecProfileLevel.AVCLevel32: return 1310720;
      case CodecProfileLevel.AVCLevel4: return 2097152;
      case CodecProfileLevel.AVCLevel41: return 2097152;
      case CodecProfileLevel.AVCLevel42: return 2228224;
      case CodecProfileLevel.AVCLevel5: return 5652480;
      case CodecProfileLevel.AVCLevel51: return 9437184;
      default: return -1;
    }
  }

}

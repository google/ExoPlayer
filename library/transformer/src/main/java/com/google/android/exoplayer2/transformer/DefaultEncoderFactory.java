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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.android.exoplayer2.util.Util.SDK_INT;
import static java.lang.Math.abs;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Pair;
import android.util.Size;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default implementation of {@link Codec.EncoderFactory}. */
// TODO(b/224949986) Split audio and video encoder factory.
public final class DefaultEncoderFactory implements Codec.EncoderFactory {
  private static final int DEFAULT_FRAME_RATE = 30;

  private final EncoderSelector videoEncoderSelector;
  private final VideoEncoderSettings requestedVideoEncoderSettings;
  private final boolean enableFallback;

  /**
   * Creates a new instance using the {@link EncoderSelector#DEFAULT default encoder selector}, a
   * default {@link VideoEncoderSettings}, and with format fallback enabled.
   */
  public DefaultEncoderFactory() {
    this(EncoderSelector.DEFAULT, /* enableFallback= */ true);
  }

  /** Creates a new instance using a default {@link VideoEncoderSettings}. */
  public DefaultEncoderFactory(EncoderSelector videoEncoderSelector, boolean enableFallback) {
    this(videoEncoderSelector, new VideoEncoderSettings.Builder().build(), enableFallback);
  }

  /**
   * Creates a new instance.
   *
   * <p>Values in {@code requestedVideoEncoderSettings} could be adjusted to improve encoding
   * quality and/or reduce failures. Specifically, {@link VideoEncoderSettings#profile} and {@link
   * VideoEncoderSettings#level} are ignored for {@link MimeTypes#VIDEO_H264}. Consider implementing
   * {@link Codec.EncoderFactory} if such adjustments are unwanted.
   *
   * <p>With format fallback enabled, and when the requested {@link Format} is not supported, {@code
   * DefaultEncoderFactory} finds a format that is supported by the device and configures the {@link
   * Codec} with it. The fallback process may change the requested {@link Format#sampleMimeType MIME
   * type}, resolution, {@link Format#bitrate bitrate}, {@link Format#codecs profile/level}, etc.
   *
   * @param videoEncoderSelector The {@link EncoderSelector}.
   * @param requestedVideoEncoderSettings The {@link VideoEncoderSettings}.
   * @param enableFallback Whether to enable fallback.
   */
  public DefaultEncoderFactory(
      EncoderSelector videoEncoderSelector,
      VideoEncoderSettings requestedVideoEncoderSettings,
      boolean enableFallback) {
    this.videoEncoderSelector = videoEncoderSelector;
    this.requestedVideoEncoderSettings = requestedVideoEncoderSettings;
    this.enableFallback = enableFallback;
  }

  @Override
  public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    // TODO(b/210591626) Add encoder selection for audio.
    checkArgument(!allowedMimeTypes.isEmpty());
    checkNotNull(format.sampleMimeType);
    if (!allowedMimeTypes.contains(format.sampleMimeType)) {
      if (enableFallback) {
        // TODO(b/210591626): Pick fallback MIME type using same strategy as for encoder
        // capabilities limitations.
        format = format.buildUpon().setSampleMimeType(allowedMimeTypes.get(0)).build();
      } else {
        throw createTransformationException(format);
      }
    }
    MediaFormat mediaFormat =
        MediaFormat.createAudioFormat(
            checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);

    @Nullable
    String mediaCodecName = EncoderUtil.findCodecForFormat(mediaFormat, /* isDecoder= */ false);
    if (mediaCodecName == null) {
      throw createTransformationException(format);
    }
    return new DefaultCodec(
        format, mediaFormat, mediaCodecName, /* isDecoder= */ false, /* outputSurface= */ null);
  }

  @Override
  public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
      throws TransformationException {
    if (format.frameRate == Format.NO_VALUE) {
      format = format.buildUpon().setFrameRate(DEFAULT_FRAME_RATE).build();
    }
    checkArgument(format.width != Format.NO_VALUE);
    checkArgument(format.height != Format.NO_VALUE);
    // According to interface Javadoc, format.rotationDegrees should be 0. The video should always
    // be encoded in landscape orientation.
    checkArgument(format.height <= format.width);
    checkArgument(format.rotationDegrees == 0);
    checkNotNull(format.sampleMimeType);
    checkArgument(!allowedMimeTypes.isEmpty());
    checkStateNotNull(videoEncoderSelector);

    @Nullable
    VideoEncoderQueryResult encoderAndClosestFormatSupport =
        findEncoderWithClosestFormatSupport(
            format,
            requestedVideoEncoderSettings,
            videoEncoderSelector,
            allowedMimeTypes,
            enableFallback);

    if (encoderAndClosestFormatSupport == null) {
      throw createTransformationException(format);
    }

    MediaCodecInfo encoderInfo = encoderAndClosestFormatSupport.encoder;
    format = encoderAndClosestFormatSupport.supportedFormat;
    VideoEncoderSettings supportedVideoEncoderSettings =
        encoderAndClosestFormatSupport.supportedEncoderSettings;

    String mimeType = checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat = MediaFormat.createVideoFormat(mimeType, format.width, format.height);
    mediaFormat.setFloat(MediaFormat.KEY_FRAME_RATE, format.frameRate);
    mediaFormat.setInteger(
        MediaFormat.KEY_BIT_RATE,
        supportedVideoEncoderSettings.bitrate != VideoEncoderSettings.NO_VALUE
            ? supportedVideoEncoderSettings.bitrate
            : getSuggestedBitrate(format.width, format.height, format.frameRate));

    mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, supportedVideoEncoderSettings.bitrateMode);

    if (supportedVideoEncoderSettings.profile != VideoEncoderSettings.NO_VALUE
        && supportedVideoEncoderSettings.level != VideoEncoderSettings.NO_VALUE
        && SDK_INT >= 23) {
      // Set profile and level at the same time to maximize compatibility, or the encoder will pick
      // the values.
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, supportedVideoEncoderSettings.profile);
      mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedVideoEncoderSettings.level);
    }

    if (mimeType.equals(MimeTypes.VIDEO_H264)) {
      adjustMediaFormatForH264EncoderSettings(mediaFormat, encoderInfo);
    }

    mediaFormat.setInteger(
        MediaFormat.KEY_COLOR_FORMAT, supportedVideoEncoderSettings.colorProfile);
    mediaFormat.setFloat(
        MediaFormat.KEY_I_FRAME_INTERVAL, supportedVideoEncoderSettings.iFrameIntervalSeconds);

    return new DefaultCodec(
        format,
        mediaFormat,
        encoderInfo.getName(),
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  /**
   * Finds a {@link MediaCodecInfo encoder} that supports the requested format most closely.
   *
   * <p>Returns the {@link MediaCodecInfo encoder} and the supported {@link Format} in a {@link
   * Pair}, or {@code null} if none is found.
   */
  @RequiresNonNull("#1.sampleMimeType")
  @Nullable
  private static VideoEncoderQueryResult findEncoderWithClosestFormatSupport(
      Format requestedFormat,
      VideoEncoderSettings videoEncoderSettings,
      EncoderSelector encoderSelector,
      List<String> allowedMimeTypes,
      boolean enableFallback) {
    String requestedMimeType = requestedFormat.sampleMimeType;
    @Nullable
    String mimeType = findFallbackMimeType(encoderSelector, requestedMimeType, allowedMimeTypes);
    if (mimeType == null || (!enableFallback && !requestedMimeType.equals(mimeType))) {
      return null;
    }

    List<MediaCodecInfo> encodersForMimeType = encoderSelector.selectEncoderInfos(mimeType);
    if (encodersForMimeType.isEmpty()) {
      return null;
    }
    if (!enableFallback) {
      return new VideoEncoderQueryResult(
          encodersForMimeType.get(0), requestedFormat, videoEncoderSettings);
    }
    ImmutableList<MediaCodecInfo> filteredEncoders =
        filterEncoders(
            encodersForMimeType,
            /* cost= */ (encoderInfo) -> {
              @Nullable
              Size closestSupportedResolution =
                  EncoderUtil.getSupportedResolution(
                      encoderInfo, mimeType, requestedFormat.width, requestedFormat.height);
              if (closestSupportedResolution == null) {
                // Drops encoder.
                return Integer.MAX_VALUE;
              }
              return abs(
                  requestedFormat.width * requestedFormat.height
                      - closestSupportedResolution.getWidth()
                          * closestSupportedResolution.getHeight());
            });
    if (filteredEncoders.isEmpty()) {
      return null;
    }

    // The supported resolution is the same for all remaining encoders.
    Size finalResolution =
        checkNotNull(
            EncoderUtil.getSupportedResolution(
                filteredEncoders.get(0), mimeType, requestedFormat.width, requestedFormat.height));

    int requestedBitrate =
        videoEncoderSettings.bitrate != VideoEncoderSettings.NO_VALUE
            ? videoEncoderSettings.bitrate
            : getSuggestedBitrate(
                finalResolution.getWidth(), finalResolution.getHeight(), requestedFormat.frameRate);
    filteredEncoders =
        filterEncoders(
            filteredEncoders,
            /* cost= */ (encoderInfo) -> {
              int achievableBitrate =
                  EncoderUtil.getClosestSupportedBitrate(encoderInfo, mimeType, requestedBitrate);
              return abs(achievableBitrate - requestedBitrate);
            });
    if (filteredEncoders.isEmpty()) {
      return null;
    }

    MediaCodecInfo pickedEncoder = filteredEncoders.get(0);
    int closestSupportedBitrate =
        EncoderUtil.getClosestSupportedBitrate(pickedEncoder, mimeType, requestedBitrate);
    VideoEncoderSettings.Builder supportedEncodingSettingBuilder =
        videoEncoderSettings.buildUpon().setBitrate(closestSupportedBitrate);
    if (videoEncoderSettings.profile == VideoEncoderSettings.NO_VALUE
        || videoEncoderSettings.level == VideoEncoderSettings.NO_VALUE
        || videoEncoderSettings.level
            > EncoderUtil.findHighestSupportedEncodingLevel(
                pickedEncoder, mimeType, videoEncoderSettings.profile)) {
      supportedEncodingSettingBuilder.setEncodingProfileLevel(
          VideoEncoderSettings.NO_VALUE, VideoEncoderSettings.NO_VALUE);
    }

    Format supportedEncoderFormat =
        requestedFormat
            .buildUpon()
            .setSampleMimeType(mimeType)
            .setWidth(finalResolution.getWidth())
            .setHeight(finalResolution.getHeight())
            .setAverageBitrate(closestSupportedBitrate)
            .build();
    return new VideoEncoderQueryResult(
        pickedEncoder, supportedEncoderFormat, supportedEncodingSettingBuilder.build());
  }

  private static final class VideoEncoderQueryResult {
    public final MediaCodecInfo encoder;
    public final Format supportedFormat;
    public final VideoEncoderSettings supportedEncoderSettings;

    public VideoEncoderQueryResult(
        MediaCodecInfo encoder,
        Format supportedFormat,
        VideoEncoderSettings supportedEncoderSettings) {
      this.encoder = encoder;
      this.supportedFormat = supportedFormat;
      this.supportedEncoderSettings = supportedEncoderSettings;
    }
  }

  /**
   * Applying suggested profile/level settings from
   * https://developer.android.com/guide/topics/media/sharing-video#b-frames_and_encoding_profiles
   *
   * <p>The adjustment is applied in-place to {@code mediaFormat}.
   */
  private static void adjustMediaFormatForH264EncoderSettings(
      MediaFormat mediaFormat, MediaCodecInfo encoderInfo) {
    // TODO(b/210593256): Remove overriding profile/level (before API 29) after switching to in-app
    // muxing.
    String mimeType = MimeTypes.VIDEO_H264;
    if (Util.SDK_INT >= 29) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      int supportedEncodingLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      if (supportedEncodingLevel != EncoderUtil.LEVEL_UNSET) {
        // Use the highest supported profile and use B-frames.
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedEncodingLevel);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 1);
      }
    } else if (Util.SDK_INT >= 26) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      int supportedEncodingLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      if (supportedEncodingLevel != EncoderUtil.LEVEL_UNSET) {
        // Use the highest-supported profile, but disable the generation of B-frames using
        // MediaFormat.KEY_LATENCY. This accommodates some limitations in the MediaMuxer in these
        // system versions.
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedEncodingLevel);
        // TODO(b/210593256): Set KEY_LATENCY to 2 to enable B-frame production after switching to
        // in-app muxing.
        mediaFormat.setInteger(MediaFormat.KEY_LATENCY, 1);
      }
    } else if (Util.SDK_INT >= 24) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
      int supportedLevel =
          EncoderUtil.findHighestSupportedEncodingLevel(
              encoderInfo, mimeType, expectedEncodingProfile);
      checkState(supportedLevel != EncoderUtil.LEVEL_UNSET);
      // Use the baseline profile for safest results, as encoding in baseline is required per
      // https://source.android.com/compatibility/5.0/android-5.0-cdd#5_2_video_encoding
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
      mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedLevel);
    } else {
      // For API levels below 24, setting profile and level can lead to failures in MediaCodec
      // configuration. The encoder selects the profile/level when we don't set them.
      mediaFormat.setString(MediaFormat.KEY_PROFILE, null);
      mediaFormat.setString(MediaFormat.KEY_LEVEL, null);
    }
  }

  private interface EncoderFallbackCost {
    /**
     * Returns a cost that represents the gap between the requested encoding parameter(s) and the
     * {@link MediaCodecInfo encoder}'s support for them.
     *
     * <p>The method must return {@link Integer#MAX_VALUE} when the {@link MediaCodecInfo encoder}
     * does not support the encoding parameters.
     */
    int getParameterSupportGap(MediaCodecInfo encoderInfo);
  }

  /**
   * Filters a list of {@link MediaCodecInfo encoders} by a {@link EncoderFallbackCost cost
   * function}.
   *
   * @param encoders A list of {@link MediaCodecInfo encoders}.
   * @param cost A {@link EncoderFallbackCost cost function}.
   * @return A list of {@link MediaCodecInfo encoders} with the lowest costs, empty if the costs of
   *     all encoders are {@link Integer#MAX_VALUE}.
   */
  private static ImmutableList<MediaCodecInfo> filterEncoders(
      List<MediaCodecInfo> encoders, EncoderFallbackCost cost) {
    List<MediaCodecInfo> filteredEncoders = new ArrayList<>(encoders.size());

    int minGap = Integer.MAX_VALUE;
    for (int i = 0; i < encoders.size(); i++) {
      MediaCodecInfo encoderInfo = encoders.get(i);
      int gap = cost.getParameterSupportGap(encoderInfo);
      if (gap == Integer.MAX_VALUE) {
        continue;
      }

      if (gap < minGap) {
        minGap = gap;
        filteredEncoders.clear();
        filteredEncoders.add(encoderInfo);
      } else if (gap == minGap) {
        filteredEncoders.add(encoderInfo);
      }
    }
    return ImmutableList.copyOf(filteredEncoders);
  }

  @Nullable
  private static String findFallbackMimeType(
      EncoderSelector encoderSelector, String requestedMimeType, List<String> allowedMimeTypes) {
    if (mimeTypeIsSupported(encoderSelector, requestedMimeType, allowedMimeTypes)) {
      return requestedMimeType;
    } else if (mimeTypeIsSupported(encoderSelector, MimeTypes.VIDEO_H265, allowedMimeTypes)) {
      return MimeTypes.VIDEO_H265;
    } else if (mimeTypeIsSupported(encoderSelector, MimeTypes.VIDEO_H264, allowedMimeTypes)) {
      return MimeTypes.VIDEO_H264;
    } else {
      for (int i = 0; i < allowedMimeTypes.size(); i++) {
        String allowedMimeType = allowedMimeTypes.get(i);
        if (mimeTypeIsSupported(encoderSelector, allowedMimeType, allowedMimeTypes)) {
          return allowedMimeType;
        }
      }
    }
    return null;
  }

  private static boolean mimeTypeIsSupported(
      EncoderSelector encoderSelector, String mimeType, List<String> allowedMimeTypes) {
    return !encoderSelector.selectEncoderInfos(mimeType).isEmpty()
        && allowedMimeTypes.contains(mimeType);
  }

  /** Computes the video bit rate using the Kush Gauge. */
  private static int getSuggestedBitrate(int width, int height, float frameRate) {
    // TODO(b/210591626) Implement bitrate estimation.
    // 1080p30 -> 6.2Mbps, 720p30 -> 2.7Mbps.
    return (int) (width * height * frameRate * 0.1);
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static TransformationException createTransformationException(Format format) {
    return TransformationException.createForCodec(
        new IllegalArgumentException("The requested encoding format is not supported."),
        MimeTypes.isVideo(format.sampleMimeType),
        /* isDecoder= */ false,
        format,
        /* mediaCodecName= */ null,
        TransformationException.ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED);
  }
}

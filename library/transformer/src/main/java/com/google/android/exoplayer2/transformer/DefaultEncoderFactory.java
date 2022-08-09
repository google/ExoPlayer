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
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.round;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Pair;
import android.util.Size;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default implementation of {@link Codec.EncoderFactory}. */
// TODO(b/224949986) Split audio and video encoder factory.
public final class DefaultEncoderFactory implements Codec.EncoderFactory {
  private static final int DEFAULT_FRAME_RATE = 30;
  /** Best effort, or as-fast-as-possible priority setting for {@link MediaFormat#KEY_PRIORITY}. */
  private static final int PRIORITY_BEST_EFFORT = 1;

  private static final String TAG = "DefaultEncoderFactory";

  /** A builder for {@link DefaultEncoderFactory} instances. */
  public static final class Builder {
    private final Context context;

    @Nullable private EncoderSelector encoderSelector;
    @Nullable private VideoEncoderSettings requestedVideoEncoderSettings;
    private boolean enableFallback;

    /** Creates a new {@link Builder}. */
    public Builder(Context context) {
      this.context = context;
      this.enableFallback = true;
    }

    /**
     * Sets the video {@link EncoderSelector}.
     *
     * <p>The default value is {@link EncoderSelector#DEFAULT}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoEncoderSelector(EncoderSelector encoderSelector) {
      this.encoderSelector = encoderSelector;
      return this;
    }

    /**
     * Sets the requested {@link VideoEncoderSettings}.
     *
     * <p>Values in {@code requestedVideoEncoderSettings} may be ignored to improve encoding quality
     * and/or reduce failures.
     *
     * <p>{@link VideoEncoderSettings#profile} and {@link VideoEncoderSettings#level} are ignored
     * for {@link MimeTypes#VIDEO_H264}. Consider implementing {@link Codec.EncoderFactory} if such
     * adjustments are unwanted.
     *
     * <p>{@code requestedVideoEncoderSettings} should be handled with care because there is no
     * fallback support for it. For example, using incompatible {@link VideoEncoderSettings#profile}
     * and {@link VideoEncoderSettings#level} can cause codec configuration failure. Setting an
     * unsupported {@link VideoEncoderSettings#bitrateMode} may cause encoder instantiation failure.
     *
     * <p>The default value is {@link VideoEncoderSettings#DEFAULT}.
     */
    @CanIgnoreReturnValue
    public Builder setRequestedVideoEncoderSettings(
        VideoEncoderSettings requestedVideoEncoderSettings) {
      this.requestedVideoEncoderSettings = requestedVideoEncoderSettings;
      return this;
    }

    /**
     * Sets whether the encoder can fallback.
     *
     * <p>With format fallback enabled, when the requested {@link Format} is not supported, {@code
     * DefaultEncoderFactory} finds a format that is supported by the device and configures the
     * {@link Codec} with it. The fallback process may change the requested {@link
     * Format#sampleMimeType MIME type}, resolution, {@link Format#bitrate bitrate}, {@link
     * Format#codecs profile/level} etc.
     *
     * <p>The default value is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder setEnableFallback(boolean enableFallback) {
      this.enableFallback = enableFallback;
      return this;
    }

    /** Creates an instance of {@link DefaultEncoderFactory}, using defaults if values are unset. */
    @SuppressWarnings("deprecation")
    public DefaultEncoderFactory build() {
      if (encoderSelector == null) {
        encoderSelector = EncoderSelector.DEFAULT;
      }
      if (requestedVideoEncoderSettings == null) {
        requestedVideoEncoderSettings = VideoEncoderSettings.DEFAULT;
      }
      return new DefaultEncoderFactory(
          context, encoderSelector, requestedVideoEncoderSettings, enableFallback);
    }
  }

  private final Context context;
  private final EncoderSelector videoEncoderSelector;
  private final VideoEncoderSettings requestedVideoEncoderSettings;
  private final boolean enableFallback;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultEncoderFactory(Context context) {
    this(context, EncoderSelector.DEFAULT, /* enableFallback= */ true);
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultEncoderFactory(
      Context context, EncoderSelector videoEncoderSelector, boolean enableFallback) {
    this(context, videoEncoderSelector, VideoEncoderSettings.DEFAULT, enableFallback);
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public DefaultEncoderFactory(
      Context context,
      EncoderSelector videoEncoderSelector,
      VideoEncoderSettings requestedVideoEncoderSettings,
      boolean enableFallback) {
    this.context = context;
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
        context,
        format,
        mediaFormat,
        mediaCodecName,
        /* isDecoder= */ false,
        /* outputSurface= */ null);
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
        findEncoderWithClosestSupportedFormat(
            format,
            requestedVideoEncoderSettings,
            videoEncoderSelector,
            allowedMimeTypes,
            enableFallback);

    if (encoderAndClosestFormatSupport == null) {
      throw createTransformationException(format);
    }

    MediaCodecInfo encoderInfo = encoderAndClosestFormatSupport.encoder;
    Format encoderSupportedFormat = encoderAndClosestFormatSupport.supportedFormat;
    VideoEncoderSettings supportedVideoEncoderSettings =
        encoderAndClosestFormatSupport.supportedEncoderSettings;

    String mimeType = checkNotNull(encoderSupportedFormat.sampleMimeType);
    MediaFormat mediaFormat =
        MediaFormat.createVideoFormat(
            mimeType, encoderSupportedFormat.width, encoderSupportedFormat.height);
    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, round(encoderSupportedFormat.frameRate));

    if (supportedVideoEncoderSettings.enableHighQualityTargeting) {
      int bitrate =
          new DeviceMappedEncoderBitrateProvider()
              .getBitrate(
                  encoderInfo.getName(),
                  encoderSupportedFormat.width,
                  encoderSupportedFormat.height,
                  encoderSupportedFormat.frameRate);
      encoderSupportedFormat =
          encoderSupportedFormat.buildUpon().setAverageBitrate(bitrate).build();
    } else if (encoderSupportedFormat.bitrate == Format.NO_VALUE) {
      int bitrate =
          getSuggestedBitrate(
              encoderSupportedFormat.width,
              encoderSupportedFormat.height,
              encoderSupportedFormat.frameRate);
      encoderSupportedFormat =
          encoderSupportedFormat.buildUpon().setAverageBitrate(bitrate).build();
    }

    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, encoderSupportedFormat.averageBitrate);
    mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, supportedVideoEncoderSettings.bitrateMode);

    if (supportedVideoEncoderSettings.profile != VideoEncoderSettings.NO_VALUE
        && supportedVideoEncoderSettings.level != VideoEncoderSettings.NO_VALUE
        && Util.SDK_INT >= 23) {
      // Set profile and level at the same time to maximize compatibility, or the encoder will pick
      // the values.
      mediaFormat.setInteger(MediaFormat.KEY_PROFILE, supportedVideoEncoderSettings.profile);
      mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedVideoEncoderSettings.level);
    }

    if (mimeType.equals(MimeTypes.VIDEO_H264)) {
      adjustMediaFormatForH264EncoderSettings(format.colorInfo, encoderInfo, mediaFormat);
    }

    MediaFormatUtil.maybeSetColorInfo(mediaFormat, encoderSupportedFormat.colorInfo);
    if (Util.SDK_INT >= 31 && ColorInfo.isTransferHdr(format.colorInfo)) {
      if (EncoderUtil.getSupportedColorFormats(encoderInfo, mimeType)
          .contains(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010)) {
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010);
      } else {
        throw createTransformationException(format);
      }
    } else {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    if (Util.SDK_INT >= 25) {
      mediaFormat.setFloat(
          MediaFormat.KEY_I_FRAME_INTERVAL, supportedVideoEncoderSettings.iFrameIntervalSeconds);
    } else {
      float iFrameIntervalSeconds = supportedVideoEncoderSettings.iFrameIntervalSeconds;
      // Only integer I-frame intervals are supported before API 25.
      // Round up values in (0, 1] to avoid the special 'all keyframes' behavior when passing 0.
      mediaFormat.setInteger(
          MediaFormat.KEY_I_FRAME_INTERVAL,
          (iFrameIntervalSeconds > 0f && iFrameIntervalSeconds <= 1f)
              ? 1
              : (int) floor(iFrameIntervalSeconds));
    }

    if (Util.SDK_INT >= 23) {
      // Setting operating rate and priority is supported from API 23.
      if (supportedVideoEncoderSettings.operatingRate == VideoEncoderSettings.NO_VALUE
          && supportedVideoEncoderSettings.priority == VideoEncoderSettings.NO_VALUE) {
        adjustMediaFormatForEncoderPerformanceSettings(mediaFormat);
      } else {
        if (supportedVideoEncoderSettings.operatingRate != VideoEncoderSettings.NO_VALUE) {
          mediaFormat.setInteger(
              MediaFormat.KEY_OPERATING_RATE, supportedVideoEncoderSettings.operatingRate);
        }
        if (supportedVideoEncoderSettings.priority != VideoEncoderSettings.NO_VALUE) {
          mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, supportedVideoEncoderSettings.priority);
        }
      }
    }

    return new DefaultCodec(
        context,
        encoderSupportedFormat,
        mediaFormat,
        encoderInfo.getName(),
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  @Override
  public boolean videoNeedsEncoding() {
    return !requestedVideoEncoderSettings.equals(VideoEncoderSettings.DEFAULT);
  }

  /**
   * Finds an {@linkplain MediaCodecInfo encoder} that supports a format closest to the requested
   * format.
   *
   * <p>Returns the {@linkplain MediaCodecInfo encoder} and the supported {@link Format} in a {@link
   * Pair}, or {@code null} if none is found.
   */
  @RequiresNonNull("#1.sampleMimeType")
  @Nullable
  private static VideoEncoderQueryResult findEncoderWithClosestSupportedFormat(
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

    ImmutableList<MediaCodecInfo> filteredEncoderInfos =
        encoderSelector.selectEncoderInfos(mimeType);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    if (!enableFallback) {
      return new VideoEncoderQueryResult(
          filteredEncoderInfos.get(0), requestedFormat, videoEncoderSettings);
    }

    filteredEncoderInfos =
        filterEncodersByResolution(
            filteredEncoderInfos, mimeType, requestedFormat.width, requestedFormat.height);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }
    // The supported resolution is the same for all remaining encoders.
    Size finalResolution =
        checkNotNull(
            EncoderUtil.getSupportedResolution(
                filteredEncoderInfos.get(0),
                mimeType,
                requestedFormat.width,
                requestedFormat.height));

    int requestedBitrate =
        videoEncoderSettings.bitrate != VideoEncoderSettings.NO_VALUE
            ? videoEncoderSettings.bitrate
            : getSuggestedBitrate(
                finalResolution.getWidth(), finalResolution.getHeight(), requestedFormat.frameRate);

    filteredEncoderInfos =
        filterEncodersByBitrate(filteredEncoderInfos, mimeType, requestedBitrate);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    filteredEncoderInfos =
        filterEncodersByBitrateMode(
            filteredEncoderInfos, mimeType, videoEncoderSettings.bitrateMode);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    // TODO(b/238094555): Check encoder supports bitrate targeted by high quality.
    MediaCodecInfo pickedEncoderInfo = filteredEncoderInfos.get(0);
    int closestSupportedBitrate =
        EncoderUtil.getSupportedBitrateRange(pickedEncoderInfo, mimeType).clamp(requestedBitrate);

    VideoEncoderSettings.Builder supportedEncodingSettingBuilder = videoEncoderSettings.buildUpon();
    Format.Builder encoderSupportedFormatBuilder =
        requestedFormat
            .buildUpon()
            .setSampleMimeType(mimeType)
            .setWidth(finalResolution.getWidth())
            .setHeight(finalResolution.getHeight());

    if (!videoEncoderSettings.enableHighQualityTargeting) {
      supportedEncodingSettingBuilder.setBitrate(closestSupportedBitrate);
      encoderSupportedFormatBuilder.setAverageBitrate(closestSupportedBitrate);
    }

    if (videoEncoderSettings.profile == VideoEncoderSettings.NO_VALUE
        || videoEncoderSettings.level == VideoEncoderSettings.NO_VALUE
        || videoEncoderSettings.level
            > EncoderUtil.findHighestSupportedEncodingLevel(
                pickedEncoderInfo, mimeType, videoEncoderSettings.profile)) {
      supportedEncodingSettingBuilder.setEncodingProfileLevel(
          VideoEncoderSettings.NO_VALUE, VideoEncoderSettings.NO_VALUE);
    }

    return new VideoEncoderQueryResult(
        pickedEncoderInfo,
        encoderSupportedFormatBuilder.build(),
        supportedEncodingSettingBuilder.build());
  }

  /** Returns a list of encoders that support the requested resolution most closely. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByResolution(
      List<MediaCodecInfo> encoders, String mimeType, int requestedWidth, int requestedHeight) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) -> {
          @Nullable
          Size closestSupportedResolution =
              EncoderUtil.getSupportedResolution(
                  encoderInfo, mimeType, requestedWidth, requestedHeight);
          if (closestSupportedResolution == null) {
            // Drops encoder.
            return Integer.MAX_VALUE;
          }
          return abs(
              requestedWidth * requestedHeight
                  - closestSupportedResolution.getWidth() * closestSupportedResolution.getHeight());
        },
        /* filterName= */ "resolution");
  }

  /** Returns a list of encoders that support the requested bitrate most closely. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByBitrate(
      List<MediaCodecInfo> encoders, String mimeType, int requestedBitrate) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) -> {
          int achievableBitrate =
              EncoderUtil.getSupportedBitrateRange(encoderInfo, mimeType).clamp(requestedBitrate);
          return abs(achievableBitrate - requestedBitrate);
        },
        /* filterName= */ "bitrate");
  }

  /** Returns a list of encoders that support the requested bitrate mode. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByBitrateMode(
      List<MediaCodecInfo> encoders, String mimeType, int requestedBitrateMode) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) ->
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, requestedBitrateMode)
                ? 0
                : Integer.MAX_VALUE, // Drops encoder.
        /* filterName= */ "bitrate mode");
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
   * Applies empirical {@link MediaFormat#KEY_PRIORITY} and {@link MediaFormat#KEY_OPERATING_RATE}
   * settings for better encoder performance.
   *
   * <p>The adjustment is applied in-place to {@code mediaFormat}.
   */
  private static void adjustMediaFormatForEncoderPerformanceSettings(MediaFormat mediaFormat) {
    if (Util.SDK_INT < 25) {
      // Not setting priority and operating rate achieves better encoding performance.
      return;
    }

    mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, PRIORITY_BEST_EFFORT);

    if (Util.SDK_INT == 26) {
      mediaFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, DEFAULT_FRAME_RATE);
    } else {
      mediaFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, Integer.MAX_VALUE);
    }
  }

  /**
   * Applying suggested profile/level settings from
   * https://developer.android.com/guide/topics/media/sharing-video#b-frames_and_encoding_profiles
   *
   * <p>The adjustment is applied in-place to {@code mediaFormat}.
   */
  private static void adjustMediaFormatForH264EncoderSettings(
      @Nullable ColorInfo colorInfo, MediaCodecInfo encoderInfo, MediaFormat mediaFormat) {
    // TODO(b/210593256): Remove overriding profile/level (before API 29) after switching to in-app
    // muxing.
    String mimeType = MimeTypes.VIDEO_H264;
    if (Util.SDK_INT >= 29) {
      int expectedEncodingProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
      if (colorInfo != null) {
        int colorTransfer = colorInfo.colorTransfer;
        ImmutableList<Integer> codecProfiles =
            EncoderUtil.getCodecProfilesForHdrFormat(mimeType, colorTransfer);
        if (!codecProfiles.isEmpty()) {
          // Default to the most compatible profile, which is first in the list.
          expectedEncodingProfile = codecProfiles.get(0);
        }
      }
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
    }
    // For API levels below 24, setting profile and level can lead to failures in MediaCodec
    // configuration. The encoder selects the profile/level when we don't set them.
  }

  private interface EncoderFallbackCost {
    /**
     * Returns a cost that represents the gap between the requested encoding parameter(s) and the
     * {@linkplain MediaCodecInfo encoder}'s support for them.
     *
     * <p>The method must return {@link Integer#MAX_VALUE} when the {@linkplain MediaCodecInfo
     * encoder} does not support the encoding parameters.
     */
    int getParameterSupportGap(MediaCodecInfo encoderInfo);
  }

  /**
   * Filters a list of {@linkplain MediaCodecInfo encoders} by a {@linkplain EncoderFallbackCost
   * cost function}.
   *
   * @param encoders A list of {@linkplain MediaCodecInfo encoders}.
   * @param cost A {@linkplain EncoderFallbackCost cost function}.
   * @return A list of {@linkplain MediaCodecInfo encoders} with the lowest costs, empty if the
   *     costs of all encoders are {@link Integer#MAX_VALUE}.
   */
  private static ImmutableList<MediaCodecInfo> filterEncoders(
      List<MediaCodecInfo> encoders, EncoderFallbackCost cost, String filterName) {
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

    List<MediaCodecInfo> removedEncoders = new ArrayList<>(encoders);
    removedEncoders.removeAll(filteredEncoders);
    StringBuilder stringBuilder =
        new StringBuilder("Encoders removed for ").append(filterName).append(":\n");
    for (int i = 0; i < removedEncoders.size(); i++) {
      MediaCodecInfo encoderInfo = removedEncoders.get(i);
      stringBuilder.append(Util.formatInvariant("  %s\n", encoderInfo.getName()));
    }
    Log.d(TAG, stringBuilder.toString());

    return ImmutableList.copyOf(filteredEncoders);
  }

  /**
   * Finds a {@linkplain MimeTypes MIME type} that is supported by the encoder and in the {@code
   * allowedMimeTypes}.
   */
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

  /**
   * Computes the video bit rate using the Kush Gauge.
   *
   * <p>{@code kushGaugeBitrate = height * width * frameRate * 0.07 * motionFactor}.
   *
   * <p>Motion factors:
   *
   * <ul>
   *   <li>Low motion video - 1
   *   <li>Medium motion video - 2
   *   <li>High motion video - 4
   * </ul>
   */
  private static int getSuggestedBitrate(int width, int height, float frameRate) {
    // TODO(b/238094555) Refactor into a BitrateProvider.
    // Assume medium motion factor.
    // 1080p60 -> 16.6Mbps, 720p30 -> 3.7Mbps.
    return (int) (width * height * frameRate * 0.07 * 2);
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

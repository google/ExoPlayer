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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.MediaFormatUtil.createMediaFormatFromFormat;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.round;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Pair;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default implementation of {@link Codec.EncoderFactory}. */
// TODO(b/224949986) Split audio and video encoder factory.
@UnstableApi
public final class DefaultEncoderFactory implements Codec.EncoderFactory {
  private static final int DEFAULT_FRAME_RATE = 30;

  /** Best effort, or as-fast-as-possible priority setting for {@link MediaFormat#KEY_PRIORITY}. */
  private static final int PRIORITY_BEST_EFFORT = 1;

  private static final String TAG = "DefaultEncoderFactory";

  /** A builder for {@link DefaultEncoderFactory} instances. */
  public static final class Builder {
    private final Context context;

    @Nullable private EncoderSelector videoEncoderSelector;
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
    public Builder setVideoEncoderSelector(EncoderSelector videoEncoderSelector) {
      this.videoEncoderSelector = videoEncoderSelector;
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
      if (videoEncoderSelector == null) {
        videoEncoderSelector = EncoderSelector.DEFAULT;
      }
      if (requestedVideoEncoderSettings == null) {
        requestedVideoEncoderSettings = VideoEncoderSettings.DEFAULT;
      }
      return new DefaultEncoderFactory(
          context, videoEncoderSelector, requestedVideoEncoderSettings, enableFallback);
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
  public DefaultCodec createForAudioEncoding(Format format) throws ExportException {
    checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat = createMediaFormatFromFormat(format);
    @Nullable
    ImmutableList<MediaCodecInfo> mediaCodecInfos =
        EncoderUtil.getSupportedEncoders(format.sampleMimeType);
    if (mediaCodecInfos.isEmpty()) {
      throw createExportException(format, "No audio media codec found");
    }
    return new DefaultCodec(
        context,
        format,
        mediaFormat,
        mediaCodecInfos.get(0).getName(),
        /* isDecoder= */ false,
        /* outputSurface= */ null);
  }

  /**
   * Returns a {@link DefaultCodec} for video encoding.
   *
   * <p>Use {@link Builder#setRequestedVideoEncoderSettings} with {@link
   * VideoEncoderSettings#bitrate} set to request for a specific encoding bitrate. Bitrate settings
   * in {@link Format} are ignored when {@link VideoEncoderSettings#bitrate} or {@link
   * VideoEncoderSettings#enableHighQualityTargeting} is set.
   */
  @Override
  public DefaultCodec createForVideoEncoding(Format format) throws ExportException {
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
    checkStateNotNull(videoEncoderSelector);

    @Nullable
    VideoEncoderQueryResult encoderAndClosestFormatSupport =
        findEncoderWithClosestSupportedFormat(
            format, requestedVideoEncoderSettings, videoEncoderSelector, enableFallback);

    if (encoderAndClosestFormatSupport == null) {
      throw createExportException(
          format, /* errorString= */ "The requested video encoding format is not supported.");
    }

    MediaCodecInfo encoderInfo = encoderAndClosestFormatSupport.encoder;
    Format encoderSupportedFormat = encoderAndClosestFormatSupport.supportedFormat;
    VideoEncoderSettings supportedVideoEncoderSettings =
        encoderAndClosestFormatSupport.supportedEncoderSettings;

    String mimeType = checkNotNull(encoderSupportedFormat.sampleMimeType);

    int finalBitrate;
    if (enableFallback) {
      finalBitrate = supportedVideoEncoderSettings.bitrate;
    } else {
      // supportedVideoEncoderSettings is identical to requestedVideoEncoderSettings.
      if (supportedVideoEncoderSettings.bitrate != VideoEncoderSettings.NO_VALUE) {
        finalBitrate = supportedVideoEncoderSettings.bitrate;
      } else if (supportedVideoEncoderSettings.enableHighQualityTargeting) {
        finalBitrate =
            new DeviceMappedEncoderBitrateProvider()
                .getBitrate(
                    encoderInfo.getName(),
                    encoderSupportedFormat.width,
                    encoderSupportedFormat.height,
                    encoderSupportedFormat.frameRate);
      } else if (encoderSupportedFormat.averageBitrate != Format.NO_VALUE) {
        finalBitrate = encoderSupportedFormat.averageBitrate;
      } else {
        finalBitrate =
            getSuggestedBitrate(
                encoderSupportedFormat.width,
                encoderSupportedFormat.height,
                encoderSupportedFormat.frameRate);
      }
    }

    encoderSupportedFormat =
        encoderSupportedFormat.buildUpon().setAverageBitrate(finalBitrate).build();

    MediaFormat mediaFormat = createMediaFormatFromFormat(encoderSupportedFormat);
    mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, supportedVideoEncoderSettings.bitrateMode);
    // Some older devices (API 21) fail to initialize the encoder if frame rate is not an integer.
    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, round(encoderSupportedFormat.frameRate));

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

    if (Util.SDK_INT >= 31 && ColorInfo.isTransferHdr(format.colorInfo)) {
      // TODO(b/260389841): Validate the picked encoder supports HDR editing.
      if (EncoderUtil.getSupportedColorFormats(encoderInfo, mimeType)
          .contains(MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010)) {
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010);
      } else {
        throw createExportException(
            format, /* errorString= */ "Encoding HDR is not supported on this device.");
      }
    } else {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    // Float I-frame intervals are only supported from API 25.
    if (Util.SDK_INT >= 25) {
      mediaFormat.setFloat(
          MediaFormat.KEY_I_FRAME_INTERVAL, supportedVideoEncoderSettings.iFrameIntervalSeconds);
    } else {
      float iFrameIntervalSeconds = supportedVideoEncoderSettings.iFrameIntervalSeconds;
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
      boolean enableFallback) {
    String mimeType = checkNotNull(requestedFormat.sampleMimeType);
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

    int requestedBitrate = Format.NO_VALUE;
    // Encoders are not filtered by bitrate if high quality targeting is enabled.
    if (!videoEncoderSettings.enableHighQualityTargeting) {
      requestedBitrate =
          videoEncoderSettings.bitrate != VideoEncoderSettings.NO_VALUE
              ? videoEncoderSettings.bitrate
              : requestedFormat.averageBitrate != Format.NO_VALUE
                  ? requestedFormat.averageBitrate
                  : getSuggestedBitrate(
                      finalResolution.getWidth(),
                      finalResolution.getHeight(),
                      requestedFormat.frameRate);
      filteredEncoderInfos =
          filterEncodersByBitrate(filteredEncoderInfos, mimeType, requestedBitrate);
      if (filteredEncoderInfos.isEmpty()) {
        return null;
      }
    }

    filteredEncoderInfos =
        filterEncodersByBitrateMode(
            filteredEncoderInfos, mimeType, videoEncoderSettings.bitrateMode);
    if (filteredEncoderInfos.isEmpty()) {
      return null;
    }

    VideoEncoderSettings.Builder supportedEncodingSettingBuilder = videoEncoderSettings.buildUpon();
    Format.Builder encoderSupportedFormatBuilder =
        requestedFormat
            .buildUpon()
            .setSampleMimeType(mimeType)
            .setWidth(finalResolution.getWidth())
            .setHeight(finalResolution.getHeight());
    MediaCodecInfo pickedEncoderInfo = filteredEncoderInfos.get(0);
    if (videoEncoderSettings.enableHighQualityTargeting) {
      requestedBitrate =
          new DeviceMappedEncoderBitrateProvider()
              .getBitrate(
                  pickedEncoderInfo.getName(),
                  finalResolution.getWidth(),
                  finalResolution.getHeight(),
                  requestedFormat.frameRate);
      // Resets the flag after getting a targeted bitrate, so that supportedEncodingSetting can have
      // bitrate set.
      supportedEncodingSettingBuilder.experimentalSetEnableHighQualityTargeting(false);
    }
    int closestSupportedBitrate =
        EncoderUtil.getSupportedBitrateRange(pickedEncoderInfo, mimeType).clamp(requestedBitrate);
    supportedEncodingSettingBuilder.setBitrate(closestSupportedBitrate);
    encoderSupportedFormatBuilder.setAverageBitrate(closestSupportedBitrate);

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
    // TODO(b/267740292): Investigate the fallback logic that might prefer software encoders.
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
        });
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
        });
  }

  /** Returns a list of encoders that support the requested bitrate mode. */
  private static ImmutableList<MediaCodecInfo> filterEncodersByBitrateMode(
      List<MediaCodecInfo> encoders, String mimeType, int requestedBitrateMode) {
    return filterEncoders(
        encoders,
        /* cost= */ (encoderInfo) ->
            EncoderUtil.isBitrateModeSupported(encoderInfo, mimeType, requestedBitrateMode)
                ? 0
                : Integer.MAX_VALUE); // Drops encoder.
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
        // Use the highest supported profile. Don't configure B-frames, because it doesn't work on
        // some devices.
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, expectedEncodingProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, supportedEncodingLevel);
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
  private static ExportException createExportException(Format format, String errorString) {
    return ExportException.createForCodec(
        new IllegalArgumentException(errorString),
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        MimeTypes.isVideo(format.sampleMimeType),
        /* isDecoder= */ false,
        format);
  }
}

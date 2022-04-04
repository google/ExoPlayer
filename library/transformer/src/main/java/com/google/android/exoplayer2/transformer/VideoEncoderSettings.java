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
import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Format;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Represents the video encoder settings. */
public final class VideoEncoderSettings {

  /** A value for various fields to indicate that the field's value is unknown or not applicable. */
  public static final int NO_VALUE = Format.NO_VALUE;
  /** The default encoding color profile. */
  public static final int DEFAULT_COLOR_PROFILE =
      MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
  /** The default I-frame interval in seconds. */
  public static final float DEFAULT_I_FRAME_INTERVAL_SECONDS = 1.0f;

  /** A default {@link VideoEncoderSettings}. */
  public static final VideoEncoderSettings DEFAULT = new Builder().build();

  /**
   * The allowed values for {@code bitrateMode}, one of
   *
   * <ul>
   *   <li>Constant quality: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CQ}.
   *   <li>Variable bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_VBR}.
   *   <li>Constant bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CBR}.
   *   <li>Constant bitrate with frame drops: {@link
   *       MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CBR_FD}, available from API31.
   * </ul>
   */
  @SuppressLint("InlinedApi")
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ,
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD
  })
  public @interface BitrateMode {}

  /** Builds {@link VideoEncoderSettings} instances. */
  public static final class Builder {
    private int bitrate;
    private @BitrateMode int bitrateMode;
    private int profile;
    private int level;
    private int colorProfile;
    private float iFrameIntervalSeconds;
    private int operatingRate;
    private int priority;

    /** Creates a new instance. */
    public Builder() {
      this.bitrate = NO_VALUE;
      this.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
      this.profile = NO_VALUE;
      this.level = NO_VALUE;
      this.colorProfile = DEFAULT_COLOR_PROFILE;
      this.iFrameIntervalSeconds = DEFAULT_I_FRAME_INTERVAL_SECONDS;
      this.operatingRate = NO_VALUE;
      this.priority = NO_VALUE;
    }

    private Builder(VideoEncoderSettings videoEncoderSettings) {
      this.bitrate = videoEncoderSettings.bitrate;
      this.bitrateMode = videoEncoderSettings.bitrateMode;
      this.profile = videoEncoderSettings.profile;
      this.level = videoEncoderSettings.level;
      this.colorProfile = videoEncoderSettings.colorProfile;
      this.iFrameIntervalSeconds = videoEncoderSettings.iFrameIntervalSeconds;
      this.operatingRate = videoEncoderSettings.operatingRate;
      this.priority = videoEncoderSettings.priority;
    }

    /**
     * Sets {@link VideoEncoderSettings#bitrate}. The default value is {@link #NO_VALUE}.
     *
     * @param bitrate The {@link VideoEncoderSettings#bitrate}.
     * @return This builder.
     */
    public Builder setBitrate(int bitrate) {
      this.bitrate = bitrate;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#bitrateMode}. The default value is {@code
     * MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR}.
     *
     * <p>Only {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_VBR} and {@link
     * MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CBR} are allowed.
     *
     * @param bitrateMode The {@link VideoEncoderSettings#bitrateMode}.
     * @return This builder.
     */
    public Builder setBitrateMode(@BitrateMode int bitrateMode) {
      checkArgument(
          bitrateMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
              || bitrateMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
      this.bitrateMode = bitrateMode;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#profile} and {@link VideoEncoderSettings#level}. The default
     * values are both {@link #NO_VALUE}.
     *
     * <p>The value must be one of the values defined in {@link MediaCodecInfo.CodecProfileLevel},
     * or {@link #NO_VALUE}.
     *
     * <p>Profile and level settings will be ignored when using {@link DefaultEncoderFactory} and
     * encoding to H264.
     *
     * @param encodingProfile The {@link VideoEncoderSettings#profile}.
     * @param encodingLevel The {@link VideoEncoderSettings#level}.
     * @return This builder.
     */
    public Builder setEncodingProfileLevel(int encodingProfile, int encodingLevel) {
      this.profile = encodingProfile;
      this.level = encodingLevel;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#colorProfile}. The default value is {@link
     * #DEFAULT_COLOR_PROFILE}.
     *
     * <p>The value must be one of the {@code COLOR_*} constants defined in {@link
     * MediaCodecInfo.CodecCapabilities}.
     *
     * @param colorProfile The {@link VideoEncoderSettings#colorProfile}.
     * @return This builder.
     */
    public Builder setColorProfile(int colorProfile) {
      this.colorProfile = colorProfile;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#iFrameIntervalSeconds}. The default value is {@link
     * #DEFAULT_I_FRAME_INTERVAL_SECONDS}.
     *
     * @param iFrameIntervalSeconds The {@link VideoEncoderSettings#iFrameIntervalSeconds}.
     * @return This builder.
     */
    public Builder setiFrameIntervalSeconds(float iFrameIntervalSeconds) {
      this.iFrameIntervalSeconds = iFrameIntervalSeconds;
      return this;
    }

    /**
     * Sets encoding operating rate and priority. The default values are {@link #NO_VALUE}.
     *
     * @param operatingRate The {@link MediaFormat#KEY_OPERATING_RATE operating rate}.
     * @param priority The {@link MediaFormat#KEY_PRIORITY priority}.
     * @return This builder.
     */
    @VisibleForTesting
    public Builder setEncoderPerformanceParameters(int operatingRate, int priority) {
      this.operatingRate = operatingRate;
      this.priority = priority;
      return this;
    }

    /** Builds the instance. */
    public VideoEncoderSettings build() {
      return new VideoEncoderSettings(
          bitrate,
          bitrateMode,
          profile,
          level,
          colorProfile,
          iFrameIntervalSeconds,
          operatingRate,
          priority);
    }
  }

  /** The encoding bitrate. */
  public final int bitrate;
  /** One of {@linkplain BitrateMode the allowed modes}. */
  public final @BitrateMode int bitrateMode;
  /** The encoding profile. */
  public final int profile;
  /** The encoding level. */
  public final int level;
  /** The encoding color profile. */
  public final int colorProfile;
  /** The encoding I-Frame interval in seconds. */
  public final float iFrameIntervalSeconds;
  /** The encoder {@link MediaFormat#KEY_OPERATING_RATE operating rate}. */
  public final int operatingRate;
  /** The encoder {@link MediaFormat#KEY_PRIORITY priority}. */
  public final int priority;

  private VideoEncoderSettings(
      int bitrate,
      int bitrateMode,
      int profile,
      int level,
      int colorProfile,
      float iFrameIntervalSeconds,
      int operatingRate,
      int priority) {
    this.bitrate = bitrate;
    this.bitrateMode = bitrateMode;
    this.profile = profile;
    this.level = level;
    this.colorProfile = colorProfile;
    this.iFrameIntervalSeconds = iFrameIntervalSeconds;
    this.operatingRate = operatingRate;
    this.priority = priority;
  }

  /**
   * Returns a {@link VideoEncoderSettings.Builder} initialized with the values of this instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VideoEncoderSettings)) {
      return false;
    }
    VideoEncoderSettings that = (VideoEncoderSettings) o;
    return bitrate == that.bitrate
        && bitrateMode == that.bitrateMode
        && profile == that.profile
        && level == that.level
        && colorProfile == that.colorProfile
        && iFrameIntervalSeconds == that.iFrameIntervalSeconds
        && operatingRate == that.operatingRate
        && priority == that.priority;
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + bitrate;
    result = 31 * result + bitrateMode;
    result = 31 * result + profile;
    result = 31 * result + level;
    result = 31 * result + colorProfile;
    result = 31 * result + Float.floatToIntBits(iFrameIntervalSeconds);
    result = 31 * result + operatingRate;
    result = 31 * result + priority;
    return result;
  }
}

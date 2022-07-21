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

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Format;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Represents the video encoder settings. */
public final class VideoEncoderSettings {

  /** A value for various fields to indicate that the field's value is unknown or not applicable. */
  public static final int NO_VALUE = Format.NO_VALUE;
  /** The default I-frame interval in seconds. */
  public static final float DEFAULT_I_FRAME_INTERVAL_SECONDS = 1.0f;

  /** A default {@link VideoEncoderSettings}. */
  public static final VideoEncoderSettings DEFAULT = new Builder().build();

  /**
   * The allowed values for {@code bitrateMode}.
   *
   * <ul>
   *   <li>Variable bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_VBR}.
   *   <li>Constant bitrate: {@link MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_CBR}.
   * </ul>
   */
  @SuppressLint("InlinedApi")
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    BITRATE_MODE_VBR,
    BITRATE_MODE_CBR,
  })
  public @interface BitrateMode {}

  /** Builds {@link VideoEncoderSettings} instances. */
  public static final class Builder {
    private int bitrate;
    private @BitrateMode int bitrateMode;
    private int profile;
    private int level;
    private float iFrameIntervalSeconds;
    private int operatingRate;
    private int priority;
    private boolean enableHighQualityTargeting;

    /** Creates a new instance. */
    public Builder() {
      this.bitrate = NO_VALUE;
      this.bitrateMode = BITRATE_MODE_VBR;
      this.profile = NO_VALUE;
      this.level = NO_VALUE;
      this.iFrameIntervalSeconds = DEFAULT_I_FRAME_INTERVAL_SECONDS;
      this.operatingRate = NO_VALUE;
      this.priority = NO_VALUE;
    }

    private Builder(VideoEncoderSettings videoEncoderSettings) {
      this.bitrate = videoEncoderSettings.bitrate;
      this.bitrateMode = videoEncoderSettings.bitrateMode;
      this.profile = videoEncoderSettings.profile;
      this.level = videoEncoderSettings.level;
      this.iFrameIntervalSeconds = videoEncoderSettings.iFrameIntervalSeconds;
      this.operatingRate = videoEncoderSettings.operatingRate;
      this.priority = videoEncoderSettings.priority;
      this.enableHighQualityTargeting = videoEncoderSettings.enableHighQualityTargeting;
    }

    /**
     * Sets {@link VideoEncoderSettings#bitrate}. The default value is {@link #NO_VALUE}.
     *
     * <p>Can not be set if enabling {@link #setEnableHighQualityTargeting(boolean)}.
     *
     * @param bitrate The {@link VideoEncoderSettings#bitrate}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setBitrate(int bitrate) {
      this.bitrate = bitrate;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#bitrateMode}. The default value is {@code
     * MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR}.
     *
     * <p>Value must be in {@link BitrateMode}.
     *
     * @param bitrateMode The {@link VideoEncoderSettings#bitrateMode}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setBitrateMode(@BitrateMode int bitrateMode) {
      checkArgument(bitrateMode == BITRATE_MODE_VBR || bitrateMode == BITRATE_MODE_CBR);
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
    @CanIgnoreReturnValue
    public Builder setEncodingProfileLevel(int encodingProfile, int encodingLevel) {
      this.profile = encodingProfile;
      this.level = encodingLevel;
      return this;
    }

    /**
     * Sets {@link VideoEncoderSettings#iFrameIntervalSeconds}. The default value is {@link
     * #DEFAULT_I_FRAME_INTERVAL_SECONDS}.
     *
     * @param iFrameIntervalSeconds The {@link VideoEncoderSettings#iFrameIntervalSeconds}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setiFrameIntervalSeconds(float iFrameIntervalSeconds) {
      this.iFrameIntervalSeconds = iFrameIntervalSeconds;
      return this;
    }

    /**
     * Sets encoding operating rate and priority. The default values are {@link #NO_VALUE}, which is
     * treated as configuring the encoder for maximum throughput.
     *
     * @param operatingRate The {@link MediaFormat#KEY_OPERATING_RATE operating rate}.
     * @param priority The {@link MediaFormat#KEY_PRIORITY priority}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    public Builder setEncoderPerformanceParameters(int operatingRate, int priority) {
      this.operatingRate = operatingRate;
      this.priority = priority;
      return this;
    }

    /**
     * Sets whether to enable automatic adjustment of the bitrate to target a high quality encoding.
     *
     * <p>Default value is {@code false}.
     *
     * <p>Requires {@link android.media.MediaCodecInfo.EncoderCapabilities#BITRATE_MODE_VBR}.
     *
     * <p>Can not be enabled alongside setting a custom bitrate with {@link #setBitrate(int)}.
     */
    @CanIgnoreReturnValue
    public Builder setEnableHighQualityTargeting(boolean enableHighQualityTargeting) {
      this.enableHighQualityTargeting = enableHighQualityTargeting;
      return this;
    }

    /** Builds the instance. */
    public VideoEncoderSettings build() {
      checkState(
          !enableHighQualityTargeting || bitrate == NO_VALUE,
          "Bitrate can not be set if enabling high quality targeting.");
      checkState(
          !enableHighQualityTargeting || bitrateMode == BITRATE_MODE_VBR,
          "Bitrate mode must be VBR if enabling high quality targeting.");
      return new VideoEncoderSettings(
          bitrate,
          bitrateMode,
          profile,
          level,
          iFrameIntervalSeconds,
          operatingRate,
          priority,
          enableHighQualityTargeting);
    }
  }

  /** The encoding bitrate. */
  public final int bitrate;
  /** One of {@linkplain BitrateMode}. */
  public final @BitrateMode int bitrateMode;
  /** The encoding profile. */
  public final int profile;
  /** The encoding level. */
  public final int level;
  /** The encoding I-Frame interval in seconds. */
  public final float iFrameIntervalSeconds;
  /** The encoder {@link MediaFormat#KEY_OPERATING_RATE operating rate}. */
  public final int operatingRate;
  /** The encoder {@link MediaFormat#KEY_PRIORITY priority}. */
  public final int priority;
  /** Whether the encoder should automatically set the bitrate to target a high quality encoding. */
  public final boolean enableHighQualityTargeting;

  private VideoEncoderSettings(
      int bitrate,
      int bitrateMode,
      int profile,
      int level,
      float iFrameIntervalSeconds,
      int operatingRate,
      int priority,
      boolean enableHighQualityTargeting) {
    this.bitrate = bitrate;
    this.bitrateMode = bitrateMode;
    this.profile = profile;
    this.level = level;
    this.iFrameIntervalSeconds = iFrameIntervalSeconds;
    this.operatingRate = operatingRate;
    this.priority = priority;
    this.enableHighQualityTargeting = enableHighQualityTargeting;
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
        && iFrameIntervalSeconds == that.iFrameIntervalSeconds
        && operatingRate == that.operatingRate
        && priority == that.priority
        && enableHighQualityTargeting == that.enableHighQualityTargeting;
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + bitrate;
    result = 31 * result + bitrateMode;
    result = 31 * result + profile;
    result = 31 * result + level;
    result = 31 * result + Float.floatToIntBits(iFrameIntervalSeconds);
    result = 31 * result + operatingRate;
    result = 31 * result + priority;
    result = 31 * result + (enableHighQualityTargeting ? 1 : 0);
    return result;
  }
}

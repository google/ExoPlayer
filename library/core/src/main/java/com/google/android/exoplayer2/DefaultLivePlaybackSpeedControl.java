/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.os.SystemClock;
import com.google.android.exoplayer2.MediaItem.LiveConfiguration;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link LivePlaybackSpeedControl} that adjusts the playback speed using a proportional
 * controller.
 *
 * <p>The control mechanism calculates the adjusted speed as {@code 1.0 + proportionalControlFactor
 * x (currentLiveOffsetSec - targetLiveOffsetSec)}. Unit speed (1.0f) is used, if the {@code
 * currentLiveOffsetSec} is marginally close to {@code targetLiveOffsetSec}, i.e. {@code
 * |currentLiveOffsetSec - targetLiveOffsetSec| <= MAXIMUM_LIVE_OFFSET_ERROR_US_FOR_UNIT_SPEED}.
 *
 * <p>The resulting speed is clamped to a minimum and maximum speed defined by the media, the
 * fallback values set with {@link Builder#setFallbackMinPlaybackSpeed(float)} and {@link
 * Builder#setFallbackMaxPlaybackSpeed(float)} or the {@link #DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED
 * minimum} and {@link #DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED maximum} fallback default values.
 */
public final class DefaultLivePlaybackSpeedControl implements LivePlaybackSpeedControl {

  /**
   * The default minimum playback speed that should be used if no minimum playback speed is defined
   * by the media.
   */
  public static final float DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED = 0.97f;

  /**
   * The default maximum playback speed that should be used if no maximum playback speed is defined
   * by the media.
   */
  public static final float DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED = 1.03f;

  /**
   * The default {@link Builder#setMinUpdateIntervalMs(long) minimum interval} between playback
   * speed changes, in milliseconds.
   */
  public static final long DEFAULT_MIN_UPDATE_INTERVAL_MS = 500;

  /**
   * The default {@link Builder#setProportionalControlFactor(float) proportional control factor}
   * used to adjust the playback speed.
   */
  public static final float DEFAULT_PROPORTIONAL_CONTROL_FACTOR = 0.05f;

  /**
   * The maximum difference between the current live offset and the target live offset for which
   * unit speed (1.0f) is used.
   */
  public static final long MAXIMUM_LIVE_OFFSET_ERROR_US_FOR_UNIT_SPEED = 5_000;

  /** Builder for a {@link DefaultLivePlaybackSpeedControl}. */
  public static final class Builder {

    private float fallbackMinPlaybackSpeed;
    private float fallbackMaxPlaybackSpeed;
    private long minUpdateIntervalMs;
    private float proportionalControlFactorUs;

    /** Creates a builder. */
    public Builder() {
      fallbackMinPlaybackSpeed = DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED;
      fallbackMaxPlaybackSpeed = DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED;
      minUpdateIntervalMs = DEFAULT_MIN_UPDATE_INTERVAL_MS;
      proportionalControlFactorUs = DEFAULT_PROPORTIONAL_CONTROL_FACTOR / C.MICROS_PER_SECOND;
    }

    /**
     * Sets the minimum playback speed that should be used if no minimum playback speed is defined
     * by the media.
     *
     * <p>The default is {@link #DEFAULT_FALLBACK_MIN_PLAYBACK_SPEED}.
     *
     * @param fallbackMinPlaybackSpeed The fallback minimum playback speed.
     * @return This builder, for convenience.
     */
    public Builder setFallbackMinPlaybackSpeed(float fallbackMinPlaybackSpeed) {
      Assertions.checkArgument(0 < fallbackMinPlaybackSpeed && fallbackMinPlaybackSpeed <= 1f);
      this.fallbackMinPlaybackSpeed = fallbackMinPlaybackSpeed;
      return this;
    }

    /**
     * Sets the maximum playback speed that should be used if no maximum playback speed is defined
     * by the media.
     *
     * <p>The default is {@link #DEFAULT_FALLBACK_MAX_PLAYBACK_SPEED}.
     *
     * @param fallbackMaxPlaybackSpeed The fallback maximum playback speed.
     * @return This builder, for convenience.
     */
    public Builder setFallbackMaxPlaybackSpeed(float fallbackMaxPlaybackSpeed) {
      Assertions.checkArgument(fallbackMaxPlaybackSpeed >= 1f);
      this.fallbackMaxPlaybackSpeed = fallbackMaxPlaybackSpeed;
      return this;
    }

    /**
     * Sets the minimum interval between playback speed changes, in milliseconds.
     *
     * <p>The default is {@link #DEFAULT_MIN_UPDATE_INTERVAL_MS}.
     *
     * @param minUpdateIntervalMs The minimum interval between playback speed changes, in
     *     milliseconds.
     * @return This builder, for convenience.
     */
    public Builder setMinUpdateIntervalMs(long minUpdateIntervalMs) {
      Assertions.checkArgument(minUpdateIntervalMs > 0);
      this.minUpdateIntervalMs = minUpdateIntervalMs;
      return this;
    }

    /**
     * Sets the proportional control factor used to adjust the playback speed.
     *
     * <p>The adjusted playback speed is calculated as {@code 1.0 + proportionalControlFactor x
     * (currentLiveOffsetSec - targetLiveOffsetSec)}.
     *
     * <p>The default is {@link #DEFAULT_PROPORTIONAL_CONTROL_FACTOR}.
     *
     * @param proportionalControlFactor The proportional control factor used to adjust the playback
     *     speed.
     * @return This builder, for convenience.
     */
    public Builder setProportionalControlFactor(float proportionalControlFactor) {
      Assertions.checkArgument(proportionalControlFactor > 0);
      this.proportionalControlFactorUs = proportionalControlFactor / C.MICROS_PER_SECOND;
      return this;
    }

    /** Builds an instance. */
    public DefaultLivePlaybackSpeedControl build() {
      return new DefaultLivePlaybackSpeedControl(
          fallbackMinPlaybackSpeed,
          fallbackMaxPlaybackSpeed,
          minUpdateIntervalMs,
          proportionalControlFactorUs);
    }
  }

  private final float fallbackMinPlaybackSpeed;
  private final float fallbackMaxPlaybackSpeed;
  private final long minUpdateIntervalMs;
  private final float proportionalControlFactor;

  private long mediaConfigurationTargetLiveOffsetUs;
  private long targetLiveOffsetOverrideUs;
  private long minTargetLiveOffsetUs;
  private long maxTargetLiveOffsetUs;
  private long currentTargetLiveOffsetUs;

  private float maxPlaybackSpeed;
  private float minPlaybackSpeed;
  private float adjustedPlaybackSpeed;
  private long lastPlaybackSpeedUpdateMs;

  private DefaultLivePlaybackSpeedControl(
      float fallbackMinPlaybackSpeed,
      float fallbackMaxPlaybackSpeed,
      long minUpdateIntervalMs,
      float proportionalControlFactor) {
    this.fallbackMinPlaybackSpeed = fallbackMinPlaybackSpeed;
    this.fallbackMaxPlaybackSpeed = fallbackMaxPlaybackSpeed;
    this.minUpdateIntervalMs = minUpdateIntervalMs;
    this.proportionalControlFactor = proportionalControlFactor;
    mediaConfigurationTargetLiveOffsetUs = C.TIME_UNSET;
    targetLiveOffsetOverrideUs = C.TIME_UNSET;
    minTargetLiveOffsetUs = C.TIME_UNSET;
    maxTargetLiveOffsetUs = C.TIME_UNSET;
    minPlaybackSpeed = fallbackMinPlaybackSpeed;
    maxPlaybackSpeed = fallbackMaxPlaybackSpeed;
    adjustedPlaybackSpeed = 1.0f;
    lastPlaybackSpeedUpdateMs = C.TIME_UNSET;
    currentTargetLiveOffsetUs = C.TIME_UNSET;
  }

  @Override
  public void setLiveConfiguration(LiveConfiguration liveConfiguration) {
    mediaConfigurationTargetLiveOffsetUs = C.msToUs(liveConfiguration.targetLiveOffsetMs);
    minTargetLiveOffsetUs = C.msToUs(liveConfiguration.minLiveOffsetMs);
    maxTargetLiveOffsetUs = C.msToUs(liveConfiguration.maxLiveOffsetMs);
    minPlaybackSpeed =
        liveConfiguration.minPlaybackSpeed != C.RATE_UNSET
            ? liveConfiguration.minPlaybackSpeed
            : fallbackMinPlaybackSpeed;
    maxPlaybackSpeed =
        liveConfiguration.maxPlaybackSpeed != C.RATE_UNSET
            ? liveConfiguration.maxPlaybackSpeed
            : fallbackMaxPlaybackSpeed;
    maybeResetTargetLiveOffsetUs();
  }

  @Override
  public void setTargetLiveOffsetOverrideUs(long liveOffsetUs) {
    targetLiveOffsetOverrideUs = liveOffsetUs;
    maybeResetTargetLiveOffsetUs();
  }

  @Override
  public float getAdjustedPlaybackSpeed(long liveOffsetUs) {
    if (mediaConfigurationTargetLiveOffsetUs == C.TIME_UNSET) {
      return 1f;
    }
    if (lastPlaybackSpeedUpdateMs != C.TIME_UNSET
        && SystemClock.elapsedRealtime() - lastPlaybackSpeedUpdateMs < minUpdateIntervalMs) {
      return adjustedPlaybackSpeed;
    }
    lastPlaybackSpeedUpdateMs = SystemClock.elapsedRealtime();

    long liveOffsetErrorUs = liveOffsetUs - currentTargetLiveOffsetUs;
    if (Math.abs(liveOffsetErrorUs) < MAXIMUM_LIVE_OFFSET_ERROR_US_FOR_UNIT_SPEED) {
      adjustedPlaybackSpeed = 1f;
    } else {
      float calculatedSpeed = 1f + proportionalControlFactor * liveOffsetErrorUs;
      adjustedPlaybackSpeed =
          Util.constrainValue(calculatedSpeed, minPlaybackSpeed, maxPlaybackSpeed);
    }
    return adjustedPlaybackSpeed;
  }

  @Override
  public long getTargetLiveOffsetUs() {
    return currentTargetLiveOffsetUs;
  }

  private void maybeResetTargetLiveOffsetUs() {
    long idealOffsetUs = C.TIME_UNSET;
    if (mediaConfigurationTargetLiveOffsetUs != C.TIME_UNSET) {
      idealOffsetUs =
          targetLiveOffsetOverrideUs != C.TIME_UNSET
              ? targetLiveOffsetOverrideUs
              : mediaConfigurationTargetLiveOffsetUs;
      if (minTargetLiveOffsetUs != C.TIME_UNSET && idealOffsetUs < minTargetLiveOffsetUs) {
        idealOffsetUs = minTargetLiveOffsetUs;
      }
      if (maxTargetLiveOffsetUs != C.TIME_UNSET && idealOffsetUs > maxTargetLiveOffsetUs) {
        idealOffsetUs = maxTargetLiveOffsetUs;
      }
    }
    if (currentTargetLiveOffsetUs == idealOffsetUs) {
      return;
    }
    currentTargetLiveOffsetUs = idealOffsetUs;
    lastPlaybackSpeedUpdateMs = C.TIME_UNSET;
  }
}

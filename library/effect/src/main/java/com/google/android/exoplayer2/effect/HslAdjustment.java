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

package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.content.Context;
import androidx.annotation.FloatRange;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Adjusts the HSL (Hue, Saturation, and Lightness) of a frame.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class HslAdjustment implements GlEffect {

  /** A builder for {@code HslAdjustment} instances. */
  public static final class Builder {
    private float hueAdjustment;
    private float saturationAdjustment;
    private float lightnessAdjustment;

    /** Creates a new instance with the default values. */
    public Builder() {}

    /**
     * Rotates the hue of the frame by {@code hueAdjustmentDegrees}.
     *
     * <p>The Hue of the frame is defined in the interval of [0, 360] degrees. The actual degrees of
     * hue adjustment applied is {@code hueAdjustmentDegrees % 360}.
     *
     * @param hueAdjustmentDegrees The hue adjustment in rotation degrees. The default value is
     *     {@code 0}, which means no change is applied.
     */
    @CanIgnoreReturnValue
    public Builder adjustHue(float hueAdjustmentDegrees) {
      hueAdjustment = hueAdjustmentDegrees % 360;
      return this;
    }

    /**
     * Adjusts the saturation of the frame by {@code saturationAdjustment}.
     *
     * <p>Saturation is defined in the interval of [0, 100] where a saturation of {@code 0} will
     * generate a grayscale frame and a saturation of {@code 100} has a maximum separation between
     * the colors.
     *
     * @param saturationAdjustment The difference of how much the saturation will be adjusted in
     *     either direction. Needs to be in the interval of [-100, 100] and the default value is
     *     {@code 0}, which means no change is applied.
     */
    @CanIgnoreReturnValue
    public Builder adjustSaturation(@FloatRange(from = -100, to = 100) float saturationAdjustment) {
      checkArgument(
          -100 <= saturationAdjustment && saturationAdjustment <= 100,
          "Can adjust the saturation by only 100 in either direction, but provided "
              + saturationAdjustment);
      this.saturationAdjustment = saturationAdjustment;
      return this;
    }

    /**
     * Adjusts the lightness of the frame by {@code lightnessAdjustment}.
     *
     * <p>Lightness is defined in the interval of [0, 100] where a lightness of {@code 0} is a black
     * frame and a lightness of {@code 100} is a white frame.
     *
     * @param lightnessAdjustment The difference by how much the lightness will be adjusted in
     *     either direction. Needs to be in the interval of [-100, 100] and the default value is
     *     {@code 0}, which means no change is applied.
     */
    @CanIgnoreReturnValue
    public Builder adjustLightness(@FloatRange(from = -100, to = 100) float lightnessAdjustment) {
      checkArgument(
          -100 <= lightnessAdjustment && lightnessAdjustment <= 100,
          "Can adjust the lightness by only 100 in either direction, but provided "
              + lightnessAdjustment);
      this.lightnessAdjustment = lightnessAdjustment;
      return this;
    }

    /** Creates a new {@link HslAdjustment} instance. */
    public HslAdjustment build() {
      return new HslAdjustment(hueAdjustment, saturationAdjustment, lightnessAdjustment);
    }
  }

  /** Indicates the hue adjustment in degrees. */
  public final float hueAdjustmentDegrees;
  /** Indicates the saturation adjustment. */
  public final float saturationAdjustment;
  /** Indicates the lightness adjustment. */
  public final float lightnessAdjustment;

  private HslAdjustment(
      float hueAdjustmentDegrees, float saturationAdjustment, float lightnessAdjustment) {
    this.hueAdjustmentDegrees = hueAdjustmentDegrees;
    this.saturationAdjustment = saturationAdjustment;
    this.lightnessAdjustment = lightnessAdjustment;
  }

  @Override
  public SingleFrameGlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new HslShaderProgram(context, /* hslAdjustment= */ this, useHdr);
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return hueAdjustmentDegrees == 0f && saturationAdjustment == 0f && lightnessAdjustment == 0f;
  }
}

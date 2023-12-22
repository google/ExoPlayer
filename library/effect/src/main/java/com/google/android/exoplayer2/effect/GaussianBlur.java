/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.annotation.FloatRange;
import androidx.annotation.RequiresApi;

/**
 * A {@link SeparableConvolution} to apply a Gaussian blur on image data.
 *
 * <p>The width of the blur is specified in pixels and applied symmetrically.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@RequiresApi(26) // See SeparableConvolution.
@Deprecated
public final class GaussianBlur extends SeparableConvolution {
  private final float sigma;
  private final float numStandardDeviations;

  /**
   * Creates an instance.
   *
   * @param sigma The half-width of 1 standard deviation, in pixels.
   * @param numStandardDeviations The size of function domain, measured in the number of standard
   *     deviations.
   */
  public GaussianBlur(
      @FloatRange(from = 0.0, fromInclusive = false) float sigma,
      @FloatRange(from = 0.0, fromInclusive = false) float numStandardDeviations) {
    this.sigma = sigma;
    this.numStandardDeviations = numStandardDeviations;
  }

  /**
   * Creates an instance with {@code numStandardDeviations} set to {@code 2.0f}.
   *
   * @param sigma The half-width of 1 standard deviation, in pixels.
   */
  public GaussianBlur(float sigma) {
    this.sigma = sigma;
    this.numStandardDeviations = 2.0f;
  }

  @Override
  public ConvolutionFunction1D getConvolution() {
    return new GaussianFunction(sigma, numStandardDeviations);
  }
}

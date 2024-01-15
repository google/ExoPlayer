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
package androidx.media3.effect;

import android.content.Context;
import androidx.annotation.FloatRange;
import androidx.annotation.RequiresApi;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * A {@link SeparableConvolution} to apply a Gaussian blur on image data.
 *
 * <p>The width of the blur is specified in pixels and applied symmetrically.
 */
@UnstableApi
@RequiresApi(26) // See SeparableConvolution.
public final class GaussianBlurWithFrameOverlaid extends SeparableConvolution {
  private final float sigma;
  private final float numStandardDeviations;
  private final float scaleSharpX;
  private final float scaleSharpY;

  /**
   * Creates an instance.
   *
   * @param sigma The half-width of 1 standard deviation, in pixels.
   * @param numStandardDeviations The size of function domain, measured in the number of standard
   *     deviations.
   * @param scaleSharpX The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the horizontal direction.
   * @param scaleSharpY The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the vertical direction.
   */
  public GaussianBlurWithFrameOverlaid(
      @FloatRange(from = 0.0, fromInclusive = false) float sigma,
      @FloatRange(from = 0.0, fromInclusive = false) float numStandardDeviations,
      float scaleSharpX,
      float scaleSharpY) {
    this.sigma = sigma;
    this.numStandardDeviations = numStandardDeviations;
    this.scaleSharpX = scaleSharpX;
    this.scaleSharpY = scaleSharpY;
  }

  /**
   * Creates an instance with {@code numStandardDeviations} set to {@code 2.0f}.
   *
   * @param sigma The half-width of 1 standard deviation, in pixels.
   * @param scaleSharpX The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the horizontal direction.
   * @param scaleSharpY The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the vertical direction.
   */
  public GaussianBlurWithFrameOverlaid(float sigma, float scaleSharpX, float scaleSharpY) {
    this(sigma, /* numStandardDeviations= */ 2.0f, scaleSharpX, scaleSharpY);
  }

  @Override
  public ConvolutionFunction1D getConvolution(long presentationTimeUs) {
    return new GaussianFunction(sigma, numStandardDeviations);
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new SharpSeparableConvolutionShaderProgram(
        context, useHdr, /* convolution= */ this, scaleSharpX, scaleSharpY);
  }
}

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

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.PI;
import static java.lang.Math.exp;
import static java.lang.Math.sqrt;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/**
 * Implementation of a symmetric Gaussian function with a limited domain.
 *
 * <p>The half-width of the domain is {@code sigma} times {@code numStdDev}. Values strictly outside
 * of that range are zero.
 */
@UnstableApi
public final class GaussianFunction implements ConvolutionFunction1D {
  private final float sigma;
  private final float numStdDev;

  /**
   * Creates an instance.
   *
   * @param sigma The one standard deviation, in pixels.
   * @param numStandardDeviations The half-width of function domain, measured in the number of
   *     standard deviations.
   */
  public GaussianFunction(
      @FloatRange(from = 0.0, fromInclusive = false) float sigma,
      @FloatRange(from = 0.0, fromInclusive = false) float numStandardDeviations) {
    checkArgument(sigma > 0 && numStandardDeviations > 0);
    this.sigma = sigma;
    this.numStdDev = numStandardDeviations;
  }

  @Override
  public float domainStart() {
    return -numStdDev * sigma;
  }

  @Override
  public float domainEnd() {
    return numStdDev * sigma;
  }

  @Override
  public float value(float samplePosition) {
    if (Math.abs(samplePosition) > numStdDev * sigma) {
      return 0.0f;
    }
    float samplePositionOverSigma = samplePosition / sigma;
    return (float)
        (exp(-samplePositionOverSigma * samplePositionOverSigma / 2) / sqrt(2 * PI) / sigma);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GaussianFunction)) {
      return false;
    }
    GaussianFunction that = (GaussianFunction) o;
    return Float.compare(that.sigma, sigma) == 0 && Float.compare(that.numStdDev, numStdDev) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sigma, numStdDev);
  }
}

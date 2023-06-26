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
package androidx.media3.test.utils;

import static java.lang.Math.pow;

import androidx.media3.common.util.UnstableApi;

/**
 * Image comparison tool that calculates the Mean Structural Similarity (MSSIM) of two images,
 * developed by Wang, Bovik, Sheikh, and Simoncelli.
 *
 * <p>MSSIM divides the image into windows, calculates SSIM of each, then returns the average.
 *
 * <p>See <a href=https://ece.uwaterloo.ca/~z70wang/publications/ssim.pdf>the SSIM paper</a>.
 */
@UnstableApi
public final class MssimCalculator {
  // Referred to as 'L' in the SSIM paper, this constant defines the maximum pixel values. The
  // range of pixel values is 0 to 255 (8 bit unsigned range).
  private static final int PIXEL_MAX_VALUE = 255;

  // K1 and K2, as defined in the SSIM paper.
  private static final double K1 = 0.01;
  private static final double K2 = 0.03;

  // C1 and C2 stabilize the SSIM value when either (referenceMean^2 + distortedMean^2) or
  // (referenceVariance + distortedVariance) is close to 0. See the SSIM formula in
  // `getWindowSsim` for how these values impact each other in the calculation.
  private static final double C1 = pow(PIXEL_MAX_VALUE * K1, 2);
  private static final double C2 = pow(PIXEL_MAX_VALUE * K2, 2);

  private static final int WINDOW_SIZE = 8;

  private MssimCalculator() {}

  /**
   * Calculates the Mean Structural Similarity (MSSIM) between two images with window skipping.
   *
   * @see #calculate(byte[], byte[], int, int, boolean)
   */
  public static double calculate(
      byte[] referenceBuffer, byte[] distortedBuffer, int width, int height) {
    return calculate(
        referenceBuffer, distortedBuffer, width, height, /* enableWindowSkipping= */ true);
  }

  /**
   * Calculates the Mean Structural Similarity (MSSIM) between two images.
   *
   * <p>The images are split into a grid of windows. For each window, the structural similarity
   * (SSIM) is calculated. The MSSIM returned from this method is the mean of these SSIM values. If
   * window skipping is enabled, only every other row and column are considered, thereby only one in
   * four windows are evaluated.
   *
   * @param referenceBuffer The luma channel (Y) buffer of the reference image.
   * @param distortedBuffer The luma channel (Y) buffer of the distorted image.
   * @param width The image width in pixels.
   * @param height The image height in pixels.
   * @param enableWindowSkipping Whether to skip every other row and column when evaluating windows
   *     for SSIM calculation.
   * @return The MSSIM score between the input images.
   */
  public static double calculate(
      byte[] referenceBuffer,
      byte[] distortedBuffer,
      int width,
      int height,
      boolean enableWindowSkipping) {
    double totalSsim = 0;
    int windowsCount = 0;

    int dimensionIncrement = WINDOW_SIZE * (enableWindowSkipping ? 2 : 1);

    for (int currentWindowY = 0; currentWindowY < height; currentWindowY += dimensionIncrement) {
      int windowHeight = computeWindowSize(currentWindowY, height);
      for (int currentWindowX = 0; currentWindowX < width; currentWindowX += dimensionIncrement) {
        windowsCount++;
        int windowWidth = computeWindowSize(currentWindowX, width);
        int bufferIndexOffset =
            get1dIndex(currentWindowX, currentWindowY, /* stride= */ width, /* offset= */ 0);
        double referenceMean =
            getMean(
                referenceBuffer, bufferIndexOffset, /* stride= */ width, windowWidth, windowHeight);
        double distortedMean =
            getMean(
                distortedBuffer, bufferIndexOffset, /* stride= */ width, windowWidth, windowHeight);

        double[] variances =
            getVariancesAndCovariance(
                referenceBuffer,
                distortedBuffer,
                referenceMean,
                distortedMean,
                bufferIndexOffset,
                /* stride= */ width,
                windowWidth,
                windowHeight);
        double referenceVariance = variances[0];
        double distortedVariance = variances[1];
        double referenceDistortedCovariance = variances[2];

        totalSsim +=
            getWindowSsim(
                referenceMean,
                distortedMean,
                referenceVariance,
                distortedVariance,
                referenceDistortedCovariance);
      }
    }

    if (windowsCount == 0) {
      return 1.0d;
    }

    return totalSsim / windowsCount;
  }

  /**
   * Returns the window size at the provided start coordinate, uses {@link #WINDOW_SIZE} if there is
   * enough space, otherwise the number of pixels between {@code start} and {@code dimension}.
   */
  private static int computeWindowSize(int start, int dimension) {
    if (start + WINDOW_SIZE <= dimension) {
      return WINDOW_SIZE;
    }
    return dimension - start;
  }

  /** Returns the SSIM of a window. */
  private static double getWindowSsim(
      double referenceMean,
      double distortedMean,
      double referenceVariance,
      double distortedVariance,
      double referenceDistortedCovariance) {

    // Uses equation 13 on page 6 from the linked paper.
    double numerator =
        (((2 * referenceMean * distortedMean) + C1) * ((2 * referenceDistortedCovariance) + C2));
    double denominator =
        ((referenceMean * referenceMean) + (distortedMean * distortedMean) + C1)
            * (referenceVariance + distortedVariance + C2);
    return numerator / denominator;
  }

  /** Returns the mean of the pixels in the window. */
  private static double getMean(
      byte[] pixelBuffer, int bufferIndexOffset, int stride, int windowWidth, int windowHeight) {
    double total = 0;
    for (int y = 0; y < windowHeight; y++) {
      for (int x = 0; x < windowWidth; x++) {
        total += pixelBuffer[get1dIndex(x, y, stride, bufferIndexOffset)] & 0xFF;
      }
    }
    return total / (windowWidth * windowHeight);
  }

  /** Calculates the variances and covariance of the pixels in the window for both buffers. */
  private static double[] getVariancesAndCovariance(
      byte[] referenceBuffer,
      byte[] distortedBuffer,
      double referenceMean,
      double distortedMean,
      int bufferIndexOffset,
      int stride,
      int windowWidth,
      int windowHeight) {
    double referenceVariance = 0;
    double distortedVariance = 0;
    double referenceDistortedCovariance = 0;
    for (int y = 0; y < windowHeight; y++) {
      for (int x = 0; x < windowWidth; x++) {
        int index = get1dIndex(x, y, stride, bufferIndexOffset);
        double referencePixelDeviation = (referenceBuffer[index] & 0xFF) - referenceMean;
        double distortedPixelDeviation = (distortedBuffer[index] & 0xFF) - distortedMean;
        referenceVariance += referencePixelDeviation * referencePixelDeviation;
        distortedVariance += distortedPixelDeviation * distortedPixelDeviation;
        referenceDistortedCovariance += referencePixelDeviation * distortedPixelDeviation;
      }
    }

    int normalizationFactor = windowWidth * windowHeight - 1;

    return new double[] {
      referenceVariance / normalizationFactor,
      distortedVariance / normalizationFactor,
      referenceDistortedCovariance / normalizationFactor
    };
  }

  /**
   * Translates a 2D coordinate into an 1D index, based on the stride of the 2D space.
   *
   * @param x The width component of coordinate.
   * @param y The height component of coordinate.
   * @param stride The width of the 2D space.
   * @param offset An offset to apply.
   * @return The 1D index.
   */
  private static int get1dIndex(int x, int y, int stride, int offset) {
    return x + (y * stride) + offset;
  }
}

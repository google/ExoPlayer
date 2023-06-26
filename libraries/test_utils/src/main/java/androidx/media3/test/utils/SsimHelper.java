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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.media.Image;
import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A helper for calculating SSIM score for transcoded videos.
 *
 * <p>SSIM (Structural Similarity) Index is a statistical measurement of the similarity between two
 * images. The mean SSIM score (taken between multiple frames) of two videos is a metric to
 * determine the similarity of the videos. SSIM does not measure the absolute difference of the two
 * images like MSE (mean squared error), but rather outputs the human perceptual difference. A
 * higher SSIM score signifies higher similarity, while a SSIM score of 1 means the two images are
 * exactly the same.
 *
 * <p>SSIM is traditionally computed with the luminance channel (Y), this class uses the luma
 * channel (Y') because the {@linkplain MediaCodec decoder} decodes to luma.
 */
@UnstableApi
@RequiresApi(21)
public final class SsimHelper {

  /** The default comparison interval. */
  public static final int DEFAULT_COMPARISON_INTERVAL = 11;

  private static final int DECODED_IMAGE_CHANNEL_COUNT = 3;
  private static final int MAX_IMAGE_READER_IMAGES_ALLOWED = 1;

  /**
   * Returns the mean SSIM score between the reference and the distorted video.
   *
   * <p>The method compares every {@link #DEFAULT_COMPARISON_INTERVAL n-th} frame from both videos.
   *
   * @param context The {@link Context}.
   * @param referenceVideoPath The path to the reference video file, which must be in {@linkplain
   *     Context#getAssets() Assets}.
   * @param distortedVideoPath The path to the distorted video file.
   * @throws IOException When unable to open the provided video paths.
   */
  public static double calculate(
      Context context, String referenceVideoPath, String distortedVideoPath)
      throws IOException, InterruptedException {
    VideoDecodingWrapper referenceDecodingWrapper =
        new VideoDecodingWrapper(
            context,
            referenceVideoPath,
            DEFAULT_COMPARISON_INTERVAL,
            MAX_IMAGE_READER_IMAGES_ALLOWED);
    VideoDecodingWrapper distortedDecodingWrapper =
        new VideoDecodingWrapper(
            context,
            distortedVideoPath,
            DEFAULT_COMPARISON_INTERVAL,
            MAX_IMAGE_READER_IMAGES_ALLOWED);
    @Nullable byte[] referenceLumaBuffer = null;
    @Nullable byte[] distortedLumaBuffer = null;
    double accumulatedSsim = 0.0;
    int comparedImagesCount = 0;
    try {
      while (true) {
        @Nullable Image referenceImage = referenceDecodingWrapper.runUntilComparisonFrameOrEnded();
        @Nullable Image distortedImage = distortedDecodingWrapper.runUntilComparisonFrameOrEnded();
        if (referenceImage == null) {
          assertThat(distortedImage).isNull();
          break;
        }
        checkNotNull(distortedImage);

        int width = referenceImage.getWidth();
        int height = referenceImage.getHeight();

        assertThat(distortedImage.getWidth()).isEqualTo(width);
        assertThat(distortedImage.getHeight()).isEqualTo(height);

        if (referenceLumaBuffer == null || referenceLumaBuffer.length != width * height) {
          referenceLumaBuffer = new byte[width * height];
        }
        if (distortedLumaBuffer == null || distortedLumaBuffer.length != width * height) {
          distortedLumaBuffer = new byte[width * height];
        }
        try {
          accumulatedSsim +=
              MssimCalculator.calculate(
                  extractLumaChannelBuffer(referenceImage, referenceLumaBuffer),
                  extractLumaChannelBuffer(distortedImage, distortedLumaBuffer),
                  width,
                  height);
        } finally {
          referenceImage.close();
          distortedImage.close();
        }
        comparedImagesCount++;
      }
    } finally {
      referenceDecodingWrapper.close();
      distortedDecodingWrapper.close();
    }
    assertWithMessage("Input had no frames.").that(comparedImagesCount).isGreaterThan(0);
    return accumulatedSsim / comparedImagesCount;
  }

  /**
   * Extracts, sets and returns the buffer of the luma (Y') channel of the image.
   *
   * @param image The {@link Image} in YUV format.
   * @param lumaChannelBuffer The buffer where the extracted luma values are stored.
   * @return The {@code lumaChannelBuffer} for convenience.
   */
  private static byte[] extractLumaChannelBuffer(Image image, byte[] lumaChannelBuffer) {
    // This method is invoked on the main thread.
    // `image` should contain YUV channels.
    Image.Plane[] imagePlanes = image.getPlanes();
    assertThat(imagePlanes).hasLength(DECODED_IMAGE_CHANNEL_COUNT);
    Image.Plane lumaPlane = imagePlanes[0];
    int rowStride = lumaPlane.getRowStride();
    int pixelStride = lumaPlane.getPixelStride();
    int width = image.getWidth();
    int height = image.getHeight();
    ByteBuffer lumaByteBuffer = lumaPlane.getBuffer();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        lumaChannelBuffer[y * width + x] = lumaByteBuffer.get(y * rowStride + x * pixelStride);
      }
    }
    return lumaChannelBuffer;
  }

  private SsimHelper() {
    // Prevent instantiation.
  }
}

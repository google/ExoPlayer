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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Math.pow;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.Closeable;
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
public final class SsimHelper {

  /** The default comparison interval. */
  public static final int DEFAULT_COMPARISON_INTERVAL = 11;

  private static final int IMAGE_AVAILABLE_TIMEOUT_MS = 10_000;
  private static final int DECODED_IMAGE_CHANNEL_COUNT = 3;

  /**
   * Returns the mean SSIM score between the expected and the actual video.
   *
   * <p>The method compares every {@link #DEFAULT_COMPARISON_INTERVAL n-th} frame from both videos.
   *
   * @param context The {@link Context}.
   * @param expectedVideoPath The path to the expected video file, must be in {@link
   *     Context#getAssets() Assets}.
   * @param actualVideoPath The path to the actual video file.
   * @throws IOException When unable to open the provided video paths.
   */
  public static double calculate(Context context, String expectedVideoPath, String actualVideoPath)
      throws IOException, InterruptedException {
    VideoDecodingWrapper expectedDecodingWrapper =
        new VideoDecodingWrapper(context, expectedVideoPath, DEFAULT_COMPARISON_INTERVAL);
    VideoDecodingWrapper actualDecodingWrapper =
        new VideoDecodingWrapper(context, actualVideoPath, DEFAULT_COMPARISON_INTERVAL);
    @Nullable byte[] expectedLumaBuffer = null;
    @Nullable byte[] actualLumaBuffer = null;
    double accumulatedSsim = 0.0;
    int comparedImagesCount = 0;
    try {
      while (true) {
        @Nullable Image expectedImage = expectedDecodingWrapper.runUntilComparisonFrameOrEnded();
        @Nullable Image actualImage = actualDecodingWrapper.runUntilComparisonFrameOrEnded();
        if (expectedImage == null) {
          assertThat(actualImage).isNull();
          break;
        }
        checkNotNull(actualImage);

        int width = expectedImage.getWidth();
        int height = expectedImage.getHeight();

        assertThat(actualImage.getWidth()).isEqualTo(width);
        assertThat(actualImage.getHeight()).isEqualTo(height);

        if (expectedLumaBuffer == null || expectedLumaBuffer.length != width * height) {
          expectedLumaBuffer = new byte[width * height];
        }
        if (actualLumaBuffer == null || actualLumaBuffer.length != width * height) {
          actualLumaBuffer = new byte[width * height];
        }
        try {
          accumulatedSsim +=
              SsimCalculator.calculate(
                  extractLumaChannelBuffer(expectedImage, expectedLumaBuffer),
                  extractLumaChannelBuffer(actualImage, actualLumaBuffer),
                  /* offset= */ 0,
                  /* stride= */ width,
                  width,
                  height);
        } finally {
          expectedImage.close();
          actualImage.close();
        }
        comparedImagesCount++;
      }
    } finally {
      expectedDecodingWrapper.close();
      actualDecodingWrapper.close();
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

  private static final class VideoDecodingWrapper implements Closeable {
    // Use ExoPlayer's 10ms timeout setting. In practise, the test durations from using timeouts of
    // 1/10/100ms don't differ significantly.
    private static final long DEQUEUE_TIMEOUT_US = 10_000;
    // SSIM should be calculated using the luma (Y') channel, thus using the YUV color space.
    private static final int IMAGE_READER_COLOR_SPACE = ImageFormat.YUV_420_888;
    private static final int MEDIA_CODEC_COLOR_SPACE =
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final String ASSET_FILE_SCHEME = "asset:///";
    private static final int MAX_IMAGES_ALLOWED = 1;

    private final MediaCodec mediaCodec;
    private final MediaExtractor mediaExtractor;
    private final MediaCodec.BufferInfo bufferInfo;
    private final ImageReader imageReader;
    private final ConditionVariable imageAvailableConditionVariable;
    private final int comparisonInterval;

    private boolean isCurrentFrameComparisonFrame;
    private boolean hasReadEndOfInputStream;
    private boolean queuedEndOfStreamToDecoder;
    private boolean dequeuedAllDecodedFrames;
    private int dequeuedFramesCount;

    /**
     * Creates a new instance.
     *
     * @param context The {@link Context}.
     * @param filePath The path to the video file.
     * @param comparisonInterval The number of frames between the frames selected for comparison by
     *     SSIM.
     * @throws IOException When failed to open the video file.
     */
    public VideoDecodingWrapper(Context context, String filePath, int comparisonInterval)
        throws IOException {
      this.comparisonInterval = comparisonInterval;
      mediaExtractor = new MediaExtractor();
      bufferInfo = new MediaCodec.BufferInfo();

      if (filePath.contains(ASSET_FILE_SCHEME)) {
        AssetFileDescriptor assetFd =
            context.getAssets().openFd(filePath.replace(ASSET_FILE_SCHEME, ""));
        mediaExtractor.setDataSource(
            assetFd.getFileDescriptor(), assetFd.getStartOffset(), assetFd.getLength());
      } else {
        mediaExtractor.setDataSource(filePath);
      }

      @Nullable MediaFormat mediaFormat = null;
      for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
        if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
          mediaFormat = mediaExtractor.getTrackFormat(i);
          mediaExtractor.selectTrack(i);
          break;
        }
      }

      checkStateNotNull(mediaFormat);
      checkState(mediaFormat.containsKey(MediaFormat.KEY_WIDTH));
      int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
      checkState(mediaFormat.containsKey(MediaFormat.KEY_HEIGHT));
      int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

      // Create a handler for the main thread to receive image available notifications. The current
      // (test) thread blocks until this callback is received.
      Handler mainThreadHandler = Util.createHandlerForCurrentOrMainLooper();
      imageAvailableConditionVariable = new ConditionVariable();
      imageReader =
          ImageReader.newInstance(width, height, IMAGE_READER_COLOR_SPACE, MAX_IMAGES_ALLOWED);
      imageReader.setOnImageAvailableListener(
          imageReader -> imageAvailableConditionVariable.open(), mainThreadHandler);

      String sampleMimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
      mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MEDIA_CODEC_COLOR_SPACE);
      mediaCodec = MediaCodec.createDecoderByType(sampleMimeType);
      mediaCodec.configure(
          mediaFormat, imageReader.getSurface(), /* crypto= */ null, /* flags= */ 0);
      mediaCodec.start();
    }

    /**
     * Returns the next decoded comparison frame, or {@code null} if the stream has ended. The
     * caller takes ownership of any returned image and is responsible for closing it before calling
     * this method again.
     */
    @Nullable
    public Image runUntilComparisonFrameOrEnded() throws InterruptedException {
      while (!hasEnded() && !isCurrentFrameComparisonFrame) {
        while (dequeueOneFrameFromDecoder()) {}
        while (queueOneFrameToDecoder()) {}
      }
      if (isCurrentFrameComparisonFrame) {
        isCurrentFrameComparisonFrame = false;
        assertThat(imageAvailableConditionVariable.block(IMAGE_AVAILABLE_TIMEOUT_MS)).isTrue();
        imageAvailableConditionVariable.close();
        return imageReader.acquireLatestImage();
      }
      return null;
    }

    /** Returns whether decoding has ended. */
    private boolean hasEnded() {
      return dequeuedAllDecodedFrames;
    }

    /** Returns whether a frame is queued to the {@link MediaCodec decoder}. */
    private boolean queueOneFrameToDecoder() {
      if (queuedEndOfStreamToDecoder) {
        return false;
      }

      int inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
      if (inputBufferIndex < 0) {
        return false;
      }

      if (hasReadEndOfInputStream) {
        mediaCodec.queueInputBuffer(
            inputBufferIndex,
            /* offset= */ 0,
            /* size= */ 0,
            /* presentationTimeUs= */ 0,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        queuedEndOfStreamToDecoder = true;
        return false;
      }

      ByteBuffer inputBuffer = checkNotNull(mediaCodec.getInputBuffer(inputBufferIndex));
      int sampleSize = mediaExtractor.readSampleData(inputBuffer, /* offset= */ 0);
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          sampleSize,
          mediaExtractor.getSampleTime(),
          mediaExtractor.getSampleFlags());
      // MediaExtractor.advance does not reliably return false for end-of-stream, so check sample
      // metadata instead as a more reliable signal. See [internal: b/121204004].
      mediaExtractor.advance();
      hasReadEndOfInputStream = mediaExtractor.getSampleTime() == -1;
      return true;
    }

    /** Returns whether a frame is decoded, renders the frame if the frame is a comparison frame. */
    private boolean dequeueOneFrameFromDecoder() {
      if (isCurrentFrameComparisonFrame) {
        return false;
      }

      int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
      if (outputBufferIndex <= 0) {
        return false;
      }
      isCurrentFrameComparisonFrame = dequeuedFramesCount % comparisonInterval == 0;
      dequeuedFramesCount++;
      mediaCodec.releaseOutputBuffer(
          outputBufferIndex, /* render= */ isCurrentFrameComparisonFrame);

      if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        dequeuedAllDecodedFrames = true;
      }
      return true;
    }

    @Override
    public void close() {
      mediaExtractor.release();
      mediaCodec.release();
      imageReader.close();
    }
  }

  /**
   * Image comparison using the Structural Similarity Index, developed by Wang, Bovik, Sheikh, and
   * Simoncelli.
   *
   * @see <a href=https://ece.uwaterloo.ca/~z70wang/publications/ssim.pdf>The SSIM paper</a>.
   */
  private static final class SsimCalculator {
    // These values were taken from the SSIM paper. Please see the linked paper for details.
    private static final double IMAGE_DYNAMIC_RANGE = 255;
    private static final double K1 = 0.01;
    private static final double K2 = 0.03;
    private static final double C1 = pow(IMAGE_DYNAMIC_RANGE * K1, 2);
    private static final double C2 = pow(IMAGE_DYNAMIC_RANGE * K2, 2);
    private static final int WINDOW_SIZE = 8;

    /**
     * Calculates the Structural Similarity Index (SSIM) between two images.
     *
     * @param expected The luma channel (Y) bitmap of the expected image.
     * @param actual The luma channel (Y) bitmap of the actual image.
     * @param offset The offset.
     * @param stride The stride of the bitmap.
     * @param width The image width in pixels.
     * @param height The image height in pixels.
     * @return The SSIM score between the input images.
     */
    public static double calculate(
        byte[] expected, byte[] actual, int offset, int stride, int width, int height) {
      double totalSsim = 0;
      int windowsCount = 0;

      // X refers to the expected image, while Y refers to the actual image.
      for (int currentWindowY = 0; currentWindowY < height; currentWindowY += WINDOW_SIZE) {
        int windowHeight = computeWindowSize(currentWindowY, height);
        for (int currentWindowX = 0; currentWindowX < width; currentWindowX += WINDOW_SIZE) {
          windowsCount++;
          int windowWidth = computeWindowSize(currentWindowX, width);
          int start = getGlobalCoordinate(currentWindowX, currentWindowY, stride, offset);
          double meanX = getMean(expected, start, stride, windowWidth, windowHeight);
          double meanY = getMean(actual, start, stride, windowWidth, windowHeight);

          double[] variances =
              getVariancesAndCovariance(
                  expected, actual, meanX, meanY, start, stride, windowWidth, windowHeight);
          // varX is the variance of window X, covXY is the covariance between window X and Y.
          double varX = variances[0];
          double varY = variances[1];
          double covXY = variances[2];

          totalSsim += getWindowSsim(meanX, meanY, varX, varY, covXY);
        }
      }

      if (windowsCount == 0) {
        return 1.0d;
      }

      return totalSsim / windowsCount;
    }

    /**
     * Returns the window size at the provided start coordinate, uses {@link #WINDOW_SIZE} if there
     * is enough space, otherwise the number of pixels between {@code start} and {@code dimension}.
     */
    private static int computeWindowSize(int start, int dimension) {
      if (start + WINDOW_SIZE <= dimension) {
        return WINDOW_SIZE;
      }
      return dimension - start;
    }

    /** Returns the SSIM of a window. */
    private static double getWindowSsim(
        double meanX, double meanY, double varX, double varY, double covXY) {

      // Uses equation 13 on page 6 from the linked paper.
      double numerator = (((2 * meanX * meanY) + C1) * ((2 * covXY) + C2));
      double denominator = ((meanX * meanX) + (meanY * meanY) + C1) * (varX + varY + C2);
      return numerator / denominator;
    }

    /** Returns the means of the pixels in the two provided windows, in order. */
    private static double getMean(
        byte[] pixels, int start, int stride, int windowWidth, int windowHeight) {
      double total = 0;
      for (int y = 0; y < windowHeight; y++) {
        for (int x = 0; x < windowWidth; x++) {
          total += pixels[getGlobalCoordinate(x, y, stride, start)];
        }
      }
      return total / windowWidth * windowHeight;
    }

    /** Returns the two variances and the covariance of the two windows. */
    private static double[] getVariancesAndCovariance(
        byte[] pixelsX,
        byte[] pixelsY,
        double meanX,
        double meanY,
        int start,
        int stride,
        int windowWidth,
        int windowHeight) {
      // The variances in X and Y.
      double varX = 0;
      double varY = 0;
      // The covariance between X and Y.
      double covXY = 0;
      for (int y = 0; y < windowHeight; y++) {
        for (int x = 0; x < windowWidth; x++) {
          int index = getGlobalCoordinate(x, y, stride, start);
          double offsetX = pixelsX[index] - meanX;
          double offsetY = pixelsY[index] - meanY;
          varX += pow(offsetX, 2);
          varY += pow(offsetY, 2);
          covXY += offsetX * offsetY;
        }
      }

      int normalizationFactor = windowWidth * windowHeight - 1;
      return new double[] {
        varX / normalizationFactor, varY / normalizationFactor, covXY / normalizationFactor
      };
    }

    private static int getGlobalCoordinate(int x, int y, int stride, int offset) {
      return x + (y * stride) + offset;
    }
  }
}

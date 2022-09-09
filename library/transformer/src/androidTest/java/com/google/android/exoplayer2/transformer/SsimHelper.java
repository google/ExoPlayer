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
        new VideoDecodingWrapper(context, referenceVideoPath, DEFAULT_COMPARISON_INTERVAL);
    VideoDecodingWrapper distortedDecodingWrapper =
        new VideoDecodingWrapper(context, distortedVideoPath, DEFAULT_COMPARISON_INTERVAL);
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
   * Image comparison using the Mean Structural Similarity (MSSIM), developed by Wang, Bovik,
   * Sheikh, and Simoncelli.
   *
   * <p>MSSIM divides the image into windows, calculates SSIM of each, then returns the average.
   *
   * @see <a href=https://ece.uwaterloo.ca/~z70wang/publications/ssim.pdf>The SSIM paper</a>.
   */
  private static final class MssimCalculator {
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

    /**
     * Calculates the Mean Structural Similarity (MSSIM) between two images.
     *
     * @param referenceBuffer The luma channel (Y) buffer of the reference image.
     * @param distortedBuffer The luma channel (Y) buffer of the distorted image.
     * @param width The image width in pixels.
     * @param height The image height in pixels.
     * @return The MSSIM score between the input images.
     */
    public static double calculate(
        byte[] referenceBuffer, byte[] distortedBuffer, int width, int height) {
      double totalSsim = 0;
      int windowsCount = 0;

      for (int currentWindowY = 0; currentWindowY < height; currentWindowY += WINDOW_SIZE) {
        int windowHeight = computeWindowSize(currentWindowY, height);
        for (int currentWindowX = 0; currentWindowX < width; currentWindowX += WINDOW_SIZE) {
          windowsCount++;
          int windowWidth = computeWindowSize(currentWindowX, width);
          int bufferIndexOffset =
              get1dIndex(currentWindowX, currentWindowY, /* stride= */ width, /* offset= */ 0);
          double referenceMean =
              getMean(
                  referenceBuffer,
                  bufferIndexOffset,
                  /* stride= */ width,
                  windowWidth,
                  windowHeight);
          double distortedMean =
              getMean(
                  distortedBuffer,
                  bufferIndexOffset,
                  /* stride= */ width,
                  windowWidth,
                  windowHeight);

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
}

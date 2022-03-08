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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;
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
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A helper for calculating SSIM score for transcoded videos.
 *
 * <p>SSIM (Structural Similarity) Index is a statistical measurement of the similarity between two
 * images. The mean SSIM score (taken between multiple frames) of two videos is a metric to
 * determine the similarity of the videos. SSIM does not measure the absolute difference of the two
 * images like MSE (mean squared error), but rather outputs the human perceptual difference. A
 * higher SSIM score signifies higher similarity, while a SSIM score of 1 means the two images are
 * exactly the same.
 */
public final class SsimHelper {

  /** The default comparison interval. */
  public static final int DEFAULT_COMPARISON_INTERVAL = 11;

  private static final int SURFACE_WAIT_MS = 10;
  private static final int DECODED_IMAGE_CHANNEL_COUNT = 3;
  private static final int[] EMPTY_BUFFER = new int[0];

  private final Context context;
  private final String expectedVideoPath;
  private final String actualVideoPath;
  private final int comparisonInterval;

  private @MonotonicNonNull VideoDecodingWrapper expectedDecodingWrapper;
  private @MonotonicNonNull VideoDecodingWrapper actualDecodingWrapper;
  private double accumulatedSsim;
  private int comparedImagesCount;

  // These atomic fields are read on both test thread (where MediaCodec is controlled) and set on
  // the main thread (where ImageReader invokes its callback).
  private final AtomicReference<int[]> expectedLumaBuffer;
  private final AtomicReference<int[]> actualLumaBuffer;
  private final AtomicInteger width;
  private final AtomicInteger height;

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
    return new SsimHelper(context, expectedVideoPath, actualVideoPath, DEFAULT_COMPARISON_INTERVAL)
        .calculateSsim();
  }

  private SsimHelper(
      Context context, String expectedVideoPath, String actualVideoPath, int comparisonInterval) {
    this.context = context;
    this.expectedVideoPath = expectedVideoPath;
    this.actualVideoPath = actualVideoPath;
    this.comparisonInterval = comparisonInterval;
    this.expectedLumaBuffer = new AtomicReference<>(EMPTY_BUFFER);
    this.actualLumaBuffer = new AtomicReference<>(EMPTY_BUFFER);
    this.width = new AtomicInteger(Format.NO_VALUE);
    this.height = new AtomicInteger(Format.NO_VALUE);
  }

  /** Calculates the SSIM score between the two videos. */
  private double calculateSsim() throws InterruptedException, IOException {
    // The test thread has no looper, so a handler is created on which the
    // ImageReader.OnImageAvailableListener is called.
    Handler mainThreadHandler = Util.createHandlerForCurrentOrMainLooper();
    ImageReader.OnImageAvailableListener onImageAvailableListener = this::onImageAvailableListener;
    expectedDecodingWrapper =
        new VideoDecodingWrapper(
            context,
            expectedVideoPath,
            onImageAvailableListener,
            mainThreadHandler,
            comparisonInterval);
    actualDecodingWrapper =
        new VideoDecodingWrapper(
            context,
            actualVideoPath,
            onImageAvailableListener,
            mainThreadHandler,
            comparisonInterval);

    try {
      while (!expectedDecodingWrapper.hasEnded() && !actualDecodingWrapper.hasEnded()) {
        if (!expectedDecodingWrapper.runUntilComparisonFrameOrEnded()
            || !actualDecodingWrapper.runUntilComparisonFrameOrEnded()) {
          continue;
        }

        while (expectedLumaBuffer.get() == EMPTY_BUFFER || actualLumaBuffer.get() == EMPTY_BUFFER) {
          // Wait for the ImageReader to call onImageAvailable and process the luma channel on the
          // main thread.
          Thread.sleep(SURFACE_WAIT_MS);
        }
        accumulatedSsim +=
            SsimCalculator.calculate(
                expectedLumaBuffer.get(),
                actualLumaBuffer.get(),
                /* offset= */ 0,
                /* stride= */ width.get(),
                width.get(),
                height.get());
        comparedImagesCount++;
        expectedLumaBuffer.set(EMPTY_BUFFER);
        actualLumaBuffer.set(EMPTY_BUFFER);
      }
    } finally {
      expectedDecodingWrapper.close();
      actualDecodingWrapper.close();
    }

    if (comparedImagesCount == 0) {
      throw new IOException("Input had no frames.");
    }
    return accumulatedSsim / comparedImagesCount;
  }

  private void onImageAvailableListener(ImageReader imageReader) {
    // This method is invoked on the main thread.
    Image image = imageReader.acquireLatestImage();
    int[] lumaBuffer = extractLumaChannelBuffer(image);
    width.set(image.getWidth());
    height.set(image.getHeight());
    image.close();

    if (imageReader == checkNotNull(expectedDecodingWrapper).imageReader) {
      expectedLumaBuffer.set(lumaBuffer);
    } else if (imageReader == checkNotNull(actualDecodingWrapper).imageReader) {
      actualLumaBuffer.set(lumaBuffer);
    } else {
      throw new IllegalStateException("Unexpected ImageReader.");
    }
  }

  /**
   * Returns the buffer of the luma (Y) channel of the image.
   *
   * @param image The {@link Image} in YUV format.
   */
  private static int[] extractLumaChannelBuffer(Image image) {
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
    int[] lumaChannelBuffer = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        lumaChannelBuffer[y * width + x] = lumaByteBuffer.get(y * rowStride + x * pixelStride);
      }
    }
    return lumaChannelBuffer;
  }

  private static final class VideoDecodingWrapper implements Closeable {
    // Use ExoPlayer's 10ms timeout setting. In practise, the test durations from using timeouts of
    // 1/10/100ms don't differ significantly.
    private static final long DEQUEUE_TIMEOUT_US = 10_000;
    // SSIM should be calculated using the luma (Y) channel, thus using the YUV color space.
    private static final int IMAGE_READER_COLOR_SPACE = ImageFormat.YUV_420_888;
    private static final int MEDIA_CODEC_COLOR_SPACE =
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final String ASSET_FILE_SCHEME = "asset:///";
    private static final int MAX_IMAGES_ALLOWED = 1;

    private final MediaCodec mediaCodec;
    private final MediaExtractor mediaExtractor;
    private final MediaCodec.BufferInfo bufferInfo;
    private final ImageReader imageReader;
    private final int comparisonInterval;

    private boolean isCurrentFrameComparisonFrame;
    private boolean hasReadEndOfInputStream;
    private boolean queuedEndOfStreamToEncoder;
    private boolean dequeuedAllDecodedFrames;
    private int dequeuedFramesCount;

    /**
     * Creates a new instance.
     *
     * @param context The {@link Context}.
     * @param filePath The path to the video file.
     * @param imageAvailableListener An {@link ImageReader.OnImageAvailableListener} implementation.
     * @param handler The {@link Handler} on which the {@code imageAvailableListener} is called.
     * @param comparisonInterval The number of frames between the frames selected for comparison by
     *     SSIM.
     * @throws IOException When failed to open the video file.
     */
    public VideoDecodingWrapper(
        Context context,
        String filePath,
        ImageReader.OnImageAvailableListener imageAvailableListener,
        Handler handler,
        int comparisonInterval)
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

      imageReader =
          ImageReader.newInstance(width, height, IMAGE_READER_COLOR_SPACE, MAX_IMAGES_ALLOWED);
      imageReader.setOnImageAvailableListener(imageAvailableListener, handler);

      String sampleMimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
      mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MEDIA_CODEC_COLOR_SPACE);
      mediaCodec = MediaCodec.createDecoderByType(sampleMimeType);
      mediaCodec.configure(
          mediaFormat, imageReader.getSurface(), /* crypto= */ null, /* flags= */ 0);
      mediaCodec.start();
    }

    /**
     * Run decoding until a comparison frame is rendered, or decoding has ended.
     *
     * <p>The method returns after rendering the comparison frame. There is no guarantee that the
     * frame is available for processing at this time.
     *
     * @return {@code true} when a comparison frame is encountered, or {@code false} if decoding
     *     {@link #hasEnded() had ended}.
     */
    public boolean runUntilComparisonFrameOrEnded() {
      while (!hasEnded() && !isCurrentFrameComparisonFrame) {
        while (dequeueOneFrameFromDecoder()) {}
        while (queueOneFrameToEncoder()) {}
      }
      if (isCurrentFrameComparisonFrame) {
        isCurrentFrameComparisonFrame = false;
        return true;
      }
      return false;
    }

    /** Returns whether decoding has ended. */
    public boolean hasEnded() {
      return queuedEndOfStreamToEncoder && dequeuedAllDecodedFrames;
    }

    /** Returns whether a frame is queued to the {@link MediaCodec decoder}. */
    private boolean queueOneFrameToEncoder() {
      if (queuedEndOfStreamToEncoder) {
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
        queuedEndOfStreamToEncoder = true;
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
      hasReadEndOfInputStream = !mediaExtractor.advance();
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

    public static double calculate(
        int[] expected, int[] actual, int offset, int stride, int width, int height) {
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
        int[] pixels, int start, int stride, int windowWidth, int windowHeight) {
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
        int[] pixelsX,
        int[] pixelsY,
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

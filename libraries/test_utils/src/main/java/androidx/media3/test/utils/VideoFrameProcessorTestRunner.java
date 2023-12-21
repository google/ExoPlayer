/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaFormat;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.ConstantRateTimestampIterator;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A test runner for {@link VideoFrameProcessor} tests. */
@UnstableApi
@RequiresApi(19)
public final class VideoFrameProcessorTestRunner {

  /** A builder for {@link VideoFrameProcessorTestRunner} instances. */
  public static final class Builder {
    /** The ratio of width over height, for each pixel in a frame. */
    private static final float DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO = 1;

    private @MonotonicNonNull String testId;
    private VideoFrameProcessor.@MonotonicNonNull Factory videoFrameProcessorFactory;
    private @MonotonicNonNull BitmapReader bitmapReader;
    private @MonotonicNonNull String videoAssetPath;
    private @MonotonicNonNull String outputFileLabel;
    private @MonotonicNonNull ImmutableList<Effect> effects;
    private float pixelWidthHeightRatio;
    private @MonotonicNonNull ColorInfo outputColorInfo;
    private OnOutputFrameAvailableForRenderingListener onOutputFrameAvailableListener;
    private OnVideoFrameProcessingEndedListener onEndedListener;

    /** Creates a new instance with default values. */
    public Builder() {
      pixelWidthHeightRatio = DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO;
      onOutputFrameAvailableListener = unused -> {};
      onEndedListener = () -> {};
    }

    /**
     * Sets the test ID, used to generate output files.
     *
     * <p>This is a required value.
     */
    @CanIgnoreReturnValue
    public Builder setTestId(String testId) {
      this.testId = testId;
      return this;
    }

    /**
     * Sets the {@link VideoFrameProcessor.Factory}.
     *
     * <p>This is a required value.
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameProcessorFactory(
        VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
      return this;
    }

    /**
     * Sets the {@link BitmapReader}.
     *
     * <p>The default value is a {@link SurfaceBitmapReader} instance.
     */
    @CanIgnoreReturnValue
    public Builder setBitmapReader(BitmapReader bitmapReader) {
      this.bitmapReader = bitmapReader;
      return this;
    }

    /**
     * Sets the input video asset path.
     *
     * <p>No default value is set. Must be set when the input is a video file.
     */
    @CanIgnoreReturnValue
    public Builder setVideoAssetPath(String videoAssetPath) {
      this.videoAssetPath = videoAssetPath;
      return this;
    }

    /**
     * Sets the output file label.
     *
     * <p>This value will be postfixed after the {@code testId} to generated output files.
     *
     * <p>The default value is an empty string.
     */
    @CanIgnoreReturnValue
    public Builder setOutputFileLabel(String outputFileLabel) {
      this.outputFileLabel = outputFileLabel;
      return this;
    }

    /**
     * Sets the {@link Effect}s used.
     *
     * <p>The default value is an empty list.
     */
    @CanIgnoreReturnValue
    public Builder setEffects(List<Effect> effects) {
      this.effects = ImmutableList.copyOf(effects);
      return this;
    }

    /**
     * Sets the {@link Effect}s used.
     *
     * <p>The default value is an empty list.
     */
    @CanIgnoreReturnValue
    public Builder setEffects(Effect... effects) {
      this.effects = ImmutableList.copyOf(effects);
      return this;
    }

    /**
     * Sets the {@code pixelWidthHeightRatio}.
     *
     * <p>The default value is {@link #DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO}.
     */
    @CanIgnoreReturnValue
    public Builder setPixelWidthHeightRatio(float pixelWidthHeightRatio) {
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      return this;
    }

    /**
     * Sets the output {@link ColorInfo}.
     *
     * <p>The default value is {@link ColorInfo#SDR_BT709_LIMITED}.
     */
    @CanIgnoreReturnValue
    public Builder setOutputColorInfo(ColorInfo outputColorInfo) {
      this.outputColorInfo = outputColorInfo;
      return this;
    }

    /**
     * Sets the method to be called in {@link
     * VideoFrameProcessor.Listener#onOutputFrameAvailableForRendering}.
     *
     * <p>The method will be called on the thread the {@link VideoFrameProcessorTestRunner} is
     * created on.
     *
     * <p>The default value is a no-op.
     */
    @CanIgnoreReturnValue
    public Builder setOnOutputFrameAvailableForRenderingListener(
        OnOutputFrameAvailableForRenderingListener onOutputFrameAvailableListener) {
      this.onOutputFrameAvailableListener = onOutputFrameAvailableListener;
      return this;
    }

    /**
     * Sets the method to be called in {@link VideoFrameProcessor.Listener#onEnded}.
     *
     * <p>The default value is a no-op.
     */
    @CanIgnoreReturnValue
    public Builder setOnEndedListener(OnVideoFrameProcessingEndedListener onEndedListener) {
      this.onEndedListener = onEndedListener;
      return this;
    }

    public VideoFrameProcessorTestRunner build() throws VideoFrameProcessingException {
      checkStateNotNull(testId, "testId must be set.");
      checkStateNotNull(videoFrameProcessorFactory, "videoFrameProcessorFactory must be set.");

      return new VideoFrameProcessorTestRunner(
          testId,
          videoFrameProcessorFactory,
          bitmapReader == null ? new SurfaceBitmapReader() : bitmapReader,
          videoAssetPath,
          outputFileLabel == null ? "" : outputFileLabel,
          effects == null ? ImmutableList.of() : effects,
          pixelWidthHeightRatio,
          outputColorInfo == null ? ColorInfo.SDR_BT709_LIMITED : outputColorInfo,
          onOutputFrameAvailableListener,
          onEndedListener);
    }
  }

  /**
   * Time to wait for the decoded frame to populate the {@link VideoFrameProcessor} instance's input
   * surface and the {@link VideoFrameProcessor} to finish processing the frame, in milliseconds.
   */
  public static final int VIDEO_FRAME_PROCESSING_WAIT_MS = 5_000;

  private final String testId;
  private final @MonotonicNonNull String videoAssetPath;
  private final String outputFileLabel;
  private final float pixelWidthHeightRatio;
  private final ConditionVariable videoFrameProcessorReadyCondition;
  private final @MonotonicNonNull CountDownLatch videoFrameProcessingEndedLatch;
  private final AtomicReference<VideoFrameProcessingException> videoFrameProcessingException;
  private final VideoFrameProcessor videoFrameProcessor;
  private final ImmutableList<Effect> effects;
  private final @MonotonicNonNull BitmapReader bitmapReader;

  private VideoFrameProcessorTestRunner(
      String testId,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      BitmapReader bitmapReader,
      @Nullable String videoAssetPath,
      String outputFileLabel,
      ImmutableList<Effect> effects,
      float pixelWidthHeightRatio,
      ColorInfo outputColorInfo,
      OnOutputFrameAvailableForRenderingListener onOutputFrameAvailableForRenderingListener,
      OnVideoFrameProcessingEndedListener onEndedListener)
      throws VideoFrameProcessingException {
    this.testId = testId;
    this.bitmapReader = bitmapReader;
    this.videoAssetPath = videoAssetPath;
    this.outputFileLabel = outputFileLabel;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    videoFrameProcessorReadyCondition = new ConditionVariable();
    videoFrameProcessingEndedLatch = new CountDownLatch(1);
    videoFrameProcessingException = new AtomicReference<>();

    videoFrameProcessor =
        videoFrameProcessorFactory.create(
            getApplicationContext(),
            DebugViewProvider.NONE,
            outputColorInfo,
            /* renderFramesAutomatically= */ true,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  List<Effect> effects,
                  FrameInfo frameInfo) {
                videoFrameProcessorReadyCondition.open();
              }

              @Override
              public void onOutputSizeChanged(int width, int height) {
                boolean useHighPrecisionColorComponents = ColorInfo.isTransferHdr(outputColorInfo);
                @Nullable
                Surface outputSurface =
                    bitmapReader.getSurface(width, height, useHighPrecisionColorComponents);
                if (outputSurface != null) {
                  checkNotNull(videoFrameProcessor)
                      .setOutputSurfaceInfo(new SurfaceInfo(outputSurface, width, height));
                }
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                // Do nothing as frames are rendered automatically.
                onOutputFrameAvailableForRenderingListener.onFrameAvailableForRendering(
                    presentationTimeUs);
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
                checkNotNull(videoFrameProcessingEndedLatch).countDown();
              }

              @Override
              public void onEnded() {
                checkNotNull(videoFrameProcessingEndedLatch).countDown();
                onEndedListener.onEnded();
              }
            });
    this.effects = effects;
  }

  public void processFirstFrameAndEnd() throws Exception {
    DecodeOneFrameUtil.decodeOneAssetFileFrame(
        checkNotNull(videoAssetPath),
        new DecodeOneFrameUtil.Listener() {
          @Override
          public void onContainerExtracted(MediaFormat mediaFormat) {
            videoFrameProcessorReadyCondition.close();
            @Nullable ColorInfo colorInfo = MediaFormatUtil.getColorInfo(mediaFormat);
            videoFrameProcessor.registerInputStream(
                INPUT_TYPE_SURFACE,
                effects,
                new FrameInfo.Builder(
                        colorInfo == null ? ColorInfo.SDR_BT709_LIMITED : colorInfo,
                        mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
                        mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
                    .setPixelWidthHeightRatio(pixelWidthHeightRatio)
                    .build());
            try {
              videoFrameProcessorReadyCondition.block();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            checkState(videoFrameProcessor.registerInputFrame());
          }

          @Override
          public void onFrameDecoded(MediaFormat mediaFormat) {
            // Do nothing.
          }
        },
        videoFrameProcessor.getInputSurface());
    endFrameProcessing();
  }

  public void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, long offsetToAddUs, float frameRate)
      throws InterruptedException {
    queueInputBitmap(inputBitmap, durationUs, offsetToAddUs, frameRate, ColorInfo.SRGB_BT709_FULL);
  }

  public void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, long offsetToAddUs, float frameRate, ColorInfo colorInfo)
      throws InterruptedException {
    videoFrameProcessorReadyCondition.close();
    videoFrameProcessor.registerInputStream(
        INPUT_TYPE_BITMAP,
        effects,
        new FrameInfo.Builder(colorInfo, inputBitmap.getWidth(), inputBitmap.getHeight())
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .setOffsetToAddUs(offsetToAddUs)
            .build());
    videoFrameProcessorReadyCondition.block();
    checkState(
        videoFrameProcessor.queueInputBitmap(
            inputBitmap, new ConstantRateTimestampIterator(durationUs, frameRate)));
  }

  public void queueInputBitmaps(int width, int height, Pair<Bitmap, TimestampIterator>... frames)
      throws InterruptedException {
    queueInputBitmaps(width, height, ColorInfo.SRGB_BT709_FULL, frames);
  }

  public void queueInputBitmaps(
      int width, int height, ColorInfo colorInfo, Pair<Bitmap, TimestampIterator>... frames)
      throws InterruptedException {
    videoFrameProcessorReadyCondition.close();
    videoFrameProcessor.registerInputStream(
        INPUT_TYPE_BITMAP,
        effects,
        new FrameInfo.Builder(colorInfo, width, height)
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .build());
    videoFrameProcessorReadyCondition.block();
    for (Pair<Bitmap, TimestampIterator> frame : frames) {
      videoFrameProcessor.queueInputBitmap(frame.first, frame.second);
    }
  }

  public void queueInputTexture(GlTextureInfo inputTexture, long pts, ColorInfo colorInfo)
      throws InterruptedException {
    videoFrameProcessor.registerInputStream(
        INPUT_TYPE_TEXTURE_ID,
        effects,
        new FrameInfo.Builder(colorInfo, inputTexture.width, inputTexture.height)
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .build());
    videoFrameProcessor.setOnInputFrameProcessedListener(
        (texId, syncObject) -> {
          try {
            GlUtil.deleteTexture(texId);
            GlUtil.deleteSyncObject(syncObject);
          } catch (GlUtil.GlException e) {
            throw new VideoFrameProcessingException(e);
          }
        });
    videoFrameProcessorReadyCondition.block();
    checkState(videoFrameProcessor.queueInputTexture(inputTexture.texId, pts));
  }

  /** {@link #endFrameProcessing(long)} with {@link #VIDEO_FRAME_PROCESSING_WAIT_MS} applied. */
  public void endFrameProcessing() {
    endFrameProcessing(VIDEO_FRAME_PROCESSING_WAIT_MS);
  }

  /**
   * Ends {@link VideoFrameProcessor} frame processing.
   *
   * <p>Waits for frame processing to end, for {@code videoFrameProcessingWaitTimeMs}.
   */
  public void endFrameProcessing(long videoFrameProcessingWaitTimeMs) {
    signalEndOfInput();
    awaitFrameProcessingEnd(videoFrameProcessingWaitTimeMs);
  }

  /**
   * Calls {@link VideoFrameProcessor#signalEndOfInput}.
   *
   * <p>Calling this and {@link #awaitFrameProcessingEnd} is an alternative to {@link
   * #endFrameProcessing}.
   */
  public void signalEndOfInput() {
    videoFrameProcessor.signalEndOfInput();
  }

  /** Calls {@link VideoFrameProcessor#flush}. */
  public void flush() {
    videoFrameProcessor.flush();
  }

  /** After {@link #signalEndOfInput}, is called, wait for this instance to end. */
  public void awaitFrameProcessingEnd(long videoFrameProcessingWaitTimeMs) {
    @Nullable Exception endFrameProcessingException = null;
    try {
      if (!checkNotNull(videoFrameProcessingEndedLatch)
          .await(videoFrameProcessingWaitTimeMs, MILLISECONDS)) {
        endFrameProcessingException =
            new IllegalStateException("Video frame processing timed out.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      endFrameProcessingException = e;
    }
    assertThat(videoFrameProcessingException.get()).isNull();
    assertThat(endFrameProcessingException).isNull();
  }

  /**
   * Returns the {@link Bitmap} from the provided {@link BitmapReader}.
   *
   * <p>Also saves the bitmap to the cache directory.
   */
  public Bitmap getOutputBitmap() {
    Bitmap outputBitmap = checkNotNull(bitmapReader).getBitmap();
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ outputFileLabel, outputBitmap, /* path= */ null);
    return outputBitmap;
  }

  public void release() {
    if (videoFrameProcessor != null) {
      videoFrameProcessor.release();
    }
  }

  public interface OnOutputFrameAvailableForRenderingListener {
    void onFrameAvailableForRendering(long presentationTimeUs);
  }

  public interface OnVideoFrameProcessingEndedListener {
    void onEnded();
  }

  /** Reads a {@link Bitmap} from {@link VideoFrameProcessor} output. */
  public interface BitmapReader {

    /** Returns the {@link VideoFrameProcessor} output {@link Surface}, if one is needed. */
    @Nullable
    Surface getSurface(int width, int height, boolean useHighPrecisionColorComponents);

    /** Returns the output {@link Bitmap}. */
    Bitmap getBitmap();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reads from a {@link Surface}. Only supports SDR input.
   */
  public static final class SurfaceBitmapReader
      implements VideoFrameProcessorTestRunner.BitmapReader {

    // ImageReader only supports SDR input.
    private @MonotonicNonNull ImageReader imageReader;

    @Override
    @SuppressLint("WrongConstant")
    @Nullable
    public Surface getSurface(int width, int height, boolean useHighPrecisionColorComponents) {
      imageReader =
          ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, /* maxImages= */ 1);
      return imageReader.getSurface();
    }

    @Override
    public Bitmap getBitmap() {
      Image outputImage = checkNotNull(imageReader).acquireLatestImage();
      Bitmap outputBitmap = createArgb8888BitmapFromRgba8888Image(outputImage);
      outputImage.close();
      return outputBitmap;
    }
  }

  public static TimestampIterator createTimestampIterator(List<Long> elements) {

    Iterator<Long> elementsIterator = elements.iterator();

    return new TimestampIterator() {
      @Override
      public boolean hasNext() {
        return elementsIterator.hasNext();
      }

      @Override
      public long next() {
        return elementsIterator.next();
      }

      @Override
      public TimestampIterator copyOf() {
        // Method not needed for effects tests.
        throw new UnsupportedOperationException();
      }
    };
  }
}

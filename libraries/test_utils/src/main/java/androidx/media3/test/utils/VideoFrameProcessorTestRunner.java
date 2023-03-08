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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
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
    private @MonotonicNonNull String videoAssetPath;
    private @MonotonicNonNull String outputFileLabel;
    private @MonotonicNonNull ImmutableList<Effect> effects;
    private float pixelWidthHeightRatio;
    private @MonotonicNonNull ColorInfo inputColorInfo;
    private @MonotonicNonNull ColorInfo outputColorInfo;
    private boolean isInputTextureExternal;
    private OnOutputFrameAvailableListener onOutputFrameAvailableListener;

    /** Creates a new instance with default values. */
    public Builder() {
      pixelWidthHeightRatio = DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO;
      isInputTextureExternal = true;
      onOutputFrameAvailableListener = unused -> {};
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
     * Sets the input {@link ColorInfo}.
     *
     * <p>The default value is {@link ColorInfo#SDR_BT709_LIMITED}.
     */
    @CanIgnoreReturnValue
    public Builder setInputColorInfo(ColorInfo inputColorInfo) {
      this.inputColorInfo = inputColorInfo;
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
     * Sets whether input comes from an external texture. See {@link
     * VideoFrameProcessor.Factory#create}.
     *
     * <p>The default value is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder setIsInputTextureExternal(boolean isInputTextureExternal) {
      this.isInputTextureExternal = isInputTextureExternal;
      return this;
    }

    /**
     * Sets the method to be called in {@link VideoFrameProcessor.Listener#onOutputFrameAvailable}.
     *
     * <p>The default value is a no-op.
     */
    @CanIgnoreReturnValue
    public Builder setOnOutputFrameAvailableListener(
        OnOutputFrameAvailableListener onOutputFrameAvailableListener) {
      this.onOutputFrameAvailableListener = onOutputFrameAvailableListener;
      return this;
    }

    public VideoFrameProcessorTestRunner build() throws VideoFrameProcessingException {
      checkStateNotNull(testId, "testId must be set.");
      checkStateNotNull(videoFrameProcessorFactory, "videoFrameProcessorFactory must be set.");

      return new VideoFrameProcessorTestRunner(
          testId,
          videoFrameProcessorFactory,
          videoAssetPath,
          outputFileLabel == null ? "" : outputFileLabel,
          effects == null ? ImmutableList.of() : effects,
          pixelWidthHeightRatio,
          inputColorInfo == null ? ColorInfo.SDR_BT709_LIMITED : inputColorInfo,
          outputColorInfo == null ? ColorInfo.SDR_BT709_LIMITED : outputColorInfo,
          isInputTextureExternal,
          onOutputFrameAvailableListener);
    }
  }

  /**
   * Time to wait for the decoded frame to populate the {@link VideoFrameProcessor} instance's input
   * surface and the {@link VideoFrameProcessor} to finish processing the frame, in milliseconds.
   */
  private static final int VIDEO_FRAME_PROCESSING_WAIT_MS = 5000;

  private final String testId;
  private final @MonotonicNonNull String videoAssetPath;
  private final String outputFileLabel;
  private final float pixelWidthHeightRatio;
  private final AtomicReference<VideoFrameProcessingException> videoFrameProcessingException;

  private final VideoFrameProcessor videoFrameProcessor;

  private volatile @MonotonicNonNull ImageReader outputImageReader;
  private volatile boolean videoFrameProcessingEnded;

  private VideoFrameProcessorTestRunner(
      String testId,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      @Nullable String videoAssetPath,
      String outputFileLabel,
      ImmutableList<Effect> effects,
      float pixelWidthHeightRatio,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      boolean isInputTextureExternal,
      OnOutputFrameAvailableListener onOutputFrameAvailableListener)
      throws VideoFrameProcessingException {
    this.testId = testId;
    this.videoAssetPath = videoAssetPath;
    this.outputFileLabel = outputFileLabel;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    videoFrameProcessingException = new AtomicReference<>();

    videoFrameProcessor =
        videoFrameProcessorFactory.create(
            getApplicationContext(),
            effects,
            DebugViewProvider.NONE,
            inputColorInfo,
            outputColorInfo,
            isInputTextureExternal,
            /* releaseFramesAutomatically= */ true,
            MoreExecutors.directExecutor(),
            new VideoFrameProcessor.Listener() {
              @Override
              public void onOutputSizeChanged(int width, int height) {
                outputImageReader =
                    ImageReader.newInstance(
                        width, height, PixelFormat.RGBA_8888, /* maxImages= */ 1);
                checkNotNull(videoFrameProcessor)
                    .setOutputSurfaceInfo(
                        new SurfaceInfo(outputImageReader.getSurface(), width, height));
              }

              @Override
              public void onOutputFrameAvailable(long presentationTimeUs) {
                // Do nothing as frames are released automatically.
                onOutputFrameAvailableListener.onFrameAvailable(presentationTimeUs);
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }

              @Override
              public void onEnded() {
                videoFrameProcessingEnded = true;
              }
            });
  }

  public Bitmap processFirstFrameAndEnd() throws Exception {
    DecodeOneFrameUtil.decodeOneAssetFileFrame(
        checkNotNull(videoAssetPath),
        new DecodeOneFrameUtil.Listener() {
          @Override
          public void onContainerExtracted(MediaFormat mediaFormat) {
            videoFrameProcessor.setInputFrameInfo(
                new FrameInfo.Builder(
                        mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
                        mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
                    .setPixelWidthHeightRatio(pixelWidthHeightRatio)
                    .build());
            videoFrameProcessor.registerInputFrame();
          }

          @Override
          public void onFrameDecoded(MediaFormat mediaFormat) {
            // Do nothing.
          }
        },
        videoFrameProcessor.getInputSurface());
    return endFrameProcessingAndGetImage();
  }

  public void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate) {
    videoFrameProcessor.setInputFrameInfo(
        new FrameInfo.Builder(inputBitmap.getWidth(), inputBitmap.getHeight())
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .build());
    videoFrameProcessor.queueInputBitmap(inputBitmap, durationUs, frameRate);
  }

  public Bitmap endFrameProcessingAndGetImage() throws Exception {
    videoFrameProcessor.signalEndOfInput();
    Thread.sleep(VIDEO_FRAME_PROCESSING_WAIT_MS);

    assertThat(videoFrameProcessingEnded).isTrue();
    assertThat(videoFrameProcessingException.get()).isNull();

    Image videoFrameProcessorOutputImage = checkNotNull(outputImageReader).acquireLatestImage();
    Bitmap actualBitmap = createArgb8888BitmapFromRgba8888Image(videoFrameProcessorOutputImage);
    videoFrameProcessorOutputImage.close();
    maybeSaveTestBitmap(testId, /* bitmapLabel= */ outputFileLabel, actualBitmap, /* path= */ null);
    return actualBitmap;
  }

  public void release() {
    if (videoFrameProcessor != null) {
      videoFrameProcessor.release();
    }
  }

  public interface OnOutputFrameAvailableListener {
    void onFrameAvailable(long presentationTimeUs);
  }
}

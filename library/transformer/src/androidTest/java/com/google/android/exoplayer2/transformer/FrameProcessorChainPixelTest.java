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
package com.google.android.exoplayer2.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for frame processing via {@link FrameProcessorChain}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameProcessorChainPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/original.png";
  public static final String SCALE_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/scale_wide.png";
  public static final String TRANSLATE_RIGHT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/translate_right.png";
  public static final String ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/rotate_then_translate.png";
  public static final String TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/translate_then_rotate.png";
  public static final String REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/request_output_height.png";
  public static final String ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/rotate_45_scale_to_fit.png";

  /** Input video of which we only use the first frame. */
  private static final String INPUT_MP4_ASSET_STRING = "media/mp4/sample.mp4";
  /** Timeout for dequeueing buffers from the codec, in microseconds. */
  private static final int DEQUEUE_TIMEOUT_US = 5_000_000;
  /**
   * Time to wait for the decoded frame to populate the {@link FrameProcessorChain}'s input surface
   * and the {@link FrameProcessorChain} to finish processing the frame, in milliseconds.
   */
  private static final int FRAME_PROCESSING_WAIT_MS = 5000;
  /** The ratio of width over height, for each pixel in a frame. */
  private static final float DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO = 1;

  private final AtomicReference<FrameProcessingException> frameProcessingException =
      new AtomicReference<>();

  private @MonotonicNonNull FrameProcessorChain frameProcessorChain;
  private @MonotonicNonNull ImageReader outputImageReader;
  private @MonotonicNonNull MediaFormat mediaFormat;

  @After
  public void release() {
    if (frameProcessorChain != null) {
      frameProcessorChain.release();
    }
  }

  @Test
  public void processData_noEdits_producesExpectedOutput() throws Exception {
    String testId = "processData_noEdits";
    setUpAndPrepareFirstFrame(DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPixelWidthHeightRatio_producesExpectedOutput() throws Exception {
    String testId = "processData_withPixelWidthHeightRatio";
    setUpAndPrepareFirstFrame(/* pixelWidthHeightRatio= */ 2f);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(SCALE_WIDE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withMatrixTransformation_translateRight_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withMatrixTransformation_translateRight";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        (MatrixTransformation) (long presentationTimeNs) -> translateRightMatrix);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(TRANSLATE_RIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withMatrixAndScaleToFitTransformation_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withMatrixAndScaleToFitTransformation";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix,
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitAndMatrixTransformation_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitAndMatrixTransformation";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build(),
        (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPresentation_setResolution_producesExpectedOutput() throws Exception {
    String testId = "processData_withPresentation_setResolution";
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO, new Presentation.Builder().setResolution(480).build());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitTransformation_rotate45_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitTransformation_rotate45";
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void
      processData_withManyComposedMatrixTransformations_producesSameOutputAsCombinedTransformation()
          throws Exception {
    String testId =
        "processData_withManyComposedMatrixTransformations_producesSameOutputAsCombinedTransformation";
    Presentation centerCrop =
        new Presentation.Builder()
            .setCrop(/* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f)
            .build();
    ImmutableList.Builder<GlEffect> full10StepRotationAndCenterCrop = new ImmutableList.Builder<>();
    for (int i = 0; i < 10; i++) {
      full10StepRotationAndCenterCrop.add(new Rotation(/* degrees= */ 36));
    }
    full10StepRotationAndCenterCrop.add(centerCrop);

    setUpAndPrepareFirstFrame(DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO, centerCrop);
    Bitmap centerCropResultBitmap = processFirstFrameAndEnd();
    setUpAndPrepareFirstFrame(
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO, full10StepRotationAndCenterCrop.build());
    Bitmap fullRotationAndCenterCropResultBitmap = processFirstFrameAndEnd();

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "centerCrop", centerCropResultBitmap);
    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId,
        /* bitmapLabel= */ "full10StepRotationAndCenterCrop",
        fullRotationAndCenterCropResultBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            centerCropResultBitmap, fullRotationAndCenterCropResultBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  /**
   * Set up and prepare the first frame from an input video, as well as relevant test
   * infrastructure. The frame will be sent towards the {@link FrameProcessorChain}, and may be
   * accessed on the {@link FrameProcessorChain}'s output {@code outputImageReader}.
   *
   * @param pixelWidthHeightRatio The ratio of width over height for each pixel.
   * @param effects The {@link GlEffect GlEffects} to apply to the input frame.
   */
  private void setUpAndPrepareFirstFrame(float pixelWidthHeightRatio, GlEffect... effects)
      throws Exception {
    setUpAndPrepareFirstFrame(pixelWidthHeightRatio, asList(effects));
  }

  private void setUpAndPrepareFirstFrame(float pixelWidthHeightRatio, List<GlEffect> effects)
      throws Exception {
    // Set up the extractor to read the first video frame and get its format.
    MediaExtractor mediaExtractor = new MediaExtractor();
    @Nullable MediaCodec mediaCodec = null;
    Context context = getApplicationContext();
    try (AssetFileDescriptor afd = context.getAssets().openFd(INPUT_MP4_ASSET_STRING)) {
      mediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
        if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
          mediaFormat = mediaExtractor.getTrackFormat(i);
          mediaExtractor.selectTrack(i);
          break;
        }
      }

      int inputWidth = checkNotNull(mediaFormat).getInteger(MediaFormat.KEY_WIDTH);
      int inputHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
      frameProcessorChain =
          FrameProcessorChain.create(
              context,
              /* listener= */ this.frameProcessingException::set,
              pixelWidthHeightRatio,
              inputWidth,
              inputHeight,
              /* streamOffsetUs= */ 0L,
              effects,
              /* enableExperimentalHdrEditing= */ false);
      Size outputSize = frameProcessorChain.getOutputSize();
      outputImageReader =
          ImageReader.newInstance(
              outputSize.getWidth(),
              outputSize.getHeight(),
              PixelFormat.RGBA_8888,
              /* maxImages= */ 1);
      frameProcessorChain.setOutputSurface(
          outputImageReader.getSurface(),
          outputSize.getWidth(),
          outputSize.getHeight(),
          /* debugSurfaceView= */ null);
      frameProcessorChain.registerInputFrame();

      // Queue the first video frame from the extractor.
      String mimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
      mediaCodec = MediaCodec.createDecoderByType(mimeType);
      mediaCodec.configure(
          mediaFormat, frameProcessorChain.getInputSurface(), /* crypto= */ null, /* flags= */ 0);
      mediaCodec.start();
      int inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
      assertThat(inputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      ByteBuffer inputBuffer = checkNotNull(mediaCodec.getInputBuffers()[inputBufferIndex]);
      int sampleSize = mediaExtractor.readSampleData(inputBuffer, /* offset= */ 0);
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          sampleSize,
          mediaExtractor.getSampleTime(),
          mediaExtractor.getSampleFlags());

      // Queue an end-of-stream buffer to force the codec to produce output.
      inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
      assertThat(inputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          /* size= */ 0,
          /* presentationTimeUs= */ 0,
          MediaCodec.BUFFER_FLAG_END_OF_STREAM);

      // Dequeue and render the output video frame.
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      int outputBufferIndex;
      do {
        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
        assertThat(outputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      } while (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
          || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
      mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ true);
    } finally {
      mediaExtractor.release();
      if (mediaCodec != null) {
        mediaCodec.release();
      }
    }
  }

  private Bitmap processFirstFrameAndEnd() throws InterruptedException {
    checkNotNull(frameProcessorChain).signalEndOfInputStream();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);
    assertThat(frameProcessorChain.isEnded()).isTrue();
    assertThat(frameProcessingException.get()).isNull();

    Image frameProcessorChainOutputImage = checkNotNull(outputImageReader).acquireLatestImage();
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromRgba8888Image(frameProcessorChainOutputImage);
    frameProcessorChainOutputImage.close();
    return actualBitmap;
  }

  /**
   * Specifies a counter-clockwise rotation while accounting for the aspect ratio difference between
   * the input frame in pixel coordinates and NDC.
   *
   * <p>Unlike {@link ScaleToFitTransformation}, this does not adjust the output size or scale to
   * preserve input pixels. Pixels rotated out of the frame are clipped.
   */
  private static final class Rotation implements MatrixTransformation {

    private final float degrees;
    private @MonotonicNonNull Matrix adjustedTransformationMatrix;

    public Rotation(float degrees) {
      this.degrees = degrees;
    }

    @Override
    public Size configure(int inputWidth, int inputHeight) {
      adjustedTransformationMatrix = new Matrix();
      adjustedTransformationMatrix.postRotate(degrees);
      float inputAspectRatio = (float) inputWidth / inputHeight;
      adjustedTransformationMatrix.preScale(/* sx= */ inputAspectRatio, /* sy= */ 1f);
      adjustedTransformationMatrix.postScale(/* sx= */ 1f / inputAspectRatio, /* sy= */ 1f);

      return new Size(inputWidth, inputHeight);
    }

    @Override
    public Matrix getMatrix(long presentationTimeUs) {
      return checkStateNotNull(adjustedTransformationMatrix);
    }
  }
}

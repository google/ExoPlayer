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
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.FIRST_FRAME_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.REQUEST_OUTPUT_HEIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.ROTATE45_SCALE_TO_FIT_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.ROTATE_THEN_TRANSLATE_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.transformer.BitmapTestUtil.TRANSLATE_THEN_ROTATE_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
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
import java.nio.ByteBuffer;
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
  private static final float PIXEL_WIDTH_HEIGHT_RATIO = 1;

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
    setUpAndPrepareFirstFrame();
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withAdvancedFrameProcessor_translateRight_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withAdvancedFrameProcessor_translateRight";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    GlFrameProcessor glFrameProcessor =
        new AdvancedFrameProcessor(getApplicationContext(), translateRightMatrix);
    setUpAndPrepareFirstFrame(glFrameProcessor);
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withAdvancedAndScaleToFitFrameProcessors_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withAdvancedAndScaleToFitFrameProcessors";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    GlFrameProcessor translateRightFrameProcessor =
        new AdvancedFrameProcessor(getApplicationContext(), translateRightMatrix);
    GlFrameProcessor rotate45FrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setRotationDegrees(45)
            .build();
    setUpAndPrepareFirstFrame(translateRightFrameProcessor, rotate45FrameProcessor);
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(TRANSLATE_THEN_ROTATE_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitAndAdvancedFrameProcessors_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitAndAdvancedFrameProcessors";
    GlFrameProcessor rotate45FrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setRotationDegrees(45)
            .build();
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    GlFrameProcessor translateRightFrameProcessor =
        new AdvancedFrameProcessor(getApplicationContext(), translateRightMatrix);
    setUpAndPrepareFirstFrame(rotate45FrameProcessor, translateRightFrameProcessor);
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ROTATE_THEN_TRANSLATE_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void
      processData_withPresentationFrameProcessor_requestOutputHeight_producesExpectedOutput()
          throws Exception {
    String testId = "processData_withPresentationFrameProcessor_requestOutputHeight";
    GlFrameProcessor glFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).setResolution(480).build();
    setUpAndPrepareFirstFrame(glFrameProcessor);
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(REQUEST_OUTPUT_HEIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitFrameProcessor_rotate45_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitFrameProcessor_rotate45";
    GlFrameProcessor glFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setRotationDegrees(45)
            .build();
    setUpAndPrepareFirstFrame(glFrameProcessor);
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ROTATE45_SCALE_TO_FIT_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  /**
   * Set up and prepare the first frame from an input video, as well as relevant test
   * infrastructure. The frame will be sent towards the {@link FrameProcessorChain}, and may be
   * accessed on the {@link FrameProcessorChain}'s output {@code outputImageReader}.
   *
   * @param frameProcessors The {@link GlFrameProcessor GlFrameProcessors} that will apply changes
   *     to the input frame.
   */
  private void setUpAndPrepareFirstFrame(GlFrameProcessor... frameProcessors) throws Exception {
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
              PIXEL_WIDTH_HEIGHT_RATIO,
              inputWidth,
              inputHeight,
              asList(frameProcessors),
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

  private Bitmap processFirstFrameAndEnd() throws InterruptedException, TransformationException {
    checkNotNull(frameProcessorChain).signalEndOfInputStream();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);
    assertThat(frameProcessorChain.isEnded()).isTrue();
    frameProcessorChain.getAndRethrowBackgroundExceptions();

    Image frameProcessorChainOutputImage = checkNotNull(outputImageReader).acquireLatestImage();
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromRgba8888Image(frameProcessorChainOutputImage);
    frameProcessorChainOutputImage.close();
    return actualBitmap;
  }
}

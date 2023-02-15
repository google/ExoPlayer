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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.effect.OverlayShaderProgramPixelTest.OVERLAY_PNG_ASSET_PATH;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.FrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for frame processing via {@link GlEffectsFrameProcessor}.
 *
 * <p>Uses a {@link GlEffectsFrameProcessor} to process one frame, and checks that the actual output
 * matches expected output, either from a golden file or from another edit.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps.
 */
@RunWith(AndroidJUnit4.class)
public final class GlEffectsFrameProcessorPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  public static final String WRAPPED_CROP_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/image_input_with_wrapped_crop.png";
  public static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";
  public static final String SCALE_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/scale_wide.png";
  public static final String TRANSLATE_RIGHT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/translate_right.png";
  public static final String ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate_then_translate.png";
  public static final String ROTATE_THEN_SCALE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate45_then_scale2w.png";
  public static final String TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/translate_then_rotate.png";
  public static final String REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/request_output_height.png";
  public static final String CROP_THEN_ASPECT_RATIO_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/crop_then_aspect_ratio.png";
  public static final String ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate_45_scale_to_fit.png";
  public static final String INCREASE_BRIGHTNESS_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/increase_brightness.png";
  public static final String GRAYSCALE_THEN_INCREASE_RED_CHANNEL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/grayscale_then_increase_red_channel.png";

  /** Input video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";

  private @MonotonicNonNull FrameProcessorTestRunner frameProcessorTestRunner;

  @After
  public void release() {
    checkNotNull(frameProcessorTestRunner).release();
  }

  @Test
  public void noEffects_matchesGoldenFile() throws Exception {
    String testId = "noEffects_matchesGoldenFile";
    frameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void noEffects_withImageInput_matchesGoldenFile() throws Exception {
    String testId = "noEffects_withImageInput_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId).setIsInputTextureExternal(false).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processImageFrameAndEnd(expectedBitmap);

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void wrappedCrop_withImageInput_matchesGoldenFile() throws Exception {
    String testId = "wrappedCrop_withImageInput_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setIsInputTextureExternal(false)
            .setEffects(
                new GlEffectWrapper(
                    new Crop(
                        /* left= */ -0.5f,
                        /* right= */ 0.5f,
                        /* bottom= */ -0.5f,
                        /* top= */ 0.5f)))
            .build();
    Bitmap originalBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(WRAPPED_CROP_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processImageFrameAndEnd(originalBitmap);

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
  // TODO(b/262693274): Once texture deletion is added to InternalTextureManager.java, add a test
  //  queuing multiple input bitmaps to ensure the operation successfully completes and that the
  //  correct number of frames has been queued.

  @Test
  public void noEffects_withFrameCache_matchesGoldenFile() throws Exception {
    String testId = "noEffects_withFrameCache_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new FrameCache(/* capacity= */ 5))
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void setPixelWidthHeightRatio_matchesGoldenFile() throws Exception {
    String testId = "setPixelWidthHeightRatio_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId).setPixelWidthHeightRatio(2f).build();
    Bitmap expectedBitmap = readBitmap(SCALE_WIDE_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void matrixTransformation_matchesGoldenFile() throws Exception {
    String testId = "matrixTransformation_matchesGoldenFile";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects((MatrixTransformation) (long presentationTimeNs) -> translateRightMatrix)
            .build();
    Bitmap expectedBitmap = readBitmap(TRANSLATE_RIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void matrixAndScaleToFitTransformation_matchesGoldenFile() throws Exception {
    String testId = "matrixAndScaleToFitTransformation_matchesGoldenFile";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(
                (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix,
                new ScaleToFitTransformation.Builder().setRotationDegrees(45).build())
            .build();
    Bitmap expectedBitmap = readBitmap(TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void bitmapOverlay_matchesGoldenFile() throws Exception {
    String testId = "bitmapOverlay_matchesGoldenFile";
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .build();
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void scaleToFitAndMatrixTransformation_matchesGoldenFile() throws Exception {
    String testId = "scaleToFitAndMatrixTransformation_matchesGoldenFile";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(
                new ScaleToFitTransformation.Builder().setRotationDegrees(45).build(),
                (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix)
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void presentation_createForHeight_matchesGoldenFile() throws Exception {
    String testId = "presentation_createForHeight_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(Presentation.createForHeight(480))
            .build();
    Bitmap expectedBitmap = readBitmap(REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void cropThenPresentation_matchesGoldenFile() throws Exception {
    String testId = "cropThenPresentation_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(
                new Crop(
                    /* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f),
                Presentation.createForAspectRatio(
                    /* aspectRatio= */ 0.5f, Presentation.LAYOUT_SCALE_TO_FIT))
            .build();
    Bitmap expectedBitmap = readBitmap(CROP_THEN_ASPECT_RATIO_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void scaleToFitTransformation_rotate45_matchesGoldenFile() throws Exception {
    String testId = "scaleToFitTransformation_rotate45_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new ScaleToFitTransformation.Builder().setRotationDegrees(45).build())
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void twoWrappedScaleToFitTransformations_matchesGoldenFile() throws Exception {
    String testId = "twoWrappedScaleToFitTransformations_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(
                new GlEffectWrapper(
                    new ScaleToFitTransformation.Builder().setRotationDegrees(45).build()),
                new GlEffectWrapper(
                    new ScaleToFitTransformation.Builder()
                        .setScale(/* scaleX= */ 2, /* scaleY= */ 1)
                        .build()))
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_SCALE_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void manyComposedMatrixEffects_matchesSingleEffect() throws Exception {
    String testId = "manyComposedMatrixEffects_matchesSingleEffect";
    Crop centerCrop =
        new Crop(/* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f);
    ImmutableList.Builder<Effect> full10StepRotationAndCenterCrop = new ImmutableList.Builder<>();
    for (int i = 0; i < 10; i++) {
      full10StepRotationAndCenterCrop.add(new Rotation(/* degrees= */ 36));
    }
    full10StepRotationAndCenterCrop.add(centerCrop);

    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(centerCrop)
            .build();
    Bitmap centerCropResultBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();
    frameProcessorTestRunner.release();
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("full10StepRotationAndCenterCrop")
            .setEffects(full10StepRotationAndCenterCrop.build())
            .build();
    Bitmap fullRotationAndCenterCropResultBitmap =
        frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropResultBitmap, fullRotationAndCenterCropResultBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void increaseBrightness_matchesGoldenFile() throws Exception {
    String testId = "increaseBrightness_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId).setEffects(new Brightness(0.5f)).build();
    Bitmap expectedBitmap = readBitmap(INCREASE_BRIGHTNESS_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void manyComposedMatrixAndRgbEffects_producesSameOutputAsCombinedEffects()
      throws Exception {
    String testId = "manyComposedMatrixAndRgbEffects_producesSameOutputAsCombinedEffects";
    Crop centerCrop =
        new Crop(/* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f);
    ImmutableList<Effect> increaseBrightnessFullRotationCenterCrop =
        ImmutableList.of(
            new Rotation(/* degrees= */ 90),
            new RgbAdjustment.Builder().setRedScale(5).build(),
            new RgbAdjustment.Builder().setGreenScale(5).build(),
            new Rotation(/* degrees= */ 90),
            new Rotation(/* degrees= */ 90),
            new RgbAdjustment.Builder().setBlueScale(5).build(),
            new Rotation(/* degrees= */ 90),
            centerCrop);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(
                new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
                centerCrop)
            .build();
    Bitmap centerCropAndBrightnessIncreaseResultBitmap =
        frameProcessorTestRunner.processFirstFrameAndEnd();

    frameProcessorTestRunner.release();
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("full4StepRotationBrightnessIncreaseAndCenterCrop")
            .setEffects(increaseBrightnessFullRotationCenterCrop)
            .build();
    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap =
        frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropAndBrightnessIncreaseResultBitmap,
            fullRotationBrightnessIncreaseAndCenterCropResultBitmap,
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void manyComposedMatrixAndRgbEffects_withFrameCache_producesSameOutputAsCombinedEffects()
      throws Exception {
    String testId =
        "manyComposedMatrixAndRgbEffects_withFrameCache_producesSameOutputAsCombinedEffects";
    Crop centerCrop =
        new Crop(/* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f);
    ImmutableList<Effect> increaseBrightnessFullRotationCenterCrop =
        ImmutableList.of(
            new Rotation(/* degrees= */ 90),
            new RgbAdjustment.Builder().setRedScale(5).build(),
            new RgbAdjustment.Builder().setGreenScale(5).build(),
            new Rotation(/* degrees= */ 90),
            new Rotation(/* degrees= */ 90),
            new RgbAdjustment.Builder().setBlueScale(5).build(),
            new FrameCache(/* capacity= */ 2),
            new Rotation(/* degrees= */ 90),
            new FrameCache(/* capacity= */ 2),
            centerCrop);
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(
                new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
                centerCrop)
            .build();
    Bitmap centerCropAndBrightnessIncreaseResultBitmap =
        frameProcessorTestRunner.processFirstFrameAndEnd();
    frameProcessorTestRunner.release();
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputFileLabel("full4StepRotationBrightnessIncreaseAndCenterCrop")
            .setEffects(increaseBrightnessFullRotationCenterCrop)
            .build();

    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap =
        frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropAndBrightnessIncreaseResultBitmap,
            fullRotationBrightnessIncreaseAndCenterCropResultBitmap,
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void grayscaleThenIncreaseRedChannel_matchesGoldenFile() throws Exception {
    String testId = "grayscaleThenIncreaseRedChannel_matchesGoldenFile";
    frameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(
                RgbFilter.createGrayscaleFilter(),
                new RgbAdjustment.Builder().setRedScale(3).build())
            .build();
    Bitmap expectedBitmap = readBitmap(GRAYSCALE_THEN_INCREASE_RED_CHANNEL_PNG_ASSET_PATH);

    Bitmap actualBitmap = frameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  // TODO(b/227624622): Add a test for HDR input after BitmapPixelTestUtil can read HDR bitmaps,
  //  using GlEffectWrapper to ensure usage of intermediate textures.

  private FrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    return new FrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setFrameProcessorFactory(new GlEffectsFrameProcessor.Factory())
        .setVideoAssetPath(INPUT_SDR_MP4_ASSET_STRING);
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

  /**
   * Wraps a {@link GlEffect} to prevent the {@link GlEffectsFrameProcessor} from detecting its
   * class and optimizing it.
   *
   * <p>This ensures that {@link GlEffectsFrameProcessor} uses a separate {@link GlShaderProgram}
   * for the wrapped {@link GlEffect} rather than merging it with preceding or subsequent {@link
   * GlEffect} instances and applying them in one combined {@link GlShaderProgram}.
   */
  private static final class GlEffectWrapper implements GlEffect {

    private final GlEffect effect;

    public GlEffectWrapper(GlEffect effect) {
      this.effect = effect;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
        throws FrameProcessingException {
      return effect.toGlShaderProgram(context, useHdr);
    }
  }
}

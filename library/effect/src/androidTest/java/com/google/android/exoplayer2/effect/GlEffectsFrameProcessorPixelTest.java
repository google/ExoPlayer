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
package com.google.android.exoplayer2.effect;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.maybeSaveTestBitmapToCacheDirectory;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.BitmapPixelTestUtil;
import com.google.android.exoplayer2.testutil.DecodeOneFrameUtil;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for frame processing via {@link GlEffectsFrameProcessor}.
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
  // This file is generated on a Pixel 7, because the emulator isn't able to decode HLG to generate
  // this file.
  public static final String TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/tone_map_hlg_to_sdr.png";
  // This file is generated on a Pixel 7, because the emulator isn't able to decode PQ to generate
  // this file.
  public static final String TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/tone_map_pq_to_sdr.png";

  /** Input video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";
  /** Input HLG video of which we only use the first frame. */
  private static final String INPUT_HLG_MP4_ASSET_STRING = "media/mp4/hlg-1080p.mp4";
  /** Input PQ video of which we only use the first frame. */
  private static final String INPUT_PQ_MP4_ASSET_STRING = "media/mp4/hdr10-1080p.mp4";

  private @MonotonicNonNull GlEffectsFrameProcessorTestRunner glEffectsFrameProcessorTestRunner;

  @After
  public void release() {
    checkNotNull(glEffectsFrameProcessorTestRunner).release();
  }

  @Test
  public void processData_noEdits_producesExpectedOutput() throws Exception {
    String testId = "processData_noEdits";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_noEditsWithCache_leavesFrameUnchanged() throws Exception {
    String testId = "processData_noEditsWithCache";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(new FrameCache(/* capacity= */ 5))
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPixelWidthHeightRatio_producesExpectedOutput() throws Exception {
    String testId = "processData_withPixelWidthHeightRatio";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId).setPixelWidthHeightRatio(2f).build();
    Bitmap expectedBitmap = readBitmap(SCALE_WIDE_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withMatrixTransformation_translateRight_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withMatrixTransformation_translateRight";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects((MatrixTransformation) (long presentationTimeNs) -> translateRightMatrix)
            .build();
    Bitmap expectedBitmap = readBitmap(TRANSLATE_RIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withMatrixAndScaleToFitTransformation_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withMatrixAndScaleToFitTransformation";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(
                (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix,
                new ScaleToFitTransformation.Builder().setRotationDegrees(45).build())
            .build();
    Bitmap expectedBitmap = readBitmap(TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitAndMatrixTransformation_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitAndMatrixTransformation";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(
                new ScaleToFitTransformation.Builder().setRotationDegrees(45).build(),
                (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix)
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPresentation_createForHeight_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withPresentation_createForHeight";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(Presentation.createForHeight(480))
            .build();
    Bitmap expectedBitmap = readBitmap(REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withCropThenPresentation_producesExpectedOutput() throws Exception {
    String testId = "processData_withCropThenPresentation";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(
                new Crop(/* left= */ -.5f, /* right= */ .5f, /* bottom= */ -.5f, /* top= */ .5f),
                Presentation.createForAspectRatio(
                    /* aspectRatio= */ .5f, Presentation.LAYOUT_SCALE_TO_FIT))
            .build();
    Bitmap expectedBitmap = readBitmap(CROP_THEN_ASPECT_RATIO_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitTransformation_rotate45_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitTransformation_rotate45";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(new ScaleToFitTransformation.Builder().setRotationDegrees(45).build())
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withTwoWrappedScaleToFitTransformations_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withTwoWrappedScaleToFitTransformations";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(
                new GlEffectWrapper(
                    new ScaleToFitTransformation.Builder().setRotationDegrees(45).build()),
                new GlEffectWrapper(
                    new ScaleToFitTransformation.Builder()
                        .setScale(/* scaleX= */ 2, /* scaleY= */ 1)
                        .build()))
            .build();
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_SCALE_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void
      processData_withManyComposedMatrixTransformations_producesSameOutputAsCombinedTransformation()
          throws Exception {
    String testId =
        "processData_withManyComposedMatrixTransformations_producesSameOutputAsCombinedTransformation";
    Crop centerCrop =
        new Crop(/* left= */ -0.5f, /* right= */ 0.5f, /* bottom= */ -0.5f, /* top= */ 0.5f);
    ImmutableList.Builder<Effect> full10StepRotationAndCenterCrop = new ImmutableList.Builder<>();
    for (int i = 0; i < 10; i++) {
      full10StepRotationAndCenterCrop.add(new Rotation(/* degrees= */ 36));
    }
    full10StepRotationAndCenterCrop.add(centerCrop);

    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(centerCrop)
            .build();
    Bitmap centerCropResultBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();
    glEffectsFrameProcessorTestRunner.release();
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("full10StepRotationAndCenterCrop")
            .setEffects(full10StepRotationAndCenterCrop.build())
            .build();
    Bitmap fullRotationAndCenterCropResultBitmap =
        glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropResultBitmap, fullRotationAndCenterCropResultBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_increaseBrightness_producesExpectedOutput() throws Exception {
    String testId = "processData_increaseBrightness";
    ImmutableList<Effect> increaseBrightness =
        ImmutableList.of(
            new RgbAdjustment.Builder().setRedScale(5).build(),
            new RgbAdjustment.Builder().setGreenScale(5).build(),
            new RgbAdjustment.Builder().setBlueScale(5).build());
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(increaseBrightness)
            .build();
    Bitmap expectedBitmap = readBitmap(INCREASE_BRIGHTNESS_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_fullRotationIncreaseBrightnessAndCenterCrop_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_fullRotationIncreaseBrightnessAndCenterCrop";
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
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(
                new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
                centerCrop)
            .build();
    Bitmap centerCropAndBrightnessIncreaseResultBitmap =
        glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    glEffectsFrameProcessorTestRunner.release();
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("full4StepRotationBrightnessIncreaseAndCenterCrop")
            .setEffects(increaseBrightnessFullRotationCenterCrop)
            .build();
    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap =
        glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropAndBrightnessIncreaseResultBitmap,
            fullRotationBrightnessIncreaseAndCenterCropResultBitmap,
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void
      processData_fullRotationIncreaseBrightnessAndCenterCropWithCache_leavesFrameUnchanged()
          throws Exception {
    String testId = "processData_fullRotationIncreaseBrightnessAndCenterCropWithCache";
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
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("centerCrop")
            .setEffects(
                new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
                centerCrop)
            .build();
    Bitmap centerCropAndBrightnessIncreaseResultBitmap =
        glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();
    glEffectsFrameProcessorTestRunner.release();
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setOutputFileLabel("full4StepRotationBrightnessIncreaseAndCenterCrop")
            .setEffects(increaseBrightnessFullRotationCenterCrop)
            .build();

    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap =
        glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            centerCropAndBrightnessIncreaseResultBitmap,
            fullRotationBrightnessIncreaseAndCenterCropResultBitmap,
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_grayscaleAndIncreaseRedChannel_producesGrayscaleAndRedImage()
      throws Exception {
    String testId = "drawFrame_grayscaleAndIncreaseRedChannel";
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setEffects(
                RgbFilter.createGrayscaleFilter(),
                new RgbAdjustment.Builder().setRedScale(3).build())
            .build();
    Bitmap expectedBitmap = readBitmap(GRAYSCALE_THEN_INCREASE_RED_CHANNEL_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  @Ignore("b/261877288 Test can only run on physical devices because decoder can't decode HLG.")
  public void drawHlgFrame_toneMap_producesExpectedOutput() throws Exception {
    // TODO(b/239735341): Move this test to mobileharness testing.
    String testId = "drawHlgFrame_toneMap";
    ColorInfo hlgColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .build();
    ColorInfo toneMapSdrColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
            .build();
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setVideoAssetPath(INPUT_HLG_MP4_ASSET_STRING)
            .setInputColorInfo(hlgColor)
            .setOutputColorInfo(toneMapSdrColor)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  @Ignore("b/261877288 Test can only run on physical devices because decoder can't decode PQ.")
  public void drawPqFrame_toneMap_producesExpectedOutput() throws Exception {
    // TODO(b/239735341): Move this test to mobileharness testing.
    String testId = "drawPqFrame_toneMap";
    ColorInfo pqColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build();
    ColorInfo toneMapSdrColor =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
            .build();
    glEffectsFrameProcessorTestRunner =
        new GlEffectsFrameProcessorTestRunner.Builder(testId)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .setInputColorInfo(pqColor)
            .setOutputColorInfo(toneMapSdrColor)
            .build();
    Bitmap expectedBitmap = readBitmap(TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap = glEffectsFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  // TODO(b/227624622): Add a test for HDR input after BitmapPixelTestUtil can read HDR bitmaps,
  //  using GlEffectWrapper to ensure usage of intermediate textures.

  /* A test runner for {@link GlEffectsFrameProcessor} tests. */
  private static final class GlEffectsFrameProcessorTestRunner {

    /** A builder for {@link GlEffectsFrameProcessorTestRunner} instances. */
    public static final class Builder {
      private final String testId;
      private String outputFileLabel;
      private @Nullable ImmutableList<Effect> effects;
      private String videoAssetPath;
      private float pixelWidthHeightRatio;
      private ColorInfo inputColorInfo;
      private ColorInfo outputColorInfo;

      /**
       * Creates a new instance with default values.
       *
       * @param testId Test ID used to generate output files.
       */
      public Builder(String testId) {
        this.testId = testId;
        outputFileLabel = "";
        videoAssetPath = INPUT_SDR_MP4_ASSET_STRING;
        pixelWidthHeightRatio = DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO;
        inputColorInfo = ColorInfo.SDR_BT709_LIMITED;
        outputColorInfo = ColorInfo.SDR_BT709_LIMITED;
      }

      /**
       * Sets the effects used.
       *
       * <p>The default value is an empty list.
       */
      @CanIgnoreReturnValue
      public Builder setEffects(List<Effect> effects) {
        this.effects = ImmutableList.copyOf(effects);
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
       * Sets the effects used.
       *
       * <p>The default value is an empty list.
       */
      @CanIgnoreReturnValue
      public Builder setEffects(Effect... effects) {
        this.effects = ImmutableList.copyOf(effects);
        return this;
      }

      /**
       * Sets the input video asset path.
       *
       * <p>The default value is {@link #INPUT_SDR_MP4_ASSET_STRING}.
       */
      @CanIgnoreReturnValue
      public Builder setVideoAssetPath(String videoAssetPath) {
        this.videoAssetPath = videoAssetPath;
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
       * Sets the input color.
       *
       * <p>The default value is {@link ColorInfo.SDR_BT709_LIMITED}.
       */
      @CanIgnoreReturnValue
      public Builder setInputColorInfo(ColorInfo inputColorInfo) {
        this.inputColorInfo = inputColorInfo;
        return this;
      }

      /**
       * Sets the output color.
       *
       * <p>The default value is {@link ColorInfo.SDR_BT709_LIMITED}.
       */
      @CanIgnoreReturnValue
      public Builder setOutputColorInfo(ColorInfo outputColorInfo) {
        this.outputColorInfo = outputColorInfo;
        return this;
      }

      public GlEffectsFrameProcessorTestRunner build() throws FrameProcessingException {
        return new GlEffectsFrameProcessorTestRunner(
            testId,
            outputFileLabel,
            effects == null ? ImmutableList.of() : effects,
            videoAssetPath,
            pixelWidthHeightRatio,
            inputColorInfo,
            outputColorInfo);
      }
    }

    /** The ratio of width over height, for each pixel in a frame. */
    private static final float DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO = 1;
    /**
     * Time to wait for the decoded frame to populate the {@link GlEffectsFrameProcessor} instance's
     * input surface and the {@link GlEffectsFrameProcessor} to finish processing the frame, in
     * milliseconds.
     */
    private static final int FRAME_PROCESSING_WAIT_MS = 5000;

    private final String testId;
    private final String outputFileLabel;
    private final String videoAssetPath;
    private final float pixelWidthHeightRatio;
    private final AtomicReference<FrameProcessingException> frameProcessingException;

    private final GlEffectsFrameProcessor glEffectsFrameProcessor;

    private volatile @MonotonicNonNull ImageReader outputImageReader;
    private volatile boolean frameProcessingEnded;

    private GlEffectsFrameProcessorTestRunner(
        String testId,
        String outputFileLabel,
        ImmutableList<Effect> effects,
        String videoAssetPath,
        float pixelWidthHeightRatio,
        ColorInfo inputColorInfo,
        ColorInfo outputColorInfo)
        throws FrameProcessingException {
      this.testId = testId;
      this.outputFileLabel = outputFileLabel;
      this.videoAssetPath = videoAssetPath;
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      frameProcessingException = new AtomicReference<>();

      glEffectsFrameProcessor =
          checkNotNull(
              new GlEffectsFrameProcessor.Factory()
                  .create(
                      getApplicationContext(),
                      effects,
                      DebugViewProvider.NONE,
                      inputColorInfo,
                      outputColorInfo,
                      /* releaseFramesAutomatically= */ true,
                      MoreExecutors.directExecutor(),
                      new FrameProcessor.Listener() {
                        @Override
                        public void onOutputSizeChanged(int width, int height) {
                          outputImageReader =
                              ImageReader.newInstance(
                                  width, height, PixelFormat.RGBA_8888, /* maxImages= */ 1);
                          checkNotNull(glEffectsFrameProcessor)
                              .setOutputSurfaceInfo(
                                  new SurfaceInfo(outputImageReader.getSurface(), width, height));
                        }

                        @Override
                        public void onOutputFrameAvailable(long presentationTimeUs) {
                          // Do nothing as frames are released automatically.
                        }

                        @Override
                        public void onFrameProcessingError(FrameProcessingException exception) {
                          frameProcessingException.set(exception);
                        }

                        @Override
                        public void onFrameProcessingEnded() {
                          frameProcessingEnded = true;
                        }
                      }));
    }

    public Bitmap processFirstFrameAndEnd() throws Exception {
      DecodeOneFrameUtil.decodeOneAssetFileFrame(
          videoAssetPath,
          new DecodeOneFrameUtil.Listener() {
            @Override
            public void onContainerExtracted(MediaFormat mediaFormat) {
              glEffectsFrameProcessor.setInputFrameInfo(
                  new FrameInfo(
                      mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
                      mediaFormat.getInteger(MediaFormat.KEY_HEIGHT),
                      pixelWidthHeightRatio,
                      /* streamOffsetUs= */ 0));
              glEffectsFrameProcessor.registerInputFrame();
            }

            @Override
            public void onFrameDecoded(MediaFormat mediaFormat) {
              // Do nothing.
            }
          },
          glEffectsFrameProcessor.getInputSurface());
      checkNotNull(glEffectsFrameProcessor).signalEndOfInput();
      Thread.sleep(FRAME_PROCESSING_WAIT_MS);

      assertThat(frameProcessingEnded).isTrue();
      assertThat(frameProcessingException.get()).isNull();

      Image frameProcessorOutputImage = checkNotNull(outputImageReader).acquireLatestImage();
      Bitmap actualBitmap = createArgb8888BitmapFromRgba8888Image(frameProcessorOutputImage);
      frameProcessorOutputImage.close();
      maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ outputFileLabel, actualBitmap);
      return actualBitmap;
    }

    public void release() {
      if (glEffectsFrameProcessor != null) {
        glEffectsFrameProcessor.release();
      }
    }
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
   * <p>This ensures that {@link GlEffectsFrameProcessor} uses a separate {@link GlTextureProcessor}
   * for the wrapped {@link GlEffect} rather than merging it with preceding or subsequent {@link
   * GlEffect} instances and applying them in one combined {@link GlTextureProcessor}.
   */
  private static final class GlEffectWrapper implements GlEffect {

    private final GlEffect effect;

    public GlEffectWrapper(GlEffect effect) {
      this.effect = effect;
    }

    @Override
    public GlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
        throws FrameProcessingException {
      return effect.toGlTextureProcessor(context, useHdr);
    }
  }
}

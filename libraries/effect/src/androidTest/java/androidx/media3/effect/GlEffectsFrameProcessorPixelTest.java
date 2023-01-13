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
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmapToCacheDirectory;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaFormat;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.DecodeOneFrameUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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

  /**
   * Time to wait for the decoded frame to populate the {@link GlEffectsFrameProcessor} instance's
   * input surface and the {@link GlEffectsFrameProcessor} to finish processing the frame, in
   * milliseconds.
   */
  private static final int FRAME_PROCESSING_WAIT_MS = 5000;
  /** The ratio of width over height, for each pixel in a frame. */
  private static final float DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO = 1;

  private final AtomicReference<FrameProcessingException> frameProcessingException =
      new AtomicReference<>();

  private @MonotonicNonNull GlEffectsFrameProcessor glEffectsFrameProcessor;
  private volatile @MonotonicNonNull ImageReader outputImageReader;
  private volatile boolean frameProcessingEnded;

  @After
  public void release() {
    if (glEffectsFrameProcessor != null) {
      glEffectsFrameProcessor.release();
    }
  }

  @Test
  public void processData_noEdits_producesExpectedOutput() throws Exception {
    String testId = "processData_noEdits";
    setUpAndPrepareFirstFrame();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_noEditsWithCache_leavesFrameUnchanged() throws Exception {
    String testId = "processData_noEditsWithCache";
    setUpAndPrepareFirstFrame(new FrameCache(/* capacity= */ 5));
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPixelWidthHeightRatio_producesExpectedOutput() throws Exception {
    String testId = "processData_withPixelWidthHeightRatio";
    setUpAndPrepareFirstFrame(
        INPUT_SDR_MP4_ASSET_STRING,
        /* pixelWidthHeightRatio= */ 2f,
        /* inputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
        /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
        /* effects= */ ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(SCALE_WIDE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        (MatrixTransformation) (long presentationTimeNs) -> translateRightMatrix);
    Bitmap expectedBitmap = readBitmap(TRANSLATE_RIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix,
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build());
    Bitmap expectedBitmap = readBitmap(TRANSLATE_THEN_ROTATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build(),
        (MatrixTransformation) (long presentationTimeUs) -> translateRightMatrix);
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_TRANSLATE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withPresentation_createForHeight_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withPresentation_createForHeight";
    setUpAndPrepareFirstFrame(Presentation.createForHeight(480));
    Bitmap expectedBitmap = readBitmap(REQUEST_OUTPUT_HEIGHT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withCropThenPresentation_producesExpectedOutput() throws Exception {
    String testId = "processData_withCropThenPresentation";
    setUpAndPrepareFirstFrame(
        new Crop(/* left= */ -.5f, /* right= */ .5f, /* bottom= */ -.5f, /* top= */ .5f),
        Presentation.createForAspectRatio(
            /* aspectRatio= */ .5f, Presentation.LAYOUT_SCALE_TO_FIT));
    Bitmap expectedBitmap = readBitmap(CROP_THEN_ASPECT_RATIO_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withScaleToFitTransformation_rotate45_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withScaleToFitTransformation_rotate45";
    setUpAndPrepareFirstFrame(
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build());
    Bitmap expectedBitmap = readBitmap(ROTATE45_SCALE_TO_FIT_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_withTwoWrappedScaleToFitTransformations_producesExpectedOutput()
      throws Exception {
    String testId = "processData_withTwoWrappedScaleToFitTransformations";
    setUpAndPrepareFirstFrame(
        new GlEffectWrapper(new ScaleToFitTransformation.Builder().setRotationDegrees(45).build()),
        new GlEffectWrapper(
            new ScaleToFitTransformation.Builder()
                .setScale(/* scaleX= */ 2, /* scaleY= */ 1)
                .build()));
    Bitmap expectedBitmap = readBitmap(ROTATE_THEN_SCALE_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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

    setUpAndPrepareFirstFrame(centerCrop);
    Bitmap centerCropResultBitmap = processFirstFrameAndEnd();
    setUpAndPrepareFirstFrame(full10StepRotationAndCenterCrop.build());
    Bitmap fullRotationAndCenterCropResultBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "centerCrop", centerCropResultBitmap);
    maybeSaveTestBitmapToCacheDirectory(
        testId,
        /* bitmapLabel= */ "full10StepRotationAndCenterCrop",
        fullRotationAndCenterCropResultBitmap);
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
    setUpAndPrepareFirstFrame(increaseBrightness);
    Bitmap expectedBitmap = readBitmap(INCREASE_BRIGHTNESS_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        ImmutableList.of(
            new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
            centerCrop));
    Bitmap centerCropAndBrightnessIncreaseResultBitmap = processFirstFrameAndEnd();
    setUpAndPrepareFirstFrame(increaseBrightnessFullRotationCenterCrop);

    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "centerCrop", centerCropAndBrightnessIncreaseResultBitmap);
    maybeSaveTestBitmapToCacheDirectory(
        testId,
        /* bitmapLabel= */ "full4StepRotationBrightnessIncreaseAndCenterCrop",
        fullRotationBrightnessIncreaseAndCenterCropResultBitmap);
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
    setUpAndPrepareFirstFrame(
        ImmutableList.of(
            new RgbAdjustment.Builder().setRedScale(5).setBlueScale(5).setGreenScale(5).build(),
            centerCrop));
    Bitmap centerCropAndBrightnessIncreaseResultBitmap = processFirstFrameAndEnd();
    setUpAndPrepareFirstFrame(increaseBrightnessFullRotationCenterCrop);

    Bitmap fullRotationBrightnessIncreaseAndCenterCropResultBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "centerCrop", centerCropAndBrightnessIncreaseResultBitmap);
    maybeSaveTestBitmapToCacheDirectory(
        testId,
        /* bitmapLabel= */ "full4StepRotationBrightnessIncreaseAndCenterCrop",
        fullRotationBrightnessIncreaseAndCenterCropResultBitmap);
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
    setUpAndPrepareFirstFrame(
        RgbFilter.createGrayscaleFilter(), new RgbAdjustment.Builder().setRedScale(3).build());
    Bitmap expectedBitmap = readBitmap(GRAYSCALE_THEN_INCREASE_RED_CHANNEL_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        INPUT_HLG_MP4_ASSET_STRING,
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        /* inputColorInfo= */ hlgColor,
        /* outputColorInfo= */ toneMapSdrColor,
        /* effects= */ ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(TONE_MAP_HLG_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
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
    setUpAndPrepareFirstFrame(
        INPUT_PQ_MP4_ASSET_STRING,
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        /* inputColorInfo= */ pqColor,
        /* outputColorInfo= */ toneMapSdrColor,
        /* effects= */ ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(TONE_MAP_PQ_TO_SDR_PNG_ASSET_PATH);

    Bitmap actualBitmap = processFirstFrameAndEnd();

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  // TODO(b/227624622): Add a test for HDR input after BitmapPixelTestUtil can read HDR bitmaps,
  //  using GlEffectWrapper to ensure usage of intermediate textures.

  /**
   * Sets up and prepares the first frame from an input video, as well as the relevant test
   * infrastructure.
   *
   * <p>The frame will be sent towards {@link #glEffectsFrameProcessor}, and output may be accessed
   * on the {@code outputImageReader}.
   *
   * @param effects The {@link GlEffect GlEffects} to apply to the input frame.
   */
  private void setUpAndPrepareFirstFrame(GlEffect... effects) throws Exception {
    setUpAndPrepareFirstFrame(asList(effects));
  }

  private void setUpAndPrepareFirstFrame(List<Effect> effects) throws Exception {
    setUpAndPrepareFirstFrame(
        INPUT_SDR_MP4_ASSET_STRING,
        DEFAULT_PIXEL_WIDTH_HEIGHT_RATIO,
        /* inputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
        /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
        effects);
  }

  private void setUpAndPrepareFirstFrame(
      String videoAssetPath,
      float pixelWidthHeightRatio,
      ColorInfo inputColorInfo,
      ColorInfo outputColorInfo,
      List<Effect> effects)
      throws Exception {
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
  }

  private Bitmap processFirstFrameAndEnd() throws InterruptedException {
    checkNotNull(glEffectsFrameProcessor).signalEndOfInput();
    Thread.sleep(FRAME_PROCESSING_WAIT_MS);
    assertThat(frameProcessingEnded).isTrue();
    assertThat(frameProcessingException.get()).isNull();

    Image frameProcessorOutputImage = checkNotNull(outputImageReader).acquireLatestImage();
    Bitmap actualBitmap = createArgb8888BitmapFromRgba8888Image(frameProcessorOutputImage);
    frameProcessorOutputImage.close();
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

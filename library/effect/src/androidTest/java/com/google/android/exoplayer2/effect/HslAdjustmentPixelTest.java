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

package com.google.android.exoplayer2.effect;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.createArgb8888BitmapWithSolidColor;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.createGlTextureFromBitmap;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel tests for {@link HslAdjustment}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link GlEffectsFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class HslAdjustmentPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/original.png";
  public static final String MINIMUM_SATURATION_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/minimum_saturation.png";
  public static final String MAXIMUM_SATURATION_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/maximum_saturation.png";
  public static final String ROTATE_HUE_BY_NEGATIVE_90_DEGREES_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/rotate_hue_by_negative_90_degrees.png";
  public static final String ROTATE_HUE_BY_60_DEGREES_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/rotate_hue_by_60_degrees.png";
  public static final String DECREASE_LIGHTNESS_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/decrease_lightness.png";
  public static final String INCREASE_LIGHTNESS_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/increase_lightness.png";
  public static final String ADJUST_ALL_HSL_SETTINGS_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/adjust_all_hsl_settings.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlTextureProcessor hslProcessor;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);

    int outputTexId =
        GlUtil.createTexture(inputWidth, inputHeight, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        eglDisplay, eglContext, placeholderEglSurface, frameBuffer, inputWidth, inputHeight);
  }

  @After
  public void release() throws GlUtil.GlException, FrameProcessingException {
    if (hslProcessor != null) {
      hslProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_noOpAdjustment_leavesFrameUnchanged() throws Exception {
    String testId = "drawFrame_noOpAdjustment";
    HslAdjustment noOpAdjustment = new HslAdjustment.Builder().build();
    hslProcessor = noOpAdjustment.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_minimumSaturation_producesGrayFrame() throws Exception {
    String testId = "drawFrame_minimumSaturation";
    HslAdjustment minimumSaturation = new HslAdjustment.Builder().adjustSaturation(-100).build();
    hslProcessor = minimumSaturation.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(MINIMUM_SATURATION_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_maximumSaturation_producesHighlySaturatedFrame() throws Exception {
    String testId = "drawFrame_maximumSaturation";
    HslAdjustment maximumSaturation = new HslAdjustment.Builder().adjustSaturation(100).build();
    hslProcessor = maximumSaturation.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(MAXIMUM_SATURATION_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_rotateHueByNegative90Degrees_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_rotateHueByNegative90Degrees";
    HslAdjustment negativeHueRotation90Degrees = new HslAdjustment.Builder().adjustHue(-90).build();
    hslProcessor = negativeHueRotation90Degrees.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ROTATE_HUE_BY_NEGATIVE_90_DEGREES_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_rotateHueBy60Degrees_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_rotateHueBy60Degrees";
    HslAdjustment hueRotation60Degrees = new HslAdjustment.Builder().adjustHue(60).build();
    hslProcessor = hueRotation60Degrees.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ROTATE_HUE_BY_60_DEGREES_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_rotateHueByNegative300Degrees_producesSameOutputAsRotateBy60DegreesHue()
      throws Exception {
    String testId = "drawFrame_rotateHueByNegative300Degrees";
    HslAdjustment hueRotation420Degrees = new HslAdjustment.Builder().adjustHue(-300).build();
    hslProcessor = hueRotation420Degrees.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ROTATE_HUE_BY_60_DEGREES_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_rotateHueBy360Degrees_leavesFrameUnchanged() throws Exception {
    String testId = "drawFrame_rotateHueBy360Degrees";
    HslAdjustment hueRotation360Degrees = new HslAdjustment.Builder().adjustHue(360).build();
    hslProcessor = hueRotation360Degrees.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_minimumLightness_producesBlackFrame() throws Exception {
    String testId = "drawFrame_minimumLightness";
    HslAdjustment minimumLightness = new HslAdjustment.Builder().adjustLightness(-100).build();
    hslProcessor = minimumLightness.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(inputWidth, inputHeight, Color.BLACK);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_decreaseLightness_producesDarkerFrame() throws Exception {
    String testId = "drawFrame_decreaseLightness";
    HslAdjustment decreasedLightness = new HslAdjustment.Builder().adjustLightness(-50).build();
    hslProcessor = decreasedLightness.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(DECREASE_LIGHTNESS_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseLightness_producesBrighterFrame() throws Exception {
    String testId = "drawFrame_increaseLightness";
    HslAdjustment increasedLightness = new HslAdjustment.Builder().adjustLightness(50).build();
    hslProcessor = increasedLightness.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(INCREASE_LIGHTNESS_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_maximumLightness_producesWhiteFrame() throws Exception {
    String testId = "drawFrame_maximumLightness";
    HslAdjustment maximumLightness = new HslAdjustment.Builder().adjustLightness(100).build();
    hslProcessor = maximumLightness.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(inputWidth, inputHeight, Color.WHITE);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_adjustAllHslSettings_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_adjustAllHslSettings";
    HslAdjustment allHslSettingsAdjusted =
        new HslAdjustment.Builder().adjustHue(60).adjustSaturation(30).adjustLightness(50).build();
    hslProcessor = allHslSettingsAdjusted.toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = hslProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ADJUST_ALL_HSL_SETTINGS_PNG_ASSET_PATH);

    hslProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
}

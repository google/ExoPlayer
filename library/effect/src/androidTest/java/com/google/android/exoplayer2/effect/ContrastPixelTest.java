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
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for contrast adjustment via {@link ContrastProcessor}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link GlEffectsFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ContrastPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/original.png";
  public static final String INCREASE_CONTRAST_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/increase_contrast.png";
  public static final String DECREASE_CONTRAST_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/decrease_contrast.png";
  public static final String MAXIMUM_CONTRAST_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/linear_colors/maximum_contrast.png";

  // OpenGL uses floats in [0, 1] and maps 0.5f to 128 = 256 / 2.
  private static final int OPENGL_NEUTRAL_RGB_VALUE = 128;

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private @MonotonicNonNull SingleFrameGlTextureProcessor contrastProcessor;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws Exception {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);
  }

  @After
  public void release() throws GlUtil.GlException, FrameProcessingException {
    if (contrastProcessor != null) {
      contrastProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_noContrastChange_leavesFrameUnchanged() throws Exception {
    String testId = "drawFrame_noContrastChange";
    contrastProcessor =
        new Contrast(/* contrast= */ 0.0f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_minimumContrast_producesAllGrayFrame() throws Exception {
    String testId = "drawFrame_minimumContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ -1.0f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(
            inputWidth,
            inputHeight,
            Color.rgb(
                OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE));

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_decreaseContrast_decreasesPixelsGreaterEqual128IncreasesBelow()
      throws Exception {
    String testId = "drawFrame_decreaseContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ -0.75f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(DECREASE_CONTRAST_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseContrast_increasesPixelsGreaterEqual128DecreasesBelow()
      throws Exception {
    String testId = "drawFrame_increaseContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ 0.75f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(INCREASE_CONTRAST_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_maximumContrast_pixelEither0or255() throws Exception {
    String testId = "drawFrame_maximumContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ 1.0f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(MAXIMUM_CONTRAST_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws GlUtil.GlException {
    int outputTexId =
        GlUtil.createTexture(
            outputWidth, outputHeight, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        checkNotNull(eglDisplay),
        checkNotNull(eglContext),
        checkNotNull(placeholderEglSurface),
        frameBuffer,
        outputWidth,
        outputHeight);
  }
}

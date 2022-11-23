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
import static com.google.android.exoplayer2.effect.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.createGlTextureFromBitmap;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory;
import static com.google.android.exoplayer2.effect.BitmapTestUtil.readBitmap;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for texture processing via {@link Presentation}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link GlEffectsFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_wide.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_with_crop_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_with_crop_wide.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_stretch_to_fit_narrow.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_stretch_to_fit_wide.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlTextureProcessor presentationTextureProcessor;
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
  }

  @After
  public void release() throws GlUtil.GlException, FrameProcessingException {
    if (presentationTextureProcessor != null) {
      presentationTextureProcessor.release();
    }
    if (eglContext != null && eglDisplay != null) {
      GlUtil.destroyEglContext(eglDisplay, eglContext);
    }
  }

  @Test
  public void drawFrame_noEdits_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_noEdits";
    presentationTextureProcessor =
        Presentation.createForHeight(C.LENGTH_UNSET)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_narrow";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(/* aspectRatio= */ 1f, Presentation.LAYOUT_SCALE_TO_FIT)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_wide";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(/* aspectRatio= */ 2f, Presentation.LAYOUT_SCALE_TO_FIT)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(
                /* aspectRatio= */ 1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_wide";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(
                /* aspectRatio= */ 2f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_narrow";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(/* aspectRatio= */ 1f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_wide";
    presentationTextureProcessor =
        Presentation.createForAspectRatio(/* aspectRatio= */ 2f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize =
        presentationTextureProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.first, outputSize.second);

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
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

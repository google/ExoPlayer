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
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
 * as recommended in {@link FrameProcessorChainPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/original.png";
  public static final String CROP_SMALLER_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/crop_smaller.png";
  public static final String CROP_LARGER_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/crop_larger.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_scale_to_fit_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_scale_to_fit_wide.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_scale_to_fit_with_crop_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_scale_to_fit_with_crop_wide.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_stretch_to_fit_narrow.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/aspect_ratio_stretch_to_fit_wide.png";

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final EGLDisplay eglDisplay = GlUtil.createEglDisplay();
  private final EGLContext eglContext = GlUtil.createEglContext(eglDisplay);
  private @MonotonicNonNull SingleFrameGlTextureProcessor presentationTextureProcessor;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int outputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createTextures() throws IOException {
    Bitmap inputBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    placeholderEglSurface = GlUtil.createPlaceholderEglSurface(eglDisplay);
    GlUtil.focusEglSurface(eglDisplay, eglContext, placeholderEglSurface, inputWidth, inputHeight);
    inputTexId = BitmapTestUtil.createGlTextureFromBitmap(inputBitmap);
  }

  @After
  public void release() {
    if (presentationTextureProcessor != null) {
      presentationTextureProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_noEdits_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_noEdits";
    presentationTextureProcessor = new Presentation.Builder().build().toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_cropSmaller_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_cropSmaller";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setCrop(/* left= */ -.9f, /* right= */ .1f, /* bottom= */ -1f, /* top= */ .5f)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(CROP_SMALLER_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_cropLarger_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_cropSmaller";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setCrop(/* left= */ -2f, /* right= */ 2f, /* bottom= */ -1f, /* top= */ 2f)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(CROP_LARGER_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_narrow";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_wide";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(2f, Presentation.LAYOUT_SCALE_TO_FIT)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_wide";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(2f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_narrow";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(1f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_wide";
    presentationTextureProcessor =
        new Presentation.Builder()
            .setAspectRatio(2f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .build()
            .toGlTextureProcessor();
    presentationTextureProcessor.initialize(
        getApplicationContext(), inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationTextureProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationTextureProcessor.drawFrame(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) {
    outputTexId = GlUtil.createTexture(outputWidth, outputHeight);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        eglDisplay,
        eglContext,
        checkNotNull(placeholderEglSurface),
        frameBuffer,
        outputWidth,
        outputHeight);
  }
}

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
package androidx.media3.transformer;

import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_SCALE_TO_FIT_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_SCALE_TO_FIT_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_STRETCH_TO_FIT_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.ASPECT_RATIO_STRETCH_TO_FIT_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.CROP_LARGER_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.CROP_SMALLER_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.FIRST_FRAME_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Size;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.GlUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for frame processing via {@link PresentationFrameProcessor}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link FrameProcessorChainPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationFrameProcessorPixelTest {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final EGLDisplay eglDisplay = GlUtil.createEglDisplay();
  private final EGLContext eglContext = GlUtil.createEglContext(eglDisplay);
  private @MonotonicNonNull GlFrameProcessor presentationFrameProcessor;
  private @MonotonicNonNull EGLSurface eglSurface;
  private int inputTexId;
  private int outputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createTextures() throws IOException {
    Bitmap inputBitmap = BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    // This surface is needed for focussing a render target, but the tests don't write output to it.
    // The frame processor's output is written to a framebuffer instead.
    eglSurface =
        GlUtil.getEglSurface(eglDisplay, new SurfaceTexture(/* singleBufferMode= */ false));
    GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, inputWidth, inputHeight);
    inputTexId = BitmapTestUtil.createGlTextureFromBitmap(inputBitmap);
  }

  @After
  public void release() {
    if (presentationFrameProcessor != null) {
      presentationFrameProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void updateProgramAndDraw_noEdits_producesExpectedOutput() throws Exception {
    String testId = "updateProgramAndDraw_noEdits";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_cropSmaller_producesExpectedOutput() throws Exception {
    String testId = "updateProgramAndDraw_cropSmaller";
    GlFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setCrop(/* left= */ -.9f, /* right= */ .1f, /* bottom= */ -1f, /* top= */ .5f)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(CROP_SMALLER_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_cropLarger_producesExpectedOutput() throws Exception {
    String testId = "updateProgramAndDraw_cropSmaller";
    GlFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setCrop(/* left= */ -2f, /* right= */ 2f, /* bottom= */ -1f, /* top= */ 2f)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(CROP_LARGER_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_changeAspectRatio_scaleToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_scaleToFit_narrow";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(1f, PresentationFrameProcessor.SCALE_TO_FIT)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(
            ASPECT_RATIO_SCALE_TO_FIT_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_changeAspectRatio_scaleToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_scaleToFit_wide";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(2f, PresentationFrameProcessor.SCALE_TO_FIT)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

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
      updateProgramAndDraw_changeAspectRatio_scaleToFitWithCrop_narrow_producesExpectedOutput()
          throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_scaleToFitWithCrop_narrow";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(1f, PresentationFrameProcessor.SCALE_TO_FIT_WITH_CROP)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(
            ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

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
      updateProgramAndDraw_changeAspectRatio_scaleToFitWithCrop_wide_producesExpectedOutput()
          throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_scaleToFitWithCrop_wide";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(2f, PresentationFrameProcessor.SCALE_TO_FIT_WITH_CROP)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(
            ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_changeAspectRatio_stretchToFit_narrow_producesExpectedOutput()
      throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_stretchToFit_narrow";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(1f, PresentationFrameProcessor.STRETCH_TO_FIT)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(
            ASPECT_RATIO_STRETCH_TO_FIT_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_changeAspectRatio_stretchToFit_wide_producesExpectedOutput()
      throws Exception {
    String testId = "updateProgramAndDraw_changeAspectRatio_stretchToFit_wide";
    presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setAspectRatio(2f, PresentationFrameProcessor.STRETCH_TO_FIT)
            .build();
    presentationFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(
            ASPECT_RATIO_STRETCH_TO_FIT_WIDE_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    presentationFrameProcessor.updateProgramAndDraw(/* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.getWidth(), outputSize.getHeight());

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws IOException {
    outputTexId = GlUtil.createTexture(outputWidth, outputHeight);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        eglDisplay,
        eglContext,
        Assertions.checkNotNull(eglSurface),
        frameBuffer,
        outputWidth,
        outputHeight);
  }
}

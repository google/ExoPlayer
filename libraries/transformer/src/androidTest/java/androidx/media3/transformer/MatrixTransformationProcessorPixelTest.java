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

import static androidx.media3.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.media3.common.util.GlUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for texture processing via {@link MatrixTransformationProcessor}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link GlEffectsFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class MatrixTransformationProcessorPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/original.png";
  public static final String TRANSLATE_RIGHT_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/translate_right.png";
  public static final String SCALE_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/scale_narrow.png";
  public static final String ROTATE_90_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/rotate90.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlTextureProcessor matrixTransformationFrameProcessor;
  private int inputTexId;
  private int outputTexId;
  private int width;
  private int height;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    Bitmap inputBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);
    width = inputBitmap.getWidth();
    height = inputBitmap.getHeight();
    EGLSurface placeholderEglSurface = GlUtil.createPlaceholderEglSurface(eglDisplay);
    GlUtil.focusEglSurface(eglDisplay, eglContext, placeholderEglSurface, width, height);
    inputTexId = BitmapTestUtil.createGlTextureFromBitmap(inputBitmap);
    outputTexId = GlUtil.createTexture(width, height, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        eglDisplay, eglContext, placeholderEglSurface, frameBuffer, width, height);
  }

  @After
  public void release() throws GlUtil.GlException, FrameProcessingException {
    if (matrixTransformationFrameProcessor != null) {
      matrixTransformationFrameProcessor.release();
    }
    if (eglContext != null && eglDisplay != null) {
      GlUtil.destroyEglContext(eglDisplay, eglContext);
    }
  }

  @Test
  public void drawFrame_noEdits_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_noEdits";
    Matrix identityMatrix = new Matrix();
    matrixTransformationFrameProcessor =
        new MatrixTransformationProcessor(
            context,
            /* useHdr= */ false,
            /* matrixTransformation= */ (long presentationTimeUs) -> identityMatrix);
    matrixTransformationFrameProcessor.configure(width, height);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);

    matrixTransformationFrameProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_translateRight_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_translateRight";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    matrixTransformationFrameProcessor =
        new MatrixTransformationProcessor(
            context,
            /* useHdr= */ false,
            /* matrixTransformation= */ (long presentationTimeUs) -> translateRightMatrix);
    matrixTransformationFrameProcessor.configure(width, height);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(TRANSLATE_RIGHT_PNG_ASSET_PATH);

    matrixTransformationFrameProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_scaleNarrow_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_scaleNarrow";
    Matrix scaleNarrowMatrix = new Matrix();
    scaleNarrowMatrix.postScale(.5f, 1.2f);
    matrixTransformationFrameProcessor =
        new MatrixTransformationProcessor(
            context,
            /* useHdr= */ false,
            /* matrixTransformation= */ (long presentationTimeUs) -> scaleNarrowMatrix);
    matrixTransformationFrameProcessor.configure(width, height);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(SCALE_NARROW_PNG_ASSET_PATH);

    matrixTransformationFrameProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_rotate90_producesExpectedOutput() throws Exception {
    String testId = "drawFrame_rotate90";
    Matrix rotate90Matrix = new Matrix();
    rotate90Matrix.postRotate(/* degrees= */ 90);
    matrixTransformationFrameProcessor =
        new MatrixTransformationProcessor(
            context,
            /* useHdr= */ false,
            /* matrixTransformation= */ (long presentationTimeUs) -> rotate90Matrix);
    matrixTransformationFrameProcessor.configure(width, height);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ROTATE_90_PNG_ASSET_PATH);

    matrixTransformationFrameProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  // TODO(b/227624622): Add a test for HDR input after BitmapTestUtil can read HDR bitmaps.
}

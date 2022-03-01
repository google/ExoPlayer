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

import static androidx.media3.transformer.BitmapTestUtil.FIRST_FRAME_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.transformer.BitmapTestUtil.ROTATE_90_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.SCALE_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.media3.transformer.BitmapTestUtil.TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
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
 * Pixel test for frame processing via {@link TransformationFrameProcessor}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link FrameEditorDataProcessingTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformationFrameProcessorTest {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final EGLDisplay eglDisplay = GlUtil.createEglDisplay();
  private final EGLContext eglContext = GlUtil.createEglContext(eglDisplay);
  private @MonotonicNonNull GlFrameProcessor transformationFrameProcessor;
  private int inputTexId;
  private int outputTexId;
  // TODO(b/214975934): Once the frame processors are allowed to have different input and output
  //  dimensions, get the output dimensions from the frame processor.
  private int width;
  private int height;

  @Before
  public void createTextures() throws IOException {
    Bitmap inputBitmap = BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING);
    width = inputBitmap.getWidth();
    height = inputBitmap.getHeight();
    // This surface is needed for focussing a render target, but the tests don't write output to it.
    // The frame processor's output is written to a framebuffer instead.
    EGLSurface eglSurface = GlUtil.getEglSurface(eglDisplay, new SurfaceTexture(false));
    GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
    inputTexId =
        BitmapTestUtil.createGlTextureFromBitmap(
            BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING));
    outputTexId = GlUtil.createTexture(width, height);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(eglDisplay, eglContext, eglSurface, frameBuffer, width, height);
  }

  @After
  public void release() {
    if (transformationFrameProcessor != null) {
      transformationFrameProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void updateProgramAndDraw_noEdits_producesExpectedOutput() throws Exception {
    final String testId = "updateProgramAndDraw_noEdits";
    Matrix identityMatrix = new Matrix();
    transformationFrameProcessor =
        new TransformationFrameProcessor(getApplicationContext(), identityMatrix);
    transformationFrameProcessor.initialize();
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(FIRST_FRAME_PNG_ASSET_STRING);

    transformationFrameProcessor.updateProgramAndDraw(inputTexId, /* presentationTimeNs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_translateRight_producesExpectedOutput() throws Exception {
    final String testId = "updateProgramAndDraw_translateRight";
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    transformationFrameProcessor =
        new TransformationFrameProcessor(getApplicationContext(), translateRightMatrix);
    transformationFrameProcessor.initialize();
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    transformationFrameProcessor.updateProgramAndDraw(inputTexId, /* presentationTimeNs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_scaleNarrow_producesExpectedOutput() throws Exception {
    final String testId = "updateProgramAndDraw_scaleNarrow";
    Matrix scaleNarrowMatrix = new Matrix();
    scaleNarrowMatrix.postScale(.5f, 1.2f);
    transformationFrameProcessor =
        new TransformationFrameProcessor(getApplicationContext(), scaleNarrowMatrix);
    transformationFrameProcessor.initialize();
    Bitmap expectedBitmap =
        BitmapTestUtil.readBitmap(SCALE_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    transformationFrameProcessor.updateProgramAndDraw(inputTexId, /* presentationTimeNs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void updateProgramAndDraw_rotate90_producesExpectedOutput() throws Exception {
    final String testId = "updateProgramAndDraw_rotate90";
    // TODO(b/213190310): After creating a Presentation class, move VideoSamplePipeline
    //  resolution-based adjustments (ex. in cl/419619743) to that Presentation class, so we can
    //  test that rotation doesn't distort the image.
    Matrix rotate90Matrix = new Matrix();
    rotate90Matrix.postRotate(/* degrees= */ 90);
    transformationFrameProcessor =
        new TransformationFrameProcessor(getApplicationContext(), rotate90Matrix);
    transformationFrameProcessor.initialize();
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ROTATE_90_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    transformationFrameProcessor.updateProgramAndDraw(inputTexId, /* presentationTimeNs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    BitmapTestUtil.saveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap, /* throwOnFailure= */ false);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
}

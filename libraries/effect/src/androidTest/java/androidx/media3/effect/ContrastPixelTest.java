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

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromFocusedGlFramebuffer;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapWithSolidColor;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createGlTextureFromBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Pixel test for contrast adjustment via {@link Contrast}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps as recommended in {@link DefaultVideoFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ContrastPixelTest {
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png";
  private static final String INCREASE_CONTRAST_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/increase_contrast.png";
  private static final String DECREASE_CONTRAST_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/decrease_contrast.png";
  private static final String MAXIMUM_CONTRAST_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/maximum_contrast.png";

  // OpenGL uses floats in [0, 1] and maps 0.5f to 128 = 256 / 2.
  private static final int OPENGL_NEUTRAL_RGB_VALUE = 128;

  private final Context context = getApplicationContext();

  private String testId;
  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private @MonotonicNonNull BaseGlShaderProgram contrastShaderProgram;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws Exception {
    eglDisplay = GlUtil.getDefaultEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);
  }

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void release() throws GlUtil.GlException, VideoFrameProcessingException {
    if (contrastShaderProgram != null) {
      contrastShaderProgram.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_noContrastChange_leavesFrameUnchanged() throws Exception {
    contrastShaderProgram =
        new Contrast(/* contrast= */ 0.0f).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = contrastShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    contrastShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_minimumContrast_producesAllGrayFrame() throws Exception {
    contrastShaderProgram =
        new Contrast(/* contrast= */ -1.0f).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = contrastShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(
            inputWidth,
            inputHeight,
            Color.rgb(
                OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE));

    contrastShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_decreaseContrast_decreasesPixelsGreaterEqual128IncreasesBelow()
      throws Exception {
    contrastShaderProgram =
        new Contrast(/* contrast= */ -0.75f).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = contrastShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(DECREASE_CONTRAST_PNG_ASSET_PATH);

    contrastShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseContrast_increasesPixelsGreaterEqual128DecreasesBelow()
      throws Exception {
    contrastShaderProgram =
        new Contrast(/* contrast= */ 0.75f).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = contrastShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(INCREASE_CONTRAST_PNG_ASSET_PATH);

    contrastShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_maximumContrast_pixelEither0or255() throws Exception {
    contrastShaderProgram =
        new Contrast(/* contrast= */ 1.0f).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = contrastShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(MAXIMUM_CONTRAST_PNG_ASSET_PATH);

    contrastShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws Exception {
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

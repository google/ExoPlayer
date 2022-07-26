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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Pair;
import androidx.media3.common.util.GlUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
  private static final String EXOPLAYER_LOGO_PNG_ASSET_PATH =
      "media/bitmap/exoplayer_logo/original.png";
  // TODO(b/239005261): Migrate png to an emulator generated picture.
  private static final String MAXIMUM_CONTRAST_PNG_ASSET_PATH =
      "media/bitmap/exoplayer_logo/maximum_contrast.png";

  // OpenGL uses floats in [0, 1] and maps 0.5f to 128 = 256 / 2.
  private static final int OPENGL_NEUTRAL_RGB_VALUE = 128;

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private @MonotonicNonNull SingleFrameGlTextureProcessor contrastProcessor;
  private int inputTexId;
  private int outputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws Exception {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);

    Bitmap inputBitmap = BitmapTestUtil.readBitmap(EXOPLAYER_LOGO_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();

    placeholderEglSurface = GlUtil.createPlaceholderEglSurface(eglDisplay);
    GlUtil.focusEglSurface(eglDisplay, eglContext, placeholderEglSurface, inputWidth, inputHeight);
    inputTexId = BitmapTestUtil.createGlTextureFromBitmap(inputBitmap);
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
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(EXOPLAYER_LOGO_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(testId, "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
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
        BitmapTestUtil.createArgb8888BitmapWithSolidColor(
            inputWidth,
            inputHeight,
            Color.rgb(
                OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE, OPENGL_NEUTRAL_RGB_VALUE));

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(testId, "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
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
    Bitmap originalBitmap = BitmapTestUtil.readBitmap(EXOPLAYER_LOGO_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(testId, "actual", actualBitmap);
    assertIncreasedOrDecreasedContrast(originalBitmap, actualBitmap, /* increased= */ false);
  }

  @Test
  public void drawFrame_increaseContrast_increasesPixelsGreaterEqual128DecreasesBelow()
      throws Exception {
    String testId = "drawFrame_increaseContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ 0.75f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap originalBitmap = BitmapTestUtil.readBitmap(EXOPLAYER_LOGO_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(testId, "actual", actualBitmap);
    assertIncreasedOrDecreasedContrast(originalBitmap, actualBitmap, /* increased= */ true);
  }

  @Test
  public void drawFrame_maximumContrast_pixelEither0or255() throws Exception {
    String testId = "drawFrame_maximumContrast";
    contrastProcessor =
        new Contrast(/* contrast= */ 1.0f).toGlTextureProcessor(context, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = contrastProcessor.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.first, outputSize.second);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(MAXIMUM_CONTRAST_PNG_ASSET_PATH);

    contrastProcessor.drawFrame(inputTexId, /* presentationTimeUs = */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(testId, "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private static void assertIncreasedOrDecreasedContrast(
      Bitmap originalBitmap, Bitmap actualBitmap, boolean increased) {

    for (int y = 0; y < actualBitmap.getHeight(); y++) {
      for (int x = 0; x < actualBitmap.getWidth(); x++) {
        int originalColor = originalBitmap.getPixel(x, y);
        int actualColor = actualBitmap.getPixel(x, y);

        int redDifference = Color.red(actualColor) - Color.red(originalColor);
        int greenDifference = Color.green(actualColor) - Color.green(originalColor);
        int blueDifference = Color.blue(actualColor) - Color.blue(originalColor);

        // If the contrast increases, all pixels with a value greater or equal to
        // OPENGL_NEUTRAL_RGB_VALUE must increase (diff is greater or equal to 0) and all pixels
        // below OPENGL_NEUTRAL_RGB_VALUE must decrease (diff is smaller or equal to 0).
        // If the contrast decreases, all pixels with a value greater or equal to
        // OPENGL_NEUTRAL_RGB_VALUE must decrease (diff is smaller or equal to 0) and all pixels
        // below OPENGL_NEUTRAL_RGB_VALUE must increase (diff is greater or equal to 0).
        // The interval limits 0 and 255 stay unchanged for either contrast in- or decrease.

        if (Color.red(originalColor) >= OPENGL_NEUTRAL_RGB_VALUE) {
          assertThat(increased ? redDifference : -redDifference).isAtLeast(0);
        } else {
          assertThat(increased ? redDifference : -redDifference).isAtMost(0);
        }

        if (Color.green(originalColor) >= OPENGL_NEUTRAL_RGB_VALUE) {
          assertThat(increased ? greenDifference : -greenDifference).isAtLeast(0);
        } else {
          assertThat(increased ? greenDifference : -greenDifference).isAtMost(0);
        }

        if (Color.blue(originalColor) >= OPENGL_NEUTRAL_RGB_VALUE) {
          assertThat(increased ? blueDifference : -blueDifference).isAtLeast(0);
        } else {
          assertThat(increased ? blueDifference : -blueDifference).isAtMost(0);
        }
      }
    }
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws GlUtil.GlException {
    outputTexId =
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

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
 * Pixel test for Lookup Tables via {@link ColorLutShaderProgram}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps as recommended in {@link DefaultVideoFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public class SingleColorLutPixelTest {
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png";
  private static final String LUT_MAP_WHITE_TO_GREEN_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/lut_map_white_to_green.png";
  private static final String GRAYSCALE_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/grayscale.png";
  private static final String INVERT_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/invert.png";
  private static final String VERTICAL_HALD_IDENTITY_LUT =
      "test-generated-goldens/lut/identity.png";
  private static final String VERTICAL_HALD_GRAYSCALE_LUT =
      "test-generated-goldens/lut/grayscale.png";
  private static final String VERTICAL_HALD_INVERTED_LUT =
      "test-generated-goldens/lut/inverted.png";

  private final Context context = getApplicationContext();

  private String testId;
  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private @MonotonicNonNull BaseGlShaderProgram colorLutShaderProgram;
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
    if (colorLutShaderProgram != null) {
      colorLutShaderProgram.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_identityCubeLutSize2_leavesFrameUnchanged() throws Exception {
    int[][][] cubeIdentityLut = createIdentityLutCube(/* length= */ 2);
    colorLutShaderProgram =
        SingleColorLut.createFromCube(cubeIdentityLut)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_identityCubeLutSize64_leavesFrameUnchanged() throws Exception {
    int[][][] cubeIdentityLut = createIdentityLutCube(/* length= */ 64);
    colorLutShaderProgram =
        SingleColorLut.createFromCube(cubeIdentityLut)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_identityBitmapLutSize2_leavesFrameUnchanged() throws Exception {
    Bitmap bitmapLut = createIdentityLutBitmap(/* length= */ 2);
    colorLutShaderProgram =
        SingleColorLut.createFromBitmap(bitmapLut).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_identityBitmapLutSize64_leavesFrameUnchanged() throws Exception {
    Bitmap bitmapLut = createIdentityLutBitmap(/* length= */ 64);
    colorLutShaderProgram =
        SingleColorLut.createFromBitmap(bitmapLut).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_identityLutFromHaldImage_leavesFrameUnchanged() throws Exception {
    Bitmap bitmapLut = readBitmap(VERTICAL_HALD_IDENTITY_LUT);
    colorLutShaderProgram =
        SingleColorLut.createFromBitmap(bitmapLut).toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_mapWhiteToGreen_producesGreenHighlights() throws Exception {
    int length = 3;
    int[][][] mapWhiteToGreen = createIdentityLutCube(length);
    mapWhiteToGreen[length - 1][length - 1][length - 1] = Color.GREEN;
    colorLutShaderProgram =
        SingleColorLut.createFromCube(mapWhiteToGreen)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(LUT_MAP_WHITE_TO_GREEN_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_applyInvertedLut_producesInvertedFrame() throws Exception {
    Bitmap invertedLutBitmap = readBitmap(VERTICAL_HALD_INVERTED_LUT);
    colorLutShaderProgram =
        SingleColorLut.createFromBitmap(invertedLutBitmap)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(INVERT_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_applyGrayscaleLut_producesGrayscaleFrame() throws Exception {
    Bitmap grayscaleLutBitmap = readBitmap(VERTICAL_HALD_GRAYSCALE_LUT);
    colorLutShaderProgram =
        SingleColorLut.createFromBitmap(grayscaleLutBitmap)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = colorLutShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(GRAYSCALE_PNG_ASSET_PATH);

    colorLutShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, "actual", actualBitmap, /* path= */ null);
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

  private static int[][][] createIdentityLutCube(int length) {
    int[][][] lut = new int[length][length][length];
    float scale = 1f / (length - 1);
    for (int r = 0; r < length; r++) {
      for (int g = 0; g < length; g++) {
        for (int b = 0; b < length; b++) {
          lut[r][g][b] =
              Color.rgb(/* red= */ r * scale, /* green= */ g * scale, /* blue= */ b * scale);
        }
      }
    }
    return lut;
  }

  private static Bitmap createIdentityLutBitmap(int length) {
    int[][][] lutCube = createIdentityLutCube(length);
    int[] colors = new int[length * length * length];

    for (int r = 0; r < length; r++) {
      for (int g = 0; g < length; g++) {
        for (int b = 0; b < length; b++) {
          int color = lutCube[r][g][b];
          int planePosition = b + length * (g + length * r);
          colors[planePosition] = color;
        }
      }
    }
    return Bitmap.createBitmap(
        colors, /* width= */ length, /* height= */ length * length, Bitmap.Config.ARGB_8888);
  }
}

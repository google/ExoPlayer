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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Pixel tests for {@link RgbMatrix}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps as recommended in {@link DefaultVideoFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class RgbAdjustmentPixelTest {
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png";
  private static final String ONLY_RED_CHANNEL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/only_red_channel.png";
  private static final String INCREASE_RED_CHANNEL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/increase_red_channel.png";
  private static final String INCREASE_BRIGHTNESS_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/increase_brightness.png";

  private final Context context = getApplicationContext();

  private String testId;
  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull BaseGlShaderProgram defaultShaderProgram;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.getDefaultEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);

    int outputTexId =
        GlUtil.createTexture(inputWidth, inputHeight, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        checkNotNull(eglDisplay),
        checkNotNull(eglContext),
        checkNotNull(placeholderEglSurface),
        frameBuffer,
        inputWidth,
        inputHeight);
  }

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void release() throws GlUtil.GlException, VideoFrameProcessingException {
    if (defaultShaderProgram != null) {
      defaultShaderProgram.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_identityMatrix_leavesFrameUnchanged() throws Exception {
    RgbMatrix identityMatrix = new RgbAdjustment.Builder().build();
    defaultShaderProgram = identityMatrix.toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeColors_producesBlackFrame() throws Exception {
    RgbMatrix removeColorMatrix =
        new RgbAdjustment.Builder().setRedScale(0).setGreenScale(0).setBlueScale(0).build();
    defaultShaderProgram = removeColorMatrix.toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(
            outputSize.getWidth(), outputSize.getHeight(), Color.BLACK);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_redOnlyFilter_removeBlueAndGreenValues() throws Exception {
    RgbMatrix redOnlyMatrix = new RgbAdjustment.Builder().setBlueScale(0).setGreenScale(0).build();
    defaultShaderProgram = redOnlyMatrix.toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ONLY_RED_CHANNEL_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseRedChannel_producesBrighterAndRedderFrame() throws Exception {
    RgbMatrix increaseRedMatrix = new RgbAdjustment.Builder().setRedScale(5).build();
    defaultShaderProgram = increaseRedMatrix.toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(INCREASE_RED_CHANNEL_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseBrightness_increasesAllValues() throws Exception {
    RgbMatrix increaseBrightnessMatrix =
        new RgbAdjustment.Builder().setRedScale(5).setGreenScale(5).setBlueScale(5).build();
    defaultShaderProgram = increaseBrightnessMatrix.toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(INCREASE_BRIGHTNESS_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeRedGreenAndBlueValuesInAChain_producesBlackImage() throws Exception {
    RgbMatrix noRed = new RgbAdjustment.Builder().setRedScale(0).build();
    RgbMatrix noGreen = new RgbAdjustment.Builder().setGreenScale(0).build();
    RgbMatrix noBlue = new RgbAdjustment.Builder().setBlueScale(0).build();
    defaultShaderProgram =
        DefaultShaderProgram.create(
            context,
            /* matrixTransformations= */ ImmutableList.of(),
            /* rgbMatrices= */ ImmutableList.of(noRed, noGreen, noBlue),
            /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(
            outputSize.getWidth(), outputSize.getHeight(), Color.BLACK);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeBlueAndGreenValuesInAChain_producesOnlyRedImage() throws Exception {
    RgbMatrix noGreen = new RgbAdjustment.Builder().setGreenScale(0).build();
    RgbMatrix noBlue = new RgbAdjustment.Builder().setBlueScale(0).build();
    defaultShaderProgram =
        DefaultShaderProgram.create(
            context,
            /* matrixTransformations= */ ImmutableList.of(),
            /* rgbMatrices= */ ImmutableList.of(noGreen, noBlue),
            /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ONLY_RED_CHANNEL_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increasesAndDecreasesRed_producesNoChange() throws Exception {
    float redScale = 4;
    RgbMatrix scaleRedMatrix = new RgbAdjustment.Builder().setRedScale(redScale).build();
    RgbMatrix scaleRedByInverseMatrix =
        new RgbAdjustment.Builder().setRedScale(1 / redScale).build();
    defaultShaderProgram =
        DefaultShaderProgram.create(
            context,
            /* matrixTransformations= */ ImmutableList.of(),
            /* rgbMatrices= */ ImmutableList.of(scaleRedMatrix, scaleRedByInverseMatrix),
            /* useHdr= */ false);
    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    defaultShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromFocusedGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
}

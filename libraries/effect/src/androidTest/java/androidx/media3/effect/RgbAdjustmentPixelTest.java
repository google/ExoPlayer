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
import static androidx.media3.effect.BitmapTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Pair;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel tests for {@link RgbMatrix}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output bitmaps
 * as recommended in {@link GlEffectsFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class RgbAdjustmentPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/original.png";
  public static final String ONLY_RED_CHANNEL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/only_red_channel.png";
  public static final String INCREASE_RED_CHANNEL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/increase_red_channel.png";
  public static final String INCREASE_BRIGHTNESS_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/increase_brightness.png";
  public static final String GRAYSCALE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/grayscale.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlTextureProcessor rgbMatrixProcessor;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int outputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    Bitmap inputBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    placeholderEglSurface = GlUtil.createPlaceholderEglSurface(eglDisplay);
    GlUtil.focusEglSurface(eglDisplay, eglContext, placeholderEglSurface, inputWidth, inputHeight);
    inputTexId = BitmapTestUtil.createGlTextureFromBitmap(inputBitmap);

    outputTexId =
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

  @After
  public void release() throws GlUtil.GlException, FrameProcessingException {
    if (rgbMatrixProcessor != null) {
      rgbMatrixProcessor.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  private static RgbMatrixProcessor createRgbMatrixProcessor(Context context, float[] rgbMatrix)
      throws FrameProcessingException {
    return ((RgbMatrix) presentationTimeUs -> rgbMatrix)
        .toGlTextureProcessor(context, /* useHdr= */ false);
  }

  @Test
  public void drawFrame_identityMatrix_leavesFrameUnchanged() throws Exception {
    String testId = "drawFrame_identityMatrix";
    RgbMatrix identityMatrix = new RgbAdjustment.Builder().build();
    rgbMatrixProcessor = new RgbMatrixProcessor(context, identityMatrix, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeColors_producesBlackFrame() throws Exception {
    String testId = "drawFrame_removeColors";
    RgbMatrix removeColorMatrix =
        new RgbAdjustment.Builder().setRedScale(0).setGreenScale(0).setBlueScale(0).build();
    rgbMatrixProcessor = new RgbMatrixProcessor(context, removeColorMatrix, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        BitmapTestUtil.createArgb8888BitmapWithSolidColor(
            outputSize.first, outputSize.second, Color.BLACK);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_redOnlyFilter_removeBlueAndGreenValues() throws Exception {
    String testId = "drawFrame_redOnlyFilter";
    RgbMatrix redOnlyMatrix = new RgbAdjustment.Builder().setBlueScale(0).setGreenScale(0).build();
    rgbMatrixProcessor = new RgbMatrixProcessor(context, redOnlyMatrix, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ONLY_RED_CHANNEL_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseRedChannel_producesBrighterAndRedderFrame() throws Exception {
    String testId = "drawFrame_increaseRedChannel";
    RgbMatrix increaseRedMatrix = new RgbAdjustment.Builder().setRedScale(5).build();
    rgbMatrixProcessor = new RgbMatrixProcessor(context, increaseRedMatrix, /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(INCREASE_RED_CHANNEL_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increaseBrightness_increasesAllValues() throws Exception {
    String testId = "drawFrame_increaseBrightness";
    RgbMatrix increaseBrightnessMatrix =
        new RgbAdjustment.Builder().setRedScale(5).setGreenScale(5).setBlueScale(5).build();
    rgbMatrixProcessor =
        new RgbMatrixProcessor(context, increaseBrightnessMatrix, /* useHdr = */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(INCREASE_BRIGHTNESS_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  // TODO(b/239430283): Move test to RgbFilterPixelTest once it exists.
  public void drawFrame_grayscale_producesGrayscaleImage() throws Exception {
    String testId = "drawFrame_grayscale";
    // Grayscale transformation matrix with the BT.709 standard from
    // https://en.wikipedia.org/wiki/Grayscale#Converting_colour_to_grayscale
    float[] grayscaleMatrix = {
      0.2126f, 0.2126f, 0.2126f, 0, 0.7152f, 0.7152f, 0.7152f, 0, 0.0722f, 0.0722f, 0.0722f, 0, 0,
      0, 0, 1
    };
    rgbMatrixProcessor = createRgbMatrixProcessor(/* context= */ context, grayscaleMatrix);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(GRAYSCALE_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeRedGreenAndBlueValuesInAChain_producesBlackImage() throws Exception {
    String testId = "drawFrame_removeRedGreenBlueValuesInAChain";
    RgbMatrix noRed = new RgbAdjustment.Builder().setRedScale(0).build();
    RgbMatrix noGreen = new RgbAdjustment.Builder().setGreenScale(0).build();
    RgbMatrix noBlue = new RgbAdjustment.Builder().setBlueScale(0).build();
    rgbMatrixProcessor =
        new RgbMatrixProcessor(
            context, ImmutableList.of(noRed, noGreen, noBlue), /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap =
        BitmapTestUtil.createArgb8888BitmapWithSolidColor(
            outputSize.first, outputSize.second, Color.BLACK);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_removeBlueAndGreenValuesInAChain_producesOnlyRedImage() throws Exception {
    String testId = "drawFrame_removeBlueAndGreenValuesInAChain";
    RgbMatrix noGreen = new RgbAdjustment.Builder().setGreenScale(0).build();
    RgbMatrix noBlue = new RgbAdjustment.Builder().setBlueScale(0).build();
    rgbMatrixProcessor =
        new RgbMatrixProcessor(context, ImmutableList.of(noGreen, noBlue), /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ONLY_RED_CHANNEL_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_increasesAndDecreasesRed_producesNoChange() throws Exception {
    String testId = "drawFrame_increaseAndDecreaseRed";
    float redScale = 4;
    RgbMatrix scaleRedMatrix = new RgbAdjustment.Builder().setRedScale(redScale).build();
    RgbMatrix scaleRedByInverseMatrix =
        new RgbAdjustment.Builder().setRedScale(1 / redScale).build();
    rgbMatrixProcessor =
        new RgbMatrixProcessor(
            context,
            ImmutableList.of(scaleRedMatrix, scaleRedByInverseMatrix),
            /* useHdr= */ false);
    Pair<Integer, Integer> outputSize = rgbMatrixProcessor.configure(inputWidth, inputHeight);
    Bitmap expectedBitmap = BitmapTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);

    rgbMatrixProcessor.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        BitmapTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
            outputSize.first, outputSize.second);

    BitmapTestUtil.maybeSaveTestBitmapToCacheDirectory(
        testId, /* bitmapLabel= */ "actual", actualBitmap);
    float averagePixelAbsoluteDifference =
        BitmapTestUtil.getAveragePixelAbsoluteDifferenceArgb8888(
            expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
}

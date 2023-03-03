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
package com.google.android.exoplayer2.effect;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.abs;
import static java.lang.Math.max;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Utilities for instrumentation tests for the {@link GlEffectsFrameProcessor} and {@link
 * SingleFrameGlTextureProcessor SingleFrameGlTextureProcessors}.
 */
public class BitmapTestUtil {

  private static final String TAG = "BitmapTestUtil";

  /**
   * Maximum allowed average pixel difference between the expected and actual edited images in pixel
   * difference-based tests. The value is chosen so that differences in decoder behavior across
   * emulator versions don't affect whether the test passes for most emulators, but substantial
   * distortions introduced by changes in the behavior of the {@link SingleFrameGlTextureProcessor
   * SingleFrameGlTextureProcessors} will cause the test to fail.
   *
   * <p>To run pixel difference-based tests on physical devices, please use a value of 5f, rather
   * than 0.5f. This higher value will ignore some very small errors, but will allow for some
   * differences caused by graphics implementations to be ignored. When the difference is close to
   * the threshold, manually inspect expected/actual bitmaps to confirm failure, as it's possible
   * this is caused by a difference in the codec or graphics implementation as opposed to a {@link
   * SingleFrameGlTextureProcessor} issue.
   */
  public static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE = 0.5f;

  /**
   * Reads a bitmap from the specified asset location.
   *
   * @param assetString Relative path to the asset within the assets directory.
   * @return A {@link Bitmap}.
   * @throws IOException If the bitmap can't be read.
   */
  public static Bitmap readBitmap(String assetString) throws IOException {
    Bitmap bitmap;
    try (InputStream inputStream = getApplicationContext().getAssets().open(assetString)) {
      bitmap = BitmapFactory.decodeStream(inputStream);
    }
    return bitmap;
  }

  /**
   * Returns a bitmap with the same information as the provided alpha/red/green/blue 8-bits per
   * component image.
   */
  public static Bitmap createArgb8888BitmapFromRgba8888Image(Image image) {
    int width = image.getWidth();
    int height = image.getHeight();
    assertThat(image.getPlanes()).hasLength(1);
    assertThat(image.getFormat()).isEqualTo(PixelFormat.RGBA_8888);
    Image.Plane plane = image.getPlanes()[0];
    ByteBuffer buffer = plane.getBuffer();
    int[] colors = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int offset = y * plane.getRowStride() + x * plane.getPixelStride();
        int r = buffer.get(offset) & 0xFF;
        int g = buffer.get(offset + 1) & 0xFF;
        int b = buffer.get(offset + 2) & 0xFF;
        int a = buffer.get(offset + 3) & 0xFF;
        colors[y * width + x] = Color.argb(a, r, g, b);
      }
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
  }

  /**
   * Returns a solid {@link Bitmap} with every pixel having the same color.
   *
   * @param width The width of image to create, in pixels.
   * @param height The height of image to create, in pixels.
   * @param color An RGBA color created by {@link Color}.
   */
  public static Bitmap createArgb8888BitmapWithSolidColor(int width, int height, int color) {
    int[] colors = new int[width * height];
    Arrays.fill(colors, color);
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
  }

  /**
   * Returns the average difference between the expected and actual bitmaps, calculated using the
   * maximum difference across all color channels for each pixel, then divided by the total number
   * of pixels in the image. The bitmap resolutions must match and they must use configuration
   * {@link Bitmap.Config#ARGB_8888}.
   *
   * <p>Tries to save a difference bitmap between expected and actual bitmaps.
   *
   * @param expected The expected {@link Bitmap}.
   * @param actual The actual {@link Bitmap} produced by the test.
   * @param testId The name of the test that produced the {@link Bitmap}, or {@code null} if the
   *     differences bitmap should not be saved to cache.
   * @return The average of the maximum absolute pixel-wise differences between the expected and
   *     actual bitmaps.
   */
  public static float getBitmapAveragePixelAbsoluteDifferenceArgb8888(
      Bitmap expected, Bitmap actual, @Nullable String testId) {
    int width = actual.getWidth();
    int height = actual.getHeight();
    assertThat(width).isEqualTo(expected.getWidth());
    assertThat(height).isEqualTo(expected.getHeight());
    assertThat(actual.getConfig()).isEqualTo(Bitmap.Config.ARGB_8888);
    long sumMaximumAbsoluteDifferences = 0;
    // Debug-only image diff without alpha. To use, set a breakpoint right before the method return
    // to view the difference between the expected and actual bitmaps. A passing test should show
    // an image that is completely black (color == 0).
    Bitmap differencesBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int actualColor = actual.getPixel(x, y);
        int expectedColor = expected.getPixel(x, y);

        int alphaDifference = abs(Color.alpha(actualColor) - Color.alpha(expectedColor));
        int redDifference = abs(Color.red(actualColor) - Color.red(expectedColor));
        int blueDifference = abs(Color.blue(actualColor) - Color.blue(expectedColor));
        int greenDifference = abs(Color.green(actualColor) - Color.green(expectedColor));
        differencesBitmap.setPixel(x, y, Color.rgb(redDifference, blueDifference, greenDifference));

        int maximumAbsoluteDifference = 0;
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, alphaDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, redDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, blueDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, greenDifference);

        sumMaximumAbsoluteDifferences += maximumAbsoluteDifference;
      }
    }
    if (testId != null) {
      maybeSaveTestBitmapToCacheDirectory(testId, "diff", differencesBitmap);
    }
    return (float) sumMaximumAbsoluteDifferences / (width * height);
  }

  /**
   * Tries to save the {@link Bitmap} to the {@link Context#getCacheDir() cache directory} as a PNG.
   *
   * <p>File name will be {@code <testId>_<bitmapLabel>.png}. If the file failed to write, any
   * {@link IOException} will be caught and logged.
   *
   * @param testId Name of the test that produced the {@link Bitmap}.
   * @param bitmapLabel Label to identify the bitmap.
   * @param bitmap The {@link Bitmap} to save.
   */
  public static void maybeSaveTestBitmapToCacheDirectory(
      String testId, String bitmapLabel, Bitmap bitmap) {
    File file =
        new File(
            getApplicationContext().getExternalCacheDir(), testId + "_" + bitmapLabel + ".png");
    try (FileOutputStream outputStream = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, outputStream);
    } catch (IOException e) {
      Log.e(TAG, "Could not write Bitmap to file path: " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Creates a bitmap with the values of the current OpenGL framebuffer.
   *
   * <p>This method may block until any previously called OpenGL commands are complete.
   *
   * @param width The width of the pixel rectangle to read.
   * @param height The height of the pixel rectangle to read.
   * @return A {@link Bitmap} with the framebuffer's values.
   */
  public static Bitmap createArgb8888BitmapFromCurrentGlFramebuffer(int width, int height)
      throws GlUtil.GlException {
    ByteBuffer rgba8888Buffer = ByteBuffer.allocateDirect(width * height * 4);
    // TODO(b/227624622): Add support for reading HDR bitmaps.
    GLES20.glReadPixels(
        0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba8888Buffer);
    GlUtil.checkGlError();
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    // According to https://www.khronos.org/opengl/wiki/Pixel_Transfer#Endian_issues,
    // the colors will have the order RGBA in client memory. This is what the bitmap expects:
    // https://developer.android.com/reference/android/graphics/Bitmap.Config#ARGB_8888.
    bitmap.copyPixelsFromBuffer(rgba8888Buffer);
    // Flip the bitmap as its positive y-axis points down while OpenGL's positive y-axis points up.
    return flipBitmapVertically(bitmap);
  }

  /**
   * Creates a {@link GLES20#GL_TEXTURE_2D 2-dimensional OpenGL texture} with the bitmap's contents.
   *
   * @param bitmap A {@link Bitmap}.
   * @return The identifier of the newly created texture.
   */
  public static int createGlTextureFromBitmap(Bitmap bitmap) throws GlUtil.GlException {
    // TODO(b/227624622): Add support for reading HDR bitmaps.
    int texId =
        GlUtil.createTexture(
            bitmap.getWidth(), bitmap.getHeight(), /* useHighPrecisionColorComponents= */ false);
    // Put the flipped bitmap in the OpenGL texture as the bitmap's positive y-axis points down
    // while OpenGL's positive y-axis points up.
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flipBitmapVertically(bitmap), 0);
    GlUtil.checkGlError();
    return texId;
  }

  private static Bitmap flipBitmapVertically(Bitmap bitmap) {
    Matrix flip = new Matrix();
    flip.postScale(1f, -1f);
    return Bitmap.createBitmap(
        bitmap,
        /* x= */ 0,
        /* y= */ 0,
        bitmap.getWidth(),
        bitmap.getHeight(),
        flip,
        /* filter= */ true);
  }

  private BitmapTestUtil() {}
}

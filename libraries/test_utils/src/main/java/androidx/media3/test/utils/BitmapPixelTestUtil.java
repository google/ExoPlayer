/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkState;
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
import android.opengl.GLES30;
import android.opengl.GLUtils;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Utilities for pixel tests. */
// TODO(b/263395272): After the bug is fixed and dependent tests are moved back to media3.effect,
//  move this back to the effect tests directory.
@UnstableApi
public class BitmapPixelTestUtil {

  private static final String TAG = "BitmapPixelTestUtil";

  /**
   * Maximum allowed average pixel difference between bitmaps generated using emulators.
   *
   * <p>This value is for for 8-bit primaries in pixel difference-based tests.
   *
   * <p>The value is chosen so that differences in decoder behavior across emulator versions don't
   * affect whether the test passes, but substantial distortions introduced by changes in tested
   * components will cause the test to fail.
   *
   * <p>When the difference is close to the threshold, manually inspect expected/actual bitmaps to
   * confirm failure, as it's possible this is caused by a difference in the codec or graphics
   * implementation as opposed to an issue in the tested component.
   */
  public static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE = 1f;

  /**
   * Maximum allowed average pixel difference between bitmaps generated using devices.
   *
   * <p>This value is for for 8-bit primaries in pixel difference-based tests.
   *
   * <p>The value is chosen so that differences in decoder behavior across devices don't affect
   * whether the test passes, but substantial distortions introduced by changes in tested components
   * will cause the test to fail.
   *
   * <p>When the difference is close to the threshold, manually inspect expected/actual bitmaps to
   * confirm failure, as it's possible this is caused by a difference in the codec or graphics
   * implementation as opposed to an issue in the tested component.
   *
   * <p>This value is larger than {@link #MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} to support the
   * larger variance in decoder outputs between different physical devices and emulators.
   */
  // TODO: b/279154364 - Stop allowing 15f threshold after bug is fixed.
  public static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE =
      !Util.MODEL.equals("H8266") && !Util.MODEL.equals("H8416") ? 5f : 15f;

  /**
   * Maximum allowed average pixel difference between bitmaps with 16-bit primaries generated using
   * devices.
   *
   * <p>The value is chosen so that differences in decoder behavior across devices in pixel
   * difference-based tests don't affect whether the test passes, but substantial distortions
   * introduced by changes in tested components will cause the test to fail.
   *
   * <p>When the difference is close to the threshold, manually inspect expected/actual bitmaps to
   * confirm failure, as it's possible this is caused by a difference in the codec or graphics
   * implementation as opposed to an issue in the tested component.
   *
   * <p>This value is larger than {@link #MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} to support the
   * larger variance in decoder outputs between different physical devices and emulators.
   */
  public static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16 = .01f;

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
  @RequiresApi(19)
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
   * Returns the average difference between the expected and actual bitmaps.
   *
   * <p>Calculated using the maximum difference across all color channels for each pixel, then
   * divided by the total number of pixels in the image. Bitmap resolutions must match and must use
   * configuration {@link Bitmap.Config#ARGB_8888}.
   *
   * <p>Tries to save a difference bitmap between expected and actual bitmaps.
   *
   * @param expected The expected {@link Bitmap}.
   * @param actual The actual {@link Bitmap} produced by the test.
   * @param testId The name of the test that produced the {@link Bitmap}, or {@code null} if the
   *     differences bitmap should not be saved to cache.
   * @param differencesBitmapPath Folder path for the produced pixel-wise difference {@link Bitmap}
   *     to be saved in or {@code null} if the assumed default save path should be used.
   * @return The average of the maximum absolute pixel-wise differences between the expected and
   *     actual bitmaps.
   */
  public static float getBitmapAveragePixelAbsoluteDifferenceArgb8888(
      Bitmap expected,
      Bitmap actual,
      @Nullable String testId,
      @Nullable String differencesBitmapPath) {
    assertBitmapsMatch(expected, actual);
    int width = actual.getWidth();
    int height = actual.getHeight();
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
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ "diff", differencesBitmap, differencesBitmapPath);
    }
    return (float) sumMaximumAbsoluteDifferences / (width * height);
  }

  /**
   * Returns the average difference between the expected and actual bitmaps.
   *
   * <p>Calculated using the maximum difference across all color channels for each pixel, then
   * divided by the total number of pixels in the image. Bitmap resolutions must match and must use
   * configuration {@link Bitmap.Config#RGBA_F16}.
   *
   * @param expected The expected {@link Bitmap}.
   * @param actual The actual {@link Bitmap} produced by the test.
   * @return The average of the maximum absolute pixel-wise differences between the expected and
   *     actual bitmaps.
   */
  @RequiresApi(29) // Bitmap#getColor()
  public static float getBitmapAveragePixelAbsoluteDifferenceFp16(Bitmap expected, Bitmap actual) {
    assertBitmapsMatch(expected, actual);
    int width = actual.getWidth();
    int height = actual.getHeight();
    float sumMaximumAbsoluteDifferences = 0;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        Color actualColor = actual.getColor(x, y);
        Color expectedColor = expected.getColor(x, y);

        float alphaDifference = abs(actualColor.alpha() - expectedColor.alpha());
        float redDifference = abs(actualColor.red() - expectedColor.red());
        float blueDifference = abs(actualColor.blue() - expectedColor.blue());
        float greenDifference = abs(actualColor.green() - expectedColor.green());

        float maximumAbsoluteDifference = 0;
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, alphaDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, redDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, blueDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, greenDifference);

        sumMaximumAbsoluteDifferences += maximumAbsoluteDifference;
      }
    }
    return sumMaximumAbsoluteDifferences / (width * height);
  }

  private static void assertBitmapsMatch(Bitmap expected, Bitmap actual) {
    assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
    assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
    assertThat(actual.getConfig()).isEqualTo(expected.getConfig());
  }

  /**
   * Returns the average difference between the expected and actual bitmaps, calculated using the
   * maximum difference across all color channels for each pixel, then divided by the total number
   * of pixels in the image, without saving the difference bitmap. See {@link
   * BitmapPixelTestUtil#getBitmapAveragePixelAbsoluteDifferenceArgb8888(Bitmap, Bitmap, String,
   * String)}.
   *
   * <p>This method is the overloaded version of {@link
   * BitmapPixelTestUtil#getBitmapAveragePixelAbsoluteDifferenceArgb8888(Bitmap, Bitmap, String,
   * String)} without a specified saved path.
   */
  public static float getBitmapAveragePixelAbsoluteDifferenceArgb8888(
      Bitmap expected, Bitmap actual, @Nullable String testId) {
    return getBitmapAveragePixelAbsoluteDifferenceArgb8888(
        expected, actual, testId, /* differencesBitmapPath= */ null);
  }

  /**
   * Tries to save the {@link Bitmap} as a PNG to the {@code <path>}, and if not provided, tries to
   * save to the {@link Context#getCacheDir() cache directory}.
   *
   * <p>File name will be {@code <testId>_<bitmapLabel>.png}. If the file failed to write, any
   * {@link IOException} will be caught and logged.
   *
   * @param testId Name of the test that produced the {@link Bitmap}.
   * @param bitmapLabel Label to identify the bitmap.
   * @param bitmap The {@link Bitmap} to save.
   * @param path Folder path for the supplied {@link Bitmap} to be saved in or {@code null} if the
   *     {@link Context#getCacheDir() cache directory} should be saved in.
   */
  public static void maybeSaveTestBitmap(
      String testId, String bitmapLabel, Bitmap bitmap, @Nullable String path) {
    String fileName = testId + (bitmapLabel.isEmpty() ? "" : "_" + bitmapLabel) + ".png";
    File file = null;

    if (path != null) {
      File folder = new File(path);
      checkState(
          folder.exists() || folder.mkdirs(), "Could not create directory to save images: " + path);
      file = new File(path, fileName);
    } else {
      file = new File(getApplicationContext().getExternalCacheDir(), fileName);
    }

    try (FileOutputStream outputStream = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, outputStream);
    } catch (IOException e) {
      Log.e(TAG, "Could not write Bitmap to file path: " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Creates a {@link Bitmap.Config#ARGB_8888} bitmap with the values of the current OpenGL
   * framebuffer.
   *
   * <p>This method may block until any previously called OpenGL commands are complete.
   *
   * @param width The width of the pixel rectangle to read.
   * @param height The height of the pixel rectangle to read.
   * @return A {@link Bitmap} with the framebuffer's values.
   */
  public static Bitmap createArgb8888BitmapFromCurrentGlFramebuffer(int width, int height)
      throws GlUtil.GlException {
    return createBitmapFromCurrentGlFrameBuffer(
        width, height, /* pixelSize= */ 4, GLES20.GL_UNSIGNED_BYTE, Bitmap.Config.ARGB_8888);
  }

  /**
   * Creates a {@link Bitmap.Config#RGBA_F16} bitmap with the values of the current OpenGL
   * framebuffer.
   *
   * <p>This method may block until any previously called OpenGL commands are complete.
   *
   * @param width The width of the pixel rectangle to read.
   * @param height The height of the pixel rectangle to read.
   * @return A {@link Bitmap} with the framebuffer's values.
   */
  @RequiresApi(26) // Bitmap.Config.RGBA_F16
  public static Bitmap createFp16BitmapFromCurrentGlFramebuffer(int width, int height)
      throws GlUtil.GlException {
    return createBitmapFromCurrentGlFrameBuffer(
        width, height, /* pixelSize= */ 8, GLES30.GL_HALF_FLOAT, Bitmap.Config.RGBA_F16);
  }

  private static Bitmap createBitmapFromCurrentGlFrameBuffer(
      int width, int height, int pixelSize, int glReadPixelsFormat, Bitmap.Config bitmapConfig)
      throws GlUtil.GlException {
    ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(width * height * pixelSize);
    GLES20.glReadPixels(
        /* x= */ 0, /* y= */ 0, width, height, GLES20.GL_RGBA, glReadPixelsFormat, pixelBuffer);
    GlUtil.checkGlError();
    Bitmap bitmap = Bitmap.createBitmap(width, height, bitmapConfig);
    // According to https://www.khronos.org/opengl/wiki/Pixel_Transfer#Endian_issues,
    // the colors will have the order RGBA in client memory. This is what the bitmap expects:
    // https://developer.android.com/reference/android/graphics/Bitmap.Config.
    bitmap.copyPixelsFromBuffer(pixelBuffer);
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
    int texId =
        GlUtil.createTexture(
            bitmap.getWidth(), bitmap.getHeight(), /* useHighPrecisionColorComponents= */ false);
    // Put the flipped bitmap in the OpenGL texture as the bitmap's positive y-axis points down
    // while OpenGL's positive y-axis points up.
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flipBitmapVertically(bitmap), 0);
    GlUtil.checkGlError();
    return texId;
  }

  public static Bitmap flipBitmapVertically(Bitmap bitmap) {
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

  private BitmapPixelTestUtil() {}
}

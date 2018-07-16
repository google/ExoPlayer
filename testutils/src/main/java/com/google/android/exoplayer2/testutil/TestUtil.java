/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * Utility methods for tests.
 */
public class TestUtil {

  private TestUtil() {}

  public static boolean sniffTestData(Extractor extractor, FakeExtractorInput input)
      throws IOException, InterruptedException {
    while (true) {
      try {
        return extractor.sniff(input);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

  public static byte[] readToEnd(DataSource dataSource) throws IOException {
    byte[] data = new byte[1024];
    int position = 0;
    int bytesRead = 0;
    while (bytesRead != C.RESULT_END_OF_INPUT) {
      if (position == data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      bytesRead = dataSource.read(data, position, data.length - position);
      if (bytesRead != C.RESULT_END_OF_INPUT) {
        position += bytesRead;
      }
    }
    return Arrays.copyOf(data, position);
  }

  public static byte[] readExactly(DataSource dataSource, int length) throws IOException {
    byte[] data = new byte[length];
    int position = 0;
    while (position < length) {
      int bytesRead = dataSource.read(data, position, data.length - position);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        fail("Not enough data could be read: " + position + " < " + length);
      } else {
        position += bytesRead;
      }
    }
    return data;
  }

  public static byte[] buildTestData(int length) {
    return buildTestData(length, length);
  }

  public static byte[] buildTestData(int length, int seed) {
    return buildTestData(length, new Random(seed));
  }

  public static byte[] buildTestData(int length, Random random) {
    byte[] source = new byte[length];
    random.nextBytes(source);
    return source;
  }

  public static String buildTestString(int maxLength, Random random) {
    int length = random.nextInt(maxLength);
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append((char) random.nextInt());
    }
    return builder.toString();
  }

  /**
   * Converts an array of integers in the range [0, 255] into an equivalent byte array.
   *
   * @param intArray An array of integers, all of which must be in the range [0, 255].
   * @return The equivalent byte array.
   */
  public static byte[] createByteArray(int... intArray) {
    byte[] byteArray = new byte[intArray.length];
    for (int i = 0; i < byteArray.length; i++) {
      Assertions.checkState(0x00 <= intArray[i] && intArray[i] <= 0xFF);
      byteArray[i] = (byte) intArray[i];
    }
    return byteArray;
  }

  public static byte[] joinByteArrays(byte[]... byteArrays) {
    int length = 0;
    for (byte[] byteArray : byteArrays) {
      length += byteArray.length;
    }
    byte[] joined = new byte[length];
    length = 0;
    for (byte[] byteArray : byteArrays) {
      System.arraycopy(byteArray, 0, joined, length, byteArray.length);
      length += byteArray.length;
    }
    return joined;
  }

  public static byte[] getByteArray(Context context, String fileName) throws IOException {
    return Util.toByteArray(getInputStream(context, fileName));
  }

  public static InputStream getInputStream(Context context, String fileName) throws IOException {
    return context.getResources().getAssets().open(fileName);
  }

  public static String getString(Context context, String fileName) throws IOException {
    return new String(getByteArray(context, fileName));
  }

  public static Bitmap readBitmapFromFile(Context context, String fileName) throws IOException {
    return BitmapFactory.decodeStream(getInputStream(context, fileName));
  }

  /**
   * Asserts that data read from a {@link DataSource} matches {@code expected}.
   *
   * @param dataSource The {@link DataSource} through which to read.
   * @param dataSpec The {@link DataSpec} to use when opening the {@link DataSource}.
   * @param expectedData The expected data.
   * @param expectKnownLength Whether to assert that {@link DataSource#open} returns the expected
   *     data length. If false then it's asserted that {@link C#LENGTH_UNSET} is returned.
   * @throws IOException If an error occurs reading fom the {@link DataSource}.
   */
  public static void assertDataSourceContent(
      DataSource dataSource, DataSpec dataSpec, byte[] expectedData, boolean expectKnownLength)
      throws IOException {
    try {
      long length = dataSource.open(dataSpec);
      assertThat(length).isEqualTo(expectKnownLength ? expectedData.length : C.LENGTH_UNSET);
      byte[] readData = readToEnd(dataSource);
      assertThat(readData).isEqualTo(expectedData);
    } finally {
      dataSource.close();
    }
  }

  /**
   * Asserts whether actual bitmap is very similar to the expected bitmap at some quality level.
   *
   * <p>This is defined as their PSNR value is greater than or equal to the threshold. The higher
   * the threshold, the more similar they are.
   *
   * @param expectedBitmap The expected bitmap.
   * @param actualBitmap The actual bitmap.
   * @param psnrThresholdDb The PSNR threshold (in dB), at or above which bitmaps are considered
   *     very similar.
   */
  public static void assertBitmapsAreSimilar(
      Bitmap expectedBitmap, Bitmap actualBitmap, double psnrThresholdDb) {
    assertThat(getPsnr(expectedBitmap, actualBitmap)).isAtLeast(psnrThresholdDb);
  }

  /**
   * Calculates the Peak-Signal-to-Noise-Ratio value for 2 bitmaps.
   *
   * <p>This is the logarithmic decibel(dB) value of the average mean-squared-error of normalized
   * (0.0-1.0) R/G/B values from the two bitmaps. The higher the value, the more similar they are.
   *
   * @param firstBitmap The first bitmap.
   * @param secondBitmap The second bitmap.
   * @return The PSNR value calculated from these 2 bitmaps.
   */
  private static double getPsnr(Bitmap firstBitmap, Bitmap secondBitmap) {
    assertThat(firstBitmap.getWidth()).isEqualTo(secondBitmap.getWidth());
    assertThat(firstBitmap.getHeight()).isEqualTo(secondBitmap.getHeight());
    long mse = 0;
    for (int i = 0; i < firstBitmap.getWidth(); i++) {
      for (int j = 0; j < firstBitmap.getHeight(); j++) {
        int firstColorInt = firstBitmap.getPixel(i, j);
        int firstRed = Color.red(firstColorInt);
        int firstGreen = Color.green(firstColorInt);
        int firstBlue = Color.blue(firstColorInt);
        int secondColorInt = secondBitmap.getPixel(i, j);
        int secondRed = Color.red(secondColorInt);
        int secondGreen = Color.green(secondColorInt);
        int secondBlue = Color.blue(secondColorInt);
        mse +=
            ((firstRed - secondRed) * (firstRed - secondRed)
                + (firstGreen - secondGreen) * (firstGreen - secondGreen)
                + (firstBlue - secondBlue) * (firstBlue - secondBlue));
      }
    }
    double normalizedMse =
        mse / (255.0 * 255.0 * 3.0 * firstBitmap.getWidth() * firstBitmap.getHeight());
    return 10 * Math.log10(1.0 / normalizedMse);
  }
}

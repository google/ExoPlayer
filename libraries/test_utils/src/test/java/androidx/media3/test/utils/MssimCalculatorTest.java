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
package androidx.media3.test.utils;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MssimCalculator}. */
@RunWith(AndroidJUnit4.class)
public class MssimCalculatorTest {

  @Test
  public void calculateSsim_sameImage() throws Exception {
    Bitmap bitmap =
        readBitmap("test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png");
    byte[] imageLuminosities = bitmapToLuminosityArray(bitmap);

    // SSIM equals 1 if the two images match.
    assertThat(
            MssimCalculator.calculate(
                imageLuminosities, imageLuminosities, bitmap.getWidth(), bitmap.getHeight()))
        .isEqualTo(1);
  }

  @Test
  public void calculateSsim_increasedBrightness() throws Exception {
    Bitmap refBitmap =
        readBitmap("test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png");
    Bitmap distBitmap =
        readBitmap(
            "test-generated-goldens/sample_mp4_first_frame/linear_colors/increase_brightness.png");

    // SSIM as calculated by ffmpeg: 0.526821 = 52%

    assertThat(
            (int)
                (MssimCalculator.calculate(
                        bitmapToLuminosityArray(refBitmap),
                        bitmapToLuminosityArray(distBitmap),
                        refBitmap.getWidth(),
                        refBitmap.getHeight())
                    * 100))
        .isEqualTo(52);
  }

  @Test
  public void calculateSsim_withWindowSkipping_similarToWithout() throws Exception {
    Bitmap referenceBitmap =
        readBitmap("test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png");
    Bitmap distortedBitmap =
        readBitmap(
            "test-generated-goldens/sample_mp4_first_frame/linear_colors/increase_brightness.png");
    byte[] referenceLuminosity = bitmapToLuminosityArray(referenceBitmap);
    byte[] distortedLuminosity = bitmapToLuminosityArray(distortedBitmap);

    assertThat(
            (int)
                (MssimCalculator.calculate(
                        referenceLuminosity,
                        distortedLuminosity,
                        referenceBitmap.getWidth(),
                        referenceBitmap.getHeight(),
                        /* enableWindowSkipping= */ false)
                    * 100))
        .isEqualTo(
            (int)
                (MssimCalculator.calculate(
                        referenceLuminosity,
                        distortedLuminosity,
                        referenceBitmap.getWidth(),
                        referenceBitmap.getHeight(),
                        /* enableWindowSkipping= */ true)
                    * 100));
  }

  private static Bitmap readBitmap(String assetString) throws IOException {
    try (InputStream inputStream = getApplicationContext().getAssets().open(assetString)) {
      return BitmapFactory.decodeStream(inputStream);
    }
  }

  private static byte[] bitmapToLuminosityArray(Bitmap bitmap) {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    @ColorInt int[] pixels = new int[width * height];
    byte[] luminosities = new byte[width * height];
    bitmap.getPixels(
        pixels, /* offset= */ 0, /* stride= */ width, /* x= */ 0, /* y= */ 0, width, height);
    for (int i = 0; i < pixels.length; i++) {
      luminosities[i] = (byte) (getLuminosity(pixels[i]) & 0xFF);
    }
    return luminosities;
  }

  /**
   * Gets the intensity of a given RGB {@link ColorInt pixel} using the luminosity formula
   *
   * <pre>l = 0.2126R + 0.7152G + 0.0722B
   */
  private static int getLuminosity(@ColorInt int pixel) {
    double l = 0;
    l += (0.2126f * Color.red(pixel));
    l += (0.7152f * Color.green(pixel));
    l += (0.0722f * Color.blue(pixel));
    return (int) l;
  }
}

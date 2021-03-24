/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.video.surfacecapturer;

import static com.google.android.exoplayer2.testutil.TestUtil.getBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PixelCopySurfaceCapturerV24}. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 24)
public final class PixelCopySurfaceCapturerV24Test {

  private PixelCopySurfaceCapturerV24 pixelCopySurfaceCapturer;

  private DummyMainThread testThread;
  private List<Bitmap> resultBitmaps;
  private List<Throwable> resultExceptions;
  private ConditionVariable callbackCalledCondition;

  private final SurfaceCapturer.Callback defaultCallback =
      new SurfaceCapturer.Callback() {
        @Override
        public void onSurfaceCaptured(Bitmap bitmap) {
          resultBitmaps.add(bitmap);
          callbackCalledCondition.open();
        }

        @Override
        public void onSurfaceCaptureError(Exception e) {
          resultExceptions.add(e);
          callbackCalledCondition.open();
        }
      };

  @Before
  public void setUp() {
    resultBitmaps = new ArrayList<>();
    resultExceptions = new ArrayList<>();
    testThread = new DummyMainThread();
    callbackCalledCondition = new ConditionVariable();
  }

  @After
  public void tearDown() {
    testThread.runOnMainThread(
        () -> {
          if (pixelCopySurfaceCapturer != null) {
            pixelCopySurfaceCapturer.release();
          }
        });
    testThread.release();
  }

  @Test
  public void getSurface_notNull() {
    int outputWidth = 80;
    int outputHeight = 60;
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          Surface surface = pixelCopySurfaceCapturer.getSurface();
          assertThat(surface).isNotNull();
        });
  }

  @Test
  public void captureSurface_bmpFile_originalSize() throws IOException, InterruptedException {
    int outputWidth = 80;
    int outputHeight = 60;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");

    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });
    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(originalBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_bmpFile_largerSize_sameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 160;
    int outputHeight = 120;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_bmpFile_largerSize_notSameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 89;
    int outputHeight = 67;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_bmpFile_smallerSize_sameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 40;
    int outputHeight = 30;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_bmpFile_smallerSize_notSameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 32;
    int outputHeight = 12;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_pngFile_originalSize() throws IOException, InterruptedException {
    int outputWidth = 256;
    int outputHeight = 256;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");

    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });
    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(originalBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_pngFile_largerSize_sameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 512;
    int outputHeight = 512;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_pngFile_largerSize_notSameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 567;
    int outputHeight = 890;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_pngFile_smallerSize_sameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 128;
    int outputHeight = 128;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_pngFile_smallerSize_notSameRatio()
      throws IOException, InterruptedException {
    int outputWidth = 210;
    int outputHeight = 123;
    Bitmap originalBitmap =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");
    Bitmap expectedBitmap =
        Bitmap.createScaledBitmap(originalBitmap, outputWidth, outputHeight, /* filter= */ true);
    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap);
        });

    callbackCalledCondition.block();
    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(1);
    assertBitmapsAreSimilar(expectedBitmap, resultBitmaps.get(0));
  }

  @Test
  public void captureSurface_multipleTimes() throws IOException, InterruptedException {
    int outputWidth = 500;
    int outputHeight = 400;
    Bitmap originalBitmap1 =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_80_60.bmp");
    Bitmap originalBitmap2 =
        getBitmap(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "media/bitmap/image_256_256.png");
    Bitmap expectedBitmap1 =
        Bitmap.createScaledBitmap(originalBitmap1, outputWidth, outputHeight, /* filter= */ true);
    Bitmap expectedBitmap2 =
        Bitmap.createScaledBitmap(originalBitmap2, outputWidth, outputHeight, /* filter= */ true);

    testThread.runOnMainThread(
        () -> {
          pixelCopySurfaceCapturer =
              new PixelCopySurfaceCapturerV24(
                  defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper()));
          drawBitmapOnSurface(originalBitmap1);
        });
    // Wait for the first bitmap to finish draw on the pixelCopySurfaceCapturer's surface.
    callbackCalledCondition.block();
    callbackCalledCondition.close();
    testThread.runOnMainThread(() -> drawBitmapOnSurface(originalBitmap2));
    callbackCalledCondition.block();

    assertThat(resultExceptions).isEmpty();
    assertThat(resultBitmaps).hasSize(2);
    assertBitmapsAreSimilar(expectedBitmap1, resultBitmaps.get(0));
    assertBitmapsAreSimilar(expectedBitmap2, resultBitmaps.get(1));
  }

  @Test
  public void getOutputWidth() {
    int outputWidth = 500;
    int outputHeight = 400;
    testThread.runOnMainThread(
        () ->
            pixelCopySurfaceCapturer =
                new PixelCopySurfaceCapturerV24(
                    defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper())));
    assertThat(pixelCopySurfaceCapturer.getOutputWidth()).isEqualTo(outputWidth);
  }

  @Test
  public void getOutputHeight() {
    int outputWidth = 500;
    int outputHeight = 400;
    testThread.runOnMainThread(
        () ->
            pixelCopySurfaceCapturer =
                new PixelCopySurfaceCapturerV24(
                    defaultCallback, outputWidth, outputHeight, new Handler(Looper.myLooper())));
    assertThat(pixelCopySurfaceCapturer.getOutputHeight()).isEqualTo(outputHeight);
  }

  // Internal methods

  private void drawBitmapOnSurface(Bitmap bitmap) {
    pixelCopySurfaceCapturer.setDefaultSurfaceTextureBufferSize(
        bitmap.getWidth(), bitmap.getHeight());
    Surface surface = pixelCopySurfaceCapturer.getSurface();
    Canvas canvas = surface.lockCanvas(/* inOutDirty= */ null);
    canvas.drawBitmap(bitmap, /* left= */ 0, /* top= */ 0, new Paint());
    surface.unlockCanvasAndPost(canvas);
  }

  /**
   * Asserts whether actual bitmap is very similar to the expected bitmap.
   *
   * <p>This is defined as their PSNR value is greater than or equal to 30.
   *
   * @param expectedBitmap The expected bitmap.
   * @param actualBitmap The actual bitmap.
   */
  private static void assertBitmapsAreSimilar(Bitmap expectedBitmap, Bitmap actualBitmap) {
    // TODO: Default PSNR threshold of 35 is quite low. Try to increase this without breaking tests.
    TestUtil.assertBitmapsAreSimilar(expectedBitmap, actualBitmap, /* psnrThresholdDb= */ 35);
  }
}

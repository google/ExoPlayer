/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.createArgb8888BitmapFromFocusedGlFramebuffer;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.createArgb8888BitmapWithSolidColor;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.createGlTextureFromBitmap;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Pixel tests for {@link ThumbnailStripEffect}. */
@RunWith(AndroidJUnit4.class)
public final class ThumbnailStripEffectPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/original.png";
  private static final String TWO_THUMBNAILS_STRIP_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/linear_colors/two_thumbnails_strip.png";
  @Rule public final TestName testName = new TestName();
  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private @MonotonicNonNull ThumbnailStripShaderProgram thumbnailStripShaderProgram;
  private String testId;

  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Before
  public void createGlObjects() throws Exception {
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

  @After
  public void releaseGlObjects() throws GlUtil.GlException, VideoFrameProcessingException {
    if (thumbnailStripShaderProgram != null) {
      thumbnailStripShaderProgram.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_withOneTimestampAndOriginalSize_producesOriginalFrame() throws Exception {
    ThumbnailStripEffect thumbnailStripEffect = new ThumbnailStripEffect(inputWidth, inputHeight);
    thumbnailStripEffect.setTimestampsMs(ImmutableList.of(0L));
    thumbnailStripShaderProgram =
        thumbnailStripEffect.toGlShaderProgram(context, /* useHdr= */ false);
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    thumbnailStripShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0L);
    Bitmap actualBitmap = createArgb8888BitmapFromFocusedGlFramebuffer(inputWidth, inputHeight);

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_zeroTimestamps_producesEmptyFrame() throws Exception {
    ThumbnailStripEffect thumbnailStripEffect = new ThumbnailStripEffect(inputWidth, inputHeight);
    thumbnailStripEffect.setTimestampsMs(ImmutableList.of());
    thumbnailStripShaderProgram =
        thumbnailStripEffect.toGlShaderProgram(context, /* useHdr= */ false);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(inputWidth, inputHeight, Color.TRANSPARENT);

    thumbnailStripShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0L);
    Bitmap actualBitmap = createArgb8888BitmapFromFocusedGlFramebuffer(inputWidth, inputHeight);

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_lateTimestamp_producesEmptyFrame() throws Exception {
    ThumbnailStripEffect thumbnailStripEffect = new ThumbnailStripEffect(inputWidth, inputHeight);
    thumbnailStripEffect.setTimestampsMs(ImmutableList.of(1L));
    thumbnailStripShaderProgram =
        thumbnailStripEffect.toGlShaderProgram(context, /* useHdr= */ false);
    Bitmap expectedBitmap =
        createArgb8888BitmapWithSolidColor(inputWidth, inputHeight, Color.TRANSPARENT);

    thumbnailStripShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0L);
    Bitmap actualBitmap = createArgb8888BitmapFromFocusedGlFramebuffer(inputWidth, inputHeight);

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_twoTimestamps_producesStrip() throws Exception {
    ThumbnailStripEffect thumbnailStripEffect = new ThumbnailStripEffect(inputWidth, inputHeight);
    thumbnailStripEffect.setTimestampsMs(ImmutableList.of(0L, 1L));
    thumbnailStripShaderProgram =
        thumbnailStripEffect.toGlShaderProgram(context, /* useHdr= */ false);
    Bitmap expectedBitmap = readBitmap(TWO_THUMBNAILS_STRIP_PNG_ASSET_PATH);

    thumbnailStripShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0L);
    thumbnailStripShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ Util.msToUs(1L));
    Bitmap actualBitmap = createArgb8888BitmapFromFocusedGlFramebuffer(inputWidth, inputHeight);

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }
}

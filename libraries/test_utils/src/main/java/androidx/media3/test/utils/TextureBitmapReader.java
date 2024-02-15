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

package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.os.Build;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@inheritDoc}
 *
 * <p>Reads from an OpenGL texture. Only for use on physical devices.
 *
 * <p>For images with alpha, this method incorrectly marks the output Bitmap as {@link
 * Bitmap#isPremultiplied() premultiplied}, even though OpenGL typically outputs only
 * non-premultiplied alpha.
 */
@UnstableApi
public final class TextureBitmapReader implements VideoFrameProcessorTestRunner.BitmapReader {

  private final Map<Long, Bitmap> outputTimestampsToBitmaps;
  private boolean useHighPrecisionColorComponents;
  private @MonotonicNonNull Bitmap outputBitmap;

  public TextureBitmapReader() {
    outputTimestampsToBitmaps = new LinkedHashMap<>();
  }

  @Nullable
  @Override
  public Surface getSurface(int width, int height, boolean useHighPrecisionColorComponents) {
    this.useHighPrecisionColorComponents = useHighPrecisionColorComponents;
    return null;
  }

  @Override
  public Bitmap getBitmap() {
    return checkStateNotNull(outputBitmap);
  }

  /**
   * @return The output {@link Bitmap} at a given {@code presentationTimeUs}.
   * @throws IllegalStateException If no such bitmap is produced.
   */
  public Bitmap getBitmapAtPresentationTimeUs(long presentationTimeUs) {
    return checkStateNotNull(outputTimestampsToBitmaps.get(presentationTimeUs));
  }

  /** Returns the timestamps in the order they were added. */
  public Set<Long> getOutputTimestamps() {
    return outputTimestampsToBitmaps.keySet();
  }

  /**
   * Reads the given {@code outputTexture}.
   *
   * <p>The read result can be fetched by calling {@link #getBitmapAtPresentationTimeUs} or {@link
   * #getBitmap}.
   *
   * <p>This implementation incorrectly marks the output Bitmap as {@link Bitmap#isPremultiplied()
   * premultiplied}, even though OpenGL typically outputs only non-premultiplied alpha. Use {@link
   * #readBitmapUnpremultipliedAlpha} to properly handle alpha.
   */
  // TODO: b/295523484 - In createBitmapFromCurrentGlFrameBuffer, call
  //  createUnpremultipliedArgb8888BitmapFromFocusedGlFramebuffer instead of
  //  createArgb8888BitmapFromFocusedGlFramebuffer, so that TextureBitmapReader always reads bitmaps
  //  as unpremultiplied alpha. Then, remove this method (as we'll already be using premultiplied
  //  alpha).
  public void readBitmap(GlTextureInfo outputTexture, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      outputBitmap =
          createBitmapFromCurrentGlFrameBuffer(
              outputTexture.width, outputTexture.height, useHighPrecisionColorComponents);
      outputTimestampsToBitmaps.put(presentationTimeUs, outputBitmap);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Reads the given {@code outputTexture} as one with unpremultiplied alpha.
   *
   * <p>The read result can be fetched by calling {@link #getBitmapAtPresentationTimeUs} or {@link
   * #getBitmap}.
   */
  public void readBitmapUnpremultipliedAlpha(GlTextureInfo outputTexture, long presentationTimeUs)
      throws VideoFrameProcessingException {
    checkState(!useHighPrecisionColorComponents);
    try {
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      outputBitmap =
          BitmapPixelTestUtil.createUnpremultipliedArgb8888BitmapFromFocusedGlFramebuffer(
              outputTexture.width, outputTexture.height);
      outputTimestampsToBitmaps.put(presentationTimeUs, outputBitmap);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private static Bitmap createBitmapFromCurrentGlFrameBuffer(
      int width, int height, boolean useHighPrecisionColorComponents) throws GlUtil.GlException {
    if (!useHighPrecisionColorComponents) {
      return BitmapPixelTestUtil.createArgb8888BitmapFromFocusedGlFramebuffer(width, height);
    }
    if (Build.VERSION.SDK_INT < 26) {
      throw new IllegalStateException("useHighPrecisionColorComponents only supported on API 26+");
    }
    return BitmapPixelTestUtil.createFp16BitmapFromFocusedGlFramebuffer(width, height);
  }
}

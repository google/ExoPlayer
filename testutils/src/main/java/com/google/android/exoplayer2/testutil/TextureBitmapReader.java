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

package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@inheritDoc}
 *
 * <p>Reads from an OpenGL texture. Only for use on physical devices.
 */
public final class TextureBitmapReader implements VideoFrameProcessorTestRunner.BitmapReader {

  // TODO(b/239172735): This outputs an incorrect black output image on emulators.
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
  public Bitmap getBitmap(long presentationTimeUs) {
    return checkStateNotNull(outputTimestampsToBitmaps.get(presentationTimeUs));
  }

  /** Returns the timestamps in the order they were added. */
  public Set<Long> getOutputTimestamps() {
    return outputTimestampsToBitmaps.keySet();
  }

  /**
   * Reads the given {@code outputTexture}.
   *
   * <p>The read result can be fetched by calling one of me {@link #getBitmap} methods.
   */
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

  private static Bitmap createBitmapFromCurrentGlFrameBuffer(
      int width, int height, boolean useHighPrecisionColorComponents) throws GlUtil.GlException {
    if (!useHighPrecisionColorComponents) {
      return BitmapPixelTestUtil.createArgb8888BitmapFromFocusedGlFramebuffer(width, height);
    }
    checkState(Util.SDK_INT > 26, "useHighPrecisionColorComponents only supported on API 26+");
    return BitmapPixelTestUtil.createFp16BitmapFromFocusedGlFramebuffer(width, height);
  }
}

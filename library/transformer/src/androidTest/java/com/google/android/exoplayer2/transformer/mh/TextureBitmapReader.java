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
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.os.Build;
import android.view.Surface;
import com.google.android.exoplayer2.effect.DefaultVideoFrameProcessor;
import com.google.android.exoplayer2.testutil.BitmapPixelTestUtil;
import com.google.android.exoplayer2.testutil.VideoFrameProcessorTestRunner;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@inheritDoc}
 *
 * <p>Reads from an OpenGL texture. Only for use on physical devices.
 */
public final class TextureBitmapReader implements VideoFrameProcessorTestRunner.BitmapReader {
  private final Map<Long, Bitmap> outputTimestampsToBitmaps;
  private boolean useHighPrecisionColorComponents;
  private @MonotonicNonNull Bitmap outputBitmap;

  public TextureBitmapReader() {
    outputTimestampsToBitmaps = new ConcurrentHashMap<>();
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

  public Bitmap getBitmap(long presentationTimeUs) {
    return checkStateNotNull(outputTimestampsToBitmaps.get(presentationTimeUs));
  }

  public Set<Long> getOutputTimestamps() {
    return outputTimestampsToBitmaps.keySet();
  }

  public void readBitmapFromTexture(
      GlTextureInfo outputTexture,
      long presentationTimeUs,
      DefaultVideoFrameProcessor.ReleaseOutputTextureCallback releaseOutputTextureCallback)
      throws VideoFrameProcessingException {
    try {
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.getFboId(), outputTexture.getWidth(), outputTexture.getHeight());
      outputBitmap =
          createBitmapFromCurrentGlFrameBuffer(
              outputTexture.getWidth(), outputTexture.getHeight(), useHighPrecisionColorComponents);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
    outputTimestampsToBitmaps.put(presentationTimeUs, outputBitmap);
    releaseOutputTextureCallback.release(presentationTimeUs);
  }

  private static Bitmap createBitmapFromCurrentGlFrameBuffer(
      int width, int height, boolean useHighPrecisionColorComponents) throws GlUtil.GlException {
    if (!useHighPrecisionColorComponents) {
      return BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);
    }
    if (Build.VERSION.SDK_INT < 26) {
      throw new IllegalStateException("useHighPrecisionColorComponents only supported on API 26+");
    }
    return BitmapPixelTestUtil.createFp16BitmapFromCurrentGlFramebuffer(width, height);
  }
}

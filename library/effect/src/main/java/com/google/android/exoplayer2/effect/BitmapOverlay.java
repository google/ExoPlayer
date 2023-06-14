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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import com.google.android.exoplayer2.upstream.DataSourceBitmapLoader;
import com.google.android.exoplayer2.util.BitmapLoader;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Creates {@link TextureOverlay}s from {@link Bitmap}s.
 *
 * <p>Useful for overlaying images and animated images (e.g. GIFs).
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public abstract class BitmapOverlay extends TextureOverlay {
  private int lastTextureId;
  private @MonotonicNonNull Bitmap lastBitmap;

  /**
   * Returns the overlay bitmap displayed at the specified timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws VideoFrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract Bitmap getBitmap(long presentationTimeUs) throws VideoFrameProcessingException;

  /**
   * {@inheritDoc}
   *
   * <p>Gets the width and height of the cached bitmap.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  @Override
  public Size getTextureSize(long presentationTimeUs) {
    return new Size(checkNotNull(lastBitmap).getWidth(), checkNotNull(lastBitmap).getHeight());
  }

  @Override
  public int getTextureId(long presentationTimeUs) throws VideoFrameProcessingException {
    Bitmap bitmap = getBitmap(presentationTimeUs);
    if (bitmap != lastBitmap) {
      try {
        lastBitmap = bitmap;
        lastTextureId =
            GlUtil.createTexture(
                bitmap.getWidth(),
                bitmap.getHeight(),
                /* useHighPrecisionColorComponents= */ false);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lastTextureId);
        GLUtils.texImage2D(
            GLES20.GL_TEXTURE_2D,
            /* level= */ 0,
            BitmapUtil.flipBitmapVertically(lastBitmap),
            /* border= */ 0);
        GlUtil.checkGlError();
      } catch (GlUtil.GlException e) {
        throw new VideoFrameProcessingException(e);
      }
    }
    return lastTextureId;
  }

  /**
   * Creates a {@link BitmapOverlay} that shows the {@code overlayBitmap} in the same position and
   * size throughout the whole video.
   *
   * @param overlayBitmap The bitmap to overlay on the video.
   */
  public static BitmapOverlay createStaticBitmapOverlay(Bitmap overlayBitmap) {
    return new BitmapOverlay() {
      @Override
      public Bitmap getBitmap(long presentationTimeUs) {
        return overlayBitmap;
      }
    };
  }

  /**
   * Creates a {@link BitmapOverlay} that shows the {@code overlayBitmap} in the same {@link
   * OverlaySettings} throughout the whole video.
   *
   * @param overlayBitmap The bitmap to overlay on the video.
   * @param overlaySettings The {@link OverlaySettings} configuring how the overlay is displayed on
   *     the frames.
   */
  public static BitmapOverlay createStaticBitmapOverlay(
      Bitmap overlayBitmap, OverlaySettings overlaySettings) {
    return new BitmapOverlay() {
      @Override
      public Bitmap getBitmap(long presentationTimeUs) {
        return overlayBitmap;
      }

      @Override
      public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return overlaySettings;
      }
    };
  }

  /**
   * Creates a {@link BitmapOverlay} that shows the input at {@code overlayBitmapUri} with the same
   * {@link OverlaySettings} throughout the whole video.
   *
   * @param context The {@link Context}.
   * @param overlayBitmapUri The {@link Uri} pointing to the resource to be converted into a bitmap.
   * @param overlaySettings The {@link OverlaySettings} configuring how the overlay is displayed on
   *     the frames.
   */
  public static BitmapOverlay createStaticBitmapOverlay(
      Context context, Uri overlayBitmapUri, OverlaySettings overlaySettings) {
    return new BitmapOverlay() {
      private @MonotonicNonNull Bitmap lastBitmap;

      @Override
      public Bitmap getBitmap(long presentationTimeUs) throws VideoFrameProcessingException {
        if (lastBitmap == null) {
          BitmapLoader bitmapLoader = new DataSourceBitmapLoader(context);
          ListenableFuture<Bitmap> future = bitmapLoader.loadBitmap(overlayBitmapUri);
          try {
            lastBitmap = future.get();
          } catch (ExecutionException | InterruptedException e) {
            throw new VideoFrameProcessingException(e);
          }
        }
        return lastBitmap;
      }

      @Override
      public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return overlaySettings;
      }
    };
  }
}

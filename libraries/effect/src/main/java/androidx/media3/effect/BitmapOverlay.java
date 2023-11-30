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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.Matrix;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSourceBitmapLoader;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Creates {@link TextureOverlay}s from {@link Bitmap}s.
 *
 * <p>Useful for overlaying images and animated images (e.g. GIFs).
 */
@UnstableApi
public abstract class BitmapOverlay extends TextureOverlay {

  private final float[] flipVerticallyMatrix;

  private int lastTextureId;
  private int lastBitmapGenerationId;
  private @Nullable Bitmap lastBitmap;

  public BitmapOverlay() {
    float[] temp = GlUtil.create4x4IdentityMatrix();
    Matrix.scaleM(temp, /* offset */ 0, /* x= */ 1f, /* y= */ -1f, /* z= */ 1f);
    flipVerticallyMatrix = temp;

    lastTextureId = C.INDEX_UNSET;
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
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoFrameProcessingException(e);
          } catch (ExecutionException e) {
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
    int generationId = bitmap.getGenerationId();
    if (bitmap != lastBitmap || generationId != lastBitmapGenerationId) {
      lastBitmap = bitmap;
      lastBitmapGenerationId = generationId;
      try {
        if (lastTextureId == C.INDEX_UNSET) {
          lastTextureId = GlUtil.generateTexture();
        }
        GlUtil.setTexture(lastTextureId, bitmap);
      } catch (GlUtil.GlException e) {
        throw new VideoFrameProcessingException(e);
      }
    }
    return lastTextureId;
  }

  @Override
  public float[] getVertexTransformation(long presentationTimeUs) {
    // Whereas the Android system uses the top-left corner as (0,0) of the
    // coordinate system, OpenGL uses the bottom-left corner as (0,0), so the
    // texture gets flipped. Flip the texture vertically to ensure the
    // orientation of the output is correct.
    return flipVerticallyMatrix;
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    lastBitmap = null;
    if (lastTextureId != C.INDEX_UNSET) {
      try {
        GlUtil.deleteTexture(lastTextureId);
      } catch (GlUtil.GlException e) {
        throw new VideoFrameProcessingException(e);
      }
    }
    lastTextureId = C.INDEX_UNSET;
  }
}

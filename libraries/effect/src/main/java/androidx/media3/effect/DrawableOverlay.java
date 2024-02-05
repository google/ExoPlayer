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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Creates a {@link TextureOverlay} from {@link Drawable}.
 *
 * <p>Uses a canvas to draw {@link DrawableOverlay} onto {@link BitmapOverlay}, which is then
 * displayed on each frame.
 */
@UnstableApi
public abstract class DrawableOverlay extends BitmapOverlay {
  private @MonotonicNonNull Bitmap lastBitmap;
  private @MonotonicNonNull Drawable lastDrawable;

  /**
   * Returns the overlay {@link Drawable} displayed at the specified timestamp.
   *
   * <p>The drawable must have its bounds set via {@link Drawable#setBounds} for drawable to be
   * displayed on the frame.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public abstract Drawable getDrawable(long presentationTimeUs);

  @Override
  public Bitmap getBitmap(long presentationTimeUs) {
    Drawable overlayDrawable = getDrawable(presentationTimeUs);
    // TODO(b/227625365): Drawable doesn't implement the equals method, so investigate other methods
    //   of detecting the need to redraw the bitmap.
    if (!overlayDrawable.equals(lastDrawable)) {
      lastDrawable = overlayDrawable;
      if (lastBitmap == null
          || lastBitmap.getWidth() != lastDrawable.getIntrinsicWidth()
          || lastBitmap.getHeight() != lastDrawable.getIntrinsicHeight()) {
        lastBitmap =
            Bitmap.createBitmap(
                lastDrawable.getIntrinsicWidth(),
                lastDrawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
      }
      Canvas canvas = new Canvas(lastBitmap);
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      lastDrawable.draw(canvas);
    }
    return checkNotNull(lastBitmap);
  }

  /**
   * Creates a {@link DrawableOverlay} that shows the {@link Drawable} with the same {@link
   * OverlaySettings} throughout the whole video.
   *
   * @param drawable The {@link Drawable} to be displayed.
   * @param overlaySettings The {@link OverlaySettings} configuring how the overlay is displayed on
   *     the frames.
   */
  public static DrawableOverlay createStaticDrawableOverlay(
      Drawable drawable, OverlaySettings overlaySettings) {
    return new DrawableOverlay() {
      @Override
      public Drawable getDrawable(long presentationTimeUs) {
        return drawable;
      }

      @Override
      public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return overlaySettings;
      }
    };
  }
}

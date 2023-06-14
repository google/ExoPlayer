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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Creates a {@link TextureOverlay} from {@link Drawable}.
 *
 * <p>Uses a canvas to draw {@link DrawableOverlay} onto {@link BitmapOverlay}, which is then
 * displayed on each frame.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
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
      lastBitmap =
          Bitmap.createBitmap(
              lastDrawable.getIntrinsicWidth(),
              lastDrawable.getIntrinsicHeight(),
              Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(lastBitmap);
      lastDrawable.draw(canvas);
    }
    return checkNotNull(lastBitmap);
  }

  /**
   * Creates a {@link TextOverlay} that shows the {@link Drawable} with the same {@link
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

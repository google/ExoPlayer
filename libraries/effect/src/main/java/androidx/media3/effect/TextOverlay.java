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
import static androidx.media3.common.util.Util.SDK_INT;
import static java.lang.Math.ceil;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Layout;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import androidx.annotation.DoNotInline;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Creates a {@link TextureOverlay} from text.
 *
 * <p>Uses a {@link SpannableString} to store the text and support advanced per-character text
 * styling.
 */
@UnstableApi
public abstract class TextOverlay extends BitmapOverlay {

  /**
   * Creates a {@link TextOverlay} that shows the {@code overlayText} with the same default settings
   * in {@link OverlaySettings} throughout the whole video.
   */
  public static TextOverlay createStaticTextOverlay(SpannableString overlayText) {
    return new TextOverlay() {
      @Override
      public SpannableString getText(long presentationTimeUs) {
        return overlayText;
      }
    };
  }

  /**
   * Creates a {@link TextOverlay} that shows the {@code overlayText} with the same {@link
   * OverlaySettings} throughout the whole video.
   *
   * @param overlayText The text to overlay on the video.
   * @param overlaySettings The {@link OverlaySettings} configuring how the overlay is displayed on
   *     the frames.
   */
  public static TextOverlay createStaticTextOverlay(
      SpannableString overlayText, OverlaySettings overlaySettings) {
    return new TextOverlay() {
      @Override
      public SpannableString getText(long presentationTimeUs) {
        return overlayText;
      }

      @Override
      public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return overlaySettings;
      }
    };
  }

  public static final int TEXT_SIZE_PIXELS = 100;

  private @MonotonicNonNull Bitmap lastBitmap;
  private @MonotonicNonNull SpannableString lastText;

  /**
   * Returns the overlay text displayed at the specified timestamp.
   *
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   */
  public abstract SpannableString getText(long presentationTimeUs);

  @Override
  public Bitmap getBitmap(long presentationTimeUs) {
    SpannableString overlayText = getText(presentationTimeUs);
    if (!overlayText.equals(lastText)) {
      lastText = overlayText;
      TextPaint textPaint = new TextPaint();
      textPaint.setTextSize(TEXT_SIZE_PIXELS);
      StaticLayout staticLayout =
          createStaticLayout(overlayText, textPaint, getSpannedTextWidth(overlayText, textPaint));
      if (lastBitmap == null
          || lastBitmap.getWidth() != staticLayout.getWidth()
          || lastBitmap.getHeight() != staticLayout.getHeight()) {
        lastBitmap =
            Bitmap.createBitmap(
                staticLayout.getWidth(), staticLayout.getHeight(), Bitmap.Config.ARGB_8888);
      }
      Canvas canvas = new Canvas(checkNotNull(lastBitmap));
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      staticLayout.draw(canvas);
    }
    return checkNotNull(lastBitmap);
  }

  private int getSpannedTextWidth(SpannableString text, TextPaint textPaint) {
    // measureText doesn't take scaling spans into account so using a StaticLayout to measure
    // the actual text width, then use a different StaticLayout to draw the text onto a Bitmap.
    int measureTextWidth = (int) textPaint.measureText(text, /* start= */ 0, text.length());
    StaticLayout widthMeasuringLayout = createStaticLayout(text, textPaint, measureTextWidth);
    int lineCount = widthMeasuringLayout.getLineCount();
    float realTextWidth = 0;
    for (int i = 0; i < lineCount; i++) {
      realTextWidth += widthMeasuringLayout.getLineWidth(i);
    }
    return (int) ceil(realTextWidth);
  }

  @SuppressLint("InlinedApi") // Inlined Layout constants.
  private StaticLayout createStaticLayout(SpannableString text, TextPaint textPaint, int width) {
    return SDK_INT >= 23
        ? Api23.getStaticLayout(text, textPaint, width)
        : new StaticLayout(
            text,
            textPaint,
            width,
            Layout.Alignment.ALIGN_NORMAL,
            Layout.DEFAULT_LINESPACING_MULTIPLIER,
            Layout.DEFAULT_LINESPACING_ADDITION,
            /* includepad= */ true);
  }

  @RequiresApi(23)
  private static final class Api23 {
    @DoNotInline
    public static StaticLayout getStaticLayout(
        SpannableString text, TextPaint textPaint, int width) {
      return StaticLayout.Builder.obtain(
              text, /* start= */ 0, /* end= */ text.length(), textPaint, width)
          .build();
    }
  }
}

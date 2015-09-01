/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A view for rendering rich-formatted captions.
 */
public final class SubtitleLayout extends View {

  /**
   * The default bottom padding to apply when {@link Cue#line} is {@link Cue#UNSET_VALUE}, as a
   * fraction of the viewport height.
   */
  public static final float DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f;

  private final List<CuePainter> painters;

  private List<Cue> cues;
  private float fontScale;
  private CaptionStyleCompat style;
  private float bottomPaddingFraction;

  public SubtitleLayout(Context context) {
    this(context, null);
  }

  public SubtitleLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    painters = new ArrayList<>();
    fontScale = 1;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;
  }

  /**
   * Sets the cues to be displayed by the view.
   *
   * @param cues The cues to display.
   */
  public void setCues(List<Cue> cues) {
    if (this.cues == cues) {
      return;
    }
    this.cues = cues;
    // Ensure we have sufficient painters.
    int cueCount = (cues == null) ? 0 : cues.size();
    while (painters.size() < cueCount) {
      painters.add(new CuePainter(getContext()));
    }
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets the scale of the font.
   *
   * @param fontScale The scale of the font.
   */
  public void setFontScale(float fontScale) {
    if (this.fontScale == fontScale) {
      return;
    }
    this.fontScale = fontScale;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Configures the view according to the given style.
   *
   * @param style A style for the view.
   */
  public void setStyle(CaptionStyleCompat style) {
    if (this.style == style) {
      return;
    }
    this.style = style;
    // Invalidate to trigger drawing.
    invalidate();
  }

  /**
   * Sets the bottom padding fraction to apply when {@link Cue#line} is {@link Cue#UNSET_VALUE},
   * as a fraction of the viewport height.
   *
   * @param bottomPaddingFraction The bottom padding fraction.
   */
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    if (this.bottomPaddingFraction == bottomPaddingFraction) {
      return;
    }
    this.bottomPaddingFraction = bottomPaddingFraction;
    // Invalidate to trigger drawing.
    invalidate();
  }

  @Override
  public void dispatchDraw(Canvas canvas) {
    int cueCount = (cues == null) ? 0 : cues.size();
    for (int i = 0; i < cueCount; i++) {
      painters.get(i).draw(cues.get(i), style, fontScale, bottomPaddingFraction, canvas, getLeft(),
          getTop(), getRight(), getBottom());
    }
  }

}

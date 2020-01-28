/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION;
import static com.google.android.exoplayer2.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link SubtitleView.Output} that uses Android's native text tooling via {@link
 * SubtitlePainter}.
 */
/* package */ final class SubtitleTextView extends View implements SubtitleView.Output {

  private final List<SubtitlePainter> painters;

  private List<Cue> cues;
  @Cue.TextSizeType private int textSizeType;
  private float textSize;
  private boolean applyEmbeddedStyles;
  private boolean applyEmbeddedFontSizes;
  private CaptionStyleCompat style;
  private float bottomPaddingFraction;

  public SubtitleTextView(Context context) {
    this(context, /* attrs= */ null);
  }

  public SubtitleTextView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    painters = new ArrayList<>();
    cues = Collections.emptyList();
    textSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    textSize = DEFAULT_TEXT_SIZE_FRACTION;
    applyEmbeddedStyles = true;
    applyEmbeddedFontSizes = true;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;
  }

  @Override
  public void onCues(List<Cue> cues) {
    if (this.cues == cues || this.cues.isEmpty() && cues.isEmpty()) {
      return;
    }
    this.cues = cues;
    // Ensure we have sufficient painters.
    while (painters.size() < cues.size()) {
      painters.add(new SubtitlePainter(getContext()));
    }
    // Invalidate to trigger drawing.
    invalidate();
  }

  @Override
  public void setTextSize(@Cue.TextSizeType int textSizeType, float textSize) {
    if (this.textSizeType == textSizeType && this.textSize == textSize) {
      return;
    }
    this.textSizeType = textSizeType;
    this.textSize = textSize;
    invalidate();
  }

  @Override
  public void setApplyEmbeddedStyles(boolean applyEmbeddedStyles) {
    if (this.applyEmbeddedStyles == applyEmbeddedStyles
        && this.applyEmbeddedFontSizes == applyEmbeddedStyles) {
      return;
    }
    this.applyEmbeddedStyles = applyEmbeddedStyles;
    this.applyEmbeddedFontSizes = applyEmbeddedStyles;
    invalidate();
  }

  @Override
  public void setApplyEmbeddedFontSizes(boolean applyEmbeddedFontSizes) {
    if (this.applyEmbeddedFontSizes == applyEmbeddedFontSizes) {
      return;
    }
    this.applyEmbeddedFontSizes = applyEmbeddedFontSizes;
    invalidate();
  }

  @Override
  public void setStyle(CaptionStyleCompat style) {
    if (this.style == style) {
      return;
    }
    this.style = style;
    invalidate();
  }

  @Override
  public void setBottomPaddingFraction(float bottomPaddingFraction) {
    if (this.bottomPaddingFraction == bottomPaddingFraction) {
      return;
    }
    this.bottomPaddingFraction = bottomPaddingFraction;
    invalidate();
  }

  @Override
  public void dispatchDraw(Canvas canvas) {
    @Nullable List<Cue> cues = this.cues;
    if (cues.isEmpty()) {
      return;
    }

    int rawViewHeight = getHeight();

    // Calculate the cue box bounds relative to the canvas after padding is taken into account.
    int left = getPaddingLeft();
    int top = getPaddingTop();
    int right = getWidth() - getPaddingRight();
    int bottom = rawViewHeight - getPaddingBottom();
    if (bottom <= top || right <= left) {
      // No space to draw subtitles.
      return;
    }
    int viewHeightMinusPadding = bottom - top;

    float defaultViewTextSizePx =
        SubtitleViewUtils.resolveTextSize(
            textSizeType, textSize, rawViewHeight, viewHeightMinusPadding);
    if (defaultViewTextSizePx <= 0) {
      // Text has no height.
      return;
    }

    int cueCount = cues.size();
    for (int i = 0; i < cueCount; i++) {
      Cue cue = cues.get(i);
      float cueTextSizePx =
          SubtitleViewUtils.resolveCueTextSize(cue, rawViewHeight, viewHeightMinusPadding);
      SubtitlePainter painter = painters.get(i);
      painter.draw(
          cue,
          applyEmbeddedStyles,
          applyEmbeddedFontSizes,
          style,
          defaultViewTextSizePx,
          cueTextSizePx,
          bottomPaddingFraction,
          canvas,
          left,
          top,
          right,
          bottom);
    }
  }
}

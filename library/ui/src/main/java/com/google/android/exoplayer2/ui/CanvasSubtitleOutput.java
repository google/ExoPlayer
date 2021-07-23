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
import com.google.android.exoplayer2.text.Cue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link SubtitleView.Output} that uses Android's native layout framework via {@link
 * SubtitlePainter}.
 */
/* package */ final class CanvasSubtitleOutput extends View implements SubtitleView.Output {

  private final List<SubtitlePainter> painters;

  private List<Cue> cues;
  @Cue.TextSizeType private int textSizeType;
  private float textSize;
  private CaptionStyleCompat style;
  private float bottomPaddingFraction;

  public CanvasSubtitleOutput(Context context) {
    this(context, /* attrs= */ null);
  }

  public CanvasSubtitleOutput(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    painters = new ArrayList<>();
    cues = Collections.emptyList();
    textSizeType = Cue.TEXT_SIZE_TYPE_FRACTIONAL;
    textSize = DEFAULT_TEXT_SIZE_FRACTION;
    style = CaptionStyleCompat.DEFAULT;
    bottomPaddingFraction = DEFAULT_BOTTOM_PADDING_FRACTION;
  }

  @Override
  public void update(
      List<Cue> cues,
      CaptionStyleCompat style,
      float textSize,
      @Cue.TextSizeType int textSizeType,
      float bottomPaddingFraction) {
    this.cues = cues;
    this.style = style;
    this.textSize = textSize;
    this.textSizeType = textSizeType;
    this.bottomPaddingFraction = bottomPaddingFraction;
    // Ensure we have sufficient painters.
    while (painters.size() < cues.size()) {
      painters.add(new SubtitlePainter(getContext()));
    }
    // Invalidate to trigger drawing.
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
      if (cue.verticalType != Cue.TYPE_UNSET) {
        cue = repositionVerticalCue(cue);
      }
      float cueTextSizePx =
          SubtitleViewUtils.resolveTextSize(
              cue.textSizeType, cue.textSize, rawViewHeight, viewHeightMinusPadding);
      SubtitlePainter painter = painters.get(i);
      painter.draw(
          cue,
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

  /**
   * Reposition a vertical cue for horizontal display.
   *
   * <p>This class doesn't support rendering vertical text, but if we naively interpret vertical
   * {@link Cue#position} and{@link Cue#line} values for horizontal display then the cues will often
   * be displayed in unexpected positions. For example, the 'default' position for vertical-rl
   * subtitles is the right-hand edge of the viewport, so cues that would appear vertically in this
   * position should appear horizontally at the bottom of the viewport (generally the default
   * position). Similarly left-edge vertical-rl cues should be shown at the top of a horizontal
   * viewport.
   *
   * <p>There isn't a meaningful way to transform {@link Cue#position} and related values (e.g.
   * alignment), so we clear these and allow {@link SubtitlePainter} to do the default behaviour of
   * centering the cue.
   */
  private static Cue repositionVerticalCue(Cue cue) {
    Cue.Builder cueBuilder =
        cue.buildUpon()
            .setPosition(Cue.DIMEN_UNSET)
            .setPositionAnchor(Cue.TYPE_UNSET)
            .setTextAlignment(null);

    if (cue.lineType == Cue.LINE_TYPE_FRACTION) {
      cueBuilder.setLine(1.0f - cue.line, Cue.LINE_TYPE_FRACTION);
    } else {
      cueBuilder.setLine(-cue.line - 1f, Cue.LINE_TYPE_NUMBER);
    }
    switch (cue.lineAnchor) {
      case Cue.ANCHOR_TYPE_END:
        cueBuilder.setLineAnchor(Cue.ANCHOR_TYPE_START);
        break;
      case Cue.ANCHOR_TYPE_START:
        cueBuilder.setLineAnchor(Cue.ANCHOR_TYPE_END);
        break;
      case Cue.ANCHOR_TYPE_MIDDLE:
      case Cue.TYPE_UNSET:
      default:
        // Fall through
    }
    return cueBuilder.build();
  }
}

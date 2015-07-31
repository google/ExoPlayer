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

import com.google.android.exoplayer.util.Util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Draws {@link Cue}s.
 */
/* package */ final class CuePainter {

  private static final String TAG = "CuePainter";

  /**
   * Ratio of inner padding to font size.
   */
  private static final float INNER_PADDING_RATIO = 0.125f;

  /**
   * Use the same line height ratio as WebVtt to match the display with the preview.
   * WebVtt specifies line height as 5.3% of the viewport height.
   */
  private static final float LINE_HEIGHT_FRACTION = 0.0533f;

  /**
   * The default bottom padding to apply when {@link Cue#line} is {@link Cue#UNSET_VALUE}, as a
   * fraction of the viewport height.
   */
  private static final float DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f;

  /**
   * Temporary rectangle used for computing line bounds.
   */
  private final RectF lineBounds = new RectF();

  // Styled dimensions.
  private final float cornerRadius;
  private final float outlineWidth;
  private final float shadowRadius;
  private final float shadowOffset;
  private final float spacingMult;
  private final float spacingAdd;

  private final TextPaint textPaint;
  private final Paint paint;

  // Previous input variables.
  private CharSequence cueText;
  private int cuePosition;
  private Alignment cueAlignment;
  private int foregroundColor;
  private int backgroundColor;
  private int windowColor;
  private int edgeColor;
  private int edgeType;
  private int parentLeft;
  private int parentTop;
  private int parentRight;
  private int parentBottom;

  // Derived drawing variables.
  private StaticLayout textLayout;
  private int textLeft;
  private int textTop;
  private int textPaddingX;

  public CuePainter(Context context) {
    int[] viewAttr = {android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier};
    TypedArray styledAttributes = context.obtainStyledAttributes(null, viewAttr, 0, 0);
    spacingAdd = styledAttributes.getDimensionPixelSize(0, 0);
    spacingMult = styledAttributes.getFloat(1, 1);
    styledAttributes.recycle();

    Resources resources = context.getResources();
    DisplayMetrics displayMetrics = resources.getDisplayMetrics();
    int twoDpInPx = Math.round((2f * displayMetrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
    cornerRadius = twoDpInPx;
    outlineWidth = twoDpInPx;
    shadowRadius = twoDpInPx;
    shadowOffset = twoDpInPx;

    textPaint = new TextPaint();
    textPaint.setAntiAlias(true);
    textPaint.setSubpixelText(true);

    paint = new Paint();
    paint.setAntiAlias(true);
    paint.setStyle(Style.FILL);
  }

  /**
   * Draws the provided {@link Cue} into a canvas with the specified styling.
   * <p>
   * A call to this method is able to use cached results of calculations made during the previous
   * call, and so an instance of this class is able to optimize repeated calls to this method in
   * which the same parameters are passed.
   *
   * @param cue The cue to draw.
   * @param style The style to use when drawing the cue text.
   * @param fontScale The font scale.
   * @param canvas The canvas into which to draw.
   * @param cueBoxLeft The left position of the enclosing cue box.
   * @param cueBoxTop The top position of the enclosing cue box.
   * @param cueBoxRight The right position of the enclosing cue box.
   * @param cueBoxBottom The bottom position of the enclosing cue box.
   */
  public void draw(Cue cue, CaptionStyleCompat style, float fontScale, Canvas canvas,
      int cueBoxLeft, int cueBoxTop, int cueBoxRight, int cueBoxBottom) {
    if (TextUtils.isEmpty(cue.text)) {
      // Nothing to draw.
      return;
    }

    if (TextUtils.equals(cueText, cue.text)
        && cuePosition == cue.position
        && Util.areEqual(cueAlignment, cue.alignment)
        && foregroundColor == style.foregroundColor
        && backgroundColor == style.backgroundColor
        && windowColor == style.windowColor
        && edgeType == style.edgeType
        && edgeColor == style.edgeColor
        && Util.areEqual(textPaint.getTypeface(), style.typeface)
        && parentLeft == cueBoxLeft
        && parentTop == cueBoxTop
        && parentRight == cueBoxRight
        && parentBottom == cueBoxBottom) {
      // We can use the cached layout.
      drawLayout(canvas);
      return;
    }

    cueText = cue.text;
    cuePosition = cue.position;
    cueAlignment = cue.alignment;
    foregroundColor = style.foregroundColor;
    backgroundColor = style.backgroundColor;
    windowColor = style.windowColor;
    edgeType = style.edgeType;
    edgeColor = style.edgeColor;
    textPaint.setTypeface(style.typeface);
    parentLeft = cueBoxLeft;
    parentTop = cueBoxTop;
    parentRight = cueBoxRight;
    parentBottom = cueBoxBottom;

    int parentWidth = parentRight - parentLeft;
    int parentHeight = parentBottom - parentTop;

    float textSize = LINE_HEIGHT_FRACTION * parentHeight * fontScale;
    textPaint.setTextSize(textSize);
    int textPaddingX = (int) (textSize * INNER_PADDING_RATIO + 0.5f);
    int availableWidth = parentWidth - textPaddingX * 2;
    if (availableWidth <= 0) {
      Log.w(TAG, "Skipped drawing subtitle cue (insufficient space)");
      return;
    }

    Alignment layoutAlignment = cueAlignment == null ? Alignment.ALIGN_CENTER : cueAlignment;
    textLayout = new StaticLayout(cueText, textPaint, availableWidth, layoutAlignment, spacingMult,
        spacingAdd, true);

    int textHeight = textLayout.getHeight();
    int textWidth = 0;
    int lineCount = textLayout.getLineCount();
    for (int i = 0; i < lineCount; i++) {
      textWidth = Math.max((int) Math.ceil(textLayout.getLineWidth(i)), textWidth);
    }
    textWidth += textPaddingX * 2;

    int textLeft = (parentWidth - textWidth) / 2;
    int textRight = textLeft + textWidth;
    int textTop = parentBottom - textHeight
        - (int) (parentHeight * DEFAULT_BOTTOM_PADDING_FRACTION);
    int textBottom = textTop + textHeight;

    if (cue.position != Cue.UNSET_VALUE) {
      if (cue.alignment == Alignment.ALIGN_OPPOSITE) {
        textRight = (parentWidth * cue.position) / 100 + parentLeft;
        textLeft = Math.max(textRight - textWidth, parentLeft);
      } else {
        textLeft = (parentWidth * cue.position) / 100 + parentLeft;
        textRight = Math.min(textLeft + textWidth, parentRight);
      }
    }
    if (cue.line != Cue.UNSET_VALUE) {
      textTop = (parentHeight * cue.line) / 100 + parentTop;
      textBottom = textTop + textHeight;
      if (textBottom > parentBottom) {
        textTop = parentBottom - textHeight;
        textBottom = parentBottom;
      }
    }
    textWidth = textRight - textLeft;

    // Update the derived drawing variables.
    this.textLayout = new StaticLayout(cueText, textPaint, textWidth, layoutAlignment, spacingMult,
        spacingAdd, true);
    this.textLeft = textLeft;
    this.textTop = textTop;
    this.textPaddingX = textPaddingX;

    drawLayout(canvas);
  }

  /**
   * Draws {@link #textLayout} into the provided canvas.
   *
   * @param canvas The canvas into which to draw.
   */
  private void drawLayout(Canvas canvas) {
    final StaticLayout layout = textLayout;
    if (layout == null) {
      // Nothing to draw.
      return;
    }

    int saveCount = canvas.save();
    canvas.translate(textLeft, textTop);

    if (Color.alpha(windowColor) > 0) {
      paint.setColor(windowColor);
      canvas.drawRect(-textPaddingX, 0, layout.getWidth() + textPaddingX, layout.getHeight(),
          paint);
    }

    if (Color.alpha(backgroundColor) > 0) {
      paint.setColor(backgroundColor);
      float previousBottom = layout.getLineTop(0);
      int lineCount = layout.getLineCount();
      for (int i = 0; i < lineCount; i++) {
        lineBounds.left = layout.getLineLeft(i) - textPaddingX;
        lineBounds.right = layout.getLineRight(i) + textPaddingX;
        lineBounds.top = previousBottom;
        lineBounds.bottom = layout.getLineBottom(i);
        previousBottom = lineBounds.bottom;
        canvas.drawRoundRect(lineBounds, cornerRadius, cornerRadius, paint);
      }
    }

    if (edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
      textPaint.setStrokeJoin(Join.ROUND);
      textPaint.setStrokeWidth(outlineWidth);
      textPaint.setColor(edgeColor);
      textPaint.setStyle(Style.FILL_AND_STROKE);
      layout.draw(canvas);
    } else if (edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
      textPaint.setShadowLayer(shadowRadius, shadowOffset, shadowOffset, edgeColor);
    } else if (edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED
        || edgeType == CaptionStyleCompat.EDGE_TYPE_DEPRESSED) {
      boolean raised = edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED;
      int colorUp = raised ? Color.WHITE : edgeColor;
      int colorDown = raised ? edgeColor : Color.WHITE;
      float offset = shadowRadius / 2f;
      textPaint.setColor(foregroundColor);
      textPaint.setStyle(Style.FILL);
      textPaint.setShadowLayer(shadowRadius, -offset, -offset, colorUp);
      layout.draw(canvas);
      textPaint.setShadowLayer(shadowRadius, offset, offset, colorDown);
    }

    textPaint.setColor(foregroundColor);
    textPaint.setStyle(Style.FILL);
    layout.draw(canvas);
    textPaint.setShadowLayer(0, 0, 0, 0);

    canvas.restoreToCount(saveCount);
  }

}

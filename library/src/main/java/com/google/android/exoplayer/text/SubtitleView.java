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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * A view for rendering a single caption.
 */
public class SubtitleView extends View {

  /**
   * Ratio of inner padding to font size.
   */
  private static final float INNER_PADDING_RATIO = 0.125f;

  /**
   * Temporary rectangle used for computing line bounds.
   */
  private final RectF lineBounds = new RectF();

  // Styled dimensions.
  private final float cornerRadius;
  private final float outlineWidth;
  private final float shadowRadius;
  private final float shadowOffset;

  private TextPaint textPaint;
  private Paint paint;

  private CharSequence text;

  private int foregroundColor;
  private int backgroundColor;
  private int edgeColor;
  private int edgeType;

  private boolean hasMeasurements;
  private int lastMeasuredWidth;
  private StaticLayout layout;

  private Alignment alignment;
  private float spacingMult;
  private float spacingAdd;
  private int innerPaddingX;

  public SubtitleView(Context context) {
    this(context, null);
  }

  public SubtitleView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SubtitleView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    int[] viewAttr = {android.R.attr.text, android.R.attr.textSize,
        android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier};
    TypedArray a = context.obtainStyledAttributes(attrs, viewAttr, defStyleAttr, 0);
    CharSequence text = a.getText(0);
    int textSize = a.getDimensionPixelSize(1, 15);
    spacingAdd = a.getDimensionPixelSize(2, 0);
    spacingMult = a.getFloat(3, 1);
    a.recycle();

    Resources resources = getContext().getResources();
    DisplayMetrics displayMetrics = resources.getDisplayMetrics();
    int twoDpInPx = Math.round((2f * displayMetrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
    cornerRadius = twoDpInPx;
    outlineWidth = twoDpInPx;
    shadowRadius = twoDpInPx;
    shadowOffset = twoDpInPx;

    textPaint = new TextPaint();
    textPaint.setAntiAlias(true);
    textPaint.setSubpixelText(true);

    alignment = Alignment.ALIGN_CENTER;

    paint = new Paint();
    paint.setAntiAlias(true);

    innerPaddingX = 0;
    setText(text);
    setTextSize(textSize);
    setStyle(CaptionStyleCompat.DEFAULT);
  }

  @Override
  public void setBackgroundColor(int color) {
    backgroundColor = color;
    forceUpdate(false);
  }

  /**
   * Sets the text to be displayed by the view.
   *
   * @param text The text to display.
   */
  public void setText(CharSequence text) {
    this.text = text;
    forceUpdate(true);
  }

  /**
   * Sets the text size in pixels.
   *
   * @param size The text size in pixels.
   */
  public void setTextSize(float size) {
    if (textPaint.getTextSize() != size) {
      textPaint.setTextSize(size);
      innerPaddingX = (int) (size * INNER_PADDING_RATIO + 0.5f);
      forceUpdate(true);
    }
  }

  /**
   * Sets the text alignment.
   *
   * @param textAlignment The text alignment.
   */
  public void setTextAlignment(Alignment textAlignment) {
    alignment = textAlignment;
  }

  /**
   * Configures the view according to the given style.
   *
   * @param style A style for the view.
   */
  public void setStyle(CaptionStyleCompat style) {
    foregroundColor = style.foregroundColor;
    backgroundColor = style.backgroundColor;
    edgeType = style.edgeType;
    edgeColor = style.edgeColor;
    setTypeface(style.typeface);
    super.setBackgroundColor(style.windowColor);
    forceUpdate(true);
  }

  private void setTypeface(Typeface typeface) {
    if (textPaint.getTypeface() != typeface) {
      textPaint.setTypeface(typeface);
      forceUpdate(true);
    }
  }

  private void forceUpdate(boolean needsLayout) {
    if (needsLayout) {
      hasMeasurements = false;
      requestLayout();
    }
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int widthSpec = MeasureSpec.getSize(widthMeasureSpec);

    if (computeMeasurements(widthSpec)) {
      final StaticLayout layout = this.layout;
      final int paddingX = getPaddingLeft() + getPaddingRight() + innerPaddingX * 2;
      final int height = layout.getHeight() + getPaddingTop() + getPaddingBottom();
      int width = 0;
      int lineCount = layout.getLineCount();
      for (int i = 0; i < lineCount; i++) {
        width = Math.max((int) Math.ceil(layout.getLineWidth(i)), width);
      }
      width += paddingX;
      setMeasuredDimension(width, height);
    } else if (Util.SDK_INT >= 11) {
      setTooSmallMeasureDimensionV11();
    } else {
      setMeasuredDimension(0, 0);
    }
  }

  @TargetApi(11)
  private void setTooSmallMeasureDimensionV11() {
    setMeasuredDimension(MEASURED_STATE_TOO_SMALL, MEASURED_STATE_TOO_SMALL);
  }

  @Override
  public void onLayout(boolean changed, int l, int t, int r, int b) {
    final int width = r - l;
    computeMeasurements(width);
  }

  private boolean computeMeasurements(int maxWidth) {
    if (hasMeasurements && maxWidth == lastMeasuredWidth) {
      return true;
    }

    // Account for padding.
    final int paddingX = getPaddingLeft() + getPaddingRight() + innerPaddingX * 2;
    maxWidth -= paddingX;
    if (maxWidth <= 0) {
      return false;
    }

    hasMeasurements = true;
    lastMeasuredWidth = maxWidth;
    layout = new StaticLayout(text, textPaint, maxWidth, alignment, spacingMult, spacingAdd, true);
    return true;
  }

  @Override
  protected void onDraw(Canvas c) {
    final StaticLayout layout = this.layout;
    if (layout == null) {
      return;
    }

    final int saveCount = c.save();
    final int innerPaddingX = this.innerPaddingX;
    c.translate(getPaddingLeft() + innerPaddingX, getPaddingTop());

    final int lineCount = layout.getLineCount();
    final Paint textPaint = this.textPaint;
    final Paint paint = this.paint;
    final RectF bounds = lineBounds;

    if (Color.alpha(backgroundColor) > 0) {
      final float cornerRadius = this.cornerRadius;
      float previousBottom = layout.getLineTop(0);

      paint.setColor(backgroundColor);
      paint.setStyle(Style.FILL);

      for (int i = 0; i < lineCount; i++) {
        bounds.left = layout.getLineLeft(i) - innerPaddingX;
        bounds.right = layout.getLineRight(i) + innerPaddingX;
        bounds.top = previousBottom;
        bounds.bottom = layout.getLineBottom(i);
        previousBottom = bounds.bottom;

        c.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
      }
    }

    if (edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
      textPaint.setStrokeJoin(Join.ROUND);
      textPaint.setStrokeWidth(outlineWidth);
      textPaint.setColor(edgeColor);
      textPaint.setStyle(Style.FILL_AND_STROKE);
      layout.draw(c);
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
      layout.draw(c);
      textPaint.setShadowLayer(shadowRadius, offset, offset, colorDown);
    }

    textPaint.setColor(foregroundColor);
    textPaint.setStyle(Style.FILL);
    layout.draw(c);
    textPaint.setShadowLayer(0, 0, 0, 0);
    c.restoreToCount(saveCount);
  }

}

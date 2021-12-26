package com.google.android.exoplayer2.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.style.LineBackgroundSpan;

public class PaddingBackgroundColorSpan implements LineBackgroundSpan {
  private int mBackgroundColor;
  private Layout.Alignment mAlignment;
  private int mPaddingX = 0;
  private int mPaddingY = 0;
  private RectF mBgRect;

  public PaddingBackgroundColorSpan(int backgroundColor, Layout.Alignment alignment) {
    super();
    mBackgroundColor = backgroundColor;
    mAlignment = alignment;
    mBgRect = new RectF();
  }

  @Override
  public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline, int bottom, CharSequence text, int start, int end, int lnum) {
    String seq = text.subSequence(start, end).toString().replace("\n", "");
    final int textWidth = Math.round(p.measureText(seq));
    final int paintColor = p.getColor();

    int rectLeft = left;
    int rectTop = top - (lnum == 0 ? mPaddingY / 2 : - (mPaddingY / 2));
    int rectRight = right;
    int rectBottom = bottom + mPaddingY / 2;

    mPaddingX = (int) (p.getTextSize() / 5);
    final float radius = (float) mPaddingX / 3;

    // TODO: RTL?
    switch (mAlignment) {
      case ALIGN_NORMAL:
        rectLeft = left - mPaddingX;
        rectRight = left + textWidth + mPaddingX;
        break;
      case ALIGN_CENTER:
        rectLeft = (right - left) / 2 - textWidth / 2 - mPaddingX;
        rectRight = (right - left) / 2 + textWidth / 2 + mPaddingX;
        break;
      case ALIGN_OPPOSITE:
        rectLeft = right - textWidth - mPaddingX;
        rectRight = right + mPaddingX;
        break;
    }
    mBgRect.set(rectLeft, rectTop, rectRight, rectBottom);
    p.setColor(mBackgroundColor);
    c.drawRoundRect(mBgRect, radius, radius, p);
    p.setColor(paintColor);
  }
}

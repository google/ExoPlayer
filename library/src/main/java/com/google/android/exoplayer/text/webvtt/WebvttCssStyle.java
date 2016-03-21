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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.util.Util;

import android.graphics.Typeface;
import android.text.Layout;

/**
 * Style object of a Css style block in a Webvtt file.
 *
 * @see <a href="https://w3c.github.io/webvtt/#applying-css-properties">W3C specification - Apply
 *     CSS properties</a>
 */
/* package */ final class WebvttCssStyle {

  public static final int UNSPECIFIED = -1;

  public static final int STYLE_NORMAL = Typeface.NORMAL;
  public static final int STYLE_BOLD = Typeface.BOLD;
  public static final int STYLE_ITALIC = Typeface.ITALIC;
  public static final int STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC;

  public static final int FONT_SIZE_UNIT_PIXEL = 1;
  public static final int FONT_SIZE_UNIT_EM = 2;
  public static final int FONT_SIZE_UNIT_PERCENT = 3;

  private static final int OFF = 0;
  private static final int ON = 1;

  private String fontFamily;
  private int fontColor;
  private boolean hasFontColor;
  private int backgroundColor;
  private boolean hasBackgroundColor;
  private int linethrough;
  private int underline;
  private int bold;
  private int italic;
  private int fontSizeUnit;
  private float fontSize;
  private Layout.Alignment textAlign;

  public WebvttCssStyle() {
    reset();
  }

  public void reset() {
    fontFamily = null;
    hasFontColor = false;
    hasBackgroundColor = false;
    linethrough = UNSPECIFIED;
    underline = UNSPECIFIED;
    bold = UNSPECIFIED;
    italic = UNSPECIFIED;
    fontSizeUnit = UNSPECIFIED;
    textAlign = null;
  }

  /**
   * Returns the style or {@link #UNSPECIFIED} when no style information is given.
   *
   * @return {@link #UNSPECIFIED}, {@link #STYLE_NORMAL}, {@link #STYLE_BOLD}, {@link #STYLE_BOLD}
   *     or {@link #STYLE_BOLD_ITALIC}.
   */
  public int getStyle() {
    if (bold == UNSPECIFIED && italic == UNSPECIFIED) {
      return UNSPECIFIED;
    }
    return (bold != UNSPECIFIED ? bold : STYLE_NORMAL)
        | (italic != UNSPECIFIED ? italic : STYLE_NORMAL);
  }

  public boolean isLinethrough() {
    return linethrough == ON;
  }

  public WebvttCssStyle setLinethrough(boolean linethrough) {
    this.linethrough = linethrough ? ON : OFF;
    return this;
  }

  public boolean isUnderline() {
    return underline == ON;
  }

  public WebvttCssStyle setUnderline(boolean underline) {
    this.underline = underline ? ON : OFF;
    return this;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public WebvttCssStyle setFontFamily(String fontFamily) {
    this.fontFamily = Util.toLowerInvariant(fontFamily);
    return this;
  }

  public int getFontColor() {
    if (!hasFontColor) {
      throw new IllegalStateException("Font color not defined");
    }
    return fontColor;
  }

  public WebvttCssStyle setFontColor(int color) {
    this.fontColor = color;
    hasFontColor = true;
    return this;
  }

  public boolean hasFontColor() {
    return hasFontColor;
  }

  public int getBackgroundColor() {
    if (!hasBackgroundColor) {
      throw new IllegalStateException("Background color not defined.");
    }
    return backgroundColor;
  }

  public WebvttCssStyle setBackgroundColor(int backgroundColor) {
    this.backgroundColor = backgroundColor;
    hasBackgroundColor = true;
    return this;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }

  public WebvttCssStyle setBold(boolean isBold) {
    bold = isBold ? STYLE_BOLD : STYLE_NORMAL;
    return this;
  }

  public WebvttCssStyle setItalic(boolean isItalic) {
    italic = isItalic ? STYLE_ITALIC : STYLE_NORMAL;
    return this;
  }

  public Layout.Alignment getTextAlign() {
    return textAlign;
  }

  public WebvttCssStyle setTextAlign(Layout.Alignment textAlign) {
    this.textAlign = textAlign;
    return this;
  }

  public WebvttCssStyle setFontSize(float fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  public WebvttCssStyle setFontSizeUnit(short unit) {
    this.fontSizeUnit = unit;
    return this;
  }

  public int getFontSizeUnit() {
    return fontSizeUnit;
  }

  public float getFontSize() {
    return fontSize;
  }

  public void cascadeFrom(WebvttCssStyle style) {
    if (style.hasFontColor) {
      setFontColor(style.fontColor);
    }
    if (style.bold != UNSPECIFIED) {
      bold = style.bold;
    }
    if (style.italic != UNSPECIFIED) {
      italic = style.italic;
    }
    if (style.fontFamily != null) {
      fontFamily = style.fontFamily;
    }
    if (linethrough == UNSPECIFIED) {
      linethrough = style.linethrough;
    }
    if (underline == UNSPECIFIED) {
      underline = style.underline;
    }
    if (textAlign == null) {
      textAlign = style.textAlign;
    }
    if (fontSizeUnit == UNSPECIFIED) {
      fontSizeUnit = style.fontSizeUnit;
      fontSize = style.fontSize;
    }
    if (style.hasBackgroundColor) {
      setBackgroundColor(style.backgroundColor);
    }
  }


}

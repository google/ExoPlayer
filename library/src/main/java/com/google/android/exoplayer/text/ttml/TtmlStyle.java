/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.text.ttml;

import com.google.android.exoplayer.util.Assertions;

import android.graphics.Typeface;
import android.text.Layout;

/**
 * Style object of a <code>TtmlNode</code>
 */
/* package */ final class TtmlStyle {

  public static final short UNSPECIFIED = -1;

  public static final short STYLE_NORMAL = Typeface.NORMAL;
  public static final short STYLE_BOLD = Typeface.BOLD;
  public static final short STYLE_ITALIC = Typeface.ITALIC;
  public static final short STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC;

  public static final short FONT_SIZE_UNIT_PIXEL = 1;
  public static final short FONT_SIZE_UNIT_EM = 2;
  public static final short FONT_SIZE_UNIT_PERCENT = 3;

  private static final short OFF = 0;
  private static final short ON = 1;

  private String fontFamily;
  private int color;
  private boolean colorSpecified;
  private int backgroundColor;
  private boolean backgroundColorSpecified;
  private short linethrough = UNSPECIFIED;
  private short underline = UNSPECIFIED;
  private short bold = UNSPECIFIED;
  private short italic = UNSPECIFIED;
  private short fontSizeUnit = UNSPECIFIED;
  private float fontSize;
  private String id;
  private TtmlStyle inheritableStyle;
  private Layout.Alignment textAlign;

  /**
   * Returns the style or <code>UNSPECIFIED</code> when no style information is given.
   *
   * @return UNSPECIFIED, STYLE_NORMAL, STYLE_BOLD, STYLE_BOLD or STYLE_BOLD_ITALIC
   */
  public short getStyle() {
    if (bold == UNSPECIFIED && italic == UNSPECIFIED) {
      return UNSPECIFIED;
    }

    short style = STYLE_NORMAL;
    if (bold != UNSPECIFIED) {
      style += bold;
    }
    if (italic != UNSPECIFIED){
      style += italic;
    }
    return style;
  }

  public boolean isLinethrough() {
    return linethrough == ON;
  }

  public TtmlStyle setLinethrough(boolean linethrough) {
    Assertions.checkState(inheritableStyle == null);
    this.linethrough = linethrough ? ON : OFF;
    return this;
  }

  public boolean isUnderline() {
    return underline == ON;
  }

  public TtmlStyle setUnderline(boolean underline) {
    Assertions.checkState(inheritableStyle == null);
    this.underline = underline ? ON : OFF;
    return this;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public TtmlStyle setFontFamily(String fontFamily) {
    Assertions.checkState(inheritableStyle == null);
    this.fontFamily = fontFamily;
    return this;
  }

  public int getColor() {
    return color;
  }

  public TtmlStyle setColor(int color) {
    Assertions.checkState(inheritableStyle == null);
    this.color = color;
    colorSpecified = true;
    return this;
  }

  public boolean hasColorSpecified() {
    return colorSpecified;
  }

  public int getBackgroundColor() {
    return backgroundColor;
  }

  public TtmlStyle setBackgroundColor(int backgroundColor) {
    this.backgroundColor = backgroundColor;
    backgroundColorSpecified = true;
    return this;
  }

  public boolean hasBackgroundColorSpecified() {
    return backgroundColorSpecified;
  }

  public TtmlStyle setBold(boolean isBold) {
    Assertions.checkState(inheritableStyle == null);
    bold = isBold ? STYLE_BOLD : STYLE_NORMAL;
    return this;
  }

  public TtmlStyle setItalic(boolean isItalic) {
    Assertions.checkState(inheritableStyle == null);
    italic = isItalic ? STYLE_ITALIC : STYLE_NORMAL;
    return this;
  }

  /**
   * Inherits from an ancestor style. Properties like <i>tts:backgroundColor</i> which
   * are not inheritable are not inherited as well as properties which are already set locally
   * are never overridden.
   *
   * @param ancestor the ancestor style to inherit from
   */
  public TtmlStyle inherit(TtmlStyle ancestor) {
    return inherit(ancestor, false);
  }

  /**
   * Chains this style to referential style. Local properties which are already set
   * are never overridden.
   *
   * @param ancestor the referential style to inherit from
   */
  public TtmlStyle chain(TtmlStyle ancestor) {
    return inherit(ancestor, true);
  }

  private TtmlStyle inherit(TtmlStyle ancestor, boolean chaining) {
    if (ancestor != null) {
      if (!colorSpecified && ancestor.colorSpecified) {
        setColor(ancestor.color);
      }
      if (bold == UNSPECIFIED) {
        bold = ancestor.bold;
      }
      if (italic == UNSPECIFIED) {
        italic = ancestor.italic;
      }
      if (fontFamily == null) {
        fontFamily = ancestor.fontFamily;
      }
      if (linethrough == UNSPECIFIED) {
        linethrough = ancestor.linethrough;
      }
      if (underline == UNSPECIFIED) {
        underline = ancestor.underline;
      }
      if (textAlign == null) {
        textAlign = ancestor.textAlign;
      }
      if (fontSizeUnit == UNSPECIFIED) {
        fontSizeUnit = ancestor.fontSizeUnit;
        fontSize = ancestor.fontSize;
      }
      // attributes not inherited as of http://www.w3.org/TR/ttml1/
      if (chaining && !backgroundColorSpecified && ancestor.backgroundColorSpecified) {
        setBackgroundColor(ancestor.backgroundColor);
      }
    }
    return this;
  }

  public TtmlStyle setId(String id) {
    this.id = id;
    return this;
  }

  public String getId() {
    return id;
  }

  public Layout.Alignment getTextAlign() {
    return textAlign;
  }

  public TtmlStyle setTextAlign(Layout.Alignment textAlign) {
    this.textAlign = textAlign;
    return this;
  }

  public TtmlStyle setFontSize(float fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  public TtmlStyle setFontSizeUnit(short unit) {
    this.fontSizeUnit = unit;
    return this;
  }

  public short getFontSizeUnit() {
    return fontSizeUnit;
  }

  public float getFontSize() {
    return fontSize;
  }

}

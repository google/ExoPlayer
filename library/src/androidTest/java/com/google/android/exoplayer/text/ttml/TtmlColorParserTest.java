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
package com.google.android.exoplayer.text.ttml;

import android.graphics.Color;
import android.test.InstrumentationTestCase;

/**
 * Unit test for <code>TtmlColorParser</code>.
 */
public class TtmlColorParserTest extends InstrumentationTestCase {

  public void testHexCodeParsing() {
    assertEquals(Color.WHITE, TtmlColorParser.parseColor("#FFFFFF"));
    assertEquals(Color.WHITE, TtmlColorParser.parseColor("#FFFFFFFF"));
    assertEquals(Color.parseColor("#FF123456"), TtmlColorParser.parseColor("#123456"));
    // Hex colors in TTML are RGBA, where-as {@link Color#parseColor} takes ARGB.
    assertEquals(Color.parseColor("#00FFFFFF"), TtmlColorParser.parseColor("#FFFFFF00"));
    assertEquals(Color.parseColor("#78123456"), TtmlColorParser.parseColor("#12345678"));
  }

  public void testColorNameParsing() {
    assertEquals(TtmlColorParser.TRANSPARENT, TtmlColorParser.parseColor("transparent"));
    assertEquals(TtmlColorParser.BLACK, TtmlColorParser.parseColor("black"));
    assertEquals(TtmlColorParser.GRAY, TtmlColorParser.parseColor("gray"));
    assertEquals(TtmlColorParser.SILVER, TtmlColorParser.parseColor("silver"));
    assertEquals(TtmlColorParser.WHITE, TtmlColorParser.parseColor("white"));
    assertEquals(TtmlColorParser.MAROON, TtmlColorParser.parseColor("maroon"));
    assertEquals(TtmlColorParser.RED, TtmlColorParser.parseColor("red"));
    assertEquals(TtmlColorParser.PURPLE, TtmlColorParser.parseColor("purple"));
    assertEquals(TtmlColorParser.FUCHSIA, TtmlColorParser.parseColor("fuchsia"));
    assertEquals(TtmlColorParser.MAGENTA, TtmlColorParser.parseColor("magenta"));
    assertEquals(TtmlColorParser.GREEN, TtmlColorParser.parseColor("green"));
    assertEquals(TtmlColorParser.LIME, TtmlColorParser.parseColor("lime"));
    assertEquals(TtmlColorParser.OLIVE, TtmlColorParser.parseColor("olive"));
    assertEquals(TtmlColorParser.YELLOW, TtmlColorParser.parseColor("yellow"));
    assertEquals(TtmlColorParser.NAVY, TtmlColorParser.parseColor("navy"));
    assertEquals(TtmlColorParser.BLUE, TtmlColorParser.parseColor("blue"));
    assertEquals(TtmlColorParser.TEAL, TtmlColorParser.parseColor("teal"));
    assertEquals(TtmlColorParser.AQUA, TtmlColorParser.parseColor("aqua"));
    assertEquals(TtmlColorParser.CYAN, TtmlColorParser.parseColor("cyan"));
  }

  public void testParseUnknownColor() {
    try {
      TtmlColorParser.parseColor("colorOfAnElectron");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testParseNull() {
    try {
      TtmlColorParser.parseColor(null);
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testParseEmpty() {
    try {
      TtmlColorParser.parseColor("");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testRgbColorParsing() {
    assertEquals(Color.WHITE, TtmlColorParser.parseColor("rgb(255,255,255)"));
    // spaces do not matter
    assertEquals(Color.WHITE, TtmlColorParser.parseColor("   rgb (      255, 255, 255)"));
  }

  public void testRgbColorParsing_rgbValuesOutOfBounds() {
    int outOfBounds = TtmlColorParser.parseColor("rgb(999, 999, 999)");
    int color = Color.rgb(999, 999, 999);
    // behave like framework Color behaves
    assertEquals(color, outOfBounds);
  }

  public void testRgbColorParsing_rgbValuesNegative() {
    try {
      TtmlColorParser.parseColor("rgb(-4, 55, 209)");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testRgbaColorParsing() {
    assertEquals(Color.WHITE, TtmlColorParser.parseColor("rgba(255,255,255,0)"));
    assertEquals(Color.argb(0, 255, 255, 255), TtmlColorParser.parseColor("rgba(255,255,255,255)"));
    assertEquals(Color.BLACK, TtmlColorParser.parseColor("rgba(0, 0, 0, 0)"));
    assertEquals(Color.argb(0, 0, 0, 0), TtmlColorParser.parseColor("rgba(0, 0, 0, 255)"));
    assertEquals(Color.RED, TtmlColorParser.parseColor("rgba(255, 0, 0, 0)"));
    assertEquals(Color.argb(0, 255, 0, 0), TtmlColorParser.parseColor("rgba(255, 0, 0, 255)"));
    assertEquals(Color.argb(205, 255, 0, 0), TtmlColorParser.parseColor("rgba(255, 0, 0, 50)"));
  }
}

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
package com.google.android.exoplayer2.util;

import android.graphics.Color;
import android.test.InstrumentationTestCase;

/**
 * Unit test for <code>ColorParser</code>.
 */
public class ColorParserTest extends InstrumentationTestCase {

  // Negative tests.

  public void testParseUnknownColor() {
    try {
      ColorParser.parseTtmlColor("colorOfAnElectron");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testParseNull() {
    try {
      ColorParser.parseTtmlColor(null);
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testParseEmpty() {
    try {
      ColorParser.parseTtmlColor("");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  public void testRgbColorParsingRgbValuesNegative() {
    try {
      ColorParser.parseTtmlColor("rgb(-4, 55, 209)");
      fail();
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  // Positive tests.

  public void testHexCodeParsing() {
    assertEquals(Color.WHITE, ColorParser.parseTtmlColor("#FFFFFF"));
    assertEquals(Color.WHITE, ColorParser.parseTtmlColor("#FFFFFFFF"));
    assertEquals(Color.parseColor("#FF123456"), ColorParser.parseTtmlColor("#123456"));
    // Hex colors in ColorParser are RGBA, where-as {@link Color#parseColor} takes ARGB.
    assertEquals(Color.parseColor("#00FFFFFF"), ColorParser.parseTtmlColor("#FFFFFF00"));
    assertEquals(Color.parseColor("#78123456"), ColorParser.parseTtmlColor("#12345678"));
  }

  public void testRgbColorParsing() {
    assertEquals(Color.WHITE, ColorParser.parseTtmlColor("rgb(255,255,255)"));
    // Spaces are ignored.
    assertEquals(Color.WHITE, ColorParser.parseTtmlColor("   rgb (      255, 255, 255)"));
  }

  public void testRgbColorParsingRgbValuesOutOfBounds() {
    int outOfBounds = ColorParser.parseTtmlColor("rgb(999, 999, 999)");
    int color = Color.rgb(999, 999, 999);
    // Behave like the framework does.
    assertEquals(color, outOfBounds);
  }

  public void testRgbaColorParsing() {
    assertEquals(Color.WHITE, ColorParser.parseTtmlColor("rgba(255,255,255,255)"));
    assertEquals(Color.argb(255, 255, 255, 255),
        ColorParser.parseTtmlColor("rgba(255,255,255,255)"));
    assertEquals(Color.BLACK, ColorParser.parseTtmlColor("rgba(0, 0, 0, 255)"));
    assertEquals(Color.argb(0, 0, 0, 255), ColorParser.parseTtmlColor("rgba(0, 0, 255, 0)"));
    assertEquals(Color.RED, ColorParser.parseTtmlColor("rgba(255, 0, 0, 255)"));
    assertEquals(Color.argb(0, 255, 0, 255), ColorParser.parseTtmlColor("rgba(255, 0, 255, 0)"));
    assertEquals(Color.argb(205, 255, 0, 0), ColorParser.parseTtmlColor("rgba(255, 0, 0, 205)"));
  }
}

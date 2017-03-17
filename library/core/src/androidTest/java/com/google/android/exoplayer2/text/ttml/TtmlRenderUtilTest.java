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
package com.google.android.exoplayer2.text.ttml;

import android.graphics.Color;
import android.test.InstrumentationTestCase;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit test for <code>TtmlRenderUtil</code>
 */
public class TtmlRenderUtilTest extends InstrumentationTestCase {

  public void testResolveStyleNoStyleAtAll() {
    assertNull(TtmlRenderUtil.resolveStyle(null, null, null));
  }
  public void testResolveStyleSingleReferentialStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0"};

    assertSame(globalStyles.get("s0"),
        TtmlRenderUtil.resolveStyle(null, styleIds, globalStyles));
  }
  public void testResolveStyleMultipleReferentialStyles() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0", "s1"};

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(null, styleIds, globalStyles);
    assertNotSame(globalStyles.get("s0"), resolved);
    assertNotSame(globalStyles.get("s1"), resolved);
    assertNull(resolved.getId());

    // inherited from s0
    assertEquals(Color.BLACK, resolved.getBackgroundColor());
    // inherited from s1
    assertEquals(Color.RED, resolved.getFontColor());
    // merged from s0 and s1
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, resolved.getStyle());
  }

  public void testResolveMergeSingleReferentialStyleIntoInlineStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0"};
    TtmlStyle style = new TtmlStyle();
    style.setBackgroundColor(Color.YELLOW);

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    assertSame(style, resolved);

    // inline attribute not overridden
    assertEquals(Color.YELLOW, resolved.getBackgroundColor());
    // inherited from referential style
    assertEquals(TtmlStyle.STYLE_BOLD, resolved.getStyle());
  }


  public void testResolveMergeMultipleReferentialStylesIntoInlineStyle() {
    Map<String, TtmlStyle> globalStyles = getGlobalStyles();
    String[] styleIds = {"s0", "s1"};
    TtmlStyle style = new TtmlStyle();
    style.setBackgroundColor(Color.YELLOW);

    TtmlStyle resolved = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    assertSame(style, resolved);

    // inline attribute not overridden
    assertEquals(Color.YELLOW, resolved.getBackgroundColor());
    // inherited from both referential style
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, resolved.getStyle());
  }

  public void testResolveStyleOnlyInlineStyle() {
    TtmlStyle inlineStyle = new TtmlStyle();
    assertSame(inlineStyle, TtmlRenderUtil.resolveStyle(inlineStyle, null, null));
  }

  private Map<String, TtmlStyle> getGlobalStyles() {
    Map<String, TtmlStyle> globalStyles = new HashMap<>();

    TtmlStyle s0 = new TtmlStyle();
    s0.setId("s0");
    s0.setBackgroundColor(Color.BLACK);
    s0.setBold(true);
    globalStyles.put(s0.getId(), s0);

    TtmlStyle s1 = new TtmlStyle();
    s1.setId("s1");
    s1.setBackgroundColor(Color.RED);
    s1.setFontColor(Color.RED);
    s1.setItalic(true);
    globalStyles.put(s1.getId(), s1);

    return globalStyles;
  }

}

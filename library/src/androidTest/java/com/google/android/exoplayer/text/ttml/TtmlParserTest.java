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

import com.google.android.exoplayer.text.Cue;

import android.graphics.Color;
import android.test.InstrumentationTestCase;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Unit test for {@link TtmlParser}.
 */
public final class TtmlParserTest extends InstrumentationTestCase {

  private static final String INLINE_ATTRIBUTES_TTML_FILE =
      "ttml/inline_style_attributes.xml";
  private static final String INHERIT_STYLE_TTML_FILE =
      "ttml/inherit_style.xml";
  private static final String INHERIT_STYLE_OVERRIDE_TTML_FILE =
      "ttml/inherit_and_override_style.xml";
  private static final String INHERIT_GLOBAL_AND_PARENT_TTML_FILE =
      "ttml/inherit_global_and_parent.xml";
  private static final String INHERIT_MULTIPLE_STYLES_TTML_FILE =
      "ttml/inherit_multiple_styles.xml";
  private static final String CHAIN_MULTIPLE_STYLES_TTML_FILE =
      "ttml/chain_multiple_styles.xml";
  private static final String NO_UNDERLINE_LINETHROUGH_TTML_FILE =
      "ttml/no_underline_linethrough.xml";
  private static final String NAMESPACE_CONFUSION_TTML_FILE =
      "ttml/namespace_confusion.xml";
  private static final String NAMESPACE_NOT_DECLARED_TTML_FILE =
      "ttml/namespace_not_declared.xml";

  public void testInlineAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;
    assertEquals(Color.parseColor("yellow"), firstPStyle.getColor());
    assertEquals(Color.parseColor("blue"), firstPStyle.getBackgroundColor());
    assertEquals("serif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isUnderline());
  }

  public void testInheritInlineAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_ITALIC,
        Color.CYAN, Color.parseColor("lime"), false, true, null);
  }

  public void testInheritGlobalStyle() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());
    assertSpans(subtitle, 10, "text 1", "serif", TtmlStyle.STYLE_BOLD_ITALIC,
        Color.BLUE, Color.YELLOW, true, false, null);
  }

  public void testInheritGlobalStyleOverriddenByInlineAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_OVERRIDE_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    assertSpans(subtitle, 10, "text 1", "serif", TtmlStyle.STYLE_BOLD_ITALIC, Color.BLUE,
        Color.YELLOW, true, false, null);
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_ITALIC, Color.RED,
        Color.YELLOW, true, false, null);
  }

  public void testInheritGlobalAndParent() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    assertSpans(subtitle, 10, "text 1", "sansSerif", TtmlStyle.STYLE_NORMAL,
        Color.RED, Color.parseColor("lime"), false, true, Layout.Alignment.ALIGN_CENTER);
    assertSpans(subtitle, 20, "text 2", "serif", TtmlStyle.STYLE_BOLD_ITALIC,
        Color.BLUE, Color.YELLOW, true, true, Layout.Alignment.ALIGN_CENTER);
  }

  public void testInheritMultipleStyles() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    assertSpans(subtitle, 10, "text 1", "sansSerif", TtmlStyle.STYLE_BOLD_ITALIC,
        Color.BLUE, Color.YELLOW, false, true, null);
  }

  public void testInheritMultipleStylesWithoutLocalAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_BOLD_ITALIC,
        Color.BLUE, Color.BLACK, false, true, null);

  }

  public void testMergeMultipleStylesWithParentStyle() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    assertSpans(subtitle, 30, "text 2.5", "sansSerifInline", TtmlStyle.STYLE_ITALIC,
        Color.RED, Color.YELLOW, true, true, null);
  }

  public void testEmptyStyleAttribute() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fourthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 3);

    assertNull(queryChildrenForTag(fourthDiv, TtmlNode.TAG_P, 0).getStyleIds());
  }

  public void testNonexistingStyleId() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fifthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 4);

    assertEquals(1, queryChildrenForTag(fifthDiv, TtmlNode.TAG_P, 0).getStyleIds().length);
  }

  public void testNonExistingAndExistingStyleIdWithRedundantSpaces() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode sixthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 5);

    String[] styleIds = queryChildrenForTag(sixthDiv, TtmlNode.TAG_P, 0).getStyleIds();
    assertEquals(2, styleIds.length);
  }

  public void testMultipleChaining() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(CHAIN_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    Map<String, TtmlStyle> globalStyles = subtitle.getGlobalStyles();

    TtmlStyle style = globalStyles.get("s2");
    assertEquals("serif", style.getFontFamily());
    assertEquals(Color.RED, style.getBackgroundColor());
    assertEquals(Color.BLACK, style.getColor());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
    assertTrue(style.isLinethrough());

    style = globalStyles.get("s3");
    // only difference: color must be RED
    assertEquals(Color.RED, style.getColor());
    assertEquals("serif", style.getFontFamily());
    assertEquals(Color.RED, style.getBackgroundColor());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
    assertTrue(style.isLinethrough());
  }

  public void testNoUnderline() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertFalse("noUnderline from inline attribute expected", style.isUnderline());
  }

  public void testNoLinethrough() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertFalse("noLineThrough from inline attribute expected in second pNode",
        style.isLinethrough());
  }

  public void testNamspaceConfusionDoesNotHurt() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(NAMESPACE_CONFUSION_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;

    assertNotNull(style);
    assertEquals(Color.BLACK, style.getBackgroundColor());
    assertEquals(Color.YELLOW, style.getColor());
    assertEquals(TtmlStyle.STYLE_ITALIC, style.getStyle());
    assertEquals("sansSerif", style.getFontFamily());
    assertFalse(style.isUnderline());
    assertTrue(style.isLinethrough());

  }

  public void testNamespaceNotDeclared() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(NAMESPACE_NOT_DECLARED_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;

    assertNotNull(style);
    assertEquals(Color.BLACK, style.getBackgroundColor());
    assertEquals(Color.YELLOW, style.getColor());
    assertEquals(TtmlStyle.STYLE_ITALIC, style.getStyle());
    assertEquals("sansSerif", style.getFontFamily());
    assertFalse(style.isUnderline());
    assertTrue(style.isLinethrough());

  }

  private void assertSpans(TtmlSubtitle subtitle, int second,
      String text, String font, int fontStyle,
      int backgroundColor, int color, boolean isUnderline,
      boolean isLinethrough, Layout.Alignment alignment) {

    long timeUs = second * 1000000;
    List<Cue> cues = subtitle.getCues(timeUs);

    assertEquals(1, cues.size());
    assertEquals(text, String.valueOf(cues.get(0).text));

    assertEquals("single cue expected for timeUs: " + timeUs, 1, cues.size());
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;

    TypefaceSpan[] typefaceSpans = spannable.getSpans(0, spannable.length(), TypefaceSpan.class);
    assertEquals(font, typefaceSpans[typefaceSpans.length - 1].getFamily());

    StyleSpan[] styleSpans = spannable.getSpans(0, spannable.length(), StyleSpan.class);
    assertEquals(fontStyle, styleSpans[styleSpans.length - 1].getStyle());

    UnderlineSpan[] underlineSpans = spannable.getSpans(0, spannable.length(),
        UnderlineSpan.class);
    assertEquals(isUnderline ? "must be underlined" : "must not be underlined",
        isUnderline ? 1 : 0, underlineSpans.length);

    StrikethroughSpan[] striketroughSpans = spannable.getSpans(0, spannable.length(),
        StrikethroughSpan.class);
    assertEquals(isLinethrough ? "must be strikethrough" : "must not be strikethrough",
        isLinethrough ? 1 : 0, striketroughSpans.length);

    BackgroundColorSpan[] backgroundColorSpans =
        spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
    if (backgroundColor != 0) {
      assertEquals(backgroundColor, backgroundColorSpans[backgroundColorSpans.length - 1]
          .getBackgroundColor());
    } else {
      assertEquals(0, backgroundColorSpans.length);
    }

    ForegroundColorSpan[] foregroundColorSpans =
        spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class);
    assertEquals(color, foregroundColorSpans[foregroundColorSpans.length - 1].getForegroundColor());

    if (alignment != null) {
      AlignmentSpan.Standard[] alignmentSpans =
          spannable.getSpans(0, spannable.length(), AlignmentSpan.Standard.class);
      assertEquals(1, alignmentSpans.length);
      assertEquals(alignment, alignmentSpans[0].getAlignment());
    } else {
      assertEquals(0, spannable.getSpans
          (0, spannable.length(), AlignmentSpan.Standard.class).length);
    }
  }

  private TtmlNode queryChildrenForTag(TtmlNode node, String tag, int pos) {
    int count = 0;
    for (int i = 0; i < node.getChildCount(); i++) {
      if (tag.equals(node.getChild(i).tag)) {
        if (pos == count++) {
          return node.getChild(i);
        }
      }
    }
    return null;
  }

  private TtmlSubtitle getSubtitle(String file) throws IOException {
    TtmlParser ttmlParser = new TtmlParser(false);
    InputStream inputStream = getInstrumentation().getContext()
        .getResources().getAssets().open(file);

    return (TtmlSubtitle) ttmlParser.parse(inputStream);
  }
}

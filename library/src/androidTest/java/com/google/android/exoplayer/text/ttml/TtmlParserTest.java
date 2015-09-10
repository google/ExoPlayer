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
import android.text.Layout;

import java.io.IOException;
import java.io.InputStream;

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
  private static final String NON_INHERTABLE_PROPERTIES_TTML_FILE =
      "ttml/non_inheritable_properties.xml";
  private static final String INHERIT_MULTIPLE_STYLES_TTML_FILE =
      "ttml/inherit_multiple_styles.xml";
  private static final String CHAIN_MULTIPLE_STYLES_TTML_FILE =
      "ttml/chain_multiple_styles.xml";
  private static final String NO_UNDERLINE_LINETHROUGH_TTML_FILE =
      "ttml/no_underline_linethrough.xml";
  private static final String INSTANCE_CREATION_TTML_FILE =
      "ttml/instance_creation.xml";
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

    TtmlNode root = subtitle.getRoot();
    // inherite inline attributes
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode secondDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);
    TtmlStyle secondPStyle = queryChildrenForTag(secondDiv, TtmlNode.TAG_P, 0).style;
    assertEquals(Color.parseColor("lime"), secondPStyle.getColor());
    assertFalse(secondPStyle.hasBackgroundColorSpecified());
    assertEquals(0, secondPStyle.getBackgroundColor());
    assertEquals("sansSerif", secondPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_ITALIC, secondPStyle.getStyle());
    assertTrue(secondPStyle.isLinethrough());
  }

  public void testInheritGlobalStyle() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

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

  public void testInheritGlobalStyleOverriddenByInlineAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_OVERRIDE_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    // first pNode inherits global style
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;
    assertEquals(Color.parseColor("yellow"), firstPStyle.getColor());
    assertEquals(Color.parseColor("blue"), firstPStyle.getBackgroundColor());
    assertEquals("serif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isUnderline());

    // second pNode inherits global style and overrides with attribute
    TtmlNode secondDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);
    TtmlStyle secondPStyle = queryChildrenForTag(secondDiv, TtmlNode.TAG_P, 0).style;
    assertEquals(Color.parseColor("yellow"), secondPStyle.getColor());
    assertEquals(Color.parseColor("red"), secondPStyle.getBackgroundColor());
    assertEquals("sansSerif", secondPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_ITALIC, secondPStyle.getStyle());
    assertTrue(secondPStyle.isUnderline());
  }

  public void testInheritGlobalAndParent() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    // first pNode inherits parent style
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;

    assertFalse(firstPStyle.hasBackgroundColorSpecified());
    assertEquals(0, firstPStyle.getBackgroundColor());

    assertEquals(Color.parseColor("lime"), firstPStyle.getColor());
    assertEquals("sansSerif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_NORMAL, firstPStyle.getStyle());
    assertTrue(firstPStyle.isLinethrough());

    // second pNode inherits parent style and overrides with global style
    TtmlNode secondDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);
    TtmlStyle secondPStyle = queryChildrenForTag(secondDiv, TtmlNode.TAG_P, 0).style;
    // attributes overridden by global style
    assertEquals(Color.parseColor("blue"), secondPStyle.getBackgroundColor());
    assertEquals(Color.parseColor("yellow"), secondPStyle.getColor());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, secondPStyle.getStyle());
    assertEquals("serif", secondPStyle.getFontFamily());
    assertTrue(secondPStyle.isUnderline());
    assertEquals(Layout.Alignment.ALIGN_CENTER, secondPStyle.getTextAlign());
  }

  public void testNonInheritablePropertiesAreNotInherited() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(NON_INHERTABLE_PROPERTIES_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlNode firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0);
    TtmlStyle spanStyle = queryChildrenForTag(firstPStyle, TtmlNode.TAG_SPAN, 0).style;

    assertFalse("background color must not be inherited from a context node",
        spanStyle.hasBackgroundColorSpecified());
  }

  public void testInheritMultipleStyles() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;

    assertEquals(Color.parseColor("blue"), firstPStyle.getBackgroundColor());
    assertEquals(Color.parseColor("yellow"), firstPStyle.getColor());
    assertEquals("sansSerif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isLinethrough());
  }

  public void testInheritMultipleStylesWithoutLocalAttributes() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode secondDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);
    TtmlStyle firstPStyle = queryChildrenForTag(secondDiv, TtmlNode.TAG_P, 0).style;

    assertEquals(Color.parseColor("blue"), firstPStyle.getBackgroundColor());
    assertEquals(Color.parseColor("black"), firstPStyle.getColor());
    assertEquals("sansSerif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isLinethrough());

  }

  public void testMergeMultipleStylesWithParentStyle() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode thirdDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 2);
    TtmlStyle firstPStyle = queryChildrenForTag(thirdDiv, TtmlNode.TAG_P, 0).style;

    // inherit from first global style
    assertEquals(Color.parseColor("red"), firstPStyle.getBackgroundColor());
    // inherit from second global style
    assertTrue(firstPStyle.isLinethrough());
    // inherited from parent node
    assertEquals("sansSerifInline", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isUnderline());
    assertEquals(Color.parseColor("yellow"), firstPStyle.getColor());
  }

  public void testEmptyStyleAttribute() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fourthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 3);

    // no styles specified
    assertNull(queryChildrenForTag(fourthDiv, TtmlNode.TAG_P, 0).style);
  }

  public void testNonexistingStyleId() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fifthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 4);

    // no styles specified
    assertNull(queryChildrenForTag(fifthDiv, TtmlNode.TAG_P, 0).style);
  }

  public void testNonExistingAndExistingStyleId() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode sixthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 5);

    // no styles specified
    TtmlStyle style = queryChildrenForTag(sixthDiv, TtmlNode.TAG_P, 0).style;
    assertNotNull(style);
    assertEquals(Color.RED, style.getBackgroundColor());
  }

  public void testMultipleChaining() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(CHAIN_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);

    // no styles specified
    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertEquals("serif", style.getFontFamily());
    assertEquals(Color.RED, style.getBackgroundColor());
    assertEquals(Color.BLACK, style.getColor());
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


  public void testOnlySingleInstance() throws IOException {
    TtmlSubtitle subtitle = getSubtitle(INSTANCE_CREATION_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlNode secondDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);
    TtmlNode thirdDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 2);

    TtmlNode firstP = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0);
    TtmlNode secondP = queryChildrenForTag(secondDiv, TtmlNode.TAG_P, 0);
    TtmlNode secondSpan = queryChildrenForTag(secondP, TtmlNode.TAG_SPAN, 0);
    TtmlNode thirdP = queryChildrenForTag(thirdDiv, TtmlNode.TAG_P, 0);
    TtmlNode thirdSpan = queryChildrenForTag(secondP, TtmlNode.TAG_SPAN, 0);

    // inherit the same instance down the tree if possible
    assertSame(body.style, firstP.style);
    assertSame(firstP.style, secondP.style);
    assertSame(secondP.style, secondSpan.style);

    // if a backgroundColor is involved it does not help
    assertNotSame(thirdP.style.getInheritableStyle(), thirdSpan.style);
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

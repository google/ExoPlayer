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
package com.google.android.exoplayer2.text.webvtt;

import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import java.util.Collections;

/**
 * Unit test for {@link WebvttCueParser}.
 */
public final class WebvttCueParserTest extends InstrumentationTestCase {

  public void testParseStrictValidClassesAndTrailingTokens() throws Exception {
    Spanned text = parseCueText("<v.first.loud Esme>"
        + "This <u.style1.style2 some stuff>is</u> text with <b.foo><i.bar>html</i></b> tags");

    assertEquals("This is text with html tags", text.toString());

    UnderlineSpan[] underlineSpans = getSpans(text, UnderlineSpan.class);
    StyleSpan[] styleSpans = getSpans(text, StyleSpan.class);
    assertEquals(1, underlineSpans.length);
    assertEquals(2, styleSpans.length);
    assertEquals(Typeface.ITALIC, styleSpans[0].getStyle());
    assertEquals(Typeface.BOLD, styleSpans[1].getStyle());

    assertEquals(5, text.getSpanStart(underlineSpans[0]));
    assertEquals(7, text.getSpanEnd(underlineSpans[0]));
    assertEquals(18, text.getSpanStart(styleSpans[0]));
    assertEquals(18, text.getSpanStart(styleSpans[1]));
    assertEquals(22, text.getSpanEnd(styleSpans[0]));
    assertEquals(22, text.getSpanEnd(styleSpans[1]));
  }

  public void testParseStrictValidUnsupportedTagsStrippedOut() throws Exception {
    Spanned text = parseCueText("<v.first.loud Esme>This <unsupported>is</unsupported> text with "
        + "<notsupp><invalid>html</invalid></notsupp> tags");
    assertEquals("This is text with html tags", text.toString());
    assertEquals(0, getSpans(text, UnderlineSpan.class).length);
    assertEquals(0, getSpans(text, StyleSpan.class).length);
  }

  public void testParseWellFormedUnclosedEndAtCueEnd() throws Exception {
    Spanned text = parseCueText("An <u some trailing stuff>unclosed u tag with "
        + "<i>italic</i> inside");

    assertEquals("An unclosed u tag with italic inside", text.toString());

    UnderlineSpan[] underlineSpans = getSpans(text, UnderlineSpan.class);
    StyleSpan[] styleSpans = getSpans(text, StyleSpan.class);
    assertEquals(1, underlineSpans.length);
    assertEquals(1, styleSpans.length);
    assertEquals(Typeface.ITALIC, styleSpans[0].getStyle());

    assertEquals(3, text.getSpanStart(underlineSpans[0]));
    assertEquals(23, text.getSpanStart(styleSpans[0]));
    assertEquals(29, text.getSpanEnd(styleSpans[0]));
    assertEquals(36, text.getSpanEnd(underlineSpans[0]));
  }

  public void testParseWellFormedUnclosedEndAtParent() throws Exception {
    Spanned text = parseCueText("An unclosed u tag with <i><u>underline and italic</i> inside");

    assertEquals("An unclosed u tag with underline and italic inside", text.toString());

    UnderlineSpan[] underlineSpans = getSpans(text, UnderlineSpan.class);
    StyleSpan[] styleSpans = getSpans(text, StyleSpan.class);
    assertEquals(1, underlineSpans.length);
    assertEquals(1, styleSpans.length);

    assertEquals(23, text.getSpanStart(underlineSpans[0]));
    assertEquals(23, text.getSpanStart(styleSpans[0]));
    assertEquals(43, text.getSpanEnd(underlineSpans[0]));
    assertEquals(43, text.getSpanEnd(styleSpans[0]));

    assertEquals(Typeface.ITALIC, styleSpans[0].getStyle());
  }

  public void testParseMalformedNestedElements() throws Exception {
    Spanned text = parseCueText("<b><u>An unclosed u tag with <i>italic</u> inside</i></b>");
    assertEquals("An unclosed u tag with italic inside", text.toString());

    UnderlineSpan[] underlineSpans = getSpans(text, UnderlineSpan.class);
    StyleSpan[] styleSpans = getSpans(text, StyleSpan.class);
    assertEquals(1, underlineSpans.length);
    assertEquals(2, styleSpans.length);

    // all tags applied until matching start tag found
    assertEquals(0, text.getSpanStart(underlineSpans[0]));
    assertEquals(29, text.getSpanEnd(underlineSpans[0]));
    if (styleSpans[0].getStyle() == Typeface.BOLD) {
      assertEquals(0, text.getSpanStart(styleSpans[0]));
      assertEquals(23, text.getSpanStart(styleSpans[1]));
      assertEquals(29, text.getSpanEnd(styleSpans[1]));
      assertEquals(36, text.getSpanEnd(styleSpans[0]));
    } else {
      assertEquals(0, text.getSpanStart(styleSpans[1]));
      assertEquals(23, text.getSpanStart(styleSpans[0]));
      assertEquals(29, text.getSpanEnd(styleSpans[0]));
      assertEquals(36, text.getSpanEnd(styleSpans[1]));
    }
  }

  public void testParseCloseNonExistingTag() throws Exception {
    Spanned text = parseCueText("blah<b>blah</i>blah</b>blah");
    assertEquals("blahblahblahblah", text.toString());

    StyleSpan[] spans = getSpans(text, StyleSpan.class);
    assertEquals(1, spans.length);
    assertEquals(Typeface.BOLD, spans[0].getStyle());
    assertEquals(4, text.getSpanStart(spans[0]));
    assertEquals(8, text.getSpanEnd(spans[0])); // should be 12 when valid
  }

  public void testParseEmptyTagName() throws Exception {
    Spanned text = parseCueText("An unclosed u tag with <>italic inside");
    assertEquals("An unclosed u tag with italic inside", text.toString());
  }

  public void testParseEntities() throws Exception {
    Spanned text = parseCueText("&amp; &gt; &lt; &nbsp;");
    assertEquals("& > <  ", text.toString());
  }

  public void testParseEntitiesUnsupported() throws Exception {
    Spanned text = parseCueText("&noway; &sure;");
    assertEquals(" ", text.toString());
  }

  public void testParseEntitiesNotTerminated() throws Exception {
    Spanned text = parseCueText("&amp here comes text");
    assertEquals("& here comes text", text.toString());
  }

  public void testParseEntitiesNotTerminatedUnsupported() throws Exception {
    Spanned text = parseCueText("&surenot here comes text");
    assertEquals(" here comes text", text.toString());
  }

  public void testParseEntitiesNotTerminatedNoSpace() throws Exception {
    Spanned text = parseCueText("&surenot");
    assertEquals("&surenot", text.toString());
  }

  public void testParseVoidTag() throws Exception {
    Spanned text = parseCueText("here comes<br/> text<br/>");
    assertEquals("here comes text", text.toString());
  }

  public void testParseMultipleTagsOfSameKind() {
    Spanned text = parseCueText("blah <b>blah</b> blah <b>foo</b>");

    assertEquals("blah blah blah foo", text.toString());
    StyleSpan[] spans = getSpans(text, StyleSpan.class);
    assertEquals(2, spans.length);
    assertEquals(5, text.getSpanStart(spans[0]));
    assertEquals(9, text.getSpanEnd(spans[0]));
    assertEquals(15, text.getSpanStart(spans[1]));
    assertEquals(18, text.getSpanEnd(spans[1]));
    assertEquals(Typeface.BOLD, spans[0].getStyle());
    assertEquals(Typeface.BOLD, spans[1].getStyle());
  }

  public void testParseInvalidVoidSlash() {
    Spanned text = parseCueText("blah <b/.st1.st2 trailing stuff> blah");

    assertEquals("blah  blah", text.toString());
    StyleSpan[] spans = getSpans(text, StyleSpan.class);
    assertEquals(0, spans.length);
  }

  public void testParseMonkey() throws Exception {
    Spanned text = parseCueText("< u>An unclosed u tag with <<<<< i>italic</u></u></u></u    >"
        + "</i><u><u> inside");
    assertEquals("An unclosed u tag with italic inside", text.toString());
    text = parseCueText(">>>>>>>>>An unclosed u tag with <<<<< italic</u></u></u>"
        + "</u  ></i><u><u> inside");
    assertEquals(">>>>>>>>>An unclosed u tag with  inside", text.toString());
  }

  public void testParseCornerCases() throws Exception {
    Spanned text = parseCueText(">");
    assertEquals(">", text.toString());

    text = parseCueText("<");
    assertEquals("", text.toString());

    text = parseCueText("<b.st1.st2 annotation");
    assertEquals("", text.toString());

    text = parseCueText("<<<<<<<<<<<<<<<<");
    assertEquals("", text.toString());

    text = parseCueText("<<<<<<>><<<<<<<<<<");
    assertEquals(">", text.toString());

    text = parseCueText("<>");
    assertEquals("", text.toString());

    text = parseCueText("&");
    assertEquals("&", text.toString());

    text = parseCueText("&&&&&&&");
    assertEquals("&&&&&&&", text.toString());
  }

  private static Spanned parseCueText(String string) {
    WebvttCue.Builder builder = new WebvttCue.Builder();
    WebvttCueParser.parseCueText(null, string, builder, Collections.<WebvttCssStyle>emptyList());
    return (Spanned) builder.build().text;
  }

  private static <T> T[] getSpans(Spanned text, Class<T> spanType) {
    return text.getSpans(0, text.length(), spanType);
  }

}

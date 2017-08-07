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

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/**
 * Unit test for {@link CssParser}.
 */
public final class CssParserTest extends InstrumentationTestCase {

  private CssParser parser;

  @Override
  public void setUp() {
    parser = new CssParser();
  }

  public void testSkipWhitespacesAndComments() {
    // Skip only whitespaces
    String skipOnlyWhitespaces = " \t\r\n\f End of skip\n /*  */";
    assertSkipsToEndOfSkip("End of skip", skipOnlyWhitespaces);

    // Skip only comments.
    String skipOnlyComments = "/*A comment***//*/It even has spaces in it*/End of skip";
    assertSkipsToEndOfSkip("End of skip", skipOnlyComments);

    // Skip interleaved.
    String skipInterleaved = " /* We have comments and */\t\n/* whitespaces*/ End of skip";
    assertSkipsToEndOfSkip("End of skip", skipInterleaved);

    // Skip nothing.
    String skipNothing = "End of skip\n \t \r";
    assertSkipsToEndOfSkip("End of skip", skipNothing);

    // Skip everything.
    String skipEverything = "\t/* Comment */\n\r/* And another */";
    assertSkipsToEndOfSkip(null, skipEverything);
  }

  public void testGetInputLimit() {
    // \r After 3 lines.
    String threeLinesThen3Cr = "One Line\nThen other\rAnd finally\r\r\r";
    assertInputLimit("", threeLinesThen3Cr);

    // \r\r After 3 lines
    String threeLinesThen2Cr = "One Line\nThen other\r\nAnd finally\r\r";
    assertInputLimit(null, threeLinesThen2Cr);

    // \n\n After 3 lines.
    String threeLinesThen2Lf = "One Line\nThen other\r\nAnd finally\n\nFinal\n\n\nLine";
    assertInputLimit("Final", threeLinesThen2Lf);

    // \r\n\n After 3 lines.
    String threeLinesThenCr2Lf = " \n \r\n \r\n\nFinal\n\n\nLine";
    assertInputLimit("Final", threeLinesThenCr2Lf);

    // Limit immediately.
    String immediateEmptyLine = "\nLine\nEnd";
    assertInputLimit("Line", immediateEmptyLine);

    // Empty string.
    assertInputLimit(null, "");
  }

  public void testParseMethodSimpleInput() {
    String styleBlock1 = " ::cue { color : black; background-color: PapayaWhip }";
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedStyle.setFontColor(0xFF000000);
    expectedStyle.setBackgroundColor(0xFFFFEFD5);
    assertParserProduces(expectedStyle, styleBlock1);

    String styleBlock2 = " ::cue { color : black }\n\n::cue { color : invalid }";
    expectedStyle = new WebvttCssStyle();
    expectedStyle.setFontColor(0xFF000000);
    assertParserProduces(expectedStyle, styleBlock2);

    String styleBlock3 = " \n::cue {\n background-color\n:#00fFFe}";
    expectedStyle = new WebvttCssStyle();
    expectedStyle.setBackgroundColor(0xFF00FFFE);
    assertParserProduces(expectedStyle, styleBlock3);
  }

  public void testMultiplePropertiesInBlock() {
    String styleBlock = "::cue(#id){text-decoration:underline; background-color:green;"
        + "color:red; font-family:Courier; font-weight:bold}";
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedStyle.setTargetId("id");
    expectedStyle.setUnderline(true);
    expectedStyle.setBackgroundColor(0xFF008000);
    expectedStyle.setFontColor(0xFFFF0000);
    expectedStyle.setFontFamily("courier");
    expectedStyle.setBold(true);

    assertParserProduces(expectedStyle, styleBlock);
  }

  public void testRgbaColorExpression() {
    String styleBlock = "::cue(#rgb){background-color: rgba(\n10/* Ugly color */,11\t, 12\n,.1);"
        + "color:rgb(1,1,\n1)}";
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedStyle.setTargetId("rgb");
    expectedStyle.setBackgroundColor(0x190A0B0C);
    expectedStyle.setFontColor(0xFF010101);

    assertParserProduces(expectedStyle, styleBlock);
  }

  public void testGetNextToken() {
    String stringInput = " lorem:ipsum\n{dolor}#sit,amet;lorem:ipsum\r\t\f\ndolor(())\n";
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(stringInput));
    StringBuilder builder = new StringBuilder();
    assertEquals("lorem", CssParser.parseNextToken(input, builder));
    assertEquals(":", CssParser.parseNextToken(input, builder));
    assertEquals("ipsum", CssParser.parseNextToken(input, builder));
    assertEquals("{", CssParser.parseNextToken(input, builder));
    assertEquals("dolor", CssParser.parseNextToken(input, builder));
    assertEquals("}", CssParser.parseNextToken(input, builder));
    assertEquals("#sit", CssParser.parseNextToken(input, builder));
    assertEquals(",", CssParser.parseNextToken(input, builder));
    assertEquals("amet", CssParser.parseNextToken(input, builder));
    assertEquals(";", CssParser.parseNextToken(input, builder));
    assertEquals("lorem", CssParser.parseNextToken(input, builder));
    assertEquals(":", CssParser.parseNextToken(input, builder));
    assertEquals("ipsum", CssParser.parseNextToken(input, builder));
    assertEquals("dolor", CssParser.parseNextToken(input, builder));
    assertEquals("(", CssParser.parseNextToken(input, builder));
    assertEquals("(", CssParser.parseNextToken(input, builder));
    assertEquals(")", CssParser.parseNextToken(input, builder));
    assertEquals(")", CssParser.parseNextToken(input, builder));
    assertEquals(null, CssParser.parseNextToken(input, builder));
  }

  public void testStyleScoreSystem() {
    WebvttCssStyle style = new WebvttCssStyle();
    // Universal selector.
    assertEquals(1, style.getSpecificityScore("", "", new String[0], ""));
    // Class match without tag match.
    style.setTargetClasses(new String[] { "class1", "class2"});
    assertEquals(8, style.getSpecificityScore("", "", new String[] { "class1", "class2", "class3" },
        ""));
    // Class and tag match
    style.setTargetTagName("b");
    assertEquals(10, style.getSpecificityScore("", "b",
        new String[] { "class1", "class2", "class3" }, ""));
    // Class insufficiency.
    assertEquals(0, style.getSpecificityScore("", "b", new String[] { "class1", "class" }, ""));
    // Voice, classes and tag match.
    style.setTargetVoice("Manuel Cráneo");
    assertEquals(14, style.getSpecificityScore("", "b",
        new String[] { "class1", "class2", "class3" }, "Manuel Cráneo"));
    // Voice mismatch.
    assertEquals(0, style.getSpecificityScore(null, "b",
        new String[] { "class1", "class2", "class3" }, "Manuel Craneo"));
    // Id, voice, classes and tag match.
    style.setTargetId("id");
    assertEquals(0x40000000 + 14, style.getSpecificityScore("id", "b",
        new String[] { "class1", "class2", "class3" }, "Manuel Cráneo"));
    // Id mismatch.
    assertEquals(0, style.getSpecificityScore("id1", "b",
        new String[] { "class1", "class2", "class3" }, ""));
  }

  // Utility methods.

  private void assertSkipsToEndOfSkip(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(s));
    CssParser.skipWhitespaceAndComments(input);
    assertEquals(expectedLine, input.readLine());
  }

  private void assertInputLimit(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(s));
    CssParser.skipStyleBlock(input);
    assertEquals(expectedLine, input.readLine());
  }

  private void assertParserProduces(WebvttCssStyle expected,
      String styleBlock){
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(styleBlock));
    WebvttCssStyle actualElem = parser.parseBlock(input);
    assertEquals(expected.hasBackgroundColor(), actualElem.hasBackgroundColor());
    if (expected.hasBackgroundColor()) {
      assertEquals(expected.getBackgroundColor(), actualElem.getBackgroundColor());
    }
    assertEquals(expected.hasFontColor(), actualElem.hasFontColor());
    if (expected.hasFontColor()) {
      assertEquals(expected.getFontColor(), actualElem.getFontColor());
    }
    assertEquals(expected.getFontFamily(), actualElem.getFontFamily());
    assertEquals(expected.getFontSize(), actualElem.getFontSize());
    assertEquals(expected.getFontSizeUnit(), actualElem.getFontSizeUnit());
    assertEquals(expected.getStyle(), actualElem.getStyle());
    assertEquals(expected.isLinethrough(), actualElem.isLinethrough());
    assertEquals(expected.isUnderline(), actualElem.isUnderline());
    assertEquals(expected.getTextAlign(), actualElem.getTextAlign());
  }

}

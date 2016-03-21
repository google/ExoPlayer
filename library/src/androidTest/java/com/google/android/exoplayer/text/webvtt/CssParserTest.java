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

import com.google.android.exoplayer.util.ParsableByteArray;

import android.test.InstrumentationTestCase;

import java.util.HashMap;
import java.util.Map;

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
    String styleBlock = " ::cue { color : black; background-color: PapayaWhip }";
    // Expected style map construction.
    Map<String, WebvttCssStyle> expectedResult = new HashMap<>();
    expectedResult.put("", new WebvttCssStyle());
    WebvttCssStyle style = expectedResult.get("");
    style.setFontColor(0xFF000000);
    style.setBackgroundColor(0xFFFFEFD5);

    assertCssProducesExpectedMap(expectedResult, new String[] { styleBlock });
  }

  public void testParseSimpleInputSeparately() {
    String styleBlock1 = " ::cue { color : black }\n\n::cue { color : invalid }";
    String styleBlock2 = " \n::cue {\n background-color\n:#00fFFe}";

    // Expected style map construction.
    Map<String, WebvttCssStyle> expectedResult = new HashMap<>();
    expectedResult.put("", new WebvttCssStyle());
    WebvttCssStyle style = expectedResult.get("");
    style.setFontColor(0xFF000000);
    style.setBackgroundColor(0xFF00FFFE);

    assertCssProducesExpectedMap(expectedResult, new String[] { styleBlock1, styleBlock2 });
  }

  public void testDifferentSelectors() {
    String styleBlock1 = " ::cue(\n#id ){text-decoration:underline;}";
    String styleBlock2 = "::cue(elem ){font-family:Courier}";
    String styleBlock3 = "::cue(.class ){font-weight: bold;}";

    // Expected style map construction.
    Map<String, WebvttCssStyle> expectedResult = new HashMap<>();
    expectedResult.put("#id", new WebvttCssStyle().setUnderline(true));
    expectedResult.put("elem", new WebvttCssStyle().setFontFamily("courier"));
    expectedResult.put(".class", new WebvttCssStyle().setBold(true));

    assertCssProducesExpectedMap(expectedResult, new String[] { styleBlock1, styleBlock2,
        styleBlock3});
  }

  public void testMultiplePropertiesInBlock() {
    String styleBlock = "::cue(#id){text-decoration:underline; background-color:green;"
        + "color:red; font-family:Courier; font-weight:bold}";

    // Expected style map construction.
    Map<String, WebvttCssStyle> expectedResult = new HashMap<>();
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedResult.put("#id", expectedStyle);
    expectedStyle.setUnderline(true);
    expectedStyle.setBackgroundColor(0xFF008000);
    expectedStyle.setFontColor(0xFFFF0000);
    expectedStyle.setFontFamily("courier");
    expectedStyle.setBold(true);

    assertCssProducesExpectedMap(expectedResult, new String[] { styleBlock });
  }

  public void testRgbaColorExpression() {
    String styleBlock = "::cue(#rgb){background-color: rgba(\n10/* Ugly color */,11\t, 12\n,.1);"
        + "color:rgb(1,1,\n1)}";

    // Expected style map construction.
    Map<String, WebvttCssStyle> expectedResult = new HashMap<>();
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedResult.put("#rgb", expectedStyle);
    expectedStyle.setBackgroundColor(0x190A0B0C);
    expectedStyle.setFontColor(0xFF010101);

    assertCssProducesExpectedMap(expectedResult, new String[] { styleBlock });
  }

  public void testGetNextToken() {
    String stringInput = " lorem:ipsum\n{dolor}#sit,amet;lorem:ipsum\r\t\f\ndolor(())\n";
    ParsableByteArray input = new ParsableByteArray(stringInput.getBytes());
    StringBuilder builder = new StringBuilder();
    assertEquals(CssParser.parseNextToken(input, builder), "lorem");
    assertEquals(CssParser.parseNextToken(input, builder), ":");
    assertEquals(CssParser.parseNextToken(input, builder), "ipsum");
    assertEquals(CssParser.parseNextToken(input, builder), "{");
    assertEquals(CssParser.parseNextToken(input, builder), "dolor");
    assertEquals(CssParser.parseNextToken(input, builder), "}");
    assertEquals(CssParser.parseNextToken(input, builder), "#sit");
    assertEquals(CssParser.parseNextToken(input, builder), ",");
    assertEquals(CssParser.parseNextToken(input, builder), "amet");
    assertEquals(CssParser.parseNextToken(input, builder), ";");
    assertEquals(CssParser.parseNextToken(input, builder), "lorem");
    assertEquals(CssParser.parseNextToken(input, builder), ":");
    assertEquals(CssParser.parseNextToken(input, builder), "ipsum");
    assertEquals(CssParser.parseNextToken(input, builder), "dolor");
    assertEquals(CssParser.parseNextToken(input, builder), "(");
    assertEquals(CssParser.parseNextToken(input, builder), "(");
    assertEquals(CssParser.parseNextToken(input, builder), ")");
    assertEquals(CssParser.parseNextToken(input, builder), ")");
    assertEquals(CssParser.parseNextToken(input, builder), null);
  }

  // Utility methods.

  private void assertSkipsToEndOfSkip(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(s.getBytes());
    CssParser.skipWhitespaceAndComments(input);
    assertEquals(expectedLine, input.readLine());
  }

  private void assertInputLimit(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(s.getBytes());
    CssParser.skipStyleBlock(input);
    assertEquals(expectedLine, input.readLine());
  }

  private void assertCssProducesExpectedMap(Map<String, WebvttCssStyle> expectedResult,
      String[] styleBlocks){
    Map<String, WebvttCssStyle> actualStyleMap = new HashMap<>();
    for (String s : styleBlocks) {
      ParsableByteArray input = new ParsableByteArray(s.getBytes());
      parser.parseBlock(input, actualStyleMap);
    }
    assertStyleMapsAreEqual(expectedResult, actualStyleMap);
  }

  private void assertStyleMapsAreEqual(Map<String, WebvttCssStyle> expected,
      Map<String, WebvttCssStyle> actual) {
    assertEquals(expected.size(), actual.size());
    for (String k : expected.keySet()) {
      WebvttCssStyle expectedElem = expected.get(k);
      WebvttCssStyle actualElem = actual.get(k);
      assertEquals(expectedElem.hasBackgroundColor(), actualElem.hasBackgroundColor());
      if (expectedElem.hasBackgroundColor()) {
        assertEquals(expectedElem.getBackgroundColor(), actualElem.getBackgroundColor());
      }
      assertEquals(expectedElem.hasFontColor(), actualElem.hasFontColor());
      if (expectedElem.hasFontColor()) {
        assertEquals(expectedElem.getFontColor(), actualElem.getFontColor());
      }
      assertEquals(expectedElem.getFontFamily(), actualElem.getFontFamily());
      assertEquals(expectedElem.getFontSize(), actualElem.getFontSize());
      assertEquals(expectedElem.getFontSizeUnit(), actualElem.getFontSizeUnit());
      assertEquals(expectedElem.getStyle(), actualElem.getStyle());
      assertEquals(expectedElem.isLinethrough(), actualElem.isLinethrough());
      assertEquals(expectedElem.isUnderline(), actualElem.isUnderline());
      assertEquals(expectedElem.getTextAlign(), actualElem.getTextAlign());
    }
  }

}

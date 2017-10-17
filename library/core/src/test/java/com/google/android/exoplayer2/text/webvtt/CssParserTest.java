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

import static com.google.android.exoplayer2.text.webvtt.CssParser.parseNextToken;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit test for {@link CssParser}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class CssParserTest {

  private CssParser parser;

  @Before
  public void setUp() {
    parser = new CssParser();
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testRgbaColorExpression() {
    String styleBlock = "::cue(#rgb){background-color: rgba(\n10/* Ugly color */,11\t, 12\n,.1);"
        + "color:rgb(1,1,\n1)}";
    WebvttCssStyle expectedStyle = new WebvttCssStyle();
    expectedStyle.setTargetId("rgb");
    expectedStyle.setBackgroundColor(0x190A0B0C);
    expectedStyle.setFontColor(0xFF010101);

    assertParserProduces(expectedStyle, styleBlock);
  }

  @Test
  public void testGetNextToken() {
    String stringInput = " lorem:ipsum\n{dolor}#sit,amet;lorem:ipsum\r\t\f\ndolor(())\n";
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(stringInput));
    StringBuilder builder = new StringBuilder();
    assertThat(parseNextToken(input, builder)).isEqualTo("lorem");
    assertThat(parseNextToken(input, builder)).isEqualTo(":");
    assertThat(parseNextToken(input, builder)).isEqualTo("ipsum");
    assertThat(parseNextToken(input, builder)).isEqualTo("{");
    assertThat(parseNextToken(input, builder)).isEqualTo("dolor");
    assertThat(parseNextToken(input, builder)).isEqualTo("}");
    assertThat(parseNextToken(input, builder)).isEqualTo("#sit");
    assertThat(parseNextToken(input, builder)).isEqualTo(",");
    assertThat(parseNextToken(input, builder)).isEqualTo("amet");
    assertThat(parseNextToken(input, builder)).isEqualTo(";");
    assertThat(parseNextToken(input, builder)).isEqualTo("lorem");
    assertThat(parseNextToken(input, builder)).isEqualTo(":");
    assertThat(parseNextToken(input, builder)).isEqualTo("ipsum");
    assertThat(parseNextToken(input, builder)).isEqualTo("dolor");
    assertThat(parseNextToken(input, builder)).isEqualTo("(");
    assertThat(parseNextToken(input, builder)).isEqualTo("(");
    assertThat(parseNextToken(input, builder)).isEqualTo(")");
    assertThat(parseNextToken(input, builder)).isEqualTo(")");
    assertThat(parseNextToken(input, builder)).isNull();
  }

  @Test
  public void testStyleScoreSystem() {
    WebvttCssStyle style = new WebvttCssStyle();
    // Universal selector.
    assertThat(style.getSpecificityScore("", "", new String[0], "")).isEqualTo(1);
    // Class match without tag match.
    style.setTargetClasses(new String[] { "class1", "class2"});
    assertThat(style.getSpecificityScore("", "", new String[]{"class1", "class2", "class3"},
        "")).isEqualTo(8);
    // Class and tag match
    style.setTargetTagName("b");
    assertThat(style.getSpecificityScore("", "b",
        new String[]{"class1", "class2", "class3"}, "")).isEqualTo(10);
    // Class insufficiency.
    assertThat(style.getSpecificityScore("", "b", new String[]{"class1", "class"}, ""))
        .isEqualTo(0);
    // Voice, classes and tag match.
    style.setTargetVoice("Manuel Cráneo");
    assertThat(style.getSpecificityScore("", "b",
        new String[]{"class1", "class2", "class3"}, "Manuel Cráneo")).isEqualTo(14);
    // Voice mismatch.
    assertThat(style.getSpecificityScore(null, "b",
        new String[]{"class1", "class2", "class3"}, "Manuel Craneo")).isEqualTo(0);
    // Id, voice, classes and tag match.
    style.setTargetId("id");
    assertThat(style.getSpecificityScore("id", "b",
        new String[]{"class1", "class2", "class3"}, "Manuel Cráneo")).isEqualTo(0x40000000 + 14);
    // Id mismatch.
    assertThat(style.getSpecificityScore("id1", "b",
        new String[]{"class1", "class2", "class3"}, "")).isEqualTo(0);
  }

  // Utility methods.

  private void assertSkipsToEndOfSkip(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(s));
    CssParser.skipWhitespaceAndComments(input);
    assertThat(input.readLine()).isEqualTo(expectedLine);
  }

  private void assertInputLimit(String expectedLine, String s) {
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(s));
    CssParser.skipStyleBlock(input);
    assertThat(input.readLine()).isEqualTo(expectedLine);
  }

  private void assertParserProduces(WebvttCssStyle expected,
      String styleBlock){
    ParsableByteArray input = new ParsableByteArray(Util.getUtf8Bytes(styleBlock));
    WebvttCssStyle actualElem = parser.parseBlock(input);
    assertThat(actualElem.hasBackgroundColor()).isEqualTo(expected.hasBackgroundColor());
    if (expected.hasBackgroundColor()) {
      assertThat(actualElem.getBackgroundColor()).isEqualTo(expected.getBackgroundColor());
    }
    assertThat(actualElem.hasFontColor()).isEqualTo(expected.hasFontColor());
    if (expected.hasFontColor()) {
      assertThat(actualElem.getFontColor()).isEqualTo(expected.getFontColor());
    }
    assertThat(actualElem.getFontFamily()).isEqualTo(expected.getFontFamily());
    assertThat(actualElem.getFontSize()).isEqualTo(expected.getFontSize());
    assertThat(actualElem.getFontSizeUnit()).isEqualTo(expected.getFontSizeUnit());
    assertThat(actualElem.getStyle()).isEqualTo(expected.getStyle());
    assertThat(actualElem.isLinethrough()).isEqualTo(expected.isLinethrough());
    assertThat(actualElem.isUnderline()).isEqualTo(expected.isUnderline());
    assertThat(actualElem.getTextAlign()).isEqualTo(expected.getTextAlign());
  }

}

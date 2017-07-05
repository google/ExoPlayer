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

import android.test.InstrumentationTestCase;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ColorParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Unit test for {@link TtmlDecoder}.
 */
public final class TtmlDecoderTest extends InstrumentationTestCase {

  private static final String INLINE_ATTRIBUTES_TTML_FILE = "ttml/inline_style_attributes.xml";
  private static final String INHERIT_STYLE_TTML_FILE = "ttml/inherit_style.xml";
  private static final String INHERIT_STYLE_OVERRIDE_TTML_FILE =
      "ttml/inherit_and_override_style.xml";
  private static final String INHERIT_GLOBAL_AND_PARENT_TTML_FILE =
      "ttml/inherit_global_and_parent.xml";
  private static final String INHERIT_MULTIPLE_STYLES_TTML_FILE =
      "ttml/inherit_multiple_styles.xml";
  private static final String CHAIN_MULTIPLE_STYLES_TTML_FILE = "ttml/chain_multiple_styles.xml";
  private static final String MULTIPLE_REGIONS_TTML_FILE = "ttml/multiple_regions.xml";
  private static final String NO_UNDERLINE_LINETHROUGH_TTML_FILE =
      "ttml/no_underline_linethrough.xml";
  private static final String FONT_SIZE_TTML_FILE = "ttml/font_size.xml";
  private static final String FONT_SIZE_MISSING_UNIT_TTML_FILE = "ttml/font_size_no_unit.xml";
  private static final String FONT_SIZE_INVALID_TTML_FILE = "ttml/font_size_invalid.xml";
  private static final String FONT_SIZE_EMPTY_TTML_FILE = "ttml/font_size_empty.xml";
  private static final String FRAME_RATE_TTML_FILE = "ttml/frame_rate.xml";

  public void testInlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();

    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode firstDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);
    TtmlStyle firstPStyle = queryChildrenForTag(firstDiv, TtmlNode.TAG_P, 0).style;
    assertEquals(ColorParser.parseTtmlColor("yellow"), firstPStyle.getFontColor());
    assertEquals(ColorParser.parseTtmlColor("blue"), firstPStyle.getBackgroundColor());
    assertEquals("serif", firstPStyle.getFontFamily());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, firstPStyle.getStyle());
    assertTrue(firstPStyle.isUnderline());
  }

  public void testInheritInlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_ITALIC,
        0xFF00FFFF, ColorParser.parseTtmlColor("lime"), false, true, null);
  }

  /**
   * Regression test for devices on JellyBean where some named colors are not correctly defined
   * on framework level. Tests that <i>lime</i> resolves to <code>#FF00FF00</code> not
   * <code>#00FF00</code>.
   *
   * @see <a href="https://github.com/android/platform_frameworks_base/blob/jb-mr2-release/graphics/java/android/graphics/Color.java#L414">
   *     JellyBean Color</a>
   *     <a href="https://github.com/android/platform_frameworks_base/blob/kitkat-mr2.2-release/graphics/java/android/graphics/Color.java#L414">
   *     Kitkat Color</a>
   * @throws IOException thrown if reading subtitle file fails.
   */
  public void testLime() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_ITALIC, 0xFF00FFFF, 0xFF00FF00,
        false, true, null);
  }

  public void testInheritGlobalStyle() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());
    assertSpans(subtitle, 10, "text 1", "serif", TtmlStyle.STYLE_BOLD_ITALIC, 0xFF0000FF,
        0xFFFFFF00, true, false, null);
  }

  public void testInheritGlobalStyleOverriddenByInlineAttributes() throws IOException,
      SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_OVERRIDE_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    assertSpans(subtitle, 10, "text 1", "serif", TtmlStyle.STYLE_BOLD_ITALIC, 0xFF0000FF,
        0xFFFFFF00, true, false, null);
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_ITALIC, 0xFFFF0000, 0xFFFFFF00,
        true, false, null);
  }

  public void testInheritGlobalAndParent() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    assertSpans(subtitle, 10, "text 1", "sansSerif", TtmlStyle.STYLE_NORMAL, 0xFFFF0000,
        ColorParser.parseTtmlColor("lime"), false, true, Layout.Alignment.ALIGN_CENTER);
    assertSpans(subtitle, 20, "text 2", "serif", TtmlStyle.STYLE_BOLD_ITALIC, 0xFF0000FF,
        0xFFFFFF00, true, true, Layout.Alignment.ALIGN_CENTER);
  }

  public void testInheritMultipleStyles() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());
    assertSpans(subtitle, 10, "text 1", "sansSerif", TtmlStyle.STYLE_BOLD_ITALIC, 0xFF0000FF,
        0xFFFFFF00, false, true, null);
  }

  public void testInheritMultipleStylesWithoutLocalAttributes() throws IOException,
      SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());
    assertSpans(subtitle, 20, "text 2", "sansSerif", TtmlStyle.STYLE_BOLD_ITALIC, 0xFF0000FF,
        0xFF000000, false, true, null);
  }

  public void testMergeMultipleStylesWithParentStyle() throws IOException,
      SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());
    assertSpans(subtitle, 30, "text 2.5", "sansSerifInline", TtmlStyle.STYLE_ITALIC, 0xFFFF0000,
        0xFFFFFF00, true, true, null);
  }

  public void testMultipleRegions() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(MULTIPLE_REGIONS_TTML_FILE);
    List<Cue> output = subtitle.getCues(1000000);
    assertEquals(2, output.size());
    Cue ttmlCue = output.get(0);
    assertEquals("lorem", ttmlCue.text.toString());
    assertEquals(10f / 100f, ttmlCue.position);
    assertEquals(10f / 100f, ttmlCue.line);
    assertEquals(20f / 100f, ttmlCue.size);

    ttmlCue = output.get(1);
    assertEquals("amet", ttmlCue.text.toString());
    assertEquals(60f / 100f, ttmlCue.position);
    assertEquals(10f / 100f, ttmlCue.line);
    assertEquals(20f / 100f, ttmlCue.size);

    output = subtitle.getCues(5000000);
    assertEquals(1, output.size());
    ttmlCue = output.get(0);
    assertEquals("ipsum", ttmlCue.text.toString());
    assertEquals(40f / 100f, ttmlCue.position);
    assertEquals(40f / 100f, ttmlCue.line);
    assertEquals(20f / 100f, ttmlCue.size);

    output = subtitle.getCues(9000000);
    assertEquals(1, output.size());
    ttmlCue = output.get(0);
    assertEquals("dolor", ttmlCue.text.toString());
    assertEquals(Cue.DIMEN_UNSET, ttmlCue.position);
    assertEquals(Cue.DIMEN_UNSET, ttmlCue.line);
    assertEquals(Cue.DIMEN_UNSET, ttmlCue.size);
    // TODO: Should be as below, once https://github.com/google/ExoPlayer/issues/2953 is fixed.
    // assertEquals(10f / 100f, ttmlCue.position);
    // assertEquals(80f / 100f, ttmlCue.line);
    // assertEquals(1f, ttmlCue.size);

    output = subtitle.getCues(21000000);
    assertEquals(1, output.size());
    ttmlCue = output.get(0);
    assertEquals("She first said this", ttmlCue.text.toString());
    assertEquals(45f / 100f, ttmlCue.position);
    assertEquals(45f / 100f, ttmlCue.line);
    assertEquals(35f / 100f, ttmlCue.size);
    output = subtitle.getCues(25000000);
    ttmlCue = output.get(0);
    assertEquals("She first said this\nThen this", ttmlCue.text.toString());
    output = subtitle.getCues(29000000);
    assertEquals(1, output.size());
    ttmlCue = output.get(0);
    assertEquals("She first said this\nThen this\nFinally this", ttmlCue.text.toString());
    assertEquals(45f / 100f, ttmlCue.position);
    assertEquals(45f / 100f, ttmlCue.line);
  }

  public void testEmptyStyleAttribute() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fourthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 3);

    assertNull(queryChildrenForTag(fourthDiv, TtmlNode.TAG_P, 0).getStyleIds());
  }

  public void testNonexistingStyleId() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fifthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 4);

    assertEquals(1, queryChildrenForTag(fifthDiv, TtmlNode.TAG_P, 0).getStyleIds().length);
  }

  public void testNonExistingAndExistingStyleIdWithRedundantSpaces() throws IOException,
      SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(12, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode sixthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 5);

    String[] styleIds = queryChildrenForTag(sixthDiv, TtmlNode.TAG_P, 0).getStyleIds();
    assertEquals(2, styleIds.length);
  }

  public void testMultipleChaining() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(CHAIN_MULTIPLE_STYLES_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());

    Map<String, TtmlStyle> globalStyles = subtitle.getGlobalStyles();

    TtmlStyle style = globalStyles.get("s2");
    assertEquals("serif", style.getFontFamily());
    assertEquals(0xFFFF0000, style.getBackgroundColor());
    assertEquals(0xFF000000, style.getFontColor());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
    assertTrue(style.isLinethrough());

    style = globalStyles.get("s3");
    // only difference: color must be RED
    assertEquals(0xFFFF0000, style.getFontColor());
    assertEquals("serif", style.getFontFamily());
    assertEquals(0xFFFF0000, style.getBackgroundColor());
    assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
    assertTrue(style.isLinethrough());
  }

  public void testNoUnderline() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertFalse("noUnderline from inline attribute expected", style.isUnderline());
  }

  public void testNoLinethrough() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertFalse("noLineThrough from inline attribute expected in second pNode",
        style.isLinethrough());
  }

  public void testFontSizeSpans() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_TTML_FILE);
    assertEquals(10, subtitle.getEventTimeCount());

    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertEquals(1, cues.size());
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("text 1", String.valueOf(spannable));
    assertAbsoluteFontSize(spannable, 32);

    cues = subtitle.getCues(20 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("text 2", String.valueOf(cues.get(0).text));
    assertRelativeFontSize(spannable, 2.2f);

    cues = subtitle.getCues(30 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("text 3", String.valueOf(cues.get(0).text));
    assertRelativeFontSize(spannable, 1.5f);

    cues = subtitle.getCues(40 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("two values", String.valueOf(cues.get(0).text));
    assertAbsoluteFontSize(spannable, 16);

    cues = subtitle.getCues(50 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("leading dot", String.valueOf(cues.get(0).text));
    assertRelativeFontSize(spannable, 0.5f);
  }

  public void testFontSizeWithMissingUnitIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_MISSING_UNIT_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());
    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertEquals(1, cues.size());
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("no unit", String.valueOf(spannable));
    assertEquals(0, spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class).length);
    assertEquals(0, spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class).length);
  }

  public void testFontSizeWithInvalidValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_INVALID_TTML_FILE);
    assertEquals(6, subtitle.getEventTimeCount());

    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertEquals(1, cues.size());
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("invalid", String.valueOf(spannable));
    assertEquals(0, spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class).length);
    assertEquals(0, spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class).length);


    cues = subtitle.getCues(20 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("invalid", String.valueOf(spannable));
    assertEquals(0, spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class).length);
    assertEquals(0, spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class).length);


    cues = subtitle.getCues(30 * 1000000);
    assertEquals(1, cues.size());
    spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("invalid dot", String.valueOf(spannable));
    assertEquals(0, spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class).length);
    assertEquals(0, spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class).length);
  }

  public void testFontSizeWithEmptyValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_EMPTY_TTML_FILE);
    assertEquals(2, subtitle.getEventTimeCount());
    List<Cue> cues = subtitle.getCues(10 * 1000000);
    assertEquals(1, cues.size());
    SpannableStringBuilder spannable = (SpannableStringBuilder) cues.get(0).text;
    assertEquals("empty", String.valueOf(spannable));
    assertEquals(0, spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class).length);
    assertEquals(0, spannable.getSpans(0, spannable.length(), AbsoluteSizeSpan.class).length);
  }

  public void testFrameRate() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FRAME_RATE_TTML_FILE);
    assertEquals(4, subtitle.getEventTimeCount());
    assertEquals(1_000_000, subtitle.getEventTime(0));
    assertEquals(1_010_000, subtitle.getEventTime(1));
    assertEquals(1_001_000_000, subtitle.getEventTime(2), 1000);
    assertEquals(2_002_000_000, subtitle.getEventTime(3), 2000);
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

    assertFont(spannable, font);
    assertStyle(spannable, fontStyle);
    assertUnderline(spannable, isUnderline);
    assertStrikethrough(spannable, isLinethrough);
    assertUnderline(spannable, isUnderline);
    assertBackground(spannable, backgroundColor);
    assertForeground(spannable, color);
    assertAlignment(spannable, alignment);
  }

  private void assertAbsoluteFontSize(Spannable spannable, int absoluteFontSize) {
    AbsoluteSizeSpan[] absoluteSizeSpans = spannable.getSpans(0, spannable.length(),
        AbsoluteSizeSpan.class);
    assertEquals(1, absoluteSizeSpans.length);
    assertEquals(absoluteFontSize, absoluteSizeSpans[0].getSize());
  }

  private void assertRelativeFontSize(Spannable spannable, float relativeFontSize) {
    RelativeSizeSpan[] relativeSizeSpans = spannable.getSpans(0, spannable.length(),
        RelativeSizeSpan.class);
    assertEquals(1, relativeSizeSpans.length);
    assertEquals(relativeFontSize, relativeSizeSpans[0].getSizeChange());
  }

  private void assertFont(Spannable spannable, String font) {
    TypefaceSpan[] typefaceSpans = spannable.getSpans(0, spannable.length(), TypefaceSpan.class);
    assertEquals(font, typefaceSpans[typefaceSpans.length - 1].getFamily());
  }

  private void assertStyle(Spannable spannable, int fontStyle) {
    StyleSpan[] styleSpans = spannable.getSpans(0, spannable.length(), StyleSpan.class);
    assertEquals(fontStyle, styleSpans[styleSpans.length - 1].getStyle());
  }

  private void assertUnderline(Spannable spannable, boolean isUnderline) {
    UnderlineSpan[] underlineSpans = spannable.getSpans(0, spannable.length(), UnderlineSpan.class);
    assertEquals(isUnderline ? "must be underlined" : "must not be underlined",
        isUnderline ? 1 : 0, underlineSpans.length);
  }

  private void assertStrikethrough(Spannable spannable, boolean isStrikethrough) {
    StrikethroughSpan[] striketroughSpans = spannable.getSpans(0, spannable.length(),
        StrikethroughSpan.class);
    assertEquals(isStrikethrough ? "must be strikethrough" : "must not be strikethrough",
        isStrikethrough ? 1 : 0, striketroughSpans.length);
  }

  private void assertBackground(Spannable spannable, int backgroundColor) {
    BackgroundColorSpan[] backgroundColorSpans =
        spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
    if (backgroundColor != 0) {
      assertEquals(backgroundColor, backgroundColorSpans[backgroundColorSpans.length - 1]
          .getBackgroundColor());
    } else {
      assertEquals(0, backgroundColorSpans.length);
    }
  }

  private void assertForeground(Spannable spannable, int foregroundColor) {
    ForegroundColorSpan[] foregroundColorSpans =
        spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class);
    assertEquals(foregroundColor,
        foregroundColorSpans[foregroundColorSpans.length - 1].getForegroundColor());
  }

  private void assertAlignment(Spannable spannable, Layout.Alignment alignment) {
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
    throw new IllegalStateException("tag not found");
  }

  private TtmlSubtitle getSubtitle(String file) throws IOException, SubtitleDecoderException {
    TtmlDecoder ttmlDecoder = new TtmlDecoder();
    byte[] bytes = TestUtil.getByteArray(getInstrumentation(), file);
    return ttmlDecoder.decode(bytes, bytes.length, false);
  }

}

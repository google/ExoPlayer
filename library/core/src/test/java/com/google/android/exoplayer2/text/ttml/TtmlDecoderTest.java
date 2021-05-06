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

import static com.google.android.exoplayer2.testutil.truth.SpannedSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.text.Layout;
import android.text.Spanned;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ColorParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TtmlDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class TtmlDecoderTest {

  private static final String INLINE_ATTRIBUTES_TTML_FILE =
      "media/ttml/inline_style_attributes.xml";
  private static final String INHERIT_STYLE_TTML_FILE = "media/ttml/inherit_style.xml";
  private static final String INHERIT_STYLE_OVERRIDE_TTML_FILE =
      "media/ttml/inherit_and_override_style.xml";
  private static final String INHERIT_GLOBAL_AND_PARENT_TTML_FILE =
      "media/ttml/inherit_global_and_parent.xml";
  private static final String INHERIT_MULTIPLE_STYLES_TTML_FILE =
      "media/ttml/inherit_multiple_styles.xml";
  private static final String CHAIN_MULTIPLE_STYLES_TTML_FILE =
      "media/ttml/chain_multiple_styles.xml";
  private static final String MULTIPLE_REGIONS_TTML_FILE = "media/ttml/multiple_regions.xml";
  private static final String NO_UNDERLINE_LINETHROUGH_TTML_FILE =
      "media/ttml/no_underline_linethrough.xml";
  private static final String FONT_SIZE_TTML_FILE = "media/ttml/font_size.xml";
  private static final String FONT_SIZE_MISSING_UNIT_TTML_FILE = "media/ttml/font_size_no_unit.xml";
  private static final String FONT_SIZE_INVALID_TTML_FILE = "media/ttml/font_size_invalid.xml";
  private static final String FONT_SIZE_EMPTY_TTML_FILE = "media/ttml/font_size_empty.xml";
  private static final String FRAME_RATE_TTML_FILE = "media/ttml/frame_rate.xml";
  private static final String BITMAP_REGION_FILE = "media/ttml/bitmap_percentage_region.xml";
  private static final String BITMAP_PIXEL_REGION_FILE = "media/ttml/bitmap_pixel_region.xml";
  private static final String BITMAP_UNSUPPORTED_REGION_FILE =
      "media/ttml/bitmap_unsupported_region.xml";
  private static final String TEXT_ALIGN_FILE = "media/ttml/text_align.xml";
  private static final String MULTI_ROW_ALIGN_FILE = "media/ttml/multi_row_align.xml";
  private static final String VERTICAL_TEXT_FILE = "media/ttml/vertical_text.xml";
  private static final String TEXT_COMBINE_FILE = "media/ttml/text_combine.xml";
  private static final String RUBIES_FILE = "media/ttml/rubies.xml";
  private static final String TEXT_EMPHASIS_FILE = "media/ttml/text_emphasis.xml";
  private static final String SHEAR_FILE = "media/ttml/shear.xml";

  @Test
  public void inlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(spanned.toString()).isEqualTo("text 1");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("serif");
    assertThat(spanned).hasBoldItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasUnderlineSpanBetween(0, spanned.length());
    assertThat(spanned)
        .hasBackgroundColorSpanBetween(0, spanned.length())
        .withColor(ColorParser.parseTtmlColor("blue"));
    assertThat(spanned)
        .hasForegroundColorSpanBetween(0, spanned.length())
        .withColor(ColorParser.parseTtmlColor("yellow"));
  }

  @Test
  public void inheritInlineAttributes() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(spanned.toString()).isEqualTo("text 2");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("sansSerif");
    assertThat(spanned).hasItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasStrikethroughSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF00FFFF);
    assertThat(spanned)
        .hasForegroundColorSpanBetween(0, spanned.length())
        .withColor(ColorParser.parseTtmlColor("lime"));
  }

  /**
   * Regression test for devices on JellyBean where some named colors are not correctly defined on
   * framework level. Tests that <i>lime</i> resolves to <code>#FF00FF00</code> not <code>#00FF00
   * </code>.
   *
   * @throws IOException thrown if reading subtitle file fails.
   * @see <a
   *     href="https://github.com/android/platform_frameworks_base/blob/jb-mr2-release/graphics/java/android/graphics/Color.java#L414">
   *     JellyBean Color</a> <a
   *     href="https://github.com/android/platform_frameworks_base/blob/kitkat-mr2.2-release/graphics/java/android/graphics/Color.java#L414">
   *     Kitkat Color</a>
   */
  @Test
  public void lime() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(spanned.toString()).isEqualTo("text 2");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("sansSerif");
    assertThat(spanned).hasItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasStrikethroughSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF00FFFF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFF00FF00);
  }

  @Test
  public void inheritGlobalStyle() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(spanned.toString()).isEqualTo("text 1");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("serif");
    assertThat(spanned).hasBoldItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasUnderlineSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF0000FF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFFFFFF00);
  }

  @Test
  public void inheritGlobalStyleOverriddenByInlineAttributes()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_STYLE_OVERRIDE_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned firstCueText = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCueText.toString()).isEqualTo("text 1");
    assertThat(firstCueText).hasTypefaceSpanBetween(0, firstCueText.length()).withFamily("serif");
    assertThat(firstCueText).hasBoldItalicSpanBetween(0, firstCueText.length());
    assertThat(firstCueText).hasUnderlineSpanBetween(0, firstCueText.length());
    assertThat(firstCueText)
        .hasBackgroundColorSpanBetween(0, firstCueText.length())
        .withColor(0xFF0000FF);
    assertThat(firstCueText)
        .hasForegroundColorSpanBetween(0, firstCueText.length())
        .withColor(0xFFFFFF00);

    Spanned secondCueText = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCueText.toString()).isEqualTo("text 2");
    assertThat(secondCueText)
        .hasTypefaceSpanBetween(0, secondCueText.length())
        .withFamily("sansSerif");
    assertThat(secondCueText).hasItalicSpanBetween(0, secondCueText.length());
    assertThat(secondCueText).hasUnderlineSpanBetween(0, secondCueText.length());
    assertThat(secondCueText)
        .hasBackgroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFFFF0000);
    assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFFFFFF00);
  }

  @Test
  public void inheritGlobalAndParent() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned firstCueText = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCueText.toString()).isEqualTo("text 1");
    assertThat(firstCueText)
        .hasTypefaceSpanBetween(0, firstCueText.length())
        .withFamily("sansSerif");
    assertThat(firstCueText).hasStrikethroughSpanBetween(0, firstCueText.length());
    assertThat(firstCueText)
        .hasBackgroundColorSpanBetween(0, firstCueText.length())
        .withColor(0xFFFF0000);
    assertThat(firstCueText)
        .hasForegroundColorSpanBetween(0, firstCueText.length())
        .withColor(ColorParser.parseTtmlColor("lime"));

    Spanned secondCueText = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCueText.toString()).isEqualTo("text 2");
    assertThat(secondCueText).hasTypefaceSpanBetween(0, secondCueText.length()).withFamily("serif");
    assertThat(secondCueText).hasBoldItalicSpanBetween(0, secondCueText.length());
    assertThat(secondCueText).hasUnderlineSpanBetween(0, secondCueText.length());
    assertThat(secondCueText).hasStrikethroughSpanBetween(0, secondCueText.length());
    assertThat(secondCueText)
        .hasBackgroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFF0000FF);
    assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFFFFFF00);
  }

  @Test
  public void inheritMultipleStyles() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(spanned.toString()).isEqualTo("text 1");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("sansSerif");
    assertThat(spanned).hasBoldItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasStrikethroughSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF0000FF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFFFFFF00);
  }

  @Test
  public void inheritMultipleStylesWithoutLocalAttributes()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    Spanned secondCueText = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCueText.toString()).isEqualTo("text 2");
    assertThat(secondCueText)
        .hasTypefaceSpanBetween(0, secondCueText.length())
        .withFamily("sansSerif");
    assertThat(secondCueText).hasBoldItalicSpanBetween(0, secondCueText.length());
    assertThat(secondCueText).hasStrikethroughSpanBetween(0, secondCueText.length());
    assertThat(secondCueText)
        .hasBackgroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFF0000FF);
    assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(0xFF000000);
  }

  @Test
  public void mergeMultipleStylesWithParentStyle() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    Spanned thirdCueText = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCueText.toString()).isEqualTo("text 2.5");
    assertThat(thirdCueText)
        .hasTypefaceSpanBetween(0, thirdCueText.length())
        .withFamily("sansSerifInline");
    assertThat(thirdCueText).hasItalicSpanBetween(0, thirdCueText.length());
    assertThat(thirdCueText).hasUnderlineSpanBetween(0, thirdCueText.length());
    assertThat(thirdCueText).hasStrikethroughSpanBetween(0, thirdCueText.length());
    assertThat(thirdCueText)
        .hasBackgroundColorSpanBetween(0, thirdCueText.length())
        .withColor(0xFFFF0000);
    assertThat(thirdCueText)
        .hasForegroundColorSpanBetween(0, thirdCueText.length())
        .withColor(0xFFFFFF00);
  }

  @Test
  public void multipleRegions() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(MULTIPLE_REGIONS_TTML_FILE);

    List<Cue> cues = subtitle.getCues(1_000_000);
    assertThat(cues).hasSize(2);
    Cue cue = cues.get(0);
    assertThat(cue.text.toString()).isEqualTo("lorem");
    assertThat(cue.position).isEqualTo(10f / 100f);
    assertThat(cue.line).isEqualTo(10f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cue = cues.get(1);
    assertThat(cue.text.toString()).isEqualTo("amet");
    assertThat(cue.position).isEqualTo(60f / 100f);
    assertThat(cue.line).isEqualTo(10f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cue = getOnlyCueAtTimeUs(subtitle, 5_000_000);
    assertThat(cue.text.toString()).isEqualTo("ipsum");
    assertThat(cue.position).isEqualTo(40f / 100f);
    assertThat(cue.line).isEqualTo(40f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cue = getOnlyCueAtTimeUs(subtitle, 9_000_000);
    assertThat(cue.text.toString()).isEqualTo("dolor");
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    // TODO: Should be as below, once https://github.com/google/ExoPlayer/issues/2953 is fixed.
    // assertEquals(10f / 100f, cue.position);
    // assertEquals(80f / 100f, cue.line);
    // assertEquals(1f, cue.size);

    cue = getOnlyCueAtTimeUs(subtitle, 21_000_000);
    assertThat(cue.text.toString()).isEqualTo("They first said this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
    assertThat(cue.size).isEqualTo(35f / 100f);

    cue = getOnlyCueAtTimeUs(subtitle, 25_000_000);
    assertThat(cue.text.toString()).isEqualTo("They first said this\nThen this");

    cue = getOnlyCueAtTimeUs(subtitle, 29_000_000);
    assertThat(cue.text.toString()).isEqualTo("They first said this\nThen this\nFinally this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
  }

  @Test
  public void emptyStyleAttribute() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fourthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 3);

    assertThat(queryChildrenForTag(fourthDiv, TtmlNode.TAG_P, 0).getStyleIds()).isNull();
  }

  @Test
  public void nonexistingStyleId() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode fifthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 4);

    assertThat(queryChildrenForTag(fifthDiv, TtmlNode.TAG_P, 0).getStyleIds()).hasLength(1);
  }

  @Test
  public void nonExistingAndExistingStyleIdWithRedundantSpaces()
      throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(12);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode sixthDiv = queryChildrenForTag(body, TtmlNode.TAG_DIV, 5);

    String[] styleIds = queryChildrenForTag(sixthDiv, TtmlNode.TAG_P, 0).getStyleIds();
    assertThat(styleIds).hasLength(2);
  }

  @Test
  public void multipleChaining() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(CHAIN_MULTIPLE_STYLES_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);

    Map<String, TtmlStyle> globalStyles = subtitle.getGlobalStyles();

    TtmlStyle style = globalStyles.get("s2");
    assertThat(style.getFontFamily()).isEqualTo("serif");
    assertThat(style.getBackgroundColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getFontColor()).isEqualTo(0xFF000000);
    assertThat(style.getStyle()).isEqualTo(TtmlStyle.STYLE_BOLD_ITALIC);
    assertThat(style.isLinethrough()).isTrue();

    style = globalStyles.get("s3");
    // only difference: color must be RED
    assertThat(style.getFontColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getFontFamily()).isEqualTo("serif");
    assertThat(style.getBackgroundColor()).isEqualTo(0xFFFF0000);
    assertThat(style.getStyle()).isEqualTo(TtmlStyle.STYLE_BOLD_ITALIC);
    assertThat(style.isLinethrough()).isTrue();
  }

  @Test
  public void noUnderline() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 0);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertWithMessage("noUnderline from inline attribute expected")
        .that(style.isUnderline())
        .isFalse();
  }

  @Test
  public void noLinethrough() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(NO_UNDERLINE_LINETHROUGH_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    TtmlNode root = subtitle.getRoot();
    TtmlNode body = queryChildrenForTag(root, TtmlNode.TAG_BODY, 0);
    TtmlNode div = queryChildrenForTag(body, TtmlNode.TAG_DIV, 1);

    TtmlStyle style = queryChildrenForTag(div, TtmlNode.TAG_P, 0).style;
    assertWithMessage("noLineThrough from inline attribute expected in second pNode")
        .that(style.isLinethrough())
        .isFalse();
  }

  @Test
  public void fontSizeSpans() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(10);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(String.valueOf(spanned)).isEqualTo("text 1");
    assertThat(spanned).hasAbsoluteSizeSpanBetween(0, spanned.length()).withAbsoluteSize(32);

    spanned = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(spanned.toString()).isEqualTo("text 2");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(2.2f);

    spanned = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(spanned.toString()).isEqualTo("text 3");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(1.5f);

    spanned = getOnlyCueTextAtTimeUs(subtitle, 40_000_000);
    assertThat(spanned.toString()).isEqualTo("two values");
    assertThat(spanned).hasAbsoluteSizeSpanBetween(0, spanned.length()).withAbsoluteSize(16);

    spanned = getOnlyCueTextAtTimeUs(subtitle, 50_000_000);
    assertThat(spanned.toString()).isEqualTo("leading dot");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(0.5f);
  }

  @Test
  public void fontSizeWithMissingUnitIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_MISSING_UNIT_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(spanned.toString()).isEqualTo("no unit");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void fontSizeWithInvalidValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_INVALID_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());

    spanned = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());

    spanned = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid dot");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void fontSizeWithEmptyValueIsIgnored() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FONT_SIZE_EMPTY_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);

    Spanned spanned = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(String.valueOf(spanned)).isEqualTo("empty");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void frameRate() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(FRAME_RATE_TTML_FILE);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);
    assertThat(subtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(subtitle.getEventTime(1)).isEqualTo(1_010_000);
    assertThat((double) subtitle.getEventTime(2)).isWithin(1000).of(1_001_000_000);
    assertThat((double) subtitle.getEventTime(3)).isWithin(2000).of(2_002_000_000);
  }

  @Test
  public void bitmapPercentageRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_REGION_FILE);

    Cue cue = getOnlyCueAtTimeUs(subtitle, 1_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);

    cue = getOnlyCueAtTimeUs(subtitle, 4_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(21f / 100f);
    assertThat(cue.line).isEqualTo(35f / 100f);
    assertThat(cue.size).isEqualTo(57f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(6f / 100f);

    cue = getOnlyCueAtTimeUs(subtitle, 7_500_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);
  }

  @Test
  public void bitmapPixelRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_PIXEL_REGION_FILE);

    Cue cue = getOnlyCueAtTimeUs(subtitle, 1_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(307f / 1280f);
    assertThat(cue.line).isEqualTo(562f / 720f);
    assertThat(cue.size).isEqualTo(653f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(86f / 720f);

    cue = getOnlyCueAtTimeUs(subtitle, 4_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(269f / 1280f);
    assertThat(cue.line).isEqualTo(612f / 720f);
    assertThat(cue.size).isEqualTo(730f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(43f / 720f);
  }

  @Test
  public void bitmapUnsupportedRegion() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(BITMAP_UNSUPPORTED_REGION_FILE);

    Cue cue = getOnlyCueAtTimeUs(subtitle, 1_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);

    cue = getOnlyCueAtTimeUs(subtitle, 4_000_000);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);
  }

  @Test
  public void textAlign() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(TEXT_ALIGN_FILE);

    Cue firstCue = getOnlyCueAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue.text.toString()).isEqualTo("Start alignment");
    assertThat(firstCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue secondCue = getOnlyCueAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue.text.toString()).isEqualTo("Left alignment");
    assertThat(secondCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue thirdCue = getOnlyCueAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue.text.toString()).isEqualTo("Center alignment");
    assertThat(thirdCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);

    Cue fourthCue = getOnlyCueAtTimeUs(subtitle, 40_000_000);
    assertThat(fourthCue.text.toString()).isEqualTo("Right alignment");
    assertThat(fourthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue fifthCue = getOnlyCueAtTimeUs(subtitle, 50_000_000);
    assertThat(fifthCue.text.toString()).isEqualTo("End alignment");
    assertThat(fifthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue sixthCue = getOnlyCueAtTimeUs(subtitle, 60_000_000);
    assertThat(sixthCue.text.toString()).isEqualTo("Justify alignment (unsupported)");
    assertThat(sixthCue.textAlignment).isNull();

    Cue seventhCue = getOnlyCueAtTimeUs(subtitle, 70_000_000);
    assertThat(seventhCue.text.toString()).isEqualTo("No textAlign property");
    assertThat(seventhCue.textAlignment).isNull();

    Cue eighthCue = getOnlyCueAtTimeUs(subtitle, 80_000_000);
    assertThat(eighthCue.text.toString()).isEqualTo("Ancestor start alignment");
    assertThat(eighthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue ninthCue = getOnlyCueAtTimeUs(subtitle, 90_000_000);
    assertThat(ninthCue.text.toString()).isEqualTo("Not a P node");
    assertThat(ninthCue.textAlignment).isNull();
  }

  @Test
  public void multiRowAlign() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(MULTI_ROW_ALIGN_FILE);

    Cue firstCue = getOnlyCueAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue secondCue = getOnlyCueAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);

    Cue thirdCue = getOnlyCueAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue fourthCue = getOnlyCueAtTimeUs(subtitle, 40_000_000);
    assertThat(fourthCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue fifthCue = getOnlyCueAtTimeUs(subtitle, 50_000_000);
    assertThat(fifthCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue sixthCue = getOnlyCueAtTimeUs(subtitle, 60_000_000);
    assertThat(sixthCue.multiRowAlignment).isNull();

    Cue seventhCue = getOnlyCueAtTimeUs(subtitle, 70_000_000);
    assertThat(seventhCue.multiRowAlignment).isNull();
  }

  @Test
  public void verticalText() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(VERTICAL_TEXT_FILE);

    Cue firstCue = getOnlyCueAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_RL);

    Cue secondCue = getOnlyCueAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_LR);

    Cue thirdCue = getOnlyCueAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue.verticalType).isEqualTo(Cue.TYPE_UNSET);
  }

  @Test
  public void textCombine() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(TEXT_COMBINE_FILE);

    Spanned firstCue = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue)
        .hasHorizontalTextInVerticalContextSpanBetween(
            "text with ".length(), "text with combined".length());

    Spanned secondCue = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue)
        .hasNoHorizontalTextInVerticalContextSpanBetween(
            "text with ".length(), "text with un-combined".length());

    Spanned thirdCue = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue).hasNoHorizontalTextInVerticalContextSpanBetween(0, thirdCue.length());
  }

  @Test
  public void rubies() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(RUBIES_FILE);

    Spanned firstCue = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(firstCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("1st rubies", TextAnnotation.POSITION_BEFORE);
    assertThat(firstCue)
        .hasRubySpanBetween("Cue with annotated ".length(), "Cue with annotated text".length())
        .withTextAndPosition("2nd rubies", TextAnnotation.POSITION_UNKNOWN);

    Spanned secondCue = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(secondCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("rubies", TextAnnotation.POSITION_UNKNOWN);

    Spanned thirdCue = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(thirdCue).hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length());

    Spanned fourthCue = getOnlyCueTextAtTimeUs(subtitle, 40_000_000);
    assertThat(fourthCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(fourthCue).hasNoRubySpanBetween(0, fourthCue.length());

    Spanned fifthCue = getOnlyCueTextAtTimeUs(subtitle, 50_000_000);
    assertThat(fifthCue.toString()).isEqualTo("Cue with text.");
    assertThat(fifthCue).hasNoRubySpanBetween(0, fifthCue.length());

    Spanned sixthCue = getOnlyCueTextAtTimeUs(subtitle, 60_000_000);
    assertThat(sixthCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(sixthCue).hasNoRubySpanBetween(0, sixthCue.length());
  }

  @Test
  public void textEmphasis() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(TEXT_EMPHASIS_FILE);

    Spanned firstCue = getOnlyCueTextAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue)
        .hasTextEmphasisSpanBetween("None ".length(), "None おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_NONE,
            TextEmphasisSpan.MARK_FILL_UNKNOWN,
            TextAnnotation.POSITION_BEFORE);

    Spanned secondCue = getOnlyCueTextAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue)
        .hasTextEmphasisSpanBetween("Auto ".length(), "Auto ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned thirdCue = getOnlyCueTextAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue)
        .hasTextEmphasisSpanBetween("Filled circle ".length(), "Filled circle こんばんは".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fourthCue = getOnlyCueTextAtTimeUs(subtitle, 40_000_000);
    assertThat(fourthCue)
        .hasTextEmphasisSpanBetween("Filled dot ".length(), "Filled dot ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fifthCue = getOnlyCueTextAtTimeUs(subtitle, 50_000_000);
    assertThat(fifthCue)
        .hasTextEmphasisSpanBetween("Filled sesame ".length(), "Filled sesame おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned sixthCue = getOnlyCueTextAtTimeUs(subtitle, 60_000_000);
    assertThat(sixthCue)
        .hasTextEmphasisSpanBetween(
            "Open circle before ".length(), "Open circle before ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_BEFORE);

    Spanned seventhCue = getOnlyCueTextAtTimeUs(subtitle, 70_000_000);
    assertThat(seventhCue)
        .hasTextEmphasisSpanBetween("Open dot after ".length(), "Open dot after おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_AFTER);

    Spanned eighthCue = getOnlyCueTextAtTimeUs(subtitle, 80_000_000);
    assertThat(eighthCue)
        .hasTextEmphasisSpanBetween(
            "Open sesame outside ".length(), "Open sesame outside ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_BEFORE);

    Spanned ninthCue = getOnlyCueTextAtTimeUs(subtitle, 90_000_000);
    assertThat(ninthCue)
        .hasTextEmphasisSpanBetween("Auto outside ".length(), "Auto outside おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned tenthCue = getOnlyCueTextAtTimeUs(subtitle, 100_000_000);
    assertThat(tenthCue)
        .hasTextEmphasisSpanBetween("Circle before ".length(), "Circle before ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned eleventhCue = getOnlyCueTextAtTimeUs(subtitle, 110_000_000);
    assertThat(eleventhCue)
        .hasTextEmphasisSpanBetween("Sesame after ".length(), "Sesame after おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER);

    Spanned twelfthCue = getOnlyCueTextAtTimeUs(subtitle, 120_000_000);
    assertThat(twelfthCue)
        .hasTextEmphasisSpanBetween("Dot outside ".length(), "Dot outside ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned thirteenthCue = getOnlyCueTextAtTimeUs(subtitle, 130_000_000);
    assertThat(thirteenthCue)
        .hasNoTextEmphasisSpanBetween(
            "No textEmphasis property ".length(), "No textEmphasis property おはよ".length());

    Spanned fourteenthCue = getOnlyCueTextAtTimeUs(subtitle, 140_000_000);
    assertThat(fourteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TBLR) ".length(), "Auto (TBLR) ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fifteenthCue = getOnlyCueTextAtTimeUs(subtitle, 150_000_000);
    assertThat(fifteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TBRL) ".length(), "Auto (TBRL) おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned sixteenthCue = getOnlyCueTextAtTimeUs(subtitle, 160_000_000);
    assertThat(sixteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TB) ".length(), "Auto (TB) ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned seventeenthCue = getOnlyCueTextAtTimeUs(subtitle, 170_000_000);
    assertThat(seventeenthCue)
        .hasTextEmphasisSpanBetween("Auto (LR) ".length(), "Auto (LR) おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void shear() throws IOException, SubtitleDecoderException {
    TtmlSubtitle subtitle = getSubtitle(SHEAR_FILE);

    Cue firstCue = getOnlyCueAtTimeUs(subtitle, 10_000_000);
    assertThat(firstCue.shearDegrees).isZero();

    Cue secondCue = getOnlyCueAtTimeUs(subtitle, 20_000_000);
    assertThat(secondCue.shearDegrees).isWithin(0.01f).of(-15f);

    Cue thirdCue = getOnlyCueAtTimeUs(subtitle, 30_000_000);
    assertThat(thirdCue.shearDegrees).isWithin(0.01f).of(15f);

    Cue fourthCue = getOnlyCueAtTimeUs(subtitle, 40_000_000);
    assertThat(fourthCue.shearDegrees).isWithin(0.01f).of(-15f);

    Cue fifthCue = getOnlyCueAtTimeUs(subtitle, 50_000_000);
    assertThat(fifthCue.shearDegrees).isWithin(0.01f).of(-22.5f);

    Cue sixthCue = getOnlyCueAtTimeUs(subtitle, 60_000_000);
    assertThat(sixthCue.shearDegrees).isWithin(0.01f).of(0f);

    Cue seventhCue = getOnlyCueAtTimeUs(subtitle, 70_000_000);
    assertThat(seventhCue.shearDegrees).isWithin(0.01f).of(-90f);

    Cue eighthCue = getOnlyCueAtTimeUs(subtitle, 80_000_000);
    assertThat(eighthCue.shearDegrees).isWithin(0.01f).of(90f);
  }

  private static Spanned getOnlyCueTextAtTimeUs(Subtitle subtitle, long timeUs) {
    Cue cue = getOnlyCueAtTimeUs(subtitle, timeUs);
    assertThat(cue.text).isInstanceOf(Spanned.class);
    return (Spanned) Assertions.checkNotNull(cue.text);
  }

  private static Cue getOnlyCueAtTimeUs(Subtitle subtitle, long timeUs) {
    List<Cue> cues = subtitle.getCues(timeUs);
    assertThat(cues).hasSize(1);
    return cues.get(0);
  }

  private static TtmlNode queryChildrenForTag(TtmlNode node, String tag, int pos) {
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

  private static TtmlSubtitle getSubtitle(String file)
      throws IOException, SubtitleDecoderException {
    TtmlDecoder ttmlDecoder = new TtmlDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), file);
    return (TtmlSubtitle) ttmlDecoder.decode(bytes, bytes.length, false);
  }
}

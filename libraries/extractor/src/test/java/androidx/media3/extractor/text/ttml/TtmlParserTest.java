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
package androidx.media3.extractor.text.ttml;

import static androidx.media3.test.utils.truth.SpannedSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import android.text.Layout;
import android.text.Spanned;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.TextAnnotation;
import androidx.media3.common.text.TextEmphasisSpan;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ColorParser;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser.OutputOptions;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TtmlParser}. */
@RunWith(AndroidJUnit4.class)
public final class TtmlParserTest {

  private static final String SIMPLE_TTML_FILE = "media/ttml/simple.xml";
  private static final String OVERLAPPING_TIMES_TTML_FILE = "media/ttml/overlapping_times.xml";
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
  public void simple_allCues() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(SIMPLE_TTML_FILE);

    assertThat(allCues).hasSize(3);

    CuesWithTiming firstCue = allCues.get(0);
    assertThat(firstCue.startTimeUs).isEqualTo(10_000_000);
    assertThat(firstCue.durationUs).isEqualTo(8_000_000);
    assertThat(firstCue.endTimeUs).isEqualTo(18_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");

    CuesWithTiming secondCue = allCues.get(1);
    assertThat(secondCue.startTimeUs).isEqualTo(20_000_000);
    assertThat(secondCue.durationUs).isEqualTo(8_000_000);
    assertThat(secondCue.endTimeUs).isEqualTo(28_000_000);
    assertThat(Lists.transform(secondCue.cues, c -> c.text.toString())).containsExactly("cue 2");

    CuesWithTiming thirdCue = allCues.get(2);
    assertThat(thirdCue.startTimeUs).isEqualTo(30_000_000);
    assertThat(thirdCue.durationUs).isEqualTo(8_000_000);
    assertThat(thirdCue.endTimeUs).isEqualTo(38_000_000);
    assertThat(Lists.transform(thirdCue.cues, c -> c.text.toString())).containsExactly("cue 3");
  }

  @Test
  public void simple_onlyCuesAfterTime() throws Exception {
    TtmlParser parser = new TtmlParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SIMPLE_TTML_FILE);

    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(bytes, OutputOptions.onlyCuesAfter(/* startTimeUs= */ 11_000_000), cues::add);

    assertThat(cues).hasSize(3);

    CuesWithTiming firstCue = cues.get(0);
    // First cue is truncated to OutputOptions.startTimeUs
    assertThat(firstCue.startTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");
    assertThat(getOnlyCueTextAtIndex(cues, 1).toString()).isEqualTo("cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 2).toString()).isEqualTo("cue 3");
  }

  @Test
  public void simple_cuesAfterTimeThenCuesBefore() throws Exception {
    TtmlParser parser = new TtmlParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), SIMPLE_TTML_FILE);

    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(bytes, OutputOptions.cuesAfterThenRemainingCuesBefore(11_000_000), cues::add);

    assertThat(cues).hasSize(4);

    CuesWithTiming firstCue = cues.get(0);
    // First cue is truncated to OutputOptions.startTimeUs
    assertThat(firstCue.startTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");

    assertThat(getOnlyCueTextAtIndex(cues, 1).toString()).isEqualTo("cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 2).toString()).isEqualTo("cue 3");

    CuesWithTiming fourthCue = cues.get(3);
    // Last cue is the part of firstCue before OutputOptions.startTimeUs
    assertThat(fourthCue.startTimeUs).isEqualTo(10_000_000);
    assertThat(fourthCue.endTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(fourthCue.cues, c -> c.text.toString())).containsExactly("cue 1");
  }

  @Test
  public void overlappingTimes_allCues() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(OVERLAPPING_TIMES_TTML_FILE);

    assertThat(allCues).hasSize(5);

    CuesWithTiming firstCue = allCues.get(0);
    assertThat(firstCue.startTimeUs).isEqualTo(10_000_000);
    assertThat(firstCue.durationUs).isEqualTo(5_000_000);
    assertThat(firstCue.endTimeUs).isEqualTo(15_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");

    CuesWithTiming secondCue = allCues.get(1);
    assertThat(secondCue.startTimeUs).isEqualTo(15_000_000);
    assertThat(secondCue.durationUs).isEqualTo(1_000_000);
    assertThat(secondCue.endTimeUs).isEqualTo(16_000_000);
    assertThat(Lists.transform(secondCue.cues, c -> c.text.toString()))
        .containsExactly("cue 1\ncue 2: nested inside cue 1");

    CuesWithTiming thirdCue = allCues.get(2);
    assertThat(thirdCue.startTimeUs).isEqualTo(16_000_000);
    assertThat(thirdCue.durationUs).isEqualTo(4_000_000);
    assertThat(thirdCue.endTimeUs).isEqualTo(20_000_000);
    assertThat(Lists.transform(thirdCue.cues, c -> c.text.toString()))
        .containsExactly("cue 1\ncue 2: nested inside cue 1\ncue 3: overlaps with cue 2");

    CuesWithTiming fourthCue = allCues.get(3);
    assertThat(fourthCue.startTimeUs).isEqualTo(20_000_000);
    assertThat(fourthCue.durationUs).isEqualTo(5_000_000);
    assertThat(fourthCue.endTimeUs).isEqualTo(25_000_000);
    assertThat(Lists.transform(fourthCue.cues, c -> c.text.toString()))
        .containsExactly("cue 1\ncue 3: overlaps with cue 2");

    CuesWithTiming fifthCue = allCues.get(4);
    assertThat(fifthCue.startTimeUs).isEqualTo(25_000_000);
    assertThat(fifthCue.durationUs).isEqualTo(3_000_000);
    assertThat(fifthCue.endTimeUs).isEqualTo(28_000_000);
    assertThat(Lists.transform(fifthCue.cues, c -> c.text.toString()))
        .containsExactly("cue 3: overlaps with cue 2");
  }

  @Test
  public void overlappingTimes_onlyCuesAfterTime() throws Exception {
    TtmlParser parser = new TtmlParser();
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), OVERLAPPING_TIMES_TTML_FILE);

    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(bytes, OutputOptions.onlyCuesAfter(/* startTimeUs= */ 11_000_000), cues::add);

    assertThat(cues).hasSize(5);

    CuesWithTiming firstCue = cues.get(0);
    // First cue is truncated to OutputOptions.startTimeUs
    assertThat(firstCue.startTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");
    assertThat(getOnlyCueTextAtIndex(cues, 1).toString())
        .isEqualTo("cue 1\ncue 2: nested inside cue 1");
    assertThat(getOnlyCueTextAtIndex(cues, 2).toString())
        .isEqualTo("cue 1\ncue 2: nested inside cue 1\ncue 3: overlaps with cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 3).toString())
        .isEqualTo("cue 1\ncue 3: overlaps with cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 4).toString()).isEqualTo("cue 3: overlaps with cue 2");
  }

  @Test
  public void overlappingTimes_cuesAfterTimeThenCuesBefore() throws Exception {
    TtmlParser parser = new TtmlParser();
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), OVERLAPPING_TIMES_TTML_FILE);

    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(
        bytes,
        OutputOptions.cuesAfterThenRemainingCuesBefore(/* startTimeUs= */ 11_000_000),
        cues::add);

    assertThat(cues).hasSize(6);

    CuesWithTiming firstCue = cues.get(0);
    // First cue is truncated to OutputOptions.startTimeUs
    assertThat(firstCue.startTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(firstCue.cues, c -> c.text.toString())).containsExactly("cue 1");

    assertThat(getOnlyCueTextAtIndex(cues, 1).toString())
        .isEqualTo("cue 1\ncue 2: nested inside cue 1");
    assertThat(getOnlyCueTextAtIndex(cues, 2).toString())
        .isEqualTo("cue 1\ncue 2: nested inside cue 1\ncue 3: overlaps with cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 3).toString())
        .isEqualTo("cue 1\ncue 3: overlaps with cue 2");
    assertThat(getOnlyCueTextAtIndex(cues, 4).toString()).isEqualTo("cue 3: overlaps with cue 2");

    CuesWithTiming sixthCue = cues.get(5);
    // Last cue is truncated to end at OutputOptions.startTimeUs
    assertThat(sixthCue.startTimeUs).isEqualTo(10_000_000);
    assertThat(sixthCue.endTimeUs).isEqualTo(11_000_000);
    assertThat(Lists.transform(sixthCue.cues, c -> c.text.toString())).containsExactly("cue 1");
  }

  @Test
  public void inlineAttributes() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
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
  public void inheritInlineAttributes() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 1);
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
   */
  // JellyBean Color:
  // https://github.com/android/platform_frameworks_base/blob/jb-mr2-release/graphics/java/android/graphics/Color.java#L414
  // KitKat Color:
  // https://github.com/android/platform_frameworks_base/blob/kitkat-mr2.2-release/graphics/java/android/graphics/Color.java#L414
  @Test
  public void lime() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INLINE_ATTRIBUTES_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(spanned.toString()).isEqualTo("text 2");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("sansSerif");
    assertThat(spanned).hasItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasStrikethroughSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF00FFFF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFF00FF00);
  }

  @Test
  public void inheritGlobalStyle() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_STYLE_TTML_FILE);

    assertThat(allCues).hasSize(1);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(spanned.toString()).isEqualTo("text 1");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("serif");
    assertThat(spanned).hasBoldItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasUnderlineSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF0000FF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFFFFFF00);
  }

  @Test
  public void inheritGlobalStyleOverriddenByInlineAttributes() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_STYLE_OVERRIDE_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned firstCueText = getOnlyCueTextAtIndex(allCues, 0);
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

    Spanned secondCueText = getOnlyCueTextAtIndex(allCues, 1);
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
  public void inheritGlobalAndParent() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_GLOBAL_AND_PARENT_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned firstCueText = getOnlyCueTextAtIndex(allCues, 0);
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

    Spanned secondCueText = getOnlyCueTextAtIndex(allCues, 1);
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
  public void inheritMultipleStyles() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(spanned.toString()).isEqualTo("text 1");
    assertThat(spanned).hasTypefaceSpanBetween(0, spanned.length()).withFamily("sansSerif");
    assertThat(spanned).hasBoldItalicSpanBetween(0, spanned.length());
    assertThat(spanned).hasStrikethroughSpanBetween(0, spanned.length());
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFF0000FF);
    assertThat(spanned).hasForegroundColorSpanBetween(0, spanned.length()).withColor(0xFFFFFF00);
  }

  @Test
  public void inheritMultipleStylesWithoutLocalAttributes() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned secondCueText = getOnlyCueTextAtIndex(allCues, 1);
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
  public void mergeMultipleStylesWithParentStyle() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned thirdCueText = getOnlyCueTextAtIndex(allCues, 2);
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
  public void multipleRegions() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(MULTIPLE_REGIONS_TTML_FILE);

    assertThat(allCues).hasSize(6);

    ImmutableList<Cue> cues = allCues.get(0).cues;
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

    cue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(cue.text.toString()).isEqualTo("ipsum");
    assertThat(cue.position).isEqualTo(40f / 100f);
    assertThat(cue.line).isEqualTo(40f / 100f);
    assertThat(cue.size).isEqualTo(20f / 100f);

    cue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(cue.text.toString()).isEqualTo("dolor");
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    // TODO: Should be as below, once https://github.com/google/ExoPlayer/issues/2953 is fixed.
    // assertEquals(10f / 100f, cue.position);
    // assertEquals(80f / 100f, cue.line);
    // assertEquals(1f, cue.size);

    cue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(cue.text.toString()).isEqualTo("They first said this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
    assertThat(cue.size).isEqualTo(35f / 100f);

    cue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(cue.text.toString()).isEqualTo("They first said this\nThen this");

    cue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(cue.text.toString()).isEqualTo("They first said this\nThen this\nFinally this");
    assertThat(cue.position).isEqualTo(45f / 100f);
    assertThat(cue.line).isEqualTo(45f / 100f);
  }

  @Test
  public void emptyStyleAttribute() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 3);

    assertThat(allCues.get(3).startTimeUs).isEqualTo(40_000_000);
    assertThat(spanned.toString()).isEqualTo("text 3");
    assertThat(spanned).hasNoSpans();
  }

  @Test
  public void nonexistingStyleId() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 4);

    assertThat(allCues.get(4).startTimeUs).isEqualTo(50_000_000);
    assertThat(spanned.toString()).isEqualTo("text 4");
    assertThat(spanned).hasNoSpans();
  }

  @Test
  public void nonExistingAndExistingStyleIdWithRedundantSpaces() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(INHERIT_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(6);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 5);

    assertThat(allCues.get(5).startTimeUs).isEqualTo(60_000_000);
    assertThat(spanned.toString()).isEqualTo("text 5");
    assertThat(spanned).hasBackgroundColorSpanBetween(0, spanned.length()).withColor(0xFFFF0000);
  }

  @Test
  public void multipleChaining() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(CHAIN_MULTIPLE_STYLES_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned1 = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(spanned1.toString()).isEqualTo("text 1");
    assertThat(spanned1).hasTypefaceSpanBetween(0, spanned1.length()).withFamily("serif");
    assertThat(spanned1).hasBackgroundColorSpanBetween(0, spanned1.length()).withColor(0xFFFF0000);
    assertThat(spanned1).hasForegroundColorSpanBetween(0, spanned1.length()).withColor(0xFF000000);
    assertThat(spanned1).hasBoldItalicSpanBetween(0, spanned1.length());
    assertThat(spanned1).hasStrikethroughSpanBetween(0, spanned1.length());

    // only difference: foreground (font) color must be RED
    Spanned spanned2 = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(spanned2.toString()).isEqualTo("text 2");
    assertThat(spanned2).hasTypefaceSpanBetween(0, spanned2.length()).withFamily("serif");
    assertThat(spanned2).hasBackgroundColorSpanBetween(0, spanned2.length()).withColor(0xFFFF0000);
    assertThat(spanned2).hasForegroundColorSpanBetween(0, spanned2.length()).withColor(0xFFFF0000);
    assertThat(spanned2).hasBoldItalicSpanBetween(0, spanned2.length());
    assertThat(spanned2).hasStrikethroughSpanBetween(0, spanned2.length());
  }

  @Test
  public void noUnderline() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(NO_UNDERLINE_LINETHROUGH_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(spanned.toString()).isEqualTo("text 1");
    // noUnderline from inline attribute overrides s0 global underline style id
    assertThat(spanned).hasNoUnderlineSpanBetween(0, spanned.length());
  }

  @Test
  public void noLinethrough() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(NO_UNDERLINE_LINETHROUGH_TTML_FILE);

    assertThat(allCues).hasSize(2);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(spanned.toString()).isEqualTo("text 2");
    // noLineThrough from inline attribute overrides s1 global lineThrough style id
    assertThat(spanned).hasNoStrikethroughSpanBetween(0, spanned.length());
  }

  @Test
  public void fontSizeSpans() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(FONT_SIZE_TTML_FILE);

    assertThat(allCues).hasSize(5);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(String.valueOf(spanned)).isEqualTo("text 1");
    assertThat(spanned).hasAbsoluteSizeSpanBetween(0, spanned.length()).withAbsoluteSize(32);

    spanned = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(spanned.toString()).isEqualTo("text 2");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(2.2f);

    spanned = getOnlyCueTextAtIndex(allCues, 2);
    assertThat(spanned.toString()).isEqualTo("text 3");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(1.5f);

    spanned = getOnlyCueTextAtIndex(allCues, 3);
    assertThat(spanned.toString()).isEqualTo("two values");
    assertThat(spanned).hasAbsoluteSizeSpanBetween(0, spanned.length()).withAbsoluteSize(16);

    spanned = getOnlyCueTextAtIndex(allCues, 4);
    assertThat(spanned.toString()).isEqualTo("leading dot");
    assertThat(spanned).hasRelativeSizeSpanBetween(0, spanned.length()).withSizeChange(0.5f);
  }

  @Test
  public void fontSizeWithMissingUnitIsIgnored() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(FONT_SIZE_MISSING_UNIT_TTML_FILE);

    assertThat(allCues).hasSize(1);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(spanned.toString()).isEqualTo("no unit");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void fontSizeWithInvalidValueIsIgnored() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(FONT_SIZE_INVALID_TTML_FILE);

    assertThat(allCues).hasSize(3);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());

    spanned = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());

    spanned = getOnlyCueTextAtIndex(allCues, 2);
    assertThat(String.valueOf(spanned)).isEqualTo("invalid dot");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void fontSizeWithEmptyValueIsIgnored() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(FONT_SIZE_EMPTY_TTML_FILE);

    assertThat(allCues).hasSize(1);

    Spanned spanned = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(String.valueOf(spanned)).isEqualTo("empty");
    assertThat(spanned).hasNoRelativeSizeSpanBetween(0, spanned.length());
    assertThat(spanned).hasNoAbsoluteSizeSpanBetween(0, spanned.length());
  }

  @Test
  public void frameRate() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(FRAME_RATE_TTML_FILE);

    assertThat(allCues).hasSize(2);
    assertThat(allCues.get(0).startTimeUs).isEqualTo(1_000_000);
    assertThat(allCues.get(0).durationUs).isEqualTo(10_000);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_010_000);
    assertThat((double) allCues.get(1).startTimeUs).isWithin(1000).of(1_001_000_000);
    assertThat((double) allCues.get(1).durationUs).isWithin(2000).of(1_001_000_000);
    assertThat((double) allCues.get(1).endTimeUs).isWithin(2000).of(2_002_000_000);
  }

  @Test
  public void bitmapPercentageRegion() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(BITMAP_REGION_FILE);

    assertThat(allCues).hasSize(3);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(200_000);
    assertThat(allCues.get(0).durationUs).isEqualTo(2_800_000);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(3_000_000);
    Cue cue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(3_200_000);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_737_000);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(6_937_000);
    cue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(21f / 100f);
    assertThat(cue.line).isEqualTo(35f / 100f);
    assertThat(cue.size).isEqualTo(57f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(6f / 100f);

    assertThat(allCues.get(2).startTimeUs).isEqualTo(7_200_000);
    cue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(24f / 100f);
    assertThat(cue.line).isEqualTo(28f / 100f);
    assertThat(cue.size).isEqualTo(51f / 100f);
    assertThat(cue.bitmapHeight).isEqualTo(12f / 100f);
  }

  @Test
  public void bitmapPixelRegion() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(BITMAP_PIXEL_REGION_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(200_000);
    assertThat(allCues.get(0).durationUs).isEqualTo(2_800_000);
    Cue cue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(307f / 1280f);
    assertThat(cue.line).isEqualTo(562f / 720f);
    assertThat(cue.size).isEqualTo(653f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(86f / 720f);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(3_200_000);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_737_000);
    cue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(269f / 1280f);
    assertThat(cue.line).isEqualTo(612f / 720f);
    assertThat(cue.size).isEqualTo(730f / 1280f);
    assertThat(cue.bitmapHeight).isEqualTo(43f / 720f);
  }

  @Test
  public void bitmapUnsupportedRegion() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(BITMAP_UNSUPPORTED_REGION_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(200_000);
    assertThat(allCues.get(0).durationUs).isEqualTo(2_800_000);
    Cue cue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(3_200_000);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_737_000);
    cue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(cue.text).isNull();
    assertThat(cue.bitmap).isNotNull();
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.bitmapHeight).isEqualTo(Cue.DIMEN_UNSET);
  }

  @Test
  public void textAlign() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(TEXT_ALIGN_FILE);

    assertThat(allCues).hasSize(9);

    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("Start alignment");
    assertThat(firstCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("Left alignment");
    assertThat(secondCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("Center alignment");
    assertThat(thirdCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);

    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("Right alignment");
    assertThat(fourthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.text.toString()).isEqualTo("End alignment");
    assertThat(fifthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.text.toString()).isEqualTo("Justify alignment (unsupported)");
    assertThat(sixthCue.textAlignment).isNull();

    Cue seventhCue = Iterables.getOnlyElement(allCues.get(6).cues);
    assertThat(seventhCue.text.toString()).isEqualTo("No textAlign property");
    assertThat(seventhCue.textAlignment).isNull();

    Cue eighthCue = Iterables.getOnlyElement(allCues.get(7).cues);
    assertThat(eighthCue.text.toString()).isEqualTo("Ancestor start alignment");
    assertThat(eighthCue.textAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue ninthCue = Iterables.getOnlyElement(allCues.get(8).cues);
    assertThat(ninthCue.text.toString()).isEqualTo("Not a P node");
    assertThat(ninthCue.textAlignment).isNull();
  }

  @Test
  public void multiRowAlign() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(MULTI_ROW_ALIGN_FILE);

    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_CENTER);

    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.multiRowAlignment).isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);

    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.multiRowAlignment).isNull();

    Cue seventhCue = Iterables.getOnlyElement(allCues.get(6).cues);
    assertThat(seventhCue.multiRowAlignment).isNull();
  }

  @Test
  public void verticalText() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(VERTICAL_TEXT_FILE);

    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_RL);

    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_LR);

    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.verticalType).isEqualTo(Cue.TYPE_UNSET);
  }

  @Test
  public void textCombine() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(TEXT_COMBINE_FILE);

    Spanned firstCue = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(firstCue)
        .hasHorizontalTextInVerticalContextSpanBetween(
            "text with ".length(), "text with combined".length());

    Spanned secondCue = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(secondCue)
        .hasNoHorizontalTextInVerticalContextSpanBetween(
            "text with ".length(), "text with un-combined".length());

    Spanned thirdCue = getOnlyCueTextAtIndex(allCues, 2);
    assertThat(thirdCue).hasNoHorizontalTextInVerticalContextSpanBetween(0, thirdCue.length());
  }

  @Test
  public void rubies() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(RUBIES_FILE);

    Spanned firstCue = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(firstCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(firstCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("1st rubies", TextAnnotation.POSITION_BEFORE);
    assertThat(firstCue)
        .hasRubySpanBetween("Cue with annotated ".length(), "Cue with annotated text".length())
        .withTextAndPosition("2nd rubies", TextAnnotation.POSITION_UNKNOWN);

    Spanned secondCue = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(secondCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(secondCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("rubies", TextAnnotation.POSITION_UNKNOWN);

    Spanned thirdCue = getOnlyCueTextAtIndex(allCues, 2);
    assertThat(thirdCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(thirdCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("rubies", TextAnnotation.POSITION_UNKNOWN);

    Spanned fourthCue = getOnlyCueTextAtIndex(allCues, 3);
    assertThat(fourthCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(fourthCue).hasNoRubySpanBetween(0, fourthCue.length());

    Spanned fifthCue = getOnlyCueTextAtIndex(allCues, 4);
    assertThat(fifthCue.toString()).isEqualTo("Cue with text.");
    assertThat(fifthCue).hasNoRubySpanBetween(0, fifthCue.length());

    Spanned sixthCue = getOnlyCueTextAtIndex(allCues, 5);
    assertThat(sixthCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(sixthCue).hasNoRubySpanBetween(0, sixthCue.length());

    Spanned seventhCue = getOnlyCueTextAtIndex(allCues, 6);
    assertThat(seventhCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(seventhCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("rubies", TextAnnotation.POSITION_BEFORE);

    Spanned eighthCue = getOnlyCueTextAtIndex(allCues, 7);
    assertThat(eighthCue.toString()).isEqualTo("Cue with annotated text.");
    assertThat(eighthCue)
        .hasRubySpanBetween("Cue with ".length(), "Cue with annotated".length())
        .withTextAndPosition("rubies", TextAnnotation.POSITION_AFTER);
  }

  @Test
  public void textEmphasis() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(TEXT_EMPHASIS_FILE);

    Spanned firstCue = getOnlyCueTextAtIndex(allCues, 0);
    assertThat(firstCue)
        .hasTextEmphasisSpanBetween("None ".length(), "None おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_NONE,
            TextEmphasisSpan.MARK_FILL_UNKNOWN,
            TextAnnotation.POSITION_BEFORE);

    Spanned secondCue = getOnlyCueTextAtIndex(allCues, 1);
    assertThat(secondCue)
        .hasTextEmphasisSpanBetween("Auto ".length(), "Auto ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned thirdCue = getOnlyCueTextAtIndex(allCues, 2);
    assertThat(thirdCue)
        .hasTextEmphasisSpanBetween("Filled circle ".length(), "Filled circle こんばんは".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fourthCue = getOnlyCueTextAtIndex(allCues, 3);
    assertThat(fourthCue)
        .hasTextEmphasisSpanBetween("Filled dot ".length(), "Filled dot ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fifthCue = getOnlyCueTextAtIndex(allCues, 4);
    assertThat(fifthCue)
        .hasTextEmphasisSpanBetween("Filled sesame ".length(), "Filled sesame おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned sixthCue = getOnlyCueTextAtIndex(allCues, 5);
    assertThat(sixthCue)
        .hasTextEmphasisSpanBetween(
            "Open circle before ".length(), "Open circle before ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_BEFORE);

    Spanned seventhCue = getOnlyCueTextAtIndex(allCues, 6);
    assertThat(seventhCue)
        .hasTextEmphasisSpanBetween("Open dot after ".length(), "Open dot after おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_AFTER);

    Spanned eighthCue = getOnlyCueTextAtIndex(allCues, 7);
    assertThat(eighthCue)
        .hasTextEmphasisSpanBetween(
            "Open sesame outside ".length(), "Open sesame outside ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_OPEN,
            TextAnnotation.POSITION_BEFORE);

    Spanned ninthCue = getOnlyCueTextAtIndex(allCues, 8);
    assertThat(ninthCue)
        .hasTextEmphasisSpanBetween("Auto outside ".length(), "Auto outside おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned tenthCue = getOnlyCueTextAtIndex(allCues, 9);
    assertThat(tenthCue)
        .hasTextEmphasisSpanBetween("Circle before ".length(), "Circle before ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned eleventhCue = getOnlyCueTextAtIndex(allCues, 10);
    assertThat(eleventhCue)
        .hasTextEmphasisSpanBetween("Sesame after ".length(), "Sesame after おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_AFTER);

    Spanned twelfthCue = getOnlyCueTextAtIndex(allCues, 11);
    assertThat(twelfthCue)
        .hasTextEmphasisSpanBetween("Dot outside ".length(), "Dot outside ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_DOT,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned thirteenthCue = getOnlyCueTextAtIndex(allCues, 12);
    assertThat(thirteenthCue)
        .hasNoTextEmphasisSpanBetween(
            "No textEmphasis property ".length(), "No textEmphasis property おはよ".length());

    Spanned fourteenthCue = getOnlyCueTextAtIndex(allCues, 13);
    assertThat(fourteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TBLR) ".length(), "Auto (TBLR) ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned fifteenthCue = getOnlyCueTextAtIndex(allCues, 14);
    assertThat(fifteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TBRL) ".length(), "Auto (TBRL) おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned sixteenthCue = getOnlyCueTextAtIndex(allCues, 15);
    assertThat(sixteenthCue)
        .hasTextEmphasisSpanBetween("Auto (TB) ".length(), "Auto (TB) ございます".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_SESAME,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);

    Spanned seventeenthCue = getOnlyCueTextAtIndex(allCues, 16);
    assertThat(seventeenthCue)
        .hasTextEmphasisSpanBetween("Auto (LR) ".length(), "Auto (LR) おはよ".length())
        .withMarkAndPosition(
            TextEmphasisSpan.MARK_SHAPE_CIRCLE,
            TextEmphasisSpan.MARK_FILL_FILLED,
            TextAnnotation.POSITION_BEFORE);
  }

  @Test
  public void shear() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getAllCues(SHEAR_FILE);

    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.shearDegrees).isZero();

    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.shearDegrees).isWithin(0.01f).of(-15f);

    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.shearDegrees).isWithin(0.01f).of(15f);

    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.shearDegrees).isWithin(0.01f).of(-15f);

    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.shearDegrees).isWithin(0.01f).of(-22.5f);

    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.shearDegrees).isWithin(0.01f).of(0f);

    Cue seventhCue = Iterables.getOnlyElement(allCues.get(6).cues);
    assertThat(seventhCue.shearDegrees).isWithin(0.01f).of(-90f);

    Cue eighthCue = Iterables.getOnlyElement(allCues.get(7).cues);
    assertThat(eighthCue.shearDegrees).isWithin(0.01f).of(90f);
  }

  private static Spanned getOnlyCueTextAtIndex(List<CuesWithTiming> allCues, int index) {
    Cue cue = getOnlyCueAtIndex(allCues, index);
    assertThat(cue.text).isInstanceOf(Spanned.class);
    return (Spanned) Assertions.checkNotNull(cue.text);
  }

  private static Cue getOnlyCueAtIndex(List<CuesWithTiming> allCues, int index) {
    ImmutableList<Cue> cues = allCues.get(index).cues;
    assertThat(cues).hasSize(1);
    return cues.get(0);
  }

  private static ImmutableList<CuesWithTiming> getAllCues(String file) throws IOException {
    TtmlParser ttmlParser = new TtmlParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), file);
    ImmutableList.Builder<CuesWithTiming> allCues = ImmutableList.builder();
    ttmlParser.parse(bytes, OutputOptions.allCues(), allCues::add);
    return allCues.build();
  }
}

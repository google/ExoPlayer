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
package androidx.media3.extractor.text.ssa;

import static androidx.media3.common.Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.graphics.Color;
import android.text.Layout;
import android.text.Spanned;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleParser.OutputOptions;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.test.utils.truth.SpannedSubject;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test {@link SsaParser}. */
@RunWith(AndroidJUnit4.class)
public final class SsaParserTest {

  private static final String EMPTY = "media/ssa/empty";
  private static final String EMPTY_STYLE_LINE = "media/ssa/empty_style_line";
  private static final String TYPICAL = "media/ssa/typical";
  private static final String TYPICAL_HEADER_ONLY = "media/ssa/typical_header";
  private static final String TYPICAL_DIALOGUE_ONLY = "media/ssa/typical_dialogue";
  private static final String TYPICAL_FORMAT_ONLY = "media/ssa/typical_format";
  private static final String TYPICAL_UTF16LE = "media/ssa/typical_utf16le";
  private static final String TYPICAL_UTF16BE = "media/ssa/typical_utf16be";
  private static final String OVERLAPPING_TIMECODES = "media/ssa/overlapping_timecodes";
  private static final String POSITIONS = "media/ssa/positioning";
  private static final String INVALID_TIMECODES = "media/ssa/invalid_timecodes";
  private static final String INVALID_POSITIONS = "media/ssa/invalid_positioning";
  private static final String POSITIONS_WITHOUT_PLAYRES = "media/ssa/positioning_without_playres";
  private static final String STYLE_PRIMARY_COLOR = "media/ssa/style_primary_color";
  private static final String STYLE_OUTLINE_COLOR = "media/ssa/style_outline_color";
  private static final String STYLE_FONT_SIZE = "media/ssa/style_font_size";
  private static final String STYLE_BOLD_ITALIC = "media/ssa/style_bold_italic";
  private static final String STYLE_UNDERLINE = "media/ssa/style_underline";
  private static final String STYLE_STRIKEOUT = "media/ssa/style_strikeout";

  @Test
  public void cuesReplacementBehaviorIsMerge() throws IOException {
    SsaParser parser = new SsaParser();
    assertThat(parser.getCueReplacementBehavior()).isEqualTo(CUE_REPLACEMENT_BEHAVIOR_MERGE);
  }

  @Test
  public void parseEmpty() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).isEmpty();
  }

  @Test
  public void parseEmptyStyleLine() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY_STYLE_LINE);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(1);
    Cue cue = Iterables.getOnlyElement(allCues.get(0).cues);
    SpannedSubject.assertThat((Spanned) cue.text).hasNoSpans();
    assertThat(cue.textSize).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.textSizeType).isEqualTo(Cue.TYPE_UNSET);
    assertThat(cue.textAlignment).isNull();
    assertThat(cue.positionAnchor).isEqualTo(Cue.TYPE_UNSET);
    assertThat(cue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.size).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.lineAnchor).isEqualTo(Cue.TYPE_UNSET);
    assertThat(cue.line).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(cue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
  }

  @Test
  public void parseTypical() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(3);
    // Check position, line, anchors & alignment are set from Alignment Style (2 - bottom-center).
    Cue firstCue = allCues.get(0).cues.get(0);
    assertWithMessage("Cue.textAlignment")
        .that(firstCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_CENTER);
    assertWithMessage("Cue.positionAnchor")
        .that(firstCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertThat(firstCue.position).isEqualTo(0.5f);
    assertThat(firstCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.95f);

    assertTypicalCue1(allCues.get(0));
    assertTypicalCue2(allCues.get(1));
    assertTypicalCue3(allCues.get(2));
  }

  @Test
  public void parseTypical_onlyCuesAfterTime() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL);
    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(bytes, OutputOptions.onlyCuesAfter(/* startTimeUs= */ 1_000_000), cues::add);

    assertThat(cues).hasSize(2);
    assertTypicalCue2(cues.get(0));
    assertTypicalCue3(cues.get(1));
  }

  @Test
  public void parseTypical_cuesAfterTimeThenCuesBefore() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL);
    List<CuesWithTiming> cues = new ArrayList<>();
    parser.parse(
        bytes,
        OutputOptions.cuesAfterThenRemainingCuesBefore(/* startTimeUs= */ 1_000_000),
        cues::add);

    assertThat(cues).hasSize(3);
    assertTypicalCue2(cues.get(0));
    assertTypicalCue3(cues.get(1));
    assertTypicalCue1(cues.get(2));
  }

  @Test
  public void parseTypicalWithInitializationData() throws IOException {
    byte[] headerBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_HEADER_ONLY);
    byte[] formatBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_FORMAT_ONLY);
    ArrayList<byte[]> initializationData = new ArrayList<>();
    initializationData.add(formatBytes);
    initializationData.add(headerBytes);
    SsaParser parser = new SsaParser(initializationData);
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_DIALOGUE_ONLY);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(3);
    assertTypicalCue1(allCues.get(0));
    assertTypicalCue2(allCues.get(1));
    assertTypicalCue3(allCues.get(2));
  }

  @Test
  public void parseTypicalWithInitializationDataAtOffsetIntoDialogueAndRestrictedLength()
      throws IOException {
    byte[] headerBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_HEADER_ONLY);
    byte[] formatBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_FORMAT_ONLY);
    ArrayList<byte[]> initializationData = new ArrayList<>();
    initializationData.add(formatBytes);
    initializationData.add(headerBytes);
    SsaParser parser = new SsaParser(initializationData);
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_DIALOGUE_ONLY);
    List<CuesWithTiming> allCues = new ArrayList<>();
    parser.parse(
        bytes,
        /* offset= */ 10,
        /* length= */ bytes.length - 30,
        OutputOptions.allCues(),
        allCues::add);

    assertThat(allCues).hasSize(2);
    // Because of the offset, we skip the first line of dialogue
    assertTypicalCue2(allCues.get(0));
    // Because of the length restriction, we only partially parse the third line of dialogue
    assertThat(allCues.get(1).startTimeUs).isEqualTo(4560000);
    assertThat(allCues.get(1).durationUs).isEqualTo(8900000 - 4560000);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(8900000);
    assertThat(allCues.get(1).cues.get(0).text.toString()).isEqualTo("This is the third subt");
  }

  @Test
  public void parseTypicalUtf16le() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_UTF16LE);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(3);
    // Check position, line, anchors & alignment are set from Alignment Style (2 - bottom-center).
    Cue firstCue = allCues.get(0).cues.get(0);
    assertWithMessage("Cue.textAlignment")
        .that(firstCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_CENTER);
    assertWithMessage("Cue.positionAnchor")
        .that(firstCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertThat(firstCue.position).isEqualTo(0.5f);
    assertThat(firstCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.95f);

    assertTypicalCue1(allCues.get(0));
    assertTypicalCue2(allCues.get(1));
    assertTypicalCue3(allCues.get(2));
  }

  @Test
  public void parseTypicalUtf16be() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_UTF16BE);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(3);
    // Check position, line, anchors & alignment are set from Alignment Style (2 - bottom-center).
    Cue firstCue = allCues.get(0).cues.get(0);
    assertWithMessage("Cue.textAlignment")
        .that(firstCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_CENTER);
    assertWithMessage("Cue.positionAnchor")
        .that(firstCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertThat(firstCue.position).isEqualTo(0.5f);
    assertThat(firstCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.95f);

    assertTypicalCue1(allCues.get(0));
    assertTypicalCue2(allCues.get(1));
    assertTypicalCue3(allCues.get(2));
  }

  @Test
  public void parseOverlappingTimecodes() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), OVERLAPPING_TIMECODES);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    String firstSubtitleText = "First subtitle - end overlaps second";
    String secondSubtitleText = "Second subtitle - beginning overlaps first";
    String thirdSubtitleText = "Third subtitle - out of order";
    String fourthSubtitleText = "Fourth subtitle - same timings as fifth";
    String fifthSubtitleText = "Fifth subtitle - same timings as fourth";
    String sixthSubtitleText = "Sixth subtitle - fully encompasses seventh";
    String seventhSubtitleText = "Seventh subtitle - nested fully inside sixth";

    assertThat(allCues).hasSize(8);
    assertThat(allCues.get(0).startTimeUs).isEqualTo(1_000_000);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Iterables.transform(allCues.get(0).cues, cue -> cue.text.toString()))
        .containsExactly(firstSubtitleText);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_000_000);
    assertThat(allCues.get(1).durationUs).isEqualTo(2_230_000);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(4_230_000);
    assertThat(Iterables.transform(allCues.get(1).cues, cue -> cue.text.toString()))
        .containsExactly(firstSubtitleText, secondSubtitleText);

    assertThat(allCues.get(2).startTimeUs).isEqualTo(4_230_000);
    assertThat(allCues.get(2).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(5_230_000);
    assertThat(Iterables.transform(allCues.get(2).cues, cue -> cue.text.toString()))
        .containsExactly(secondSubtitleText);

    assertThat(allCues.get(3).startTimeUs).isEqualTo(6_000_000);
    assertThat(allCues.get(3).durationUs).isEqualTo(2_440_000);
    assertThat(allCues.get(3).endTimeUs).isEqualTo(8_440_000);
    assertThat(Iterables.transform(allCues.get(3).cues, cue -> cue.text.toString()))
        .containsExactly(thirdSubtitleText);

    assertThat(allCues.get(4).startTimeUs).isEqualTo(8_440_000);
    assertThat(allCues.get(4).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(4).endTimeUs).isEqualTo(9_440_000);
    assertThat(Iterables.transform(allCues.get(4).cues, cue -> cue.text.toString()))
        .containsExactly(fourthSubtitleText, fifthSubtitleText);

    assertThat(allCues.get(5).startTimeUs).isEqualTo(10_720_000);
    assertThat(allCues.get(5).durationUs).isEqualTo(2_500_000);
    assertThat(allCues.get(5).endTimeUs).isEqualTo(13_220_000);
    assertThat(Iterables.transform(allCues.get(5).cues, cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText);

    assertThat(allCues.get(6).startTimeUs).isEqualTo(13_220_000);
    assertThat(allCues.get(6).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(6).endTimeUs).isEqualTo(14_220_000);
    assertThat(Iterables.transform(allCues.get(6).cues, cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText, seventhSubtitleText);

    assertThat(allCues.get(7).startTimeUs).isEqualTo(14_220_000);
    assertThat(allCues.get(7).durationUs).isEqualTo(1_430_000);
    assertThat(allCues.get(7).endTimeUs).isEqualTo(15_650_000);
    assertThat(Iterables.transform(allCues.get(7).cues, cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText);
  }

  @Test
  public void parsePositions() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), POSITIONS);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    // Check \pos() sets position & line
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.position).isEqualTo(0.5f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.25f);

    // Check the \pos() doesn't need to be at the start of the line.
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.position).isEqualTo(0.25f);
    assertThat(secondCue.line).isEqualTo(0.25f);

    // Check only the last \pos() value is used.
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.position).isEqualTo(0.25f);

    // Check \move() is treated as \pos()
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.position).isEqualTo(0.5f);
    assertThat(fourthCue.line).isEqualTo(0.25f);

    // Check alignment override in a separate brace (to bottom-center) affects textAlignment and
    // both line & position anchors.
    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.position).isEqualTo(0.5f);
    assertThat(fifthCue.line).isEqualTo(0.5f);
    assertWithMessage("Cue.positionAnchor")
        .that(fifthCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertThat(fifthCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertWithMessage("Cue.textAlignment")
        .that(fifthCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_CENTER);

    // Check alignment override in the same brace (to top-right) affects textAlignment and both line
    // & position anchors.
    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.position).isEqualTo(0.5f);
    assertThat(sixthCue.line).isEqualTo(0.5f);
    assertWithMessage("Cue.positionAnchor")
        .that(sixthCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(sixthCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_START);
    assertWithMessage("Cue.textAlignment")
        .that(sixthCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE);
  }

  @Test
  public void parseInvalidPositions() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INVALID_POSITIONS);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    // Negative parameter to \pos() - fall back to the positions implied by middle-left alignment.
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.position).isEqualTo(0.05f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.5f);

    // Negative parameter to \move() - fall back to the positions implied by middle-left alignment.
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.position).isEqualTo(0.05f);
    assertThat(secondCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(secondCue.line).isEqualTo(0.5f);

    // Check invalid alignment override (11) is skipped and style-provided one is used (4).
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertWithMessage("Cue.positionAnchor")
        .that(thirdCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);
    assertThat(thirdCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertWithMessage("Cue.textAlignment")
        .that(thirdCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    // No braces - fall back to the positions implied by middle-left alignment
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.position).isEqualTo(0.05f);
    assertThat(fourthCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(fourthCue.line).isEqualTo(0.5f);
  }

  @Test
  public void parsePositionsWithMissingPlayResY() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), POSITIONS_WITHOUT_PLAYRES);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    // The dialogue line has a valid \pos() override, but it's ignored because PlayResY isn't
    // set (so we don't know the denominator).
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(Cue.DIMEN_UNSET);
  }

  @Test
  public void parseInvalidTimecodes() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INVALID_TIMECODES);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);

    assertThat(allCues).hasSize(1);
    assertTypicalCue3(Iterables.getOnlyElement(allCues));
  }

  @Test
  public void parsePrimaryColor() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_PRIMARY_COLOR);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(7);
    // &H000000FF (AABBGGRR) -> #FFFF0000 (AARRGGBB)
    Spanned firstCueText = (Spanned) Iterables.getOnlyElement(allCues.get(0).cues).text;
    SpannedSubject.assertThat(firstCueText)
        .hasForegroundColorSpanBetween(0, firstCueText.length())
        .withColor(Color.RED);
    // &H0000FFFF (AABBGGRR) -> #FFFFFF00 (AARRGGBB)
    Spanned secondCueText = (Spanned) Iterables.getOnlyElement(allCues.get(1).cues).text;
    SpannedSubject.assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(Color.YELLOW);
    // &HFF00 (GGRR) -> #FF00FF00 (AARRGGBB)
    Spanned thirdCueText = (Spanned) Iterables.getOnlyElement(allCues.get(2).cues).text;
    SpannedSubject.assertThat(thirdCueText)
        .hasForegroundColorSpanBetween(0, thirdCueText.length())
        .withColor(Color.GREEN);
    // &HA00000FF (AABBGGRR) -> #5FFF0000 (AARRGGBB)
    Spanned fourthCueText = (Spanned) Iterables.getOnlyElement(allCues.get(3).cues).text;
    SpannedSubject.assertThat(fourthCueText)
        .hasForegroundColorSpanBetween(0, fourthCueText.length())
        .withColor(0x5FFF0000);
    // 16711680 (AABBGGRR) -> &H00FF0000 (AABBGGRR) -> #FF0000FF (AARRGGBB)
    Spanned fifthCueText = (Spanned) Iterables.getOnlyElement(allCues.get(4).cues).text;
    SpannedSubject.assertThat(fifthCueText)
        .hasForegroundColorSpanBetween(0, fifthCueText.length())
        .withColor(0xFF0000FF);
    // 2164195328 (AABBGGRR) -> &H80FF0000 (AABBGGRR) -> #7F0000FF (AARRGGBB)
    Spanned sixthCueText = (Spanned) Iterables.getOnlyElement(allCues.get(5).cues).text;
    SpannedSubject.assertThat(sixthCueText)
        .hasForegroundColorSpanBetween(0, sixthCueText.length())
        .withColor(0x7F0000FF);
    Spanned seventhCueText = (Spanned) Iterables.getOnlyElement(allCues.get(6).cues).text;
    SpannedSubject.assertThat(seventhCueText)
        .hasNoForegroundColorSpanBetween(0, seventhCueText.length());
  }

  @Test
  public void parseOutlineColor() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_OUTLINE_COLOR);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(2);
    Spanned firstCueText = (Spanned) Iterables.getOnlyElement(allCues.get(0).cues).text;
    SpannedSubject.assertThat(firstCueText)
        .hasBackgroundColorSpanBetween(0, firstCueText.length())
        .withColor(Color.BLUE);

    // OutlineColour should be treated as background only when BorderStyle=3
    Spanned secondCueText = (Spanned) Iterables.getOnlyElement(allCues.get(1).cues).text;
    SpannedSubject.assertThat(secondCueText)
        .hasNoBackgroundColorSpanBetween(0, secondCueText.length());
  }

  @Test
  public void parseFontSize() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_FONT_SIZE);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(2);

    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.textSize).isWithin(1.0e-8f).of(30f / 720f);
    assertThat(firstCue.textSizeType).isEqualTo(Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.textSize).isWithin(1.0e-8f).of(72.2f / 720f);
    assertThat(secondCue.textSizeType).isEqualTo(Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING);
  }

  @Test
  public void parseBoldItalic() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_BOLD_ITALIC);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(3);

    Spanned firstCueText = (Spanned) Iterables.getOnlyElement(allCues.get(0).cues).text;
    SpannedSubject.assertThat(firstCueText).hasBoldSpanBetween(0, firstCueText.length());
    Spanned secondCueText = (Spanned) Iterables.getOnlyElement(allCues.get(1).cues).text;
    SpannedSubject.assertThat(secondCueText).hasItalicSpanBetween(0, secondCueText.length());
    Spanned thirdCueText = (Spanned) Iterables.getOnlyElement(allCues.get(2).cues).text;
    SpannedSubject.assertThat(thirdCueText).hasBoldItalicSpanBetween(0, thirdCueText.length());
  }

  @Test
  public void parseUnderline() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_UNDERLINE);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(2);

    Spanned firstCueText = (Spanned) Iterables.getOnlyElement(allCues.get(0).cues).text;
    SpannedSubject.assertThat(firstCueText).hasUnderlineSpanBetween(0, firstCueText.length());
    Spanned secondCueText = (Spanned) Iterables.getOnlyElement(allCues.get(1).cues).text;
    SpannedSubject.assertThat(secondCueText).hasNoUnderlineSpanBetween(0, secondCueText.length());
  }

  @Test
  public void parseStrikeout() throws IOException {
    SsaParser parser = new SsaParser();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_STRIKEOUT);
    ImmutableList<CuesWithTiming> allCues = parseAllCues(parser, bytes);
    assertThat(allCues).hasSize(2);

    Spanned firstCueText = (Spanned) Iterables.getOnlyElement(allCues.get(0).cues).text;
    SpannedSubject.assertThat(firstCueText).hasStrikethroughSpanBetween(0, firstCueText.length());
    Spanned secondCueText = (Spanned) Iterables.getOnlyElement(allCues.get(1).cues).text;
    SpannedSubject.assertThat(secondCueText)
        .hasNoStrikethroughSpanBetween(0, secondCueText.length());
  }

  private static ImmutableList<CuesWithTiming> parseAllCues(SubtitleParser parser, byte[] data) {
    ImmutableList.Builder<CuesWithTiming> cues = ImmutableList.builder();
    parser.parse(data, OutputOptions.allCues(), cues::add);
    return cues.build();
  }

  private static void assertTypicalCue1(CuesWithTiming cuesWithTiming) {
    assertThat(cuesWithTiming.startTimeUs).isEqualTo(0);
    assertThat(cuesWithTiming.durationUs).isEqualTo(1230000);
    assertThat(cuesWithTiming.endTimeUs).isEqualTo(1230000);
    assertThat(cuesWithTiming.cues.get(0).text.toString()).isEqualTo("This is the first subtitle.");
    assertThat(Objects.requireNonNull(cuesWithTiming.cues.get(0).textAlignment))
        .isEqualTo(Layout.Alignment.ALIGN_CENTER);
  }

  private static void assertTypicalCue2(CuesWithTiming cuesWithTiming) {
    assertThat(cuesWithTiming.startTimeUs).isEqualTo(2340000);
    assertThat(cuesWithTiming.durationUs).isEqualTo(3450000 - 2340000);
    assertThat(cuesWithTiming.endTimeUs).isEqualTo(3450000);
    assertThat(cuesWithTiming.cues.get(0).text.toString())
        .isEqualTo("This is the second subtitle \nwith a newline \nand another.");
  }

  private static void assertTypicalCue3(CuesWithTiming cuesWithTiming) {
    assertThat(cuesWithTiming.startTimeUs).isEqualTo(4560000);
    assertThat(cuesWithTiming.durationUs).isEqualTo(8900000 - 4560000);
    assertThat(cuesWithTiming.endTimeUs).isEqualTo(8900000);
    assertThat(cuesWithTiming.cues.get(0).text.toString())
        .isEqualTo("This is the third subtitle, with a comma.");
  }
}

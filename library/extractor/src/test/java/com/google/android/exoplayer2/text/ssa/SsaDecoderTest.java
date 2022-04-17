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
package com.google.android.exoplayer2.text.ssa;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.graphics.Color;
import android.text.Layout;
import android.text.Spanned;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.testutil.truth.SpannedSubject;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SsaDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class SsaDecoderTest {

  private static final String EMPTY = "media/ssa/empty";
  private static final String EMPTY_STYLE_LINE = "media/ssa/empty_style_line";
  private static final String TYPICAL = "media/ssa/typical";
  private static final String TYPICAL_HEADER_ONLY = "media/ssa/typical_header";
  private static final String TYPICAL_DIALOGUE_ONLY = "media/ssa/typical_dialogue";
  private static final String TYPICAL_FORMAT_ONLY = "media/ssa/typical_format";
  private static final String OVERLAPPING_TIMECODES = "media/ssa/overlapping_timecodes";
  private static final String POSITIONS = "media/ssa/positioning";
  private static final String INVALID_TIMECODES = "media/ssa/invalid_timecodes";
  private static final String INVALID_POSITIONS = "media/ssa/invalid_positioning";
  private static final String POSITIONS_WITHOUT_PLAYRES = "media/ssa/positioning_without_playres";
  private static final String STYLE_COLORS = "media/ssa/style_colors";
  private static final String STYLE_FONT_SIZE = "media/ssa/style_font_size";
  private static final String STYLE_BOLD_ITALIC = "media/ssa/style_bold_italic";
  private static final String STYLE_UNDERLINE = "media/ssa/style_underline";
  private static final String STYLE_STRIKEOUT = "media/ssa/style_strikeout";
  private static final String STYLE_MARGIN = "media/ssa/style_margin";

  @Test
  public void decodeEmpty() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(0);
  }

  @Test
  public void decodeEmptyStyleLine() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY_STYLE_LINE);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, /* reset= */ false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    Cue cue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
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
  public void decodeTypical() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    // Check position, line, anchors & alignment are set from Alignment Style (2 - bottom-center).
    Cue firstCue = subtitle.getCues(subtitle.getEventTime(0)).get(0);
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

    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeTypicalWithInitializationData() throws IOException {
    byte[] headerBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_HEADER_ONLY);
    byte[] formatBytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_FORMAT_ONLY);
    ArrayList<byte[]> initializationData = new ArrayList<>();
    initializationData.add(formatBytes);
    initializationData.add(headerBytes);
    SsaDecoder decoder = new SsaDecoder(initializationData);
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TYPICAL_DIALOGUE_ONLY);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);
    assertTypicalCue1(subtitle, 0);
    assertTypicalCue2(subtitle, 2);
    assertTypicalCue3(subtitle, 4);
  }

  @Test
  public void decodeOverlappingTimecodes() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), OVERLAPPING_TIMECODES);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(subtitle.getEventTime(1)).isEqualTo(2_000_000);
    assertThat(subtitle.getEventTime(2)).isEqualTo(4_230_000);
    assertThat(subtitle.getEventTime(3)).isEqualTo(5_230_000);
    assertThat(subtitle.getEventTime(4)).isEqualTo(6_000_000);
    assertThat(subtitle.getEventTime(5)).isEqualTo(8_440_000);
    assertThat(subtitle.getEventTime(6)).isEqualTo(9_440_000);
    assertThat(subtitle.getEventTime(7)).isEqualTo(10_720_000);
    assertThat(subtitle.getEventTime(8)).isEqualTo(13_220_000);
    assertThat(subtitle.getEventTime(9)).isEqualTo(14_220_000);
    assertThat(subtitle.getEventTime(10)).isEqualTo(15_650_000);

    String firstSubtitleText = "First subtitle - end overlaps second";
    String secondSubtitleText = "Second subtitle - beginning overlaps first";
    String thirdSubtitleText = "Third subtitle - out of order";
    String fourthSubtitleText = "Fourth subtitle - same timings as fifth";
    String fifthSubtitleText = "Fifth subtitle - same timings as fourth";
    String sixthSubtitleText = "Sixth subtitle - fully encompasses seventh";
    String seventhSubtitleText = "Seventh subtitle - nested fully inside sixth";
    assertThat(Iterables.transform(subtitle.getCues(1_000_010), cue -> cue.text.toString()))
        .containsExactly(firstSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(2_000_010), cue -> cue.text.toString()))
        .containsExactly(firstSubtitleText, secondSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(4_230_010), cue -> cue.text.toString()))
        .containsExactly(secondSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(5_230_010), cue -> cue.text.toString()))
        .isEmpty();
    assertThat(Iterables.transform(subtitle.getCues(6_000_010), cue -> cue.text.toString()))
        .containsExactly(thirdSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(8_440_010), cue -> cue.text.toString()))
        .containsExactly(fourthSubtitleText, fifthSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(9_440_010), cue -> cue.text.toString()))
        .isEmpty();
    assertThat(Iterables.transform(subtitle.getCues(10_720_010), cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(13_220_010), cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText, seventhSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(14_220_010), cue -> cue.text.toString()))
        .containsExactly(sixthSubtitleText);
    assertThat(Iterables.transform(subtitle.getCues(15_650_010), cue -> cue.text.toString()))
        .isEmpty();
  }

  @Test
  public void decodePositions() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), POSITIONS);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    // Check \pos() sets position & line
    Cue firstCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
    assertThat(firstCue.position).isEqualTo(0.5f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.25f);

    // Check the \pos() doesn't need to be at the start of the line.
    Cue secondCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2)));
    assertThat(secondCue.position).isEqualTo(0.25f);
    assertThat(secondCue.line).isEqualTo(0.25f);

    // Check only the last \pos() value is used.
    Cue thirdCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(4)));
    assertThat(thirdCue.position).isEqualTo(0.25f);

    // Check \move() is treated as \pos()
    Cue fourthCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(6)));
    assertThat(fourthCue.position).isEqualTo(0.5f);
    assertThat(fourthCue.line).isEqualTo(0.25f);

    // Check alignment override in a separate brace (to bottom-center) affects textAlignment and
    // both line & position anchors.
    Cue fifthCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(8)));
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
    Cue sixthCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(10)));
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
  public void decodeInvalidPositions() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INVALID_POSITIONS);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    // Negative parameter to \pos() - fall back to the positions implied by middle-left alignment.
    Cue firstCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
    assertThat(firstCue.position).isEqualTo(0.05f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.5f);

    // Negative parameter to \move() - fall back to the positions implied by middle-left alignment.
    Cue secondCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2)));
    assertThat(secondCue.position).isEqualTo(0.05f);
    assertThat(secondCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(secondCue.line).isEqualTo(0.5f);

    // Check invalid alignment override (11) is skipped and style-provided one is used (4).
    Cue thirdCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(4)));
    assertWithMessage("Cue.positionAnchor")
        .that(thirdCue.positionAnchor)
        .isEqualTo(Cue.ANCHOR_TYPE_START);
    assertThat(thirdCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);
    assertWithMessage("Cue.textAlignment")
        .that(thirdCue.textAlignment)
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL);

    // No braces - fall back to the positions implied by middle-left alignment
    Cue fourthCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(6)));
    assertThat(fourthCue.position).isEqualTo(0.05f);
    assertThat(fourthCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(fourthCue.line).isEqualTo(0.5f);
  }

  @Test
  public void decodePositionsWithMissingPlayResY() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(
            ApplicationProvider.getApplicationContext(), POSITIONS_WITHOUT_PLAYRES);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    // The dialogue line has a valid \pos() override, but it's ignored because PlayResY isn't
    // set (so we don't know the denominator).
    Cue firstCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
    assertThat(firstCue.position).isEqualTo(Cue.DIMEN_UNSET);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(Cue.DIMEN_UNSET);
  }

  @Test
  public void decodeInvalidTimecodes() throws IOException {
    // Parsing should succeed, parsing the third cue only.
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), INVALID_TIMECODES);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    assertThat(subtitle.getEventTimeCount()).isEqualTo(2);
    assertTypicalCue3(subtitle, 0);
  }

  @Test
  public void decodeColors() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_COLORS);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(14);
    // &H000000FF (AABBGGRR) -> #FFFF0000 (AARRGGBB)
    Spanned firstCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0))).text;
    SpannedSubject.assertThat(firstCueText)
        .hasForegroundColorSpanBetween(0, firstCueText.length())
        .withColor(Color.RED);
    // &H0000FFFF (AABBGGRR) -> #FFFFFF00 (AARRGGBB)
    Spanned secondCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2))).text;
    SpannedSubject.assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(Color.YELLOW);
    // &HFF00 (GGRR) -> #FF00FF00 (AARRGGBB)
    Spanned thirdCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(4))).text;
    SpannedSubject.assertThat(thirdCueText)
        .hasForegroundColorSpanBetween(0, thirdCueText.length())
        .withColor(Color.GREEN);
    // &HA00000FF (AABBGGRR) -> #5FFF0000 (AARRGGBB)
    Spanned fourthCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(6))).text;
    SpannedSubject.assertThat(fourthCueText)
        .hasForegroundColorSpanBetween(0, fourthCueText.length())
        .withColor(0x5FFF0000);
    // 16711680 (AABBGGRR) -> &H00FF0000 (AABBGGRR) -> #FF0000FF (AARRGGBB)
    Spanned fifthCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(8))).text;
    SpannedSubject.assertThat(fifthCueText)
        .hasForegroundColorSpanBetween(0, fifthCueText.length())
        .withColor(0xFF0000FF);
    // 2164195328 (AABBGGRR) -> &H80FF0000 (AABBGGRR) -> #7F0000FF (AARRGGBB)
    Spanned sixthCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(10))).text;
    SpannedSubject.assertThat(sixthCueText)
        .hasForegroundColorSpanBetween(0, sixthCueText.length())
        .withColor(0x7F0000FF);
    Spanned seventhCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(12))).text;
    SpannedSubject.assertThat(seventhCueText)
        .hasNoForegroundColorSpanBetween(0, seventhCueText.length());
  }

  @Test
  public void decodeFontSize() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_FONT_SIZE);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Cue firstCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
    assertThat(firstCue.textSize).isWithin(1.0e-8f).of(30f / 720f);
    assertThat(firstCue.textSizeType).isEqualTo(Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING);
    Cue secondCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2)));
    assertThat(secondCue.textSize).isWithin(1.0e-8f).of(72.2f / 720f);
    assertThat(secondCue.textSizeType).isEqualTo(Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING);
  }

  @Test
  public void decodeBoldItalic() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_BOLD_ITALIC);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(6);

    Spanned firstCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0))).text;
    SpannedSubject.assertThat(firstCueText).hasBoldSpanBetween(0, firstCueText.length());
    Spanned secondCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2))).text;
    SpannedSubject.assertThat(secondCueText).hasItalicSpanBetween(0, secondCueText.length());
    Spanned thirdCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(4))).text;
    SpannedSubject.assertThat(thirdCueText).hasBoldItalicSpanBetween(0, thirdCueText.length());
  }

  @Test
  public void decodeUnderline() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_UNDERLINE);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned firstCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0))).text;
    SpannedSubject.assertThat(firstCueText).hasUnderlineSpanBetween(0, firstCueText.length());
    Spanned secondCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2))).text;
    SpannedSubject.assertThat(secondCueText).hasNoUnderlineSpanBetween(0, secondCueText.length());
  }

  @Test
  public void decodeStrikeout() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_STRIKEOUT);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);
    assertThat(subtitle.getEventTimeCount()).isEqualTo(4);

    Spanned firstCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0))).text;
    SpannedSubject.assertThat(firstCueText).hasStrikethroughSpanBetween(0, firstCueText.length());
    Spanned secondCueText =
        (Spanned) Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2))).text;
    SpannedSubject.assertThat(secondCueText)
        .hasNoStrikethroughSpanBetween(0, secondCueText.length());
  }

  @Test
  public void decodeMargins() throws IOException {
    SsaDecoder decoder = new SsaDecoder();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), STYLE_MARGIN);
    Subtitle subtitle = decoder.decode(bytes, bytes.length, false);

    // PlayResX=1280px, PlayResY=720px

    // Alignment 1, position anchor = start, position = (0.05f, 0.95f)
    // margin_left = 128px = 0.1f, margin_right 256px = 0.2f
    Cue firstCue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(0)));
    assertThat(firstCue.position).isEqualTo(0.15f); // = 0.05f + margin_left
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(firstCue.line).isEqualTo(0.95f);
    assertThat(firstCue.size).isEqualTo(0.7f); // = 1 - margin_right - margin_left

    // Alignment 6, position anchor = end, position = (0.95f, 0.5f)
    // margin_left = 128px = 0.1f, margin_right = 256px = 0.2f
    Cue secondClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(2)));
    assertThat(secondClue.position).isEqualTo(0.75f); // = 1 - margin_right
    assertThat(secondClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(secondClue.line).isEqualTo(0.5f);
    assertThat(secondClue.size).isEqualTo(0.7f); // = 1 - margin_right - margin_left

    // Alignment 2, position anchor = middle, position = (0.5f, 0.95f)
    // margin_left = 128px = 0.1f, margin_right = 256px = 0.2f
    Cue thirdClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(4)));
    assertThat(thirdClue.position).isEqualTo(0.45f); // 0.5f + (margin_left - margin_right)/2
    assertThat(thirdClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(thirdClue.line).isEqualTo(0.95f);
    assertThat(thirdClue.size).isEqualTo(0.7f); // = 1 - margin_right - margin_left

    // Alignment 5, position anchor = middle, position = (0.5f, 0.5f)
    // margin_vertical = 144px = 0.2f but needs to be ignored when alignment is middle [4,5,6]
    Cue fourthClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(6)));
    assertThat(fourthClue.position).isEqualTo(0.5f);
    assertThat(fourthClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(fourthClue.line).isEqualTo(0.5f);

    // Alignment 2, position anchor = middle, position = (0.5f, 0.95f)
    // margin_vertical = 144px = 0.2f, to be applied from bottom when alignment is bottom [1,2,3]
    Cue fifthClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(8)));
    assertThat(fifthClue.position).isEqualTo(0.5f);
    assertThat(fifthClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(fifthClue.line).isEqualTo(0.75f); // = 0.95f - margin_vertical

    // Alignment 9, position anchor = end, position = (0.95f, 0.05f)
    // margin_vertical = 144px = 0.2f, to be applied from top when alignment is top [7,8,9]
    Cue sixthClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(10)));
    assertThat(sixthClue.position).isEqualTo(0.95f); // alignment 9
    assertThat(sixthClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(sixthClue.line).isEqualTo(0.25f); // = 0.05f + margin_vertical

    // Alignment 2, position anchor = middle, position = (0.5f, 0.95f)
    // margin_left = 128px = 0.1f, margin_vertical = 144px = 0.2f, margin_right = 0f (from Dialogue)
    Cue seventhClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(12)));
    assertThat(seventhClue.position).isEqualTo(0.55f); // 0.5f + (margin_left - margin_right)/2
    assertThat(seventhClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(seventhClue.line).isEqualTo(0.75f); // 0.95 - margin_vertical
    assertThat(seventhClue.size).isEqualTo(0.9f); // 1 - margin_right - margin_left

    // Position override {\pos(640,180)} -> ignore margins
    Cue eighthClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(14)));
    assertThat(eighthClue.position).isEqualTo(0.5f);
    assertThat(eighthClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(eighthClue.line).isEqualTo(0.25f);

    // Alignment override {\an5}, position = (0.5f, 0.5f) -> ignore margins
    Cue ninthClue = Iterables.getOnlyElement(subtitle.getCues(subtitle.getEventTime(16)));
    assertThat(ninthClue.position).isEqualTo(0.5f);
    assertThat(ninthClue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(ninthClue.line).isEqualTo(0.5f);
  }

  private static void assertTypicalCue1(Subtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(0);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the first subtitle.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(1230000);
  }

  private static void assertTypicalCue2(Subtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(2340000);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the second subtitle \nwith a newline \nand another.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(3450000);
  }

  private static void assertTypicalCue3(Subtitle subtitle, int eventIndex) {
    assertThat(subtitle.getEventTime(eventIndex)).isEqualTo(4560000);
    assertThat(subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString())
        .isEqualTo("This is the third subtitle, with a comma.");
    assertThat(subtitle.getEventTime(eventIndex + 1)).isEqualTo(8900000);
  }
}

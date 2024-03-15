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
package androidx.media3.extractor.text.webvtt;

import static androidx.media3.common.Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;
import static androidx.media3.test.utils.truth.SpannedSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.text.Layout.Alignment;
import android.text.Spanned;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.TextAnnotation;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ColorParser;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Expect;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link WebvttParser}. */
@RunWith(AndroidJUnit4.class)
public class WebvttParserTest {

  private static final String TYPICAL_FILE = "media/webvtt/typical";
  private static final String TYPICAL_WITH_BAD_TIMESTAMPS =
      "media/webvtt/typical_with_bad_timestamps";
  private static final String TYPICAL_WITH_IDS_FILE = "media/webvtt/typical_with_identifiers";
  private static final String TYPICAL_WITH_COMMENTS_FILE = "media/webvtt/typical_with_comments";
  private static final String WITH_POSITIONING_FILE = "media/webvtt/with_positioning";
  private static final String WITH_CONSECUTIVE_TIMESTAMPS_FILE =
      "media/webvtt/with_consecutive_cues";
  private static final String WITH_OVERLAPPING_TIMESTAMPS_FILE =
      "media/webvtt/with_overlapping_timestamps";
  private static final String WITH_VERTICAL_FILE = "media/webvtt/with_vertical";
  private static final String WITH_RUBIES_FILE = "media/webvtt/with_rubies";
  private static final String WITH_BAD_CUE_HEADER_FILE = "media/webvtt/with_bad_cue_header";
  private static final String WITH_TAGS_FILE = "media/webvtt/with_tags";
  private static final String WITH_CSS_STYLES = "media/webvtt/with_css_styles";
  private static final String WITH_FONT_SIZE = "media/webvtt/with_font_size";
  private static final String WITH_CSS_COMPLEX_SELECTORS =
      "media/webvtt/with_css_complex_selectors";
  private static final String WITH_CSS_TEXT_COMBINE_UPRIGHT =
      "media/webvtt/with_css_text_combine_upright";
  private static final String WITH_BOM = "media/webvtt/with_bom";
  private static final String EMPTY_FILE = "media/webvtt/empty";

  @Rule public final Expect expect = Expect.create();

  @Test
  public void cueReplacementBehaviorIsMerge() throws IOException {
    WebvttParser parser = new WebvttParser();
    assertThat(parser.getCueReplacementBehavior()).isEqualTo(CUE_REPLACEMENT_BEHAVIOR_MERGE);
  }

  @Test
  public void parseEmpty() throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), EMPTY_FILE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.parse(
                bytes, SubtitleParser.OutputOptions.allCues(), unusedCuesWithTiming -> {}));
  }

  @Test
  public void parseTypical() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(TYPICAL_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseTypical_withStartTime() throws Exception {
    ImmutableList<CuesWithTiming> allCues =
        getCuesForTestAsset(TYPICAL_FILE, SubtitleParser.OutputOptions.onlyCuesAfter(2_000_000));

    assertThat(allCues).hasSize(1);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(0).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseTypical_withStartTimeAndRemainingCues() throws Exception {
    ImmutableList<CuesWithTiming> allCues =
        getCuesForTestAsset(
            TYPICAL_FILE, SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(2_000_000));

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(0).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(1).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");
  }

  @Test
  public void parseWithBom() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_BOM);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseTypicalWithBadTimestamps() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(TYPICAL_WITH_BAD_TIMESTAMPS);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseTypicalWithIds() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(TYPICAL_WITH_IDS_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseTypicalWithComments() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(TYPICAL_WITH_COMMENTS_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseWithTags() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_TAGS_FILE);

    assertThat(allCues).hasSize(4);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");

    assertThat(allCues.get(2).startTimeUs).isEqualTo(4_000_000L);
    assertThat(allCues.get(2).durationUs).isEqualTo(5_000_000L - 4_000_000L);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(5_000_000L);
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("This is the third subtitle.");

    assertThat(allCues.get(3).startTimeUs).isEqualTo(6_000_000L);
    assertThat(allCues.get(3).durationUs).isEqualTo(7_000_000L - 6_000_000L);
    assertThat(allCues.get(3).endTimeUs).isEqualTo(7_000_000L);
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("This is the <fourth> &subtitle.");
  }

  @Test
  public void parseWithPositioning() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_POSITIONING_FILE);

    assertThat(allCues).hasSize(8);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");
    assertThat(firstCue.position).isEqualTo(0.6f);
    assertThat(firstCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(firstCue.textAlignment).isEqualTo(Alignment.ALIGN_NORMAL);
    assertThat(firstCue.size).isEqualTo(0.35f);

    // Unspecified values should use WebVTT defaults
    assertThat(firstCue.line).isEqualTo(-1f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);
    assertThat(firstCue.verticalType).isEqualTo(Cue.TYPE_UNSET);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
    // Position is invalid so defaults to 0.5
    assertThat(secondCue.position).isEqualTo(0.5f);
    assertThat(secondCue.textAlignment).isEqualTo(Alignment.ALIGN_OPPOSITE);

    assertThat(allCues.get(2).startTimeUs).isEqualTo(4_000_000L);
    assertThat(allCues.get(2).durationUs).isEqualTo(5_000_000L - 4_000_000L);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(5_000_000L);
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("This is the third subtitle.");
    assertThat(thirdCue.line).isEqualTo(0.45f);
    assertThat(thirdCue.lineType).isEqualTo(Cue.LINE_TYPE_FRACTION);
    assertThat(thirdCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
    assertThat(thirdCue.textAlignment).isEqualTo(Alignment.ALIGN_CENTER);
    // Derived from `align:middle`:
    assertThat(thirdCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(allCues.get(3).startTimeUs).isEqualTo(6_000_000L);
    assertThat(allCues.get(3).durationUs).isEqualTo(7_000_000L - 6_000_000L);
    assertThat(allCues.get(3).endTimeUs).isEqualTo(7_000_000L);
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("This is the fourth subtitle.");
    assertThat(fourthCue.line).isEqualTo(-10f);
    assertThat(fourthCue.lineAnchor).isEqualTo(Cue.ANCHOR_TYPE_START);
    assertThat(fourthCue.textAlignment).isEqualTo(Alignment.ALIGN_CENTER);
    // Derived from `align:middle`:
    assertThat(fourthCue.position).isEqualTo(0.5f);
    assertThat(fourthCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(allCues.get(4).startTimeUs).isEqualTo(8_000_000L);
    assertThat(allCues.get(4).durationUs).isEqualTo(9_000_000L - 8_000_000L);
    assertThat(allCues.get(4).endTimeUs).isEqualTo(9_000_000L);
    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.text.toString()).isEqualTo("This is the fifth subtitle.");
    assertThat(fifthCue.textAlignment).isEqualTo(Alignment.ALIGN_OPPOSITE);
    // Derived from `align:right`:
    assertThat(fifthCue.position).isEqualTo(1.0f);
    assertThat(fifthCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);

    assertThat(allCues.get(5).startTimeUs).isEqualTo(10_000_000L);
    assertThat(allCues.get(5).durationUs).isEqualTo(11_000_000L - 10_000_000L);
    assertThat(allCues.get(5).endTimeUs).isEqualTo(11_000_000L);
    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.text.toString()).isEqualTo("This is the sixth subtitle.");
    assertThat(sixthCue.textAlignment).isEqualTo(Alignment.ALIGN_CENTER);
    // Derived from `align:center`:
    assertThat(sixthCue.position).isEqualTo(0.5f);
    assertThat(sixthCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_MIDDLE);

    assertThat(allCues.get(6).startTimeUs).isEqualTo(12_000_000L);
    assertThat(allCues.get(6).durationUs).isEqualTo(13_000_000L - 12_000_000L);
    assertThat(allCues.get(6).endTimeUs).isEqualTo(13_000_000L);
    Cue seventhCue = Iterables.getOnlyElement(allCues.get(6).cues);
    assertThat(seventhCue.text.toString()).isEqualTo("This is the seventh subtitle.");
    assertThat(seventhCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_START);

    assertThat(allCues.get(7).startTimeUs).isEqualTo(14_000_000L);
    assertThat(allCues.get(7).durationUs).isEqualTo(15_000_000L - 14_000_000L);
    assertThat(allCues.get(7).endTimeUs).isEqualTo(15_000_000L);
    Cue eighthCue = Iterables.getOnlyElement(allCues.get(7).cues);
    assertThat(eighthCue.text.toString()).isEqualTo("This is the eighth subtitle.");
    assertThat(eighthCue.positionAnchor).isEqualTo(Cue.ANCHOR_TYPE_END);
  }

  // https://github.com/androidx/media/issues/1177
  @Test
  public void parseWithConsecutiveTimestamps() throws Exception {
    ImmutableList<CuesWithTiming> allCues = getCuesForTestAsset(WITH_CONSECUTIVE_TIMESTAMPS_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 1_234_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the second subtitle.");
  }

  @Test
  public void parseWithOverlappingTimestamps() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_OVERLAPPING_TIMESTAMPS_FILE);

    assertThat(allCues).hasSize(6);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_000_000);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("Displayed at the bottom for 3 seconds.");
    assertThat(firstCue.line).isEqualTo(-1f);
    assertThat(firstCue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(1_000_000);
    assertThat(allCues.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(2_000_000);
    ImmutableList<Cue> firstAndSecondCue = allCues.get(1).cues;
    assertThat(firstAndSecondCue).hasSize(2);
    assertThat(firstAndSecondCue.get(0).text.toString())
        .isEqualTo("Displayed at the bottom for 3 seconds.");
    assertThat(firstAndSecondCue.get(0).line).isEqualTo(-1f);
    assertThat(firstAndSecondCue.get(0).lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);
    assertThat(firstAndSecondCue.get(1).text.toString())
        .isEqualTo("Appears directly above for 1 second.");
    assertThat(firstAndSecondCue.get(1).line).isEqualTo(-2f);
    assertThat(firstAndSecondCue.get(1).lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);

    assertThat(allCues.get(2).startTimeUs).isEqualTo(2_000_000);
    assertThat(allCues.get(2).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(3_000_000);
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("Displayed at the bottom for 3 seconds.");
    assertThat(thirdCue.line).isEqualTo(-1f);
    assertThat(thirdCue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);

    assertThat(allCues.get(3).startTimeUs).isEqualTo(4_000_000);
    assertThat(allCues.get(3).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(3).endTimeUs).isEqualTo(5_000_000);
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("Displayed at the bottom for 2 seconds.");
    assertThat(fourthCue.line).isEqualTo(-1f);
    assertThat(fourthCue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);

    assertThat(allCues.get(4).startTimeUs).isEqualTo(5_000_000);
    assertThat(allCues.get(4).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(4).endTimeUs).isEqualTo(6_000_000);
    ImmutableList<Cue> fourthAndFifth = allCues.get(4).cues;
    assertThat(fourthAndFifth).hasSize(2);
    assertThat(fourthAndFifth.get(0).text.toString())
        .isEqualTo("Displayed at the bottom for 2 seconds.");
    assertThat(fourthAndFifth.get(0).line).isEqualTo(-1f);
    assertThat(fourthAndFifth.get(0).lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);
    assertThat(fourthAndFifth.get(1).text.toString())
        .isEqualTo("Appears directly above the previous cue, then replaces it after 1 second.");
    assertThat(fourthAndFifth.get(1).line).isEqualTo(-2f);
    assertThat(fourthAndFifth.get(1).lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);

    assertThat(allCues.get(5).startTimeUs).isEqualTo(6_000_000);
    assertThat(allCues.get(5).durationUs).isEqualTo(1_000_000);
    assertThat(allCues.get(5).endTimeUs).isEqualTo(7_000_000);
    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.text.toString())
        .isEqualTo("Appears directly above the previous cue, then replaces it after 1 second.");
    assertThat(sixthCue.line).isEqualTo(-1f);
    assertThat(sixthCue.lineType).isEqualTo(Cue.LINE_TYPE_NUMBER);
  }

  @Test
  public void parseWithVertical() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_VERTICAL_FILE);

    assertThat(allCues).hasSize(3);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("Vertical right-to-left (e.g. Japanese)");
    assertThat(firstCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_RL);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_345_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(3_456_000L - 2_345_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(3_456_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("Vertical left-to-right (e.g. Mongolian)");
    assertThat(secondCue.verticalType).isEqualTo(Cue.VERTICAL_TYPE_LR);

    assertThat(allCues.get(2).startTimeUs).isEqualTo(4_000_000L);
    assertThat(allCues.get(2).durationUs).isEqualTo(5_000_000L - 4_000_000L);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(5_000_000L);
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("No vertical setting (i.e. horizontal)");
    assertThat(thirdCue.verticalType).isEqualTo(Cue.TYPE_UNSET);
  }

  @Test
  public void parseWithRubies() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_RUBIES_FILE);

    assertThat(allCues).hasSize(4);

    // Check that an explicit `over` position is read from CSS.
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("Some text with over-ruby.");
    assertThat((Spanned) firstCue.text)
        .hasRubySpanBetween("Some ".length(), "Some text with over-ruby".length())
        .withTextAndPosition("over", TextAnnotation.POSITION_BEFORE);

    // Check that `under` is read from CSS and unspecified defaults to `over`.
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString())
        .isEqualTo("Some text with under-ruby and over-ruby (default).");
    assertThat((Spanned) secondCue.text)
        .hasRubySpanBetween("Some ".length(), "Some text with under-ruby".length())
        .withTextAndPosition("under", TextAnnotation.POSITION_AFTER);
    assertThat((Spanned) secondCue.text)
        .hasRubySpanBetween(
            "Some text with under-ruby and ".length(),
            "Some text with under-ruby and over-ruby (default)".length())
        .withTextAndPosition("over", TextAnnotation.POSITION_BEFORE);

    // Check many <rt> tags with different positions nested in a single <ruby> span.
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("base1base2base3.");
    assertThat((Spanned) thirdCue.text)
        .hasRubySpanBetween(/* start= */ 0, "base1".length())
        .withTextAndPosition("over1", TextAnnotation.POSITION_BEFORE);
    assertThat((Spanned) thirdCue.text)
        .hasRubySpanBetween("base1".length(), "base1base2".length())
        .withTextAndPosition("under2", TextAnnotation.POSITION_AFTER);
    assertThat((Spanned) thirdCue.text)
        .hasRubySpanBetween("base1base2".length(), "base1base2base3".length())
        .withTextAndPosition("under3", TextAnnotation.POSITION_AFTER);

    // Check a <ruby> span with no <rt> tags.
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("Some text with no ruby text.");
    assertThat((Spanned) fourthCue.text).hasNoSpans();
  }

  @Test
  public void parseWithBadCueHeader() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_BAD_CUE_HEADER_FILE);

    assertThat(allCues).hasSize(2);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(1_234_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(1_234_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("This is the first subtitle.");

    assertThat(allCues.get(1).startTimeUs).isEqualTo(4_000_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(5_000_000L - 4_000_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(5_000_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("This is the third subtitle.");
  }

  @Test
  public void parseWithCssFontSizeStyle() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_FONT_SIZE);

    assertThat(allCues).hasSize(6);

    assertThat(allCues.get(0).startTimeUs).isEqualTo(0L);
    assertThat(allCues.get(0).durationUs).isEqualTo(2_000_000L);
    assertThat(allCues.get(0).endTimeUs).isEqualTo(2_000_000L);
    Cue firstCue = Iterables.getOnlyElement(allCues.get(0).cues);
    assertThat(firstCue.text.toString()).isEqualTo("Sentence with font-size set to 4.4em.");
    assertThat((Spanned) firstCue.text)
        .hasRelativeSizeSpanBetween(0, "Sentence with font-size set to 4.4em.".length())
        .withSizeChange(4.4f);

    assertThat(allCues.get(1).startTimeUs).isEqualTo(2_100_000L);
    assertThat(allCues.get(1).durationUs).isEqualTo(2_400_000L - 2_100_000L);
    assertThat(allCues.get(1).endTimeUs).isEqualTo(2_400_000L);
    Cue secondCue = Iterables.getOnlyElement(allCues.get(1).cues);
    assertThat(secondCue.text.toString()).isEqualTo("Sentence with bad font-size unit.");
    assertThat((Spanned) secondCue.text).hasNoSpans();

    assertThat(allCues.get(2).startTimeUs).isEqualTo(2_500_000L);
    assertThat(allCues.get(2).durationUs).isEqualTo(4_000_000L - 2_500_000L);
    assertThat(allCues.get(2).endTimeUs).isEqualTo(4_000_000L);
    Cue thirdCue = Iterables.getOnlyElement(allCues.get(2).cues);
    assertThat(thirdCue.text.toString()).isEqualTo("Absolute font-size expressed in px unit!");
    assertThat((Spanned) thirdCue.text)
        .hasAbsoluteSizeSpanBetween(0, "Absolute font-size expressed in px unit!".length())
        .withAbsoluteSize(2);

    assertThat(allCues.get(3).startTimeUs).isEqualTo(4_500_000L);
    assertThat(allCues.get(3).durationUs).isEqualTo(6_000_000L - 4_500_000L);
    assertThat(allCues.get(3).endTimeUs).isEqualTo(6_000_000L);
    Cue fourthCue = Iterables.getOnlyElement(allCues.get(3).cues);
    assertThat(fourthCue.text.toString()).isEqualTo("Relative font-size expressed in % unit!");
    assertThat((Spanned) fourthCue.text)
        .hasRelativeSizeSpanBetween(0, "Relative font-size expressed in % unit!".length())
        .withSizeChange(0.035f);

    assertThat(allCues.get(4).startTimeUs).isEqualTo(6_100_000L);
    assertThat(allCues.get(4).durationUs).isEqualTo(6_400_000L - 6_100_000L);
    assertThat(allCues.get(4).endTimeUs).isEqualTo(6_400_000L);
    Cue fifthCue = Iterables.getOnlyElement(allCues.get(4).cues);
    assertThat(fifthCue.text.toString()).isEqualTo("Sentence with bad font-size value.");
    assertThat((Spanned) secondCue.text).hasNoSpans();

    assertThat(allCues.get(5).startTimeUs).isEqualTo(6_500_000L);
    assertThat(allCues.get(5).durationUs).isEqualTo(8_000_000L - 6_500_000L);
    assertThat(allCues.get(5).endTimeUs).isEqualTo(8_000_000L);
    Cue sixthCue = Iterables.getOnlyElement(allCues.get(5).cues);
    assertThat(sixthCue.text.toString())
        .isEqualTo("Upper and lower case letters in font-size unit.");
    assertThat((Spanned) sixthCue.text)
        .hasAbsoluteSizeSpanBetween(0, "Upper and lower case letters in font-size unit.".length())
        .withAbsoluteSize(2);
  }

  @Test
  public void webvttWithCssStyle() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_CSS_STYLES);

    Spanned firstCueText = getUniqueSpanTextAt(allCues.get(0));
    assertThat(firstCueText.toString()).isEqualTo("This is the first subtitle.");
    assertThat(firstCueText)
        .hasForegroundColorSpanBetween(0, firstCueText.length())
        .withColor(ColorParser.parseCssColor("papayawhip"));
    assertThat(firstCueText)
        .hasBackgroundColorSpanBetween(0, firstCueText.length())
        .withColor(ColorParser.parseCssColor("green"));

    Spanned secondCueText = getUniqueSpanTextAt(allCues.get(1));
    assertThat(secondCueText.toString()).isEqualTo("This is the second subtitle.");
    assertThat(secondCueText)
        .hasForegroundColorSpanBetween(0, secondCueText.length())
        .withColor(ColorParser.parseCssColor("peachpuff"));

    Spanned thirdCueText = getUniqueSpanTextAt(allCues.get(2));
    assertThat(thirdCueText.toString()).isEqualTo("This is a reference by element");
    assertThat(thirdCueText).hasUnderlineSpanBetween("This is a ".length(), thirdCueText.length());

    Spanned fourthCueText = getUniqueSpanTextAt(allCues.get(3));
    assertThat(fourthCueText.toString()).isEqualTo("You are an idiot\nYou don't have the guts");
    assertThat(fourthCueText)
        .hasBackgroundColorSpanBetween(0, "You are an idiot".length())
        .withColor(ColorParser.parseCssColor("lime"));
    assertThat(fourthCueText)
        .hasBoldSpanBetween("You are an idiot\n".length(), fourthCueText.length());
  }

  @Test
  public void withComplexCssSelectors() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_CSS_COMPLEX_SELECTORS);
    Spanned firstCueText = getUniqueSpanTextAt(allCues.get(0));
    assertThat(firstCueText).hasUnderlineSpanBetween(0, firstCueText.length());
    assertThat(firstCueText)
        .hasForegroundColorSpanBetween(
            "This should be underlined and ".length(), firstCueText.length())
        .withColor(ColorParser.parseCssColor("violet"));
    assertThat(firstCueText)
        .hasTypefaceSpanBetween("This should be underlined and ".length(), firstCueText.length())
        .withFamily("courier");

    Spanned secondCueText = getUniqueSpanTextAt(allCues.get(1));
    assertThat(secondCueText)
        .hasTypefaceSpanBetween("This ".length(), secondCueText.length())
        .withFamily("courier");
    assertThat(secondCueText)
        .hasNoForegroundColorSpanBetween("This ".length(), secondCueText.length());

    Spanned thirdCueText = getUniqueSpanTextAt(allCues.get(2));
    assertThat(thirdCueText).hasBoldSpanBetween("This ".length(), thirdCueText.length());
    assertThat(thirdCueText)
        .hasTypefaceSpanBetween("This ".length(), thirdCueText.length())
        .withFamily("courier");

    Spanned fourthCueText = getUniqueSpanTextAt(allCues.get(3));
    assertThat(fourthCueText)
        .hasNoStyleSpanBetween("This ".length(), "shouldn't be bold.".length());
    assertThat(fourthCueText)
        .hasBoldSpanBetween("This shouldn't be bold.\nThis ".length(), fourthCueText.length());

    Spanned fifthCueText = getUniqueSpanTextAt(allCues.get(4));
    assertThat(fifthCueText)
        .hasNoStyleSpanBetween("This is ".length(), "This is specific".length());
    assertThat(fifthCueText)
        .hasItalicSpanBetween("This is specific\n".length(), fifthCueText.length());
  }

  @Test
  public void webvttWithCssTextCombineUpright() throws Exception {
    List<CuesWithTiming> allCues = getCuesForTestAsset(WITH_CSS_TEXT_COMBINE_UPRIGHT);

    Spanned firstCueText = getUniqueSpanTextAt(allCues.get(0));
    assertThat(firstCueText)
        .hasHorizontalTextInVerticalContextSpanBetween("Combine ".length(), "Combine all".length());

    Spanned secondCueText = getUniqueSpanTextAt(allCues.get(1));
    assertThat(secondCueText)
        .hasHorizontalTextInVerticalContextSpanBetween(
            "Combine ".length(), "Combine 0004".length());
  }

  private ImmutableList<CuesWithTiming> getCuesForTestAsset(String asset) throws IOException {
    return getCuesForTestAsset(asset, SubtitleParser.OutputOptions.allCues());
  }

  private ImmutableList<CuesWithTiming> getCuesForTestAsset(
      String asset, SubtitleParser.OutputOptions outputOptions) throws IOException {
    WebvttParser parser = new WebvttParser();
    byte[] bytes = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), asset);
    ImmutableList.Builder<CuesWithTiming> result = ImmutableList.builder();
    parser.parse(bytes, outputOptions, /* output= */ result::add);
    return result.build();
  }

  private Spanned getUniqueSpanTextAt(CuesWithTiming cuesWithTiming) {
    return (Spanned) Assertions.checkNotNull(cuesWithTiming.cues.get(0).text);
  }
}

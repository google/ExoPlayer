/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.text.webvtt;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.LegacySubtitleUtil;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link LegacySubtitleUtil} using {@link WebvttSubtitle}.
 *
 * <p>This is in the webvtt package so we don't need to increase the visibility of {@link
 * WebvttSubtitle}.
 */
@RunWith(AndroidJUnit4.class)
public class LegacySubtitleUtilWebvttTest {

  private static final String FIRST_SUBTITLE_STRING = "This is the first subtitle.";
  private static final String SECOND_SUBTITLE_STRING = "This is the second subtitle.";

  private static final WebvttSubtitle SIMPLE_SUBTITLE =
      new WebvttSubtitle(
          Arrays.asList(
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(FIRST_SUBTITLE_STRING),
                  /* startTimeUs= */ 1_000_000,
                  /* endTimeUs= */ 2_000_000),
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(SECOND_SUBTITLE_STRING),
                  /* startTimeUs= */ 3_000_000,
                  /* endTimeUs= */ 4_000_000)));

  private static final WebvttSubtitle CONSECUTIVE_SUBTITLE =
      new WebvttSubtitle(
          Arrays.asList(
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(FIRST_SUBTITLE_STRING),
                  /* startTimeUs= */ 1_000_000,
                  /* endTimeUs= */ 2_000_000),
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(SECOND_SUBTITLE_STRING),
                  /* startTimeUs= */ 2_000_000,
                  /* endTimeUs= */ 4_000_000)));

  private static final WebvttSubtitle OVERLAPPING_SUBTITLE =
      new WebvttSubtitle(
          Arrays.asList(
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(FIRST_SUBTITLE_STRING),
                  /* startTimeUs= */ 1_000_000,
                  /* endTimeUs= */ 3_000_000),
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(SECOND_SUBTITLE_STRING),
                  /* startTimeUs= */ 2_000_000,
                  /* endTimeUs= */ 4_000_000)));

  @Test
  public void toCuesWithTiming_allCues_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(SIMPLE_SUBTITLE, SubtitleParser.OutputOptions.allCues());

    assertThat(cuesWithTimingsList).hasSize(2);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_allCues_consecutiveSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(CONSECUTIVE_SUBTITLE, SubtitleParser.OutputOptions.allCues());

    assertThat(cuesWithTimingsList).hasSize(2);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_allCues_overlappingSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(OVERLAPPING_SUBTITLE, SubtitleParser.OutputOptions.allCues());

    assertThat(cuesWithTimingsList).hasSize(3);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(3_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING)
        .inOrder();
    assertThat(cuesWithTimingsList.get(2).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(2).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(2).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(2).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startBetweenCues_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(2_500_000));

    assertThat(cuesWithTimingsList).hasSize(1);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startAtCueEnd_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(2_000_000));

    assertThat(cuesWithTimingsList).hasSize(1);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startAtCueStart_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(3_000_000));

    assertThat(cuesWithTimingsList).hasSize(1);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startInMiddleOfCue_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(1_500_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    // First cue is truncated to start at OutputOptions.startTimeUs
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startBetweenCues_consecutiveSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            CONSECUTIVE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(2_000_000));

    assertThat(cuesWithTimingsList).hasSize(1);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_startInMiddleOfCue_consecutiveSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            CONSECUTIVE_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(1_500_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    // First cue is truncated to start at OutputOptions.startTimeUs
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_onlyEmitCuesAfterStartTime_overlappingSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            OVERLAPPING_SUBTITLE, SubtitleParser.OutputOptions.onlyCuesAfter(2_500_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    // First event is truncated to start at OutputOptions.startTimeUs.
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(2_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(3_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING)
        .inOrder();
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
  }

  @Test
  public void
      toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_startAtStartOfCue_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(3_000_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
  }

  @Test
  public void
      toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_startInMiddleOfCue_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(1_500_000));

    assertThat(cuesWithTimingsList).hasSize(3);
    // First event is truncated to start at OutputOptions.startTimeUs.
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    // Final event is the part of the 'first event' that is before OutputOptions.startTimeUs
    assertThat(cuesWithTimingsList.get(2).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(2).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(2).endTimeUs).isEqualTo(1_500_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(2).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
  }

  @Test
  public void
      toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_startAtEndOfCue_simpleSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            SIMPLE_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(2_000_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
  }

  @Test
  public void
      toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_startBetweenCues_consecutiveSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            CONSECUTIVE_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(2_000_000));

    assertThat(cuesWithTimingsList).hasSize(2);
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
  }

  @Test
  public void
      toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_startInMiddleOfCue_consecutiveSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            CONSECUTIVE_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(1_500_000));

    assertThat(cuesWithTimingsList).hasSize(3);
    // First event is truncated to start at OutputOptions.startTimeUs.
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(1_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    // Final event is the part of the 'first event' that is before OutputOptions.startTimeUs
    assertThat(cuesWithTimingsList.get(2).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(2).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(2).endTimeUs).isEqualTo(1_500_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(2).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
  }

  @Test
  public void toCuesWithTiming_emitCuesAfterStartTimeThenThoseBefore_overlappingSubtitle() {
    ImmutableList<CuesWithTiming> cuesWithTimingsList =
        toCuesWithTimingList(
            OVERLAPPING_SUBTITLE,
            SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(2_500_000));

    assertThat(cuesWithTimingsList).hasSize(4);
    // First event is truncated to start at OutputOptions.startTimeUs.
    assertThat(cuesWithTimingsList.get(0).startTimeUs).isEqualTo(2_500_000);
    assertThat(cuesWithTimingsList.get(0).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(0).endTimeUs).isEqualTo(3_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(0).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING)
        .inOrder();
    assertThat(cuesWithTimingsList.get(1).startTimeUs).isEqualTo(3_000_000);
    assertThat(cuesWithTimingsList.get(1).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(1).endTimeUs).isEqualTo(4_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(1).cues, c -> c.text))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(cuesWithTimingsList.get(2).startTimeUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(2).durationUs).isEqualTo(1_000_000);
    assertThat(cuesWithTimingsList.get(2).endTimeUs).isEqualTo(2_000_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(2).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING);
    // Final event is the part of the 'first event' that is before OutputOptions.startTimeUs
    assertThat(cuesWithTimingsList.get(3).startTimeUs).isEqualTo(2_000_000);
    assertThat(cuesWithTimingsList.get(3).durationUs).isEqualTo(500_000);
    assertThat(cuesWithTimingsList.get(3).endTimeUs).isEqualTo(2_500_000);
    assertThat(Lists.transform(cuesWithTimingsList.get(3).cues, c -> c.text))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING)
        .inOrder();
  }

  private static ImmutableList<CuesWithTiming> toCuesWithTimingList(
      Subtitle subtitle, SubtitleParser.OutputOptions outputOptions) {
    ImmutableList.Builder<CuesWithTiming> result = ImmutableList.builder();
    LegacySubtitleUtil.toCuesWithTiming(subtitle, outputOptions, result::add);
    return result.build();
  }
}

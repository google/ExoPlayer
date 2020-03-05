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

import static com.google.android.exoplayer2.C.INDEX_UNSET;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Long.MAX_VALUE;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.text.Cue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link WebvttSubtitle}. */
@RunWith(AndroidJUnit4.class)
public class WebvttSubtitleTest {

  private static final String FIRST_SUBTITLE_STRING = "This is the first subtitle.";
  private static final String SECOND_SUBTITLE_STRING = "This is the second subtitle.";
  private static final String FIRST_AND_SECOND_SUBTITLE_STRING =
      FIRST_SUBTITLE_STRING + "\n" + SECOND_SUBTITLE_STRING;

  private static final WebvttSubtitle emptySubtitle = new WebvttSubtitle(Collections.emptyList());

  private static final WebvttSubtitle simpleSubtitle =
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

  private static final WebvttSubtitle overlappingSubtitle =
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

  private static final WebvttSubtitle nestedSubtitle =
      new WebvttSubtitle(
          Arrays.asList(
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(FIRST_SUBTITLE_STRING),
                  /* startTimeUs= */ 1_000_000,
                  /* endTimeUs= */ 4_000_000),
              new WebvttCueInfo(
                  WebvttCueParser.newCueForText(SECOND_SUBTITLE_STRING),
                  /* startTimeUs= */ 2_000_000,
                  /* endTimeUs= */ 3_000_000)));

  @Test
  public void eventCount() {
    assertThat(emptySubtitle.getEventTimeCount()).isEqualTo(0);
    assertThat(simpleSubtitle.getEventTimeCount()).isEqualTo(4);
    assertThat(overlappingSubtitle.getEventTimeCount()).isEqualTo(4);
    assertThat(nestedSubtitle.getEventTimeCount()).isEqualTo(4);
  }

  @Test
  public void simpleSubtitleEventTimes() {
    testSubtitleEventTimesHelper(simpleSubtitle);
  }

  @Test
  public void simpleSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(simpleSubtitle);
  }

  @Test
  public void simpleSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(0));
    assertSingleCueEmpty(simpleSubtitle.getCues(500_000));
    assertSingleCueEmpty(simpleSubtitle.getCues(999_999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1_000_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1_500_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, simpleSubtitle.getCues(1_999_999));

    // Test after first subtitle, before second subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(2_000_000));
    assertSingleCueEmpty(simpleSubtitle.getCues(2_500_000));
    assertSingleCueEmpty(simpleSubtitle.getCues(2_999_999));

    // Test second subtitle
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3_000_000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3_500_000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, simpleSubtitle.getCues(3_999_999));

    // Test after second subtitle
    assertSingleCueEmpty(simpleSubtitle.getCues(4_000_000));
    assertSingleCueEmpty(simpleSubtitle.getCues(4_500_000));
    assertSingleCueEmpty(simpleSubtitle.getCues(Long.MAX_VALUE));
  }

  @Test
  public void overlappingSubtitleEventTimes() {
    testSubtitleEventTimesHelper(overlappingSubtitle);
  }

  @Test
  public void overlappingSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(overlappingSubtitle);
  }

  @Test
  public void overlappingSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(overlappingSubtitle.getCues(0));
    assertSingleCueEmpty(overlappingSubtitle.getCues(500_000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(999_999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1_000_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1_500_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, overlappingSubtitle.getCues(1_999_999));

    // Test after first and second subtitle
    assertSingleCueTextEquals(
        FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(2_000_000));
    assertSingleCueTextEquals(
        FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(2_500_000));
    assertSingleCueTextEquals(
        FIRST_AND_SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(2_999_999));

    // Test second subtitle
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3_000_000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3_500_000));
    assertSingleCueTextEquals(SECOND_SUBTITLE_STRING, overlappingSubtitle.getCues(3_999_999));

    // Test after second subtitle
    assertSingleCueEmpty(overlappingSubtitle.getCues(4_000_000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(4_500_000));
    assertSingleCueEmpty(overlappingSubtitle.getCues(Long.MAX_VALUE));
  }

  @Test
  public void nestedSubtitleEventTimes() {
    testSubtitleEventTimesHelper(nestedSubtitle);
  }

  @Test
  public void nestedSubtitleEventIndices() {
    testSubtitleEventIndicesHelper(nestedSubtitle);
  }

  @Test
  public void nestedSubtitleText() {
    // Test before first subtitle
    assertSingleCueEmpty(nestedSubtitle.getCues(0));
    assertSingleCueEmpty(nestedSubtitle.getCues(500_000));
    assertSingleCueEmpty(nestedSubtitle.getCues(999_999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1_000_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1_500_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(1_999_999));

    // Test after first and second subtitle
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2_000_000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2_500_000));
    assertSingleCueTextEquals(FIRST_AND_SECOND_SUBTITLE_STRING, nestedSubtitle.getCues(2_999_999));

    // Test first subtitle
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3_000_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3_500_000));
    assertSingleCueTextEquals(FIRST_SUBTITLE_STRING, nestedSubtitle.getCues(3_999_999));

    // Test after second subtitle
    assertSingleCueEmpty(nestedSubtitle.getCues(4_000_000));
    assertSingleCueEmpty(nestedSubtitle.getCues(4_500_000));
    assertSingleCueEmpty(nestedSubtitle.getCues(Long.MAX_VALUE));
  }

  private void testSubtitleEventTimesHelper(WebvttSubtitle subtitle) {
    assertThat(subtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(subtitle.getEventTime(1)).isEqualTo(2_000_000);
    assertThat(subtitle.getEventTime(2)).isEqualTo(3_000_000);
    assertThat(subtitle.getEventTime(3)).isEqualTo(4_000_000);
  }

  private void testSubtitleEventIndicesHelper(WebvttSubtitle subtitle) {
    // Test first event
    assertThat(subtitle.getNextEventTimeIndex(0)).isEqualTo(0);
    assertThat(subtitle.getNextEventTimeIndex(500_000)).isEqualTo(0);
    assertThat(subtitle.getNextEventTimeIndex(999_999)).isEqualTo(0);

    // Test second event
    assertThat(subtitle.getNextEventTimeIndex(1_000_000)).isEqualTo(1);
    assertThat(subtitle.getNextEventTimeIndex(1_500_000)).isEqualTo(1);
    assertThat(subtitle.getNextEventTimeIndex(1_999_999)).isEqualTo(1);

    // Test third event
    assertThat(subtitle.getNextEventTimeIndex(2_000_000)).isEqualTo(2);
    assertThat(subtitle.getNextEventTimeIndex(2_500_000)).isEqualTo(2);
    assertThat(subtitle.getNextEventTimeIndex(2_999_999)).isEqualTo(2);

    // Test fourth event
    assertThat(subtitle.getNextEventTimeIndex(3_000_000)).isEqualTo(3);
    assertThat(subtitle.getNextEventTimeIndex(3_500_000)).isEqualTo(3);
    assertThat(subtitle.getNextEventTimeIndex(3_999_999)).isEqualTo(3);

    // Test null event (i.e. look for events after the last event)
    assertThat(subtitle.getNextEventTimeIndex(4_000_000)).isEqualTo(INDEX_UNSET);
    assertThat(subtitle.getNextEventTimeIndex(4_500_000)).isEqualTo(INDEX_UNSET);
    assertThat(subtitle.getNextEventTimeIndex(MAX_VALUE)).isEqualTo(INDEX_UNSET);
  }

  private void assertSingleCueEmpty(List<Cue> cues) {
    assertThat(cues).isEmpty();
  }

  private void assertSingleCueTextEquals(String expected, List<Cue> cues) {
    assertThat(cues).hasSize(1);
    assertThat(cues.get(0).text.toString()).isEqualTo(expected);
  }
}

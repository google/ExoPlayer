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
import java.util.ArrayList;
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
    assertThat(simpleSubtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(simpleSubtitle.getEventTime(1)).isEqualTo(2_000_000);
    assertThat(simpleSubtitle.getEventTime(2)).isEqualTo(3_000_000);
    assertThat(simpleSubtitle.getEventTime(3)).isEqualTo(4_000_000);
  }

  @Test
  public void simpleSubtitleEventIndices() {
    // Test first event
    assertThat(simpleSubtitle.getNextEventTimeIndex(0)).isEqualTo(0);
    assertThat(simpleSubtitle.getNextEventTimeIndex(500_000)).isEqualTo(0);
    assertThat(simpleSubtitle.getNextEventTimeIndex(999_999)).isEqualTo(0);

    // Test second event
    assertThat(simpleSubtitle.getNextEventTimeIndex(1_000_000)).isEqualTo(1);
    assertThat(simpleSubtitle.getNextEventTimeIndex(1_500_000)).isEqualTo(1);
    assertThat(simpleSubtitle.getNextEventTimeIndex(1_999_999)).isEqualTo(1);

    // Test third event
    assertThat(simpleSubtitle.getNextEventTimeIndex(2_000_000)).isEqualTo(2);
    assertThat(simpleSubtitle.getNextEventTimeIndex(2_500_000)).isEqualTo(2);
    assertThat(simpleSubtitle.getNextEventTimeIndex(2_999_999)).isEqualTo(2);

    // Test fourth event
    assertThat(simpleSubtitle.getNextEventTimeIndex(3_000_000)).isEqualTo(3);
    assertThat(simpleSubtitle.getNextEventTimeIndex(3_500_000)).isEqualTo(3);
    assertThat(simpleSubtitle.getNextEventTimeIndex(3_999_999)).isEqualTo(3);

    // Test null event (i.e. look for events after the last event)
    assertThat(simpleSubtitle.getNextEventTimeIndex(4_000_000)).isEqualTo(INDEX_UNSET);
    assertThat(simpleSubtitle.getNextEventTimeIndex(4_500_000)).isEqualTo(INDEX_UNSET);
    assertThat(simpleSubtitle.getNextEventTimeIndex(MAX_VALUE)).isEqualTo(INDEX_UNSET);
  }

  @Test
  public void simpleSubtitleText() {
    // Test before first subtitle
    assertThat(simpleSubtitle.getCues(0)).isEmpty();
    assertThat(simpleSubtitle.getCues(500_000)).isEmpty();
    assertThat(simpleSubtitle.getCues(999_999)).isEmpty();

    // Test first subtitle
    assertThat(getCueTexts(simpleSubtitle.getCues(1_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(simpleSubtitle.getCues(1_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(simpleSubtitle.getCues(1_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING);

    // Test after first subtitle, before second subtitle
    assertThat(simpleSubtitle.getCues(2_000_000)).isEmpty();
    assertThat(simpleSubtitle.getCues(2_500_000)).isEmpty();
    assertThat(simpleSubtitle.getCues(2_999_999)).isEmpty();

    // Test second subtitle
    assertThat(getCueTexts(simpleSubtitle.getCues(3_000_000)))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(simpleSubtitle.getCues(3_500_000)))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(simpleSubtitle.getCues(3_999_999)))
        .containsExactly(SECOND_SUBTITLE_STRING);

    // Test after second subtitle
    assertThat(simpleSubtitle.getCues(4_000_000)).isEmpty();
    assertThat(simpleSubtitle.getCues(4_500_000)).isEmpty();
    assertThat(simpleSubtitle.getCues(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  public void overlappingSubtitleEventTimes() {
    assertThat(overlappingSubtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(overlappingSubtitle.getEventTime(1)).isEqualTo(2_000_000);
    assertThat(overlappingSubtitle.getEventTime(2)).isEqualTo(3_000_000);
    assertThat(overlappingSubtitle.getEventTime(3)).isEqualTo(4_000_000);
  }

  @Test
  public void overlappingSubtitleEventIndices() {
    // Test first event
    assertThat(overlappingSubtitle.getNextEventTimeIndex(0)).isEqualTo(0);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(500_000)).isEqualTo(0);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(999_999)).isEqualTo(0);

    // Test second event
    assertThat(overlappingSubtitle.getNextEventTimeIndex(1_000_000)).isEqualTo(1);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(1_500_000)).isEqualTo(1);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(1_999_999)).isEqualTo(1);

    // Test third event
    assertThat(overlappingSubtitle.getNextEventTimeIndex(2_000_000)).isEqualTo(2);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(2_500_000)).isEqualTo(2);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(2_999_999)).isEqualTo(2);

    // Test fourth event
    assertThat(overlappingSubtitle.getNextEventTimeIndex(3_000_000)).isEqualTo(3);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(3_500_000)).isEqualTo(3);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(3_999_999)).isEqualTo(3);

    // Test null event (i.e. look for events after the last event)
    assertThat(overlappingSubtitle.getNextEventTimeIndex(4_000_000)).isEqualTo(INDEX_UNSET);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(4_500_000)).isEqualTo(INDEX_UNSET);
    assertThat(overlappingSubtitle.getNextEventTimeIndex(MAX_VALUE)).isEqualTo(INDEX_UNSET);
  }

  @Test
  public void overlappingSubtitleText() {
    // Test before first subtitle
    assertThat(overlappingSubtitle.getCues(0)).isEmpty();
    assertThat(overlappingSubtitle.getCues(500_000)).isEmpty();
    assertThat(overlappingSubtitle.getCues(999_999)).isEmpty();

    // Test first subtitle
    assertThat(getCueTexts(overlappingSubtitle.getCues(1_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(1_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(1_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING);

    // Test after first and second subtitle
    assertThat(getCueTexts(overlappingSubtitle.getCues(2_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(2_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(2_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);

    // Test second subtitle
    assertThat(getCueTexts(overlappingSubtitle.getCues(3_000_000)))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(3_500_000)))
        .containsExactly(SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(overlappingSubtitle.getCues(3_999_999)))
        .containsExactly(SECOND_SUBTITLE_STRING);

    // Test after second subtitle
    assertThat(overlappingSubtitle.getCues(4_000_000)).isEmpty();
    assertThat(overlappingSubtitle.getCues(4_500_000)).isEmpty();
    assertThat(overlappingSubtitle.getCues(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  public void nestedSubtitleEventTimes() {
    assertThat(nestedSubtitle.getEventTime(0)).isEqualTo(1_000_000);
    assertThat(nestedSubtitle.getEventTime(1)).isEqualTo(2_000_000);
    assertThat(nestedSubtitle.getEventTime(2)).isEqualTo(3_000_000);
    assertThat(nestedSubtitle.getEventTime(3)).isEqualTo(4_000_000);
  }

  @Test
  public void nestedSubtitleEventIndices() {
    // Test first event
    assertThat(nestedSubtitle.getNextEventTimeIndex(0)).isEqualTo(0);
    assertThat(nestedSubtitle.getNextEventTimeIndex(500_000)).isEqualTo(0);
    assertThat(nestedSubtitle.getNextEventTimeIndex(999_999)).isEqualTo(0);

    // Test second event
    assertThat(nestedSubtitle.getNextEventTimeIndex(1_000_000)).isEqualTo(1);
    assertThat(nestedSubtitle.getNextEventTimeIndex(1_500_000)).isEqualTo(1);
    assertThat(nestedSubtitle.getNextEventTimeIndex(1_999_999)).isEqualTo(1);

    // Test third event
    assertThat(nestedSubtitle.getNextEventTimeIndex(2_000_000)).isEqualTo(2);
    assertThat(nestedSubtitle.getNextEventTimeIndex(2_500_000)).isEqualTo(2);
    assertThat(nestedSubtitle.getNextEventTimeIndex(2_999_999)).isEqualTo(2);

    // Test fourth event
    assertThat(nestedSubtitle.getNextEventTimeIndex(3_000_000)).isEqualTo(3);
    assertThat(nestedSubtitle.getNextEventTimeIndex(3_500_000)).isEqualTo(3);
    assertThat(nestedSubtitle.getNextEventTimeIndex(3_999_999)).isEqualTo(3);

    // Test null event (i.e. look for events after the last event)
    assertThat(nestedSubtitle.getNextEventTimeIndex(4_000_000)).isEqualTo(INDEX_UNSET);
    assertThat(nestedSubtitle.getNextEventTimeIndex(4_500_000)).isEqualTo(INDEX_UNSET);
    assertThat(nestedSubtitle.getNextEventTimeIndex(MAX_VALUE)).isEqualTo(INDEX_UNSET);
  }

  @Test
  public void nestedSubtitleText() {
    // Test before first subtitle
    assertThat(nestedSubtitle.getCues(0)).isEmpty();
    assertThat(nestedSubtitle.getCues(500_000)).isEmpty();
    assertThat(nestedSubtitle.getCues(999_999)).isEmpty();

    // Test first subtitle
    assertThat(getCueTexts(nestedSubtitle.getCues(1_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(1_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(1_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING);

    // Test after first and second subtitle
    assertThat(getCueTexts(nestedSubtitle.getCues(2_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(2_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(2_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING, SECOND_SUBTITLE_STRING);

    // Test first subtitle
    assertThat(getCueTexts(nestedSubtitle.getCues(3_000_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(3_500_000)))
        .containsExactly(FIRST_SUBTITLE_STRING);
    assertThat(getCueTexts(nestedSubtitle.getCues(3_999_999)))
        .containsExactly(FIRST_SUBTITLE_STRING);

    // Test after second subtitle
    assertThat(nestedSubtitle.getCues(4_000_000)).isEmpty();
    assertThat(nestedSubtitle.getCues(4_500_000)).isEmpty();
    assertThat(nestedSubtitle.getCues(Long.MAX_VALUE)).isEmpty();
  }

  private static List<String> getCueTexts(List<Cue> cues) {
    List<String> cueTexts = new ArrayList<>();
    for (int i = 0; i < cues.size(); i++) {
      cueTexts.add(cues.get(i).text.toString());
    }
    return cueTexts;
  }
}

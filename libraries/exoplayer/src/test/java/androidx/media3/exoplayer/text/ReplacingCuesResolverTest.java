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
package androidx.media3.exoplayer.text;

import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCueTextBetween;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCueTextUntilEnd;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCuesEndAt;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCuesStartAt;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertNoCuesBetween;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ReplacingCuesResolver}. */
@RunWith(AndroidJUnit4.class)
public final class ReplacingCuesResolverTest {

  private static final ImmutableList<Cue> FIRST_CUES =
      ImmutableList.of(new Cue.Builder().setText("first cue").build());
  public static final ImmutableList<Cue> SECOND_CUES =
      ImmutableList.of(
          new Cue.Builder().setText("second group: cue1").build(),
          new Cue.Builder().setText("second group: cue2").build());
  public static final ImmutableList<Cue> THIRD_CUES =
      ImmutableList.of(new Cue.Builder().setText("third cue").build());

  @Test
  public void empty() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();

    assertThat(replacingCuesResolver.getPreviousCueChangeTimeUs(999_999_999))
        .isEqualTo(C.TIME_UNSET);
    assertThat(replacingCuesResolver.getNextCueChangeTimeUs(0)).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(replacingCuesResolver.getCuesAtTimeUs(0)).isEmpty();
  }

  @Test
  public void unsetDuration() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(
            FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ C.TIME_UNSET);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(
            SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ C.TIME_UNSET);

    // Reverse the addCues call to check everything still works (it should).
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(replacingCuesResolver, 3_000_000);
    assertCueTextBetween(replacingCuesResolver, 3_000_000, 6_000_000, "first cue");
    assertCueTextUntilEnd(
        replacingCuesResolver, 6_000_000, "second group: cue1", "second group: cue2");
  }

  @Test
  public void nonOverlappingCues() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);

    replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(replacingCuesResolver, 3_000_000);

    assertCueTextBetween(replacingCuesResolver, 3_000_000, 5_000_000, "first cue");
    assertNoCuesBetween(replacingCuesResolver, 5_000_000, 6_000_000);
    assertCueTextBetween(
        replacingCuesResolver, 6_000_000, 7_000_000, "second group: cue1", "second group: cue2");
    assertCuesEndAt(replacingCuesResolver, 7_000_000);
  }

  @Test
  public void overlappingCues_secondReplacesFirst() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 3_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 2_000_000, /* durationUs= */ 4_000_000);

    replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(replacingCuesResolver, 1_000_000);
    assertCueTextBetween(replacingCuesResolver, 1_000_000, 2_000_000, "first cue");
    assertCueTextBetween(
        replacingCuesResolver, 2_000_000, 6_000_000, "second group: cue1", "second group: cue2");
    assertCuesEndAt(replacingCuesResolver, 6_000_000);
  }

  @Test
  public void overlappingCues_matchingStartTimes_onlySecondEmitted() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 4_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 3_000_000);

    replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(replacingCuesResolver, 1_000_000);
    assertCueTextBetween(
        replacingCuesResolver, 1_000_000, 4_000_000, "second group: cue1", "second group: cue2");
    assertCuesEndAt(replacingCuesResolver, 4_000_000);
  }

  @Test
  public void addCues_cuesStartAfterCurrentPosition() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 1_000_000))
        .isFalse();
  }

  @Test
  public void addCues_cuesStartAtCurrentPosition() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 3_000_000))
        .isTrue();
  }

  @Test
  public void addCues_cuesDisplayedAtCurrentPosition() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 4_000_000))
        .isTrue();
  }

  @Test
  public void addCues_cuesDisplayedAtCurrentPosition_butAlreadyReplacedByLaterCues() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 3_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 4_000_000, /* durationUs= */ 3_000_000);
    CuesWithTiming thirdCuesWithTiming =
        new CuesWithTiming(THIRD_CUES, /* startTimeUs= */ 5_000_000, /* durationUs= */ 3_000_000);
    replacingCuesResolver.addCues(thirdCuesWithTiming, /* currentPositionUs= */ 0);

    assertThat(
            replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 5_000_000))
        .isFalse();
    assertThat(
            replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 5_500_000))
        .isFalse();
  }

  @Test
  public void addCues_cuesEndBeforeCurrentPosition() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 6_000_000))
        .isFalse();
  }

  @Test
  public void addCues_cuesEndAtCurrentPosition() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(replacingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 5_000_000))
        .isFalse();
  }

  @Test
  public void discardCuesBeforeTimeUs() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);

    replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    // Remove firstCuesWithTiming
    replacingCuesResolver.discardCuesBeforeTimeUs(5_500_000);

    // Query with a time that *should* be inside firstCuesWithTiming, but it's been removed.
    assertThat(replacingCuesResolver.getCuesAtTimeUs(4_999_990)).isEmpty();
    assertThat(replacingCuesResolver.getPreviousCueChangeTimeUs(4_999_990)).isEqualTo(C.TIME_UNSET);
    assertThat(replacingCuesResolver.getNextCueChangeTimeUs(4_999_990)).isEqualTo(6_000_000);
  }

  @Test
  public void clear_clearsAllCues() {
    ReplacingCuesResolver replacingCuesResolver = new ReplacingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);
    CuesWithTiming thirdCuesWithTiming =
        new CuesWithTiming(THIRD_CUES, /* startTimeUs= */ 8_000_000, /* durationUs= */ 4_000_000);

    replacingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);
    replacingCuesResolver.addCues(thirdCuesWithTiming, /* currentPositionUs= */ 0);

    replacingCuesResolver.clear();

    assertThat(replacingCuesResolver.getPreviousCueChangeTimeUs(999_999_999))
        .isEqualTo(C.TIME_UNSET);
    assertThat(replacingCuesResolver.getNextCueChangeTimeUs(0)).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(replacingCuesResolver.getCuesAtTimeUs(0)).isEmpty();
  }
}

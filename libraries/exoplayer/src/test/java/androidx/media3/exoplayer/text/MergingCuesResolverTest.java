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
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCuesEndAt;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertCuesStartAt;
import static androidx.media3.exoplayer.text.CuesListTestUtil.assertNoCuesBetween;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MergingCuesResolver}. */
@RunWith(AndroidJUnit4.class)
public final class MergingCuesResolverTest {

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
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();

    assertThat(mergingCuesResolver.getPreviousCueChangeTimeUs(999_999_999)).isEqualTo(C.TIME_UNSET);
    assertThat(mergingCuesResolver.getNextCueChangeTimeUs(0)).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(mergingCuesResolver.getCuesAtTimeUs(0)).isEmpty();
  }

  @Test
  public void nonOverlappingCues() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);

    // Reverse the addCues call to check everything still works (it should).
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(mergingCuesResolver, 3_000_000);

    assertCueTextBetween(mergingCuesResolver, 3_000_000, 5_000_000, "first cue");
    assertNoCuesBetween(mergingCuesResolver, 5_000_000, 6_000_000);
    assertCueTextBetween(
        mergingCuesResolver, 6_000_000, 7_000_000, "second group: cue1", "second group: cue2");
    assertCuesEndAt(mergingCuesResolver, 7_000_000);
  }

  @Test
  public void overlappingCues() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 3_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 2_000_000, /* durationUs= */ 4_000_000);

    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(mergingCuesResolver, 1_000_000);
    assertCueTextBetween(mergingCuesResolver, 1_000_000, 2_000_000, "first cue");
    // secondCuesWithTiming has a later start time (despite longer duration), so should appear later
    // in the list.
    assertCueTextBetween(
        mergingCuesResolver,
        2_000_000,
        4_000_000,
        "first cue",
        "second group: cue1",
        "second group: cue2");
    assertCueTextBetween(
        mergingCuesResolver, 4_000_000, 6_000_000, "second group: cue1", "second group: cue2");
    assertCuesEndAt(mergingCuesResolver, 6_000_000);
  }

  @Test
  public void overlappingCues_matchingStartTimes() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 4_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 3_000_000);

    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(mergingCuesResolver, 1_000_000);
    // secondCuesWithTiming has a shorter duration than firstCuesWithTiming, so should appear later
    // in the list.
    assertCueTextBetween(
        mergingCuesResolver,
        1_000_000,
        4_000_000,
        "first cue",
        "second group: cue1",
        "second group: cue2");
    assertCueTextBetween(mergingCuesResolver, 4_000_000, 5_000_000, "first cue");
    assertCuesEndAt(mergingCuesResolver, 5_000_000);
  }

  @Test
  public void overlappingCues_matchingEndTimes() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 1_000_000, /* durationUs= */ 4_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 2_000_000, /* durationUs= */ 3_000_000);

    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);

    assertCuesStartAt(mergingCuesResolver, 1_000_000);
    // secondCuesWithTiming has a shorter duration than firstCuesWithTiming, so should appear later
    // in the list.
    assertCueTextBetween(mergingCuesResolver, 1_000_000, 2_000_000, "first cue");
    assertCueTextBetween(
        mergingCuesResolver,
        2_000_000,
        5_000_000,
        "first cue",
        "second group: cue1",
        "second group: cue2");
    assertCuesEndAt(mergingCuesResolver, 5_000_000);
  }

  @Test
  public void unsetDuration_unsupported() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(
            FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ C.TIME_UNSET);

    assertThrows(
        IllegalArgumentException.class,
        () -> mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 0));
  }

  @Test
  public void addCues_cuesStartAfterCurrentPosition() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 1_000_000))
        .isFalse();
  }

  @Test
  public void addCues_cuesStartAtCurrentPosition() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 3_000_000))
        .isTrue();
  }

  @Test
  public void addCues_cuesDisplayedAtCurrentPosition() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 4_000_000))
        .isTrue();
  }

  @Test
  public void addCues_cuesEndBeforeCurrentPosition() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 6_000_000))
        .isFalse();
  }

  @Test
  public void addCues_cuesEndAtCurrentPosition() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming cuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);

    assertThat(mergingCuesResolver.addCues(cuesWithTiming, /* currentPositionUs= */ 5_000_000))
        .isFalse();
  }

  @Test
  public void discardCuesBeforeTimeUs() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);
    CuesWithTiming thirdCuesWithTiming =
        new CuesWithTiming(THIRD_CUES, /* startTimeUs= */ 8_000_000, /* durationUs= */ 4_000_000);

    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(thirdCuesWithTiming, /* currentPositionUs= */ 0);

    // Remove only firstCuesWithTiming (secondCuesWithTiming should be kept because it ends after
    // this time).
    mergingCuesResolver.discardCuesBeforeTimeUs(6_500_000);

    // Query with a time that *should* be inside firstCuesWithTiming, but it's been removed.
    assertThat(mergingCuesResolver.getCuesAtTimeUs(4_999_990)).isEmpty();
    assertThat(mergingCuesResolver.getPreviousCueChangeTimeUs(4_999_990)).isEqualTo(C.TIME_UNSET);
    assertThat(mergingCuesResolver.getNextCueChangeTimeUs(4_999_990)).isEqualTo(6_000_000);
  }

  @Test
  public void clear_clearsAllCues() {
    MergingCuesResolver mergingCuesResolver = new MergingCuesResolver();
    CuesWithTiming firstCuesWithTiming =
        new CuesWithTiming(FIRST_CUES, /* startTimeUs= */ 3_000_000, /* durationUs= */ 2_000_000);
    CuesWithTiming secondCuesWithTiming =
        new CuesWithTiming(SECOND_CUES, /* startTimeUs= */ 6_000_000, /* durationUs= */ 1_000_000);
    CuesWithTiming thirdCuesWithTiming =
        new CuesWithTiming(THIRD_CUES, /* startTimeUs= */ 8_000_000, /* durationUs= */ 4_000_000);

    mergingCuesResolver.addCues(firstCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(secondCuesWithTiming, /* currentPositionUs= */ 0);
    mergingCuesResolver.addCues(thirdCuesWithTiming, /* currentPositionUs= */ 0);

    mergingCuesResolver.clear();

    assertThat(mergingCuesResolver.getPreviousCueChangeTimeUs(999_999_999)).isEqualTo(C.TIME_UNSET);
    assertThat(mergingCuesResolver.getNextCueChangeTimeUs(0)).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(mergingCuesResolver.getCuesAtTimeUs(0)).isEmpty();
  }
}

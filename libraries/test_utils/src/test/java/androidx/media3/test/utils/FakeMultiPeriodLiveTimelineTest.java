/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.test.utils;

import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.AD_PERIOD_DURATION_US;
import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.PERIOD_DURATION_US;
import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.calculateAdSequencePatternDurationUs;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FakeMultiPeriodLiveTimeline}. */
@RunWith(AndroidJUnit4.class)
public class FakeMultiPeriodLiveTimelineTest {

  private static final long A_DAY_US = 24L * 60L * 60L * 1_000_000L;

  @Test
  public void newInstance_availabilitySinceStartOfUnixEpoch_correctLiveWindow() {
    boolean[] adSequencePattern = {false, true, true};
    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_000L,
            adSequencePattern,
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();

    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).id).isEqualTo(0);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-0[c]");
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).durationUs).isEqualTo(30_000_000L);
    assertThat(timeline.getPeriod(1, period).positionInWindowUs).isEqualTo(30_000_000L);
    assertThat(timeline.getPeriod(1, period).durationUs).isEqualTo(10_000_000L);
    assertThat(timeline.getPeriod(2, period).positionInWindowUs).isEqualTo(40_000_000L);
    assertThat(timeline.getPeriod(2, period).durationUs).isEqualTo(10_000_000L);
    assertThat(timeline.getPeriod(3, period).positionInWindowUs).isEqualTo(50_000_000L);
    assertThat(timeline.getPeriod(3, period).durationUs).isEqualTo(C.TIME_UNSET);
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_000L,
            adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void newInstance_timelineWithAdsPopulated_correctPlaybackStates() {
    boolean[] adSequencePattern = {false, true, true};
    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 50_000_000L,
            /* nowUs= */ 100_000_000L,
            adSequencePattern,
            /* isContentTimeline= */ false,
            /* populateAds= */ true);
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();

    assertThat(timeline.getPeriodCount()).isEqualTo(3);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-3[c]");
    assertThat(timeline.getPeriod(1, period).uid).isEqualTo("uid-4[a]");
    assertThat(timeline.getPeriod(1, period).getAdGroupCount()).isEqualTo(2);
    assertThat(timeline.getPeriod(1, period).getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(0L);
    assertThat(timeline.getPeriod(1, period).getAdCountInAdGroup(/* adGroupIndex= */ 0))
        .isEqualTo(1);
    assertThat(
            timeline
                .getPeriod(1, period)
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(10_000_000L);
    assertThat(timeline.getPeriod(1, period).getAdGroupTimeUs(/* adGroupIndex= */ 1))
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(timeline.getPeriod(1, period).getAdCountInAdGroup(/* adGroupIndex= */ 1))
        .isEqualTo(C.LENGTH_UNSET);
    assertThat(timeline.getPeriod(1, period).isServerSideInsertedAdGroup(/* adGroupIndex= */ 1))
        .isTrue();
    assertThat(timeline.getPeriod(2, period).uid).isEqualTo("uid-5[a]");
    assertThat(timeline.getPeriod(2, period).getAdGroupCount()).isEqualTo(2);
    assertThat(timeline.getPeriod(2, period).getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(0L);
    assertThat(timeline.getPeriod(2, period).getAdCountInAdGroup(/* adGroupIndex= */ 0))
        .isEqualTo(1);
    assertThat(timeline.getPeriod(2, period).isServerSideInsertedAdGroup(/* adGroupIndex= */ 1))
        .isTrue();
    assertThat(
            timeline
                .getPeriod(2, period)
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(10_000_000L);
    assertThat(timeline.getPeriod(2, period).getAdGroupTimeUs(/* adGroupIndex= */ 1))
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(timeline.getPeriod(2, period).getAdCountInAdGroup(/* adGroupIndex= */ 1))
        .isEqualTo(C.LENGTH_UNSET);
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 50_000_000L,
            /* nowUs= */ 100_000_000L,
            adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void newInstance_timelineWithAdsNotPopulated_correctPlaybackStates() {
    boolean[] adSequencePattern = {false, true, true};
    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 50_000_000L,
            /* nowUs= */ 100_000_000L,
            adSequencePattern,
            /* isContentTimeline= */ false,
            /* populateAds= */ false);
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();

    // Assert that each period has no ads but a fake postroll ad group at the end.
    assertThat(timeline.getPeriodCount()).isEqualTo(3);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-3[c]");
    assertThat(timeline.getPeriod(0, period).getAdGroupCount()).isEqualTo(1);
    assertThat(timeline.getPeriod(0, period).getAdGroupTimeUs(/* adGroupIndex= */ 0))
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(timeline.getPeriod(0, period).getAdCountInAdGroup(/* adGroupIndex= */ 0))
        .isEqualTo(C.LENGTH_UNSET);
    assertThat(timeline.getPeriod(0, period).isServerSideInsertedAdGroup(/* adGroupIndex= */ 0))
        .isTrue();
    assertThat(timeline.getPeriod(1, period).uid).isEqualTo("uid-4[a]");
    assertThat(timeline.getPeriod(1, period).getAdGroupCount()).isEqualTo(1);
    assertThat(timeline.getPeriod(1, period).getAdGroupTimeUs(/* adGroupIndex= */ 0))
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(timeline.getPeriod(1, period).getAdCountInAdGroup(/* adGroupIndex= */ 0))
        .isEqualTo(C.LENGTH_UNSET);
    assertThat(timeline.getPeriod(1, period).isServerSideInsertedAdGroup(/* adGroupIndex= */ 0))
        .isTrue();
    assertThat(timeline.getPeriod(2, period).uid).isEqualTo("uid-5[a]");
    assertThat(timeline.getPeriod(2, period).getAdGroupCount()).isEqualTo(1);
    assertThat(timeline.getPeriod(2, period).getAdGroupTimeUs(/* adGroupIndex= */ 0))
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(timeline.getPeriod(2, period).getAdCountInAdGroup(/* adGroupIndex= */ 0))
        .isEqualTo(C.LENGTH_UNSET);
    assertThat(timeline.getPeriod(2, period).isServerSideInsertedAdGroup(/* adGroupIndex= */ 0))
        .isTrue();
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 50_000_000L,
            /* nowUs= */ 100_000_000L,
            adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void advanceTimeUs_availabilitySinceStartOfUnixEpoch_correctPeriodsInLiveWindow() {
    boolean[] adSequencePattern = {false, true, true};
    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_123L,
            adSequencePattern,
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();

    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).id).isEqualTo(0);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-0[c]");
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(-123L);
    assertThat(timeline.getPeriod(1, period).positionInWindowUs).isEqualTo(29_999_877L);
    assertThat(timeline.getPeriod(2, period).positionInWindowUs).isEqualTo(39_999_877L);
    assertThat(timeline.getPeriod(3, period).positionInWindowUs).isEqualTo(49_999_877L);
    assertThat(timeline.getPeriod(3, period).durationUs).isEqualTo(C.TIME_UNSET);
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_123L,
            adSequencePattern),
        adSequencePattern);

    // Advance nowUs so that the window ends just 1us before the next period moves into the window.
    timeline.advanceNowUs(19999877L);

    // Assert that an additional period has not been included in the live window.
    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(20_000L);
    assertThat(timeline.getPeriod(0, period).id).isEqualTo(0);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-0[c]");
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(-20_000_000L);
    assertThat(timeline.getPeriod(1, period).positionInWindowUs).isEqualTo(10_000_000L);
    assertThat(timeline.getPeriod(2, period).positionInWindowUs).isEqualTo(20_000_000L);
    assertThat(timeline.getPeriod(3, period).positionInWindowUs).isEqualTo(30_000_000L);
    assertThat(timeline.getPeriod(3, period).durationUs).isEqualTo(C.TIME_UNSET);
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_123L + 19999877L,
            adSequencePattern),
        adSequencePattern);

    // Advance the window by 1us to add the next period at the end of the window.
    timeline.advanceNowUs(1L);

    // Assert that the previously first period has been moved out of the live window.
    assertThat(timeline.getPeriodCount()).isEqualTo(5);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(20_000L);
    assertThat(timeline.getPeriod(0, period).id).isEqualTo(0);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-0[c]");
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(-20_000_001L);
    assertThat(timeline.getPeriod(1, period).positionInWindowUs).isEqualTo(9_999_999L);
    assertThat(timeline.getPeriod(2, period).positionInWindowUs).isEqualTo(19_999_999L);
    assertThat(timeline.getPeriod(3, period).positionInWindowUs).isEqualTo(29_999_999L);
    assertThat(timeline.getPeriod(4, period).positionInWindowUs).isEqualTo(59_999_999L);
    assertThat(timeline.getPeriod(4, period).durationUs).isEqualTo(C.TIME_UNSET);
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 60_000_000L,
            /* nowUs= */ 60_000_123L + 19999878L,
            adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void newInstance_advancedAvailabilityStartTime_correctlyInterpolatedPeriodIds() {
    Timeline.Period period = new Timeline.Period();
    long availabilityStartTimeUs = 0;
    long nowUs = 120_000_123;
    long liveWindowDurationUs = 60_000_987L;
    boolean[] adSequencePattern = {false, true, true};
    long sequenceDurationUs = calculateAdSequencePatternDurationUs(adSequencePattern);

    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            availabilityStartTimeUs,
            liveWindowDurationUs,
            nowUs,
            adSequencePattern,
            /* isContentTimeline= */ true,
            /* populateAds= */ false);

    assertThat(timeline.getWindow(0, new Timeline.Window()).windowStartTimeMs)
        .isEqualTo(Util.usToMs(nowUs - liveWindowDurationUs));
    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period).id).isEqualTo(3);
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period).uid).isEqualTo("uid-3[c]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 1, period).id).isEqualTo(4);
    assertThat(timeline.getPeriod(/* periodIndex= */ 1, period).uid).isEqualTo("uid-4[a]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 2, period).id).isEqualTo(5);
    assertThat(timeline.getPeriod(/* periodIndex= */ 2, period).uid).isEqualTo("uid-5[a]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 3, period).id).isEqualTo(6);
    assertThat(timeline.getPeriod(/* periodIndex= */ 3, period).uid).isEqualTo("uid-6[c]");
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            availabilityStartTimeUs, liveWindowDurationUs, nowUs, adSequencePattern),
        adSequencePattern);

    timeline.advanceNowUs(sequenceDurationUs * 13);

    assertThat(timeline.getWindow(0, new Timeline.Window()).windowStartTimeMs)
        .isEqualTo(Util.usToMs((nowUs + sequenceDurationUs * 13) - liveWindowDurationUs));
    assertThat(timeline.getPeriodCount()).isEqualTo(4);
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period).id).isEqualTo((13 * 3) + 3);
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period).uid)
        .isEqualTo("uid-" + ((13 * 3) + 3) + "[c]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 1, period).id).isEqualTo((13 * 3) + 4);
    assertThat(timeline.getPeriod(/* periodIndex= */ 1, period).uid)
        .isEqualTo("uid-" + ((13 * 3) + 4) + "[a]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 2, period).id).isEqualTo((13 * 3) + 5);
    assertThat(timeline.getPeriod(/* periodIndex= */ 2, period).uid)
        .isEqualTo("uid-" + ((13 * 3) + 5) + "[a]");
    assertThat(timeline.getPeriod(/* periodIndex= */ 3, period).id).isEqualTo((13 * 3) + 6);
    assertThat(timeline.getPeriod(/* periodIndex= */ 3, period).uid)
        .isEqualTo("uid-" + ((13 * 3) + 6) + "[c]");
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            availabilityStartTimeUs,
            liveWindowDurationUs,
            (nowUs + sequenceDurationUs * 13),
            adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void newInstance_availabilitySinceAWeekAfterStartOfUnixEpoch_correctLiveWindow() {
    long availabilityStartTimeUs = 7 * A_DAY_US;
    long nowUs = 18 * A_DAY_US + 135_000_000;
    long liveWindowDurationUs = 60_000_000L;
    boolean[] adSequencePattern = {false, true, true};

    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            availabilityStartTimeUs,
            liveWindowDurationUs,
            nowUs,
            adSequencePattern,
            /* isContentTimeline= */ true,
            /* populateAds= */ false);

    assertThat(timeline.getWindow(0, new Timeline.Window()).windowStartTimeMs)
        .isEqualTo(Util.usToMs(nowUs - liveWindowDurationUs));
    assertExpectedWindow(
        timeline,
        calculateExpectedWindow(
            availabilityStartTimeUs, liveWindowDurationUs, nowUs, adSequencePattern),
        adSequencePattern);
  }

  @Test
  public void newInstance_adSequencePattern_correctPeriodTypesFromStartOfAvailability() {
    FakeMultiPeriodLiveTimeline timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 120_000_000L,
            /* nowUs= */ 120_000_000L,
            new boolean[] {false, true, true, true},
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    Timeline.Period period = new Timeline.Period();
    Timeline.Window window = new Timeline.Window();

    assertThat(timeline.getPeriodCount()).isEqualTo(8);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-0[c]");
    assertThat(timeline.getPeriod(1, period).uid).isEqualTo("uid-1[a]");
    assertThat(timeline.getPeriod(2, period).uid).isEqualTo("uid-2[a]");
    assertThat(timeline.getPeriod(3, period).uid).isEqualTo("uid-3[a]");
    assertThat(timeline.getPeriod(4, period).uid).isEqualTo("uid-4[c]");
    assertThat(timeline.getPeriod(5, period).uid).isEqualTo("uid-5[a]");
    assertThat(timeline.getPeriod(6, period).uid).isEqualTo("uid-6[a]");
    assertThat(timeline.getPeriod(7, period).uid).isEqualTo("uid-7[a]");

    timeline.advanceNowUs(40_000_000L);

    assertThat(timeline.getPeriodCount()).isEqualTo(8);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(40_000L);
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-2[a]");
    assertThat(timeline.getPeriod(1, period).uid).isEqualTo("uid-3[a]");
    assertThat(timeline.getPeriod(2, period).uid).isEqualTo("uid-4[c]");
    assertThat(timeline.getPeriod(3, period).uid).isEqualTo("uid-5[a]");
    assertThat(timeline.getPeriod(4, period).uid).isEqualTo("uid-6[a]");
    assertThat(timeline.getPeriod(5, period).uid).isEqualTo("uid-7[a]");
    assertThat(timeline.getPeriod(6, period).uid).isEqualTo("uid-8[c]");
    assertThat(timeline.getPeriod(7, period).uid).isEqualTo("uid-9[a]");

    timeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0L,
            /* liveWindowDurationUs= */ 220_000_000L,
            /* nowUs= */ 250_000_000L,
            new boolean[] {false, true, false, true, false},
            /* isContentTimeline= */ true,
            /* populateAds= */ false);

    assertThat(timeline.getPeriodCount()).isEqualTo(10);
    assertThat(timeline.getWindow(0, window).windowStartTimeMs).isEqualTo(30_000L);
    assertThat(timeline.getPeriod(0, period).positionInWindowUs).isEqualTo(0L);
    assertThat(timeline.getPeriod(0, period).uid).isEqualTo("uid-1[a]");
    assertThat(timeline.getPeriod(1, period).uid).isEqualTo("uid-2[c]");
    assertThat(timeline.getPeriod(2, period).uid).isEqualTo("uid-3[a]");
    assertThat(timeline.getPeriod(3, period).uid).isEqualTo("uid-4[c]");
    assertThat(timeline.getPeriod(4, period).uid).isEqualTo("uid-5[c]");
    assertThat(timeline.getPeriod(5, period).uid).isEqualTo("uid-6[a]");
    assertThat(timeline.getPeriod(6, period).uid).isEqualTo("uid-7[c]");
    assertThat(timeline.getPeriod(7, period).uid).isEqualTo("uid-8[a]");
    assertThat(timeline.getPeriod(8, period).uid).isEqualTo("uid-9[c]");
    assertThat(timeline.getPeriod(9, period).uid).isEqualTo("uid-10[c]");
  }

  private ExpectedWindow calculateExpectedWindow(
      long availabilityStartTimeUs,
      long liveWindowDurationUs,
      long nowUs,
      boolean[] adSequencePattern) {
    long windowStartTimeUs = nowUs - liveWindowDurationUs;
    long sequenceDurationUs = calculateAdSequencePatternDurationUs(adSequencePattern);
    long durationBeforeWindowStartUs = windowStartTimeUs - availabilityStartTimeUs;
    long skippedSequenceCount = durationBeforeWindowStartUs / sequenceDurationUs;
    long remainingDurationBeforeWindowUs = durationBeforeWindowStartUs % sequenceDurationUs;
    int idOfFirstPeriodInWindow = (int) (skippedSequenceCount * adSequencePattern.length);
    long lastSkippedPeriodDurationUs = 0L;
    // Skip period by period until we reach the window start.
    while (remainingDurationBeforeWindowUs > 0) {
      boolean isAd = adSequencePattern[idOfFirstPeriodInWindow++ % adSequencePattern.length];
      lastSkippedPeriodDurationUs = isAd ? AD_PERIOD_DURATION_US : PERIOD_DURATION_US;
      remainingDurationBeforeWindowUs -= lastSkippedPeriodDurationUs;
    }
    long positionOfFirstPeriodInWindowUs = 0;
    if (remainingDurationBeforeWindowUs < 0) {
      // The previous period overlaps into the window, so the window starts in the previous period.
      idOfFirstPeriodInWindow--;
      // The negative duration of the part of the period that is not overlapping the window.
      positionOfFirstPeriodInWindowUs =
          -(lastSkippedPeriodDurationUs + remainingDurationBeforeWindowUs);
    }
    long durationOfFirstPeriodInWindowUs =
        adSequencePattern[idOfFirstPeriodInWindow % adSequencePattern.length]
            ? AD_PERIOD_DURATION_US
            : PERIOD_DURATION_US;
    long durationInWindowUs =
        remainingDurationBeforeWindowUs == 0
            ? durationOfFirstPeriodInWindowUs
            : -remainingDurationBeforeWindowUs;
    int idOfLastPeriodInWindow = idOfFirstPeriodInWindow;
    while (durationInWindowUs < liveWindowDurationUs) {
      boolean isAd = adSequencePattern[++idOfLastPeriodInWindow % adSequencePattern.length];
      durationInWindowUs += isAd ? AD_PERIOD_DURATION_US : PERIOD_DURATION_US;
    }
    return new ExpectedWindow(
        idOfFirstPeriodInWindow, idOfLastPeriodInWindow, positionOfFirstPeriodInWindowUs);
  }

  private void assertExpectedWindow(
      Timeline timeline, ExpectedWindow expectedWindow, boolean[] adSequencePattern) {
    Timeline.Period period = new Timeline.Period();
    assertThat(timeline.getPeriodCount())
        .isEqualTo(expectedWindow.idOfLastPeriod - expectedWindow.idOfFirstPeriod + 1);
    long positionInWindowUs = expectedWindow.positionOfFirstPeriodInWindowUs;
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      int id = expectedWindow.idOfFirstPeriod + i;
      boolean isAd = adSequencePattern[id % adSequencePattern.length];
      assertThat(timeline.getPeriod(i, period).id).isEqualTo(id);
      assertThat(timeline.getPeriod(i, period).uid)
          .isEqualTo("uid-" + id + "[" + (isAd ? "a" : "c") + "]");
      assertThat(timeline.getPeriod(i, period).positionInWindowUs).isEqualTo(positionInWindowUs);
      positionInWindowUs += isAd ? AD_PERIOD_DURATION_US : PERIOD_DURATION_US;
    }
  }

  private static class ExpectedWindow {

    private final int idOfFirstPeriod;
    private final int idOfLastPeriod;
    private final long positionOfFirstPeriodInWindowUs;

    /** Creates an instance. */
    public ExpectedWindow(
        int idOfFirstPeriod, int idOfLastPeriod, long positionOfFirstPeriodInWindowUs) {
      this.idOfFirstPeriod = idOfFirstPeriod;
      this.idOfLastPeriod = idOfLastPeriod;
      this.positionOfFirstPeriodInWindowUs = positionOfFirstPeriodInWindowUs;
    }
  }
}

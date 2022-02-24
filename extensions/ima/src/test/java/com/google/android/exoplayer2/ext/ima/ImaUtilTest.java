/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima;

import static com.google.android.exoplayer2.ext.ima.ImaUtil.getAdGroupAndIndexInMultiPeriodWindow;
import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US;
import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ImaUtil}. */
@RunWith(AndroidJUnit4.class)
public class ImaUtilTest {

  @Test
  public void splitAdPlaybackStateForPeriods_emptyTimeline_emptyMapOfAdPlaybackStates() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "", 0, 20_000, C.TIME_END_OF_SOURCE);

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, Timeline.EMPTY);

    assertThat(adPlaybackStates).isEmpty();
  }

  @Test
  public void splitAdPlaybackStateForPeriods_singlePeriod_doesNotSplit() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "", 0, 20_000, C.TIME_END_OF_SOURCE);
    FakeTimeline singlePeriodTimeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, singlePeriodTimeline);

    assertThat(adPlaybackStates).hasSize(1);
    assertThat(adPlaybackStates).containsEntry(new Pair<>(0L, 0), adPlaybackState);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_livePlaceholder_isIgnored() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "", C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline singlePeriodTimeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(/* periodCount= */ 3, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, singlePeriodTimeline);

    assertThat(adPlaybackStates).hasSize(3);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_noAds_splitToEmptyAdPlaybackStates() {
    AdPlaybackState adPlaybackState = new AdPlaybackState(/* adsId= */ "adsId");
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(/* periodCount= */ 11, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(11);
    for (AdPlaybackState periodAdPlaybackState : adPlaybackStates.values()) {
      assertThat(periodAdPlaybackState.adsId).isEqualTo("adsId");
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(0);
    }
  }

  @Test
  public void splitAdPlaybackStateForPeriods_twoPrerollAds_splitToFirstTwoPeriods() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", /* adGroupTimesUs... */ 0)
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0,
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs,
                periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    for (int i = 0; i < 2; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.adsId).isEqualTo("adsId");
      assertThat(periodAdPlaybackState.getAdGroup(0).timeUs).isEqualTo(0);
      assertThat(periodAdPlaybackState.getAdGroup(0).isServerSideInserted).isTrue();
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      int adDurationUs = i == 0 ? 125_500_000 : 2_500_000;
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(adDurationUs);
    }
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_onePrerollAdGroup_splitToFirstThreePeriods() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", /* adGroupTimesUs... */ 0)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0,
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 3 * periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    for (int i = 0; i < 3; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      int adDurationUs = i == 0 ? 125_500_000 : 2_500_000;
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(adDurationUs);
    }
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_twoMidrollAds_splitToMiddleTwoPeriods() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId", DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 0, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    for (int i = 1; i < 3; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).timeUs).isEqualTo(0);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(2_500_000);
    }
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_oneMidrollAdGroupOneAd_adSpansTwoPeriods() {
    int periodCount = 5;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId", DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 2 * periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    for (int i = 1; i < 3; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(2_000_000);
    }
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_oneMidrollAdGroupTwoAds_eachAdSplitsToOnePeriod() {
    int periodCount = 5;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId", DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 0, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    for (int i = 1; i < 3; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(2_000_000);
    }
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).adGroupCount).isEqualTo(0);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_twoPostrollAds_splitToLastTwoPeriods() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId",
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 2 * periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 0, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).adGroupCount).isEqualTo(0);
    for (int i = 2; i < periodCount; i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(2_500_000);
    }
  }

  @Test
  public void splitAdPlaybackStateForPeriods_onePostrollAdGroup_splitToLastThreePeriods() {
    int periodCount = 7;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId",
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 4 * periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 3 * periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
    for (int i = 4; i < adPlaybackStates.size(); i++) {
      Pair<Long, Integer> periodUid = new Pair<>(0L, i);
      AdPlaybackState periodAdPlaybackState = adPlaybackStates.get(periodUid);
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs).hasLength(1);
      assertThat(periodAdPlaybackState.getAdGroup(0).durationsUs[0]).isEqualTo(periodDurationUs);
    }
  }

  @Test
  public void splitAdPlaybackStateForPeriods_preMidAndPostrollAdGroup_splitCorrectly() {
    int periodCount = 11;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", 0, (2 * periodDurationUs), (5 * periodDurationUs))
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0,
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs,
                periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withAdCount(/* adGroupIndex= */ 1, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 1, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, true)
            .withAdCount(/* adGroupIndex= */ 2, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 2, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 5)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 6)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 7)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 8)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 9)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 10)).adGroupCount).isEqualTo(1);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_midAndPostrollAdGroup_splitCorrectly() {
    int periodCount = 9;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId",
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + (2 * periodDurationUs),
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + (5 * periodDurationUs))
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 0, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withAdCount(/* adGroupIndex= */ 1, 2)
            .withAdDurationsUs(/* adGroupIndex= */ 1, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 5)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 6)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 7)).adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 8)).adGroupCount).isEqualTo(1);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_correctAdsIdInSplitPlaybackStates() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId",
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 2 * periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 2 * periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    for (int i = 0; i < adPlaybackStates.size(); i++) {
      assertThat(adPlaybackStates.get(new Pair<>(0L, i)).adsId).isEqualTo("adsId");
    }
  }

  @Test
  public void splitAdPlaybackStateForPeriods_correctAdPlaybackStates() {
    int periodCount = 7;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", 0)
            .withAdCount(/* adGroupIndex= */ 0, periodCount)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0, /* adDurationsUs...= */
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs,
                periodDurationUs,
                periodDurationUs,
                periodDurationUs,
                periodDurationUs,
                periodDurationUs,
                periodDurationUs)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withSkippedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1)
            .withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_SKIPPED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_ERROR);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_singleAdOfAdGroupSpansMultiplePeriods_correctState() {
    int periodCount = 8;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", 0, periodDurationUs, 2 * periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdCount(/* adGroupIndex= */ 1, 1)
            .withAdCount(/* adGroupIndex= */ 2, 1)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0, /* adDurationsUs...= */
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + (2 * periodDurationUs))
            .withAdDurationsUs(
                /* adGroupIndex= */ 1, /* adDurationsUs...= */ (2 * periodDurationUs))
            .withAdDurationsUs(
                /* adGroupIndex= */ 2, /* adDurationsUs...= */ (2 * periodDurationUs))
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 0)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 5)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 6)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 7)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
  }

  @Test
  public void splitAdPlaybackStateForPeriods_lateMidrollAdGroupStartTimeUs_adGroupIgnored() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ "adsId",
                // TODO(b/192231683) Reduce additional period duration to 1 when rounding work
                //  around removed.
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs + 2)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    for (AdPlaybackState periodAdPlaybackState : adPlaybackStates.values()) {
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(0);
    }
  }

  @Test
  public void splitAdPlaybackStateForPeriods_earlyMidrollAdGroupStartTimeUs_adGroupIgnored() {
    int periodCount = 4;
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / periodCount;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", periodDurationUs - 1)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(
                /* periodCount= */ periodCount, /* id= */ 0L));

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, timeline);

    assertThat(adPlaybackStates).hasSize(periodCount);
    for (AdPlaybackState periodAdPlaybackState : adPlaybackStates.values()) {
      assertThat(periodAdPlaybackState.adGroupCount).isEqualTo(0);
      assertThat(periodAdPlaybackState.adsId).isEqualTo("adsId");
    }
  }

  @Test
  public void expandAdGroupPlaceHolder_expandWithFirstAdInGroup_correctExpansion() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 30_000_000);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 30_000_000,
            /* adIndexInAdGroup= */ 0,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 3,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(10_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(20_000_000);
    assertThat(adGroup.durationsUs[2]).isEqualTo(0);
  }

  @Test
  public void expandAdGroupPlaceHolder_expandWithMiddleAdInGroup_correctExpansion() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 30_000_000);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 30_000_000,
            /* adIndexInAdGroup= */ 1,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 3,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(0);
    assertThat(adGroup.durationsUs[1]).isEqualTo(10_000_000);
    assertThat(adGroup.durationsUs[2]).isEqualTo(20_000_000);
  }

  @Test
  public void expandAdGroupPlaceHolder_expandWithLastAdInGroup_correctDurationWrappedAround() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 30_000_000);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 30_000_000,
            /* adIndexInAdGroup= */ 2,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 3,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(20_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(0);
    assertThat(adGroup.durationsUs[2]).isEqualTo(10_000_000);
  }

  @Test
  public void expandAdGroupPlaceHolder_expandSingleAdInAdGroup_noExpansionCorrectDuration() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 30_000_000);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 30_000_000,
            /* adIndexInAdGroup= */ 0,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 1,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(1);
    assertThat(adGroup.durationsUs[0]).isEqualTo(10_000_000);
  }

  @Test
  public void expandAdGroupPlaceHolder_singleAdInAdGroupOverLength_correctsAdDuration() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 10_000_001);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 10_000_000,
            /* adIndexInAdGroup= */ 0,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 1,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(1);
    assertThat(adGroup.durationsUs[0]).isEqualTo(10_000_000);
  }

  @Test
  public void expandAdGroupPlaceHolder_initialDurationTooLarge_overriddenWhenExpanded() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 30_000_000);

    adPlaybackState =
        ImaUtil.expandAdGroupPlaceholder(
            /* adGroupIndex= */ 0,
            /* adGroupDurationUs= */ 20_000_000,
            /* adIndexInAdGroup= */ 1,
            /* adDurationUs= */ 10_000_000,
            /* adsInAdGroupCount= */ 2,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.count).isEqualTo(2);
    assertThat(adGroup.durationsUs[0]).isEqualTo(10_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(10_000_000);
  }

  @Test
  public void insertAdDurationInAdGroup_correctDurationAndPropagation() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 10_000_000,
            20_000_000,
            0);

    adPlaybackState =
        ImaUtil.updateAdDurationInAdGroup(
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 1,
            /* adDurationUs= */ 15_000_000,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(10_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(15_000_000);
    assertThat(adGroup.durationsUs[2]).isEqualTo(5_000_000);
  }

  @Test
  public void insertAdDurationInAdGroup_insertLast_correctDurationAndPropagation() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 0,
            10_000_000,
            20_000_000);

    adPlaybackState =
        ImaUtil.updateAdDurationInAdGroup(
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 2,
            /* adDurationUs= */ 15_000_000,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(5_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(10_000_000);
    assertThat(adGroup.durationsUs[2]).isEqualTo(15_000_000);
  }

  @Test
  public void insertAdDurationInAdGroup_allDurationsSetAlready_setDurationNoPropagation() {
    AdPlaybackState adPlaybackState =
        ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState(
            AdPlaybackState.NONE,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 5_000_000,
            10_000_000,
            20_000_000);

    adPlaybackState =
        ImaUtil.updateAdDurationInAdGroup(
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 1,
            /* adDurationUs= */ 5_000_000,
            adPlaybackState);

    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(0);
    assertThat(adGroup.count).isEqualTo(3);
    assertThat(adGroup.durationsUs[0]).isEqualTo(5_000_000);
    assertThat(adGroup.durationsUs[1]).isEqualTo(5_000_000);
    assertThat(adGroup.durationsUs[2]).isEqualTo(20_000_000);
  }

  @Test
  public void getAdGroupAndIndexInMultiPeriodWindow_correctAdGroupIndexAndAdIndexInAdGroup() {
    FakeTimeline timeline =
        new FakeTimeline(
            new FakeTimeline.TimelineWindowDefinition(/* periodCount= */ 9, new Object()));
    long periodDurationUs = DEFAULT_WINDOW_DURATION_US / 9;
    // [ad, ad, content, ad, ad, ad, content, ad, ad]
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", 0, periodDurationUs, 2 * periodDurationUs)
            .withAdCount(/* adGroupIndex= */ 0, 2)
            .withAdCount(/* adGroupIndex= */ 1, 3)
            .withAdCount(/* adGroupIndex= */ 2, 2)
            .withAdDurationsUs(
                /* adGroupIndex= */ 0,
                DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + periodDurationUs,
                periodDurationUs)
            .withAdDurationsUs(
                /* adGroupIndex= */ 1, periodDurationUs, periodDurationUs, periodDurationUs)
            .withAdDurationsUs(/* adGroupIndex= */ 2, periodDurationUs, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    Pair<Integer, Integer> adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 0, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(0);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(0);

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 1, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(0);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(1);

    Assert.assertThrows(
        IllegalStateException.class,
        () ->
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 2, adPlaybackState, timeline));

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 3, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(1);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(0);

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 4, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(1);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(1);

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 5, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(1);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(2);

    Assert.assertThrows(
        IllegalStateException.class,
        () ->
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 6, adPlaybackState, timeline));

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 7, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(2);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(0);

    adGroupIndexAndAdIndexInAdGroup =
        getAdGroupAndIndexInMultiPeriodWindow(/* adPeriodIndex= */ 8, adPlaybackState, timeline);
    assertThat(adGroupIndexAndAdIndexInAdGroup.first).isEqualTo(2);
    assertThat(adGroupIndexAndAdIndexInAdGroup.second).isEqualTo(1);
  }
}

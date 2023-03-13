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
package androidx.media3.exoplayer.ima;

import static androidx.media3.common.AdPlaybackState.AD_STATE_AVAILABLE;
import static androidx.media3.common.AdPlaybackState.AD_STATE_ERROR;
import static androidx.media3.common.AdPlaybackState.AD_STATE_PLAYED;
import static androidx.media3.common.AdPlaybackState.AD_STATE_SKIPPED;
import static androidx.media3.common.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static androidx.media3.exoplayer.ima.ImaUtil.addLiveAdBreak;
import static androidx.media3.exoplayer.ima.ImaUtil.getAdGroupAndIndexInMultiPeriodWindow;
import static androidx.media3.exoplayer.ima.ImaUtil.splitAdGroup;
import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.AD_PERIOD_DURATION_US;
import static androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US;
import static androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.Window;
import androidx.media3.exoplayer.source.ads.ServerSideAdInsertionUtil;
import androidx.media3.test.utils.FakeMultiPeriodLiveTimeline;
import androidx.media3.test.utils.FakeTimeline;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ImaUtil}. */
@RunWith(AndroidJUnit4.class)
public class ImaUtilTest {

  @Test
  public void splitAdPlaybackStateForPeriods_emptyTimeline_throwsIllegalArgumentException() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "", 0, 20_000, C.TIME_END_OF_SOURCE);

    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, Timeline.EMPTY));
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
        .isEqualTo(AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_SKIPPED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_ERROR);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_UNAVAILABLE);
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
        .isEqualTo(AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 1)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 2)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 3)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 4)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_PLAYED);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 5)).adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 6)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_UNAVAILABLE);
    assertThat(adPlaybackStates.get(new Pair<>(0L, 7)).getAdGroup(/* adGroupIndex= */ 0).states[0])
        .isEqualTo(AD_STATE_UNAVAILABLE);
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
  public void
      splitAdPlaybackStateForPeriods_liveAdGroupStartedAndMovedOutOfWindow_splitCorrectly() {
    long adPeriodDurationUs = AD_PERIOD_DURATION_US;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    // First window start time (UNIX epoch): 50_000_000
    // Period durations: content=30_000_000, ad=10_000_000
    FakeMultiPeriodLiveTimeline liveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0,
            /* liveWindowDurationUs= */ 100_000_000,
            /* nowUs= */ 150_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    // Ad event received from SDK around 130s.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 130_000_000,
            adPeriodDurationUs,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * adPeriodDurationUs,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);

    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);

    // Move 1us forward to include the first us of the next period.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(adPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);

    // Move 29_999_999us forward to the last us of the first content period.
    liveTimeline.advanceNowUs(/* durationUs= */ 29_999_998L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(79_999L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-29_999_999L);
    assertThat(adPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);

    // Move 1us forward to the drop the first content period at the beginning of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(80_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);

    // Move 1us forward to add the next ad period at the end of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(80_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(1);

    // Mark previous ad group as played.
    Pair<Integer, Integer> adGroupAndAdIndex =
        ImaUtil.getAdGroupAndIndexInMultiPeriodWindow(
            /* adPeriodIndex= */ 3, adPlaybackState, liveTimeline);
    adPlaybackState =
        adPlaybackState.withPlayedAd(
            /* adGroupIndex= */ adGroupAndAdIndex.first,
            /* adIndexInAdGroup= */ adGroupAndAdIndex.second);
    adGroupAndAdIndex =
        ImaUtil.getAdGroupAndIndexInMultiPeriodWindow(
            /* adPeriodIndex= */ 4, adPlaybackState, liveTimeline);
    adPlaybackState =
        adPlaybackState.withPlayedAd(
            /* adGroupIndex= */ adGroupAndAdIndex.first,
            /* adIndexInAdGroup= */ adGroupAndAdIndex.second);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    AdPlaybackState.AdGroup adGroup =
        adPlaybackStates.get("uid-7[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.getFirstAdIndexToPlay()).isEqualTo(0);
    assertThat(adGroup.states[0]).isEqualTo(AD_STATE_PLAYED);
    adGroup = adPlaybackStates.get("uid-8[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.getFirstAdIndexToPlay()).isEqualTo(0);
    assertThat(adGroup.states[0]).isEqualTo(AD_STATE_PLAYED);

    // Move 9_999_998us forward to the last us of the first ad period. Same periods, shifted.
    liveTimeline.advanceNowUs(/* durationUs= */ 9_999_998L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(89_999L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-9_999_999L);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(1);

    // Ad event received from SDK around 180s.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 180_000_000,
            adPeriodDurationUs,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * adPeriodDurationUs,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(89_999L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-9_999_999L);
    assertThat(adPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);

    // Move 1us forward to drop the first ad from the beginning of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(90_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);

    // Move 1us forward to add the next ad period at the end of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(90_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(adPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-6[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-11[a]").adGroupCount).isEqualTo(2);

    // Move 39_999_999us to drop an ad and a content period at the beginning of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 39_999_999L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(130_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-7[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-11[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-12[c]").adGroupCount).isEqualTo(1);

    // Move 10_000_000us to drop an ad (incomplete ad group at the beginning of the window).
    liveTimeline.advanceNowUs(/* durationUs= */ 10_000_000L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(140_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-11[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-12[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-13[a]").adGroupCount).isEqualTo(1);

    // Mark previous ad group as played.
    adGroupAndAdIndex =
        ImaUtil.getAdGroupAndIndexInMultiPeriodWindow(
            /* adPeriodIndex= */ 2, adPlaybackState, liveTimeline);
    adPlaybackState =
        adPlaybackState.withPlayedAd(
            /* adGroupIndex= */ adGroupAndAdIndex.first,
            /* adIndexInAdGroup= */ adGroupAndAdIndex.second);
    adGroupAndAdIndex =
        ImaUtil.getAdGroupAndIndexInMultiPeriodWindow(
            /* adPeriodIndex= */ 3, adPlaybackState, liveTimeline);
    adPlaybackState =
        adPlaybackState.withPlayedAd(
            /* adGroupIndex= */ adGroupAndAdIndex.first,
            /* adIndexInAdGroup= */ adGroupAndAdIndex.second);
    // Ad event received from SDK around 230s for ad period with unknown duration.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 230_000_000,
            adPeriodDurationUs - 1000L, // SDK fallback duration.
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * adPeriodDurationUs - 1000,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(6);
    assertThat(adPlaybackStates).hasSize(6);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(140_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L); // Exact.
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-11[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-12[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-13[a]").adGroupCount).isEqualTo(2);
    AdPlaybackState.AdGroup actualAdGroup =
        adPlaybackStates.get("uid-13[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(actualAdGroup.count).isEqualTo(1);
    assertThat(actualAdGroup.durationsUs[0]).isEqualTo(adPeriodDurationUs - 1000L);

    // Move 1us forward to add the next ad period at the end of the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    adPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(7);
    assertThat(adPlaybackStates).hasSize(7);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(140_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(adPlaybackStates.get("uid-8[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-9[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-10[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-11[a]").adGroupCount).isEqualTo(2);
    assertThat(adPlaybackStates.get("uid-12[c]").adGroupCount).isEqualTo(1);
    assertThat(adPlaybackStates.get("uid-13[a]").adGroupCount).isEqualTo(2);
    actualAdGroup = adPlaybackStates.get("uid-13[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(actualAdGroup.count).isEqualTo(1);
    assertThat(actualAdGroup.durationsUs[0]).isEqualTo(adPeriodDurationUs);
    assertThat(adPlaybackStates.get("uid-14[a]").adGroupCount).isEqualTo(2);
  }

  @Test
  public void
      splitAdPlaybackStateForPeriods_fullAdGroupAtBeginOfWindow_adPeriodsCorrectlyDetected() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ "adsId", C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    // Window start time (UNIX epoch): 29_999_999
    // Period durations: content=30_000_000, ad=10_000_000
    FakeMultiPeriodLiveTimeline liveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0,
            /* liveWindowDurationUs= */ 30_000_000,
            /* nowUs= */ 59_999_999,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    // Ad event received from SDK around 30s.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 30_000_000,
            AD_PERIOD_DURATION_US,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * AD_PERIOD_DURATION_US,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);

    ImmutableMap<Object, AdPlaybackState> splitAdPlaybackStates =
        ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(4);
    assertThat(splitAdPlaybackStates).hasSize(4);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(29_999L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-29_999_999L);
    assertThat(splitAdPlaybackStates.get("uid-0[c]").adGroupCount).isEqualTo(1);
    assertThat(splitAdPlaybackStates.get("uid-1[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-2[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Move window start to the first microsecond of the first ad period.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(3);
    assertThat(splitAdPlaybackStates).hasSize(3);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(30_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L);
    assertThat(splitAdPlaybackStates.get("uid-1[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-2[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Move window start to the last microsecond of the first ad period.
    liveTimeline.advanceNowUs(/* durationUs= */ 9_999_999L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(3);
    assertThat(splitAdPlaybackStates).hasSize(3);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(39_999L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-9_999_999L);
    assertThat(splitAdPlaybackStates.get("uid-1[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-2[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Mark previous ad group as played.
    adPlaybackState =
        adPlaybackState
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);

    // Move first ad period out of live window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(2);
    assertThat(splitAdPlaybackStates).hasSize(2);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(40_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0L);
    assertThat(splitAdPlaybackStates.get("uid-2[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Move window start to the last microsecond of the second ad period. Same periods, shifted.
    liveTimeline.advanceNowUs(/* durationUs= */ 9_999_999L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(2);
    assertThat(splitAdPlaybackStates).hasSize(2);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L - 1L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-9_999_999L);
    assertThat(splitAdPlaybackStates.get("uid-2[a]").adGroupCount).isEqualTo(2);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Move second ad period out of live window. Only a single content period in the window.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(1);
    assertThat(splitAdPlaybackStates).hasSize(1);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(0);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);

    // Move window start 1 microsecond to require 1us microsecond of the next period.
    // Note: The ad period is now the last in the window with a duration of TIME_UNSET. Also, the ad
    // playback state doesn't know yet that the period is an ad.
    liveTimeline.advanceNowUs(/* durationUs= */ 1L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(2);
    assertThat(splitAdPlaybackStates).hasSize(2);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L);
    // TODO(bachinger): Rounding inaccuracies of 1us because windowStartTimeMs is in milliseconds.
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(splitAdPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(1);

    // The ad break arrives that tells the ad playback state about the ad in the timeline. We assert
    // that the same timeline now gets the period marked as an ad expected.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 80_000_000,
            AD_PERIOD_DURATION_US - 1000L, // SDK fallback duration.
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * AD_PERIOD_DURATION_US - 1001L,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(2);
    assertThat(splitAdPlaybackStates).hasSize(2);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(50_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-1L);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(splitAdPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(2);
    AdPlaybackState.AdGroup actualAdGroup =
        splitAdPlaybackStates.get("uid-4[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(actualAdGroup.count).isEqualTo(1);
    assertThat(actualAdGroup.durationsUs[0]).isEqualTo(AD_PERIOD_DURATION_US - 1000L);

    // Advance to make the window overlap 1 microsecond into the second ad period. Assert whether
    // both ad periods, including the last with unknown duration, are correctly marked as ad.
    liveTimeline.advanceNowUs(10_000_000L);
    splitAdPlaybackStates = ImaUtil.splitAdPlaybackStateForPeriods(adPlaybackState, liveTimeline);

    assertThat(liveTimeline.getPeriodCount()).isEqualTo(3);
    assertThat(splitAdPlaybackStates).hasSize(3);
    assertThat(liveTimeline.getWindow(0, new Window()).windowStartTimeMs).isEqualTo(60_000L);
    assertThat(liveTimeline.getPeriod(0, new Period()).positionInWindowUs).isEqualTo(-10_000_001L);
    assertThat(splitAdPlaybackStates.get("uid-3[c]").adGroupCount).isEqualTo(1);
    assertThat(splitAdPlaybackStates.get("uid-4[a]").adGroupCount).isEqualTo(2);
    actualAdGroup = splitAdPlaybackStates.get("uid-4[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(actualAdGroup.count).isEqualTo(1);
    assertThat(actualAdGroup.durationsUs[0]).isEqualTo(AD_PERIOD_DURATION_US);
    assertThat(splitAdPlaybackStates.get("uid-5[a]").adGroupCount).isEqualTo(2);
    actualAdGroup = splitAdPlaybackStates.get("uid-5[a]").getAdGroup(/* adGroupIndex= */ 0);
    assertThat(actualAdGroup.count).isEqualTo(1);
    assertThat(actualAdGroup.durationsUs[0]).isEqualTo(AD_PERIOD_DURATION_US - 1L); // SDK fallback.
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

  @Test
  public void
      getAdGroupAndIndexInMultiPeriodWindow_liveWindow_correctAdGroupIndexAndAdIndexInAdGroup() {
    FakeMultiPeriodLiveTimeline contentTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeUs= */ 0,
            /* liveWindowDurationUs= */ 100_000_000,
            /* nowUs= */ 150_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* isContentTimeline= */ true,
            /* populateAds= */ false);
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId").withLivePostrollPlaceholderAppended();
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 80_000_000,
            /* adDurationUs= */ AD_PERIOD_DURATION_US,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ AD_PERIOD_DURATION_US,
            /* totalAdsInAdPod= */ 1,
            adPlaybackState);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 90_000_000,
            /* adDurationUs= */ AD_PERIOD_DURATION_US,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ AD_PERIOD_DURATION_US,
            /* totalAdsInAdPod= */ 1,
            adPlaybackState);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 130_000_000,
            /* adDurationUs= */ AD_PERIOD_DURATION_US,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 2 * AD_PERIOD_DURATION_US,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 130_000_000,
            /* adDurationUs= */ AD_PERIOD_DURATION_US,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 2 * AD_PERIOD_DURATION_US,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    AdPlaybackState finalAdPlaybackState = adPlaybackState;

    assertThat(
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 1, adPlaybackState, contentTimeline))
        .isEqualTo(new Pair<>(0, 0));
    assertThat(
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 2, adPlaybackState, contentTimeline))
        .isEqualTo(new Pair<>(1, 0));
    assertThat(
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 4, adPlaybackState, contentTimeline))
        .isEqualTo(new Pair<>(2, 0));
    assertThat(
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 5, adPlaybackState, contentTimeline))
        .isEqualTo(new Pair<>(2, 1));
    Assert.assertThrows(
        IllegalStateException.class,
        () ->
            getAdGroupAndIndexInMultiPeriodWindow(
                /* adPeriodIndex= */ 0, finalAdPlaybackState, contentTimeline));
  }

  @Test
  public void addLiveAdBreak_threeAdsHappyPath_createsNewAdGroupAndPropagates() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    // Initial LOADED event while playing in content, makes the player advancing to the first ad
    // period: [/* adGroupIndex= */ 0, /* adIndexInAdGroup */ 0, /* nextAdGroupIndex= */ -1].
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L,
            /* adDurationUs= */ 10_000_001L,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 30_000_001L,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).isServerSideInserted).isTrue();
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 20_000_000L, 0L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(30_000_001);

    // Second load event while first ad is playing.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_001L,
            /* adDurationUs= */ 10_000_010L,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 30_000_011L,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);
    // Player advances to the second ad period:
    // [/* adGroupIndex= */ 0, /* adIndexInAdGroup */ 1, /* nextAdGroupIndex= */ -1].
    // The first ad period is marked as played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_PLAYED, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 9_999_990L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(30_000_001L);

    // Player advances to the third ad period:
    // [/* adGroupIndex= */ 0, /* adIndexInAdGroup */ 2, /* nextAdGroupIndex= */ -1].
    // The 2nd ad period is marked as played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);
    // Third LOADED event while already playing on the last ad period.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_001L + 10_000_010L,
            /* adDurationUs= */ 10_000_100L,
            /* adPositionInAdPod= */ 3,
            /* totalAdDurationUs= */ 30_000_111L,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_PLAYED, AD_STATE_PLAYED, AD_STATE_AVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 10_000_100L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(30_000_111L);

    // Additional pre-fetch LOADED event with no remaining unavailable ad slot increases ad count.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000
                + 10_000_001L
                + 10_000_010L
                + 10_000_100L,
            /* adDurationUs= */ 10_001_000L,
            /* adPositionInAdPod= */ 4,
            /* totalAdDurationUs= */ 29_001_111L,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);
    // Player advances to the content period:
    // [/* adGroupIndex= */ -1, /* adIndexInAdGroup */ -1, /* nextAdGroupIndex= */ 1].
    // The 3rd ad period is marked as played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_PLAYED, AD_STATE_PLAYED, AD_STATE_PLAYED, AD_STATE_AVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 10_000_100L, 10_001_000L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(40_001_111L);
  }

  @Test
  public void addLiveAdBreak_groupExpandsFromTwoAdsToFourAds_createsNewAdGroupAndExpands() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    // Initial LOADED event while playing in content.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L,
            /* adDurationUs= */ 10_000_001L,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 19_000_011L,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).isServerSideInserted).isTrue();
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 9_000_010L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(19_000_011);

    // Second LOADED event: switch to a ad pod with 4 ads
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_001L,
            /* adDurationUs= */ 10_000_010L,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 40_000_011L,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(
            AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 30_000_001L, 0L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(50_000_012L);

    // Third LOADED event
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_001L + 10_000_010L,
            /* adDurationUs= */ 10_000_100L,
            /* adPositionInAdPod= */ 3,
            /* totalAdDurationUs= */ 40_000_111L,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(
            AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 10_000_100L, 19999901L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(50_000_012L);

    // Last LOADED event
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L
                + 10_000_001L
                + 10_000_010L
                + 10_000_100L,
            /* adDurationUs= */ 10_001_000L,
            /* adPositionInAdPod= */ 4,
            /* totalAdDurationUs= */ 40_001_111L,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(
            AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_AVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_001L, 10_000_010L, 10_000_100L, 10_001_000L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(40_001_111);
  }

  @Test
  public void addLiveAdBreak_groupExpandsFromOneToTwoAdsAfterAdGroupCompletion_createsNewAdGroup() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    // Initial LOADED event while playing in content.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L,
            /* adDurationUs= */ 10_000_001L,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 10_000_001L,
            /* totalAdsInAdPod= */ 1,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).states).asList().containsExactly(AD_STATE_AVAILABLE);
    assertThat(adPlaybackState.getAdGroup(0).durationsUs).asList().containsExactly(10_000_001L);
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(10_000_001L);

    // Player advances to the content period:
    // [/* adGroupIndex= */ -1, /* adIndexInAdGroup */ -1, /* nextAdGroupIndex= */ 1]
    // The ad group is completely played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    // A 'late LOADED event' at the end of the completed ad group.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_001L,
            /* adDurationUs= */ 10_000_010L,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 20_000_011L,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(1).originalCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(1).timeUs).isEqualTo(123_000_000L + 10_000_001L);
    assertThat(adPlaybackState.getAdGroup(1).states).asList().containsExactly(AD_STATE_AVAILABLE);
    assertThat(adPlaybackState.getAdGroup(1).durationsUs).asList().containsExactly(10_000_010L);
    assertThat(adPlaybackState.getAdGroup(1).contentResumeOffsetUs).isEqualTo(10_000_010L);
  }

  @Test
  public void addLiveAdBreak_joinInSecondAd_createsNewAdGroupAndExpands() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    // First LOADED event arrives with position 2 like when joining during an ad.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 30_000_000,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).isServerSideInserted).isTrue();
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_000L, 20_000_000L) // Placeholder duration.
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(30_000_000L);

    // Second LOADED event overrides placeholder duration.
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_000L,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 3,
            /* totalAdDurationUs= */ 30_000_000,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_AVAILABLE);
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_000L, 10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(20_000_000L);

    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);
    // Delayed pre-fetch LOADED event in content (creates new ad group).
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000L + 10_000_000L + 10_000_000L,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 4,
            /* totalAdDurationUs= */ 30_000_000,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(1).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(1).states).asList().containsExactly(AD_STATE_AVAILABLE);
    assertThat(adPlaybackState.getAdGroup(1).durationsUs).asList().containsExactly(10_000_000L);
    assertThat(adPlaybackState.getAdGroup(1).contentResumeOffsetUs).isEqualTo(10_000_000L);
  }

  @Test
  public void splitAdGroup_singleTrailingAdInCompletedGroup_correctlySplit() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 10_000_000,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000 + 10_000_000,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 10_000_000,
            /* totalAdsInAdPod= */ 2,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000 + 10_000_000 + 10_000_000,
            /* adDurationUs= */ 15_000_000,
            /* adPositionInAdPod= */ 3,
            /* totalAdDurationUs= */ 45_000_000,
            /* totalAdsInAdPod= */ 3,
            adPlaybackState);
    adPlaybackState =
        adPlaybackState
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);
    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);

    // Split the current adGroup at ad index 2:
    // [AD_STATE_PLAYED, AD_STATE_PLAYED, AD_STATE_AVAILABLE]
    adPlaybackState =
        splitAdGroup(adGroup, /* adGroupIndex= */ 0, /* splitIndexExclusive= */ 2, adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(2);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(20_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).durationsUs)
        .asList()
        .containsExactly(10_000_000L, 10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).states)
        .asList()
        .containsExactly(AD_STATE_PLAYED, AD_STATE_PLAYED);
    assertThat(adPlaybackState.getAdGroup(1).timeUs).isEqualTo(123_000_000 + 20_000_000);
    assertThat(adPlaybackState.getAdGroup(1).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(1).originalCount).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(1).contentResumeOffsetUs).isEqualTo(15_000_000L);
    assertThat(adPlaybackState.getAdGroup(1).durationsUs).asList().containsExactly(15_000_000L);
    assertThat(adPlaybackState.getAdGroup(1).states).asList().containsExactly(AD_STATE_AVAILABLE);
  }

  @Test
  public void splitAdGroup_multipleTrailingAds_correctlySplit() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 10_000_000,
            /* totalAdsInAdPod= */ 1,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000 + 10_000_000,
            /* adDurationUs= */ 20_000_000,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 100_000_000,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000 + 10_000_000 + 10_000_000,
            /* adDurationUs= */ 30_000_000,
            /* adPositionInAdPod= */ 3,
            /* totalAdDurationUs= */ 100_000_000,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);

    // Split the current adGroup at ad index 1:
    // [AD_STATE_PLAYED, AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE]
    adPlaybackState =
        splitAdGroup(adGroup, /* adGroupIndex= */ 0, /* splitIndexExclusive= */ 1, adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).durationsUs).asList().containsExactly(10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).states).asList().containsExactly(AD_STATE_PLAYED);
    assertThat(adPlaybackState.getAdGroup(1).timeUs).isEqualTo(123_000_000 + 10_000_000);
    assertThat(adPlaybackState.getAdGroup(1).count).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).contentResumeOffsetUs).isEqualTo(100_000_000L);
    assertThat(adPlaybackState.getAdGroup(1).durationsUs)
        .asList()
        .containsExactly(20_000_000L, 30_000_000L, 50_000_000L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(1).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
  }

  @Test
  public void splitAdGroup_lastAdWithZeroDuration_correctlySplit() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000,
            /* adDurationUs= */ 10_000_000,
            /* adPositionInAdPod= */ 1,
            /* totalAdDurationUs= */ 10_000_000,
            /* totalAdsInAdPod= */ 1,
            adPlaybackState);
    adPlaybackState =
        addLiveAdBreak(
            /* currentContentPeriodPositionUs= */ 123_000_000 + 10_000_000,
            /* adDurationUs= */ 20_000_000,
            /* adPositionInAdPod= */ 2,
            /* totalAdDurationUs= */ 100_000_000,
            /* totalAdsInAdPod= */ 4,
            adPlaybackState);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ 0);

    // Split the current adGroup at ad index 1:
    // [AD_STATE_PLAYED, AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE]
    adPlaybackState =
        splitAdGroup(adGroup, /* adGroupIndex= */ 0, /* splitIndexExclusive= */ 1, adPlaybackState);

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(0).timeUs).isEqualTo(123_000_000);
    assertThat(adPlaybackState.getAdGroup(0).count).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(0).originalCount).isEqualTo(4);
    assertThat(adPlaybackState.getAdGroup(0).contentResumeOffsetUs).isEqualTo(10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).durationsUs).asList().containsExactly(10_000_000L);
    assertThat(adPlaybackState.getAdGroup(0).states).asList().containsExactly(AD_STATE_PLAYED);
    assertThat(adPlaybackState.getAdGroup(1).timeUs).isEqualTo(123_000_000 + 10_000_000);
    assertThat(adPlaybackState.getAdGroup(1).count).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).originalCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(1).contentResumeOffsetUs).isEqualTo(100_000_000L);
    assertThat(adPlaybackState.getAdGroup(1).durationsUs)
        .asList()
        .containsExactly(20_000_000L, 80_000_000L, 0L)
        .inOrder();
    assertThat(adPlaybackState.getAdGroup(1).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_UNAVAILABLE, AD_STATE_UNAVAILABLE)
        .inOrder();
  }
}

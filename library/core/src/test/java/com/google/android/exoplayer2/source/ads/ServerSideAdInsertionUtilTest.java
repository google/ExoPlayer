/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getAdCountInGroup;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getMediaPeriodPositionUsForAd;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getMediaPeriodPositionUsForContent;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getStreamPositionUsForAd;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getStreamPositionUsForContent;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ServerSideAdInsertionUtil}. */
@RunWith(AndroidJUnit4.class)
public final class ServerSideAdInsertionUtilTest {

  private static final Object ADS_ID = new Object();

  @Test
  public void addAdGroupToAdPlaybackState_insertsCorrectAdGroupData() {
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 1, C.TIME_END_OF_SOURCE)
            .withRemovedAdGroupCount(2);

    // stream: 0-- content --4300-- ad1 --4500-- content
    // content timeline: 0-4300 - [ad1] - 4700-end
    state =
        addAdGroupToAdPlaybackState(
            state,
            /* fromPositionUs= */ 4300,
            /* contentResumeOffsetUs= */ 400,
            /* adDurationsUs...= */ 200);

    assertThat(state)
        .isEqualTo(
            new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 0, 4300, C.TIME_END_OF_SOURCE)
                .withRemovedAdGroupCount(2)
                .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
                .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 400)
                .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 200));

    // stream: 0-- content --2100-- ad1 --2400-- content --4300-- ad2 --4500-- content
    // content timeline: 0-2100 - [ad1] - 2100-4000 - [ad2] - 4400-end
    state =
        addAdGroupToAdPlaybackState(
            state,
            /* fromPositionUs= */ 2100,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 300);

    assertThat(state)
        .isEqualTo(
            new AdPlaybackState(
                    ADS_ID, /* adGroupTimesUs...= */ 0, 0, 2100, 4000, C.TIME_END_OF_SOURCE)
                .withRemovedAdGroupCount(2)
                .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
                .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 3, /* isServerSideInserted= */ true)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 3, /* contentResumeOffsetUs= */ 400)
                .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 300)
                .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 200));

    // stream: 0-- ad1 --100-- content --2100-- ad2 --2400-- content --4300-- ad3 --4500-- content
    // content timeline: 0 - [ad1] - 50-2050 -[ad2] - 2050-3950 - [ad3] - 4350-end
    state =
        addAdGroupToAdPlaybackState(
            state,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 50,
            /* adDurationsUs...= */ 100);

    assertThat(state)
        .isEqualTo(
            new AdPlaybackState(
                    ADS_ID, /* adGroupTimesUs...= */ 0, 0, 0, 2050, 3950, C.TIME_END_OF_SOURCE)
                .withRemovedAdGroupCount(2)
                .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 1)
                .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 3, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 4, /* isServerSideInserted= */ true)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 50)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
                .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 100)
                .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 300)
                .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 200));

    // stream: 0-- ad1 --100-- c --2100-- ad2 --2400-- c --4300-- ad3 --4500-- c --5000-- ad4 --6000
    // content timeline: 0 - [ad1] - 50-2050 -[ad2] - 2050-3950 - [ad3] - 4350-4850 - [ad4] - 4850
    state =
        addAdGroupToAdPlaybackState(
            state,
            /* fromPositionUs= */ 5000,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ 1000);

    assertThat(state)
        .isEqualTo(
            new AdPlaybackState(
                    ADS_ID, /* adGroupTimesUs...= */
                    0,
                    0,
                    0,
                    2050,
                    3950,
                    4850,
                    C.TIME_END_OF_SOURCE)
                .withRemovedAdGroupCount(2)
                .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 5, /* adCount= */ 1)
                .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 3, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 4, /* isServerSideInserted= */ true)
                .withIsServerSideInserted(/* adGroupIndex= */ 5, /* isServerSideInserted= */ true)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 50)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
                .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 100)
                .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 300)
                .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 200)
                .withAdDurationsUs(/* adGroupIndex= */ 5, /* adDurationsUs...= */ 1000));
  }

  @Test
  public void addAdGroupToAdPlaybackState_emptyLeadingAds_markedAsSkipped() {
    AdPlaybackState state = new AdPlaybackState(ADS_ID);

    state =
        addAdGroupToAdPlaybackState(
            state,
            /* fromPositionUs= */ 0,
            /* contentResumeOffsetUs= */ 50_000,
            /* adDurationsUs...= */ 0,
            0,
            10_000,
            40_000,
            0);

    AdPlaybackState.AdGroup adGroup = state.getAdGroup(/* adGroupIndex= */ 0);
    assertThat(adGroup.durationsUs[0]).isEqualTo(0);
    assertThat(adGroup.states[0]).isEqualTo(AdPlaybackState.AD_STATE_SKIPPED);
    assertThat(adGroup.durationsUs[1]).isEqualTo(0);
    assertThat(adGroup.states[1]).isEqualTo(AdPlaybackState.AD_STATE_SKIPPED);
    assertThat(adGroup.durationsUs[2]).isEqualTo(10_000);
    assertThat(adGroup.states[2]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(adGroup.durationsUs[4]).isEqualTo(0);
    assertThat(adGroup.states[4]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(stream(adGroup.durationsUs).sum()).isEqualTo(50_000);
  }

  @Test
  public void getStreamPositionUsForAd_returnsCorrectPositions() {
    // stream: 0-- ad1 --200-- content --2100-- ad2 --2300-- content --4300-- ad3 --4500-- content
    // content timeline: 0 - [ad1] - 100-2000 -[ad2] - 2000-4000 - [ad3] - 4400-end
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 0, 0, 2000, 4000)
            .withRemovedAdGroupCount(2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 3)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 100)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 3, /* contentResumeOffsetUs= */ 0)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 150, 50)
            .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 200)
            .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 50, 50, 100);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(0);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(100);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 200, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(200);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ -50, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(100);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(150);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(250);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ -50, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(2050);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(2100);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 200, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(2300);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 300, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(2400);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ -50, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(4250);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(4300);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(4400);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ -50, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(4300);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(4350);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(4450);

    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ -50, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(4350);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 50, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(4450);
    assertThat(
            getStreamPositionUsForAd(
                /* positionUs= */ 150, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(4550);
  }

  @Test
  public void getMediaPeriodPositionUsForAd_returnsCorrectPositions() {
    // stream: 0-- ad1 --200-- content --2100-- ad2 --2300-- content --4300-- ad3 --4500-- content
    // content timeline: 0 - [ad1] - 100-2000 -[ad2] - 2000-4000 - [ad3] - 4400-end
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 0, 0, 2000, 4000)
            .withRemovedAdGroupCount(2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 3)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 100)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 3, /* contentResumeOffsetUs= */ 0)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 150, 50)
            .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 200)
            .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 50, 50, 100);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 0, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(0);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(100);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 100, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(-50);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 200, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(200);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 200, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(50);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 300, /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(150);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 2000, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(-100);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 2100, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(0);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 2300, /* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(200);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4300, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(0);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4300, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(-50);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4300, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(-100);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4400, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 0, state))
        .isEqualTo(100);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4400, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(50);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4400, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(0);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4500, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(150);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4500, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(100);

    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4700, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 1, state))
        .isEqualTo(350);
    assertThat(
            getMediaPeriodPositionUsForAd(
                /* positionUs= */ 4700, /* adGroupIndex= */ 4, /* adIndexInAdGroup= */ 2, state))
        .isEqualTo(300);
  }

  @Test
  public void getStreamPositionUsForContent_returnsCorrectPositions() {
    // stream: 0-- ad1 --200-- content --2100-- ad2 --2300-- content --4300-- ad3 --4500-- content
    // content timeline: 0 - [ad1] - 100-2000 -[ad2] - 2000-4000 - [ad3] - 4400-end
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 0, 0, 2000, 4000)
            .withRemovedAdGroupCount(2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 3)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 100)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 3, /* contentResumeOffsetUs= */ 0)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 150, 50)
            .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 200)
            .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 50, 50, 100);

    assertThat(getStreamPositionUsForContent(/* positionUs= */ 0, /* nextAdGroupIndex= */ 2, state))
        .isEqualTo(0);
    assertThat(getStreamPositionUsForContent(/* positionUs= */ 0, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(100);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 0, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(100);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 50, /* nextAdGroupIndex= */ 2, state))
        .isEqualTo(50);
    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 50, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(150);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 50, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(150);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 100, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(200);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 100, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(200);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 1999, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(2099);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 1999, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(2099);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 2000, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(2100);
    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 2000, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(2300);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 2000, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(2300);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 3999, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(4299);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 3999, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4299);

    assertThat(
            getStreamPositionUsForContent(/* positionUs= */ 4000, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(4300);
    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 4000, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4300);

    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 4200, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4300);

    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 4300, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4400);

    assertThat(
            getStreamPositionUsForContent(
                /* positionUs= */ 4400, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4500);
  }

  @Test
  public void getMediaPeriodPositionUsForContent_returnsCorrectPositions() {
    // stream: 0-- ad1 --200-- content --2100-- ad2 --2300-- content --4300-- ad3 --4500-- content
    // content timeline: 0 - [ad1] - 100-2000 -[ad2] - 2000-4000 - [ad3] - 4400-end
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 0, 0, 2000, 4000)
            .withRemovedAdGroupCount(2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 3)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 100)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 3, /* contentResumeOffsetUs= */ 0)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 4, /* contentResumeOffsetUs= */ 400)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 150, 50)
            .withAdDurationsUs(/* adGroupIndex= */ 3, /* adDurationsUs...= */ 200)
            .withAdDurationsUs(/* adGroupIndex= */ 4, /* adDurationsUs...= */ 50, 50, 100);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 0, /* nextAdGroupIndex= */ 2, state))
        .isEqualTo(0);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 0, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(0);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 0, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(0);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 100, /* nextAdGroupIndex= */ 2, state))
        .isEqualTo(100);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 100, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(0);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 100, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(0);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 200, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(100);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 200, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(100);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2099, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(1999);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2099, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(1999);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2100, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(2000);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2100, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(2000);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2100, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(2000);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2300, /* nextAdGroupIndex= */ 3, state))
        .isEqualTo(2200);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2300, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(2000);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 2300, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(2000);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4299, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(3999);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4299, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(3999);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4300, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(4000);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4300, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4200);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4500, /* nextAdGroupIndex= */ 4, state))
        .isEqualTo(4200);
    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4500, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4400);

    assertThat(
            getMediaPeriodPositionUsForContent(
                /* positionUs= */ 4700, /* nextAdGroupIndex= */ C.INDEX_UNSET, state))
        .isEqualTo(4600);
  }

  @Test
  public void getAdCountInGroup_withUnsetCount_returnsZero() {
    AdPlaybackState state = new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 2000);

    assertThat(getAdCountInGroup(state, /* adGroupIndex= */ 0)).isEqualTo(0);
    assertThat(getAdCountInGroup(state, /* adGroupIndex= */ 1)).isEqualTo(0);
  }

  @Test
  public void getAdCountInGroup_withSetCount_returnsCount() {
    AdPlaybackState state =
        new AdPlaybackState(ADS_ID, /* adGroupTimesUs...= */ 0, 2000)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 4)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 6);

    assertThat(getAdCountInGroup(state, /* adGroupIndex= */ 0)).isEqualTo(4);
    assertThat(getAdCountInGroup(state, /* adGroupIndex= */ 1)).isEqualTo(6);
  }
}

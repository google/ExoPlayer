/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US;
import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FakeTimeline}. */
@RunWith(AndroidJUnit4.class)
public class FakeTimelineTest {

  @Test
  public void createMultiPeriodAdTimeline_firstPeriodIsAd() {
    Timeline.Window window = new Timeline.Window();
    Timeline.Period period = new Timeline.Period();
    Object windowId = new Object();
    int numberOfPlayedAds = 2;
    FakeTimeline timeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            windowId,
            numberOfPlayedAds,
            /* isAdPeriodFlags...= */ true,
            false,
            true,
            true,
            true,
            false,
            true,
            true);

    assertThat(timeline.getWindowCount()).isEqualTo(1);
    assertThat(timeline.getPeriodCount()).isEqualTo(8);
    // Assert content periods and window duration.
    Timeline.Period contentPeriod1 = timeline.getPeriod(/* periodIndex= */ 1, period);
    Timeline.Period contentPeriod5 = timeline.getPeriod(/* periodIndex= */ 5, period);
    assertThat(contentPeriod1.durationUs).isEqualTo(DEFAULT_WINDOW_DURATION_US / 8);
    assertThat(contentPeriod5.durationUs).isEqualTo(DEFAULT_WINDOW_DURATION_US / 8);
    assertThat(contentPeriod1.getAdGroupCount()).isEqualTo(0);
    assertThat(contentPeriod5.getAdGroupCount()).isEqualTo(0);
    timeline.getWindow(/* windowIndex= */ 0, window);
    assertThat(window.uid).isEqualTo(windowId);
    assertThat(window.durationUs).isEqualTo(DEFAULT_WINDOW_DURATION_US);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US);
    // Assert ad periods.
    int[] adIndices = {0, 2, 3, 4, 6};
    int adCounter = 0;
    for (int periodIndex : adIndices) {
      Timeline.Period adPeriod = timeline.getPeriod(periodIndex, period);
      assertThat(adPeriod.isServerSideInsertedAdGroup(0)).isTrue();
      assertThat(adPeriod.getAdGroupCount()).isEqualTo(1);
      if (adPeriod.getAdGroupCount() > 0) {
        if (adCounter < numberOfPlayedAds) {
          assertThat(adPeriod.getAdState(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
              .isEqualTo(AdPlaybackState.AD_STATE_PLAYED);
        } else {
          assertThat(adPeriod.getAdState(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
              .isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
        }
        adCounter++;
      }
      long expectedDurationUs =
          (DEFAULT_WINDOW_DURATION_US / 8)
              + (periodIndex == 0 ? DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US : 0);
      assertThat(adPeriod.durationUs).isEqualTo(expectedDurationUs);
      assertThat(adPeriod.getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
          .isEqualTo(expectedDurationUs);
    }
  }

  @Test
  public void createMultiPeriodAdTimeline_firstPeriodIsContent_correctWindowDurationUs() {
    Timeline.Window window = new Timeline.Window();
    FakeTimeline timeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            /* windowId= */ new Object(),
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            false);

    timeline.getWindow(/* windowIndex= */ 0, window);
    // Assert content periods and window duration.
    assertThat(window.durationUs).isEqualTo(DEFAULT_WINDOW_DURATION_US);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US);
  }
}

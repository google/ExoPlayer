/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit test for {@link SinglePeriodTimeline}.
 */
@RunWith(RobolectricTestRunner.class)
public final class SinglePeriodTimelineTest {

  private Window window;
  private Period period;

  @Before
  public void setUp() throws Exception {
    window = new Window();
    period = new Period();
  }

  @Test
  public void testGetPeriodPositionDynamicWindowUnknownDuration() {
    SinglePeriodTimeline timeline = new SinglePeriodTimeline(C.TIME_UNSET, false, true);
    // Should return null with any positive position projection.
    Pair<Integer, Long> position = timeline.getPeriodPosition(window, period, 0, C.TIME_UNSET, 1);
    assertThat(position).isNull();
    // Should return (0, 0) without a position projection.
    position = timeline.getPeriodPosition(window, period, 0, C.TIME_UNSET, 0);
    assertThat(position.first).isEqualTo(0);
    assertThat(position.second).isEqualTo(0);
  }

  @Test
  public void testGetPeriodPositionDynamicWindowKnownDuration() {
    long windowDurationUs = 1000;
    SinglePeriodTimeline timeline = new SinglePeriodTimeline(windowDurationUs, windowDurationUs, 0,
        0, false, true);
    // Should return null with a positive position projection beyond window duration.
    Pair<Integer, Long> position = timeline.getPeriodPosition(window, period, 0, C.TIME_UNSET,
        windowDurationUs + 1);
    assertThat(position).isNull();
    // Should return (0, duration) with a projection equal to window duration.
    position = timeline.getPeriodPosition(window, period, 0, C.TIME_UNSET, windowDurationUs);
    assertThat(position.first).isEqualTo(0);
    assertThat(position.second).isEqualTo(windowDurationUs);
    // Should return (0, 0) without a position projection.
    position = timeline.getPeriodPosition(window, period, 0, C.TIME_UNSET, 0);
    assertThat(position.first).isEqualTo(0);
    assertThat(position.second).isEqualTo(0);
  }

}

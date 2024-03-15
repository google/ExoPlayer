/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.SpeedProviderUtil.getDurationAfterSpeedProviderApplied;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.audio.SpeedProvider;
import com.google.android.exoplayer2.testutil.TestSpeedProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SpeedProviderUtil}. */
@RunWith(AndroidJUnit4.class)
public class SpeedProviderUtilTest {

  @Test
  public void getDurationAfterProcessorApplied_returnsCorrectDuration() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 120}, /* speeds= */ new float[] {3, 0.5f});

    assertThat(getDurationAfterSpeedProviderApplied(speedProvider, /* durationUs= */ 150))
        .isEqualTo(100);
  }

  @Test
  public void getDurationAfterProcessorApplied_durationOnSpeedChange_returnsCorrectDuration()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 113}, /* speeds= */ new float[] {2, 1});

    assertThat(getDurationAfterSpeedProviderApplied(speedProvider, /* durationUs= */ 113))
        .isEqualTo(57);
  }
}

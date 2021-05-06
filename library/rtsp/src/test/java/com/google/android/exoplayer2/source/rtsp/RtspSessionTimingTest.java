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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspSessionTiming}. */
@RunWith(AndroidJUnit4.class)
public class RtspSessionTimingTest {
  @Test
  public void parseTiming_withNowLiveTiming() throws Exception {
    RtspSessionTiming sessionTiming = RtspSessionTiming.parseTiming("npt=now-");
    assertThat(sessionTiming.getDurationMs()).isEqualTo(C.TIME_UNSET);
    assertThat(sessionTiming.isLive()).isTrue();
  }

  @Test
  public void parseTiming_withZeroLiveTiming() throws Exception {
    RtspSessionTiming sessionTiming = RtspSessionTiming.parseTiming("npt=0-");
    assertThat(sessionTiming.getDurationMs()).isEqualTo(C.TIME_UNSET);
    assertThat(sessionTiming.isLive()).isTrue();
  }

  @Test
  public void parseTiming_withDecimalZeroLiveTiming() throws Exception {
    RtspSessionTiming sessionTiming = RtspSessionTiming.parseTiming("npt=0.000-");
    assertThat(sessionTiming.getDurationMs()).isEqualTo(C.TIME_UNSET);
    assertThat(sessionTiming.isLive()).isTrue();
  }

  @Test
  public void parseTiming_withRangeTiming() throws Exception {
    RtspSessionTiming sessionTiming = RtspSessionTiming.parseTiming("npt=0.000-32.054");
    assertThat(sessionTiming.getDurationMs()).isEqualTo(32054);
    assertThat(sessionTiming.isLive()).isFalse();
  }

  @Test
  public void parseTiming_withInvalidRangeTiming_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> RtspSessionTiming.parseTiming("npt=10.000-2.054"));
  }
}

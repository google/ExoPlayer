/*
 * Copyright 2024 The Android Open Source Project
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

import static java.lang.Math.min;
import static java.lang.Math.round;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.SpeedProvider;

/**
 * Utilities for {@link SpeedProvider}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class SpeedProviderUtil {

  private SpeedProviderUtil() {}

  /**
   * Returns the duration of the output when the given {@link SpeedProvider} is applied given an
   * input stream with the given {@code durationUs}.
   */
  public static long getDurationAfterSpeedProviderApplied(
      SpeedProvider speedProvider, long durationUs) {
    long speedChangeTimeUs = 0;
    double outputDurationUs = 0;
    while (speedChangeTimeUs < durationUs) {
      long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(speedChangeTimeUs);
      if (nextSpeedChangeTimeUs == C.TIME_UNSET) {
        nextSpeedChangeTimeUs = Long.MAX_VALUE;
      }
      outputDurationUs +=
          (min(nextSpeedChangeTimeUs, durationUs) - speedChangeTimeUs)
              / (double) speedProvider.getSpeed(speedChangeTimeUs);
      speedChangeTimeUs = nextSpeedChangeTimeUs;
    }
    return round(outputDurationUs);
  }
}

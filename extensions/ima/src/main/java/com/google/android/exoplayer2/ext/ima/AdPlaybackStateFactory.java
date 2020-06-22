/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import java.util.Arrays;
import java.util.List;

/**
 * Static utility class for constructing {@link AdPlaybackState} instances from IMA-specific data.
 */
/* package */ final class AdPlaybackStateFactory {
  private AdPlaybackStateFactory() {}

  /**
   * Construct an {@link AdPlaybackState} from the provided {@code cuePoints}.
   *
   * @param cuePoints The cue points of the ads in seconds.
   * @return The {@link AdPlaybackState}.
   */
  public static AdPlaybackState fromCuePoints(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      // If no cue points are specified, there is a preroll ad.
      return new AdPlaybackState(/* adGroupTimesUs...= */ 0);
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = Math.round(C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return new AdPlaybackState(adGroupTimesUs);
  }
}

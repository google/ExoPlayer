/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Util.minValue;

import android.util.SparseLongArray;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.util.MediaClock;

@RequiresApi(18)
/* package */ final class TransformerMediaClock implements MediaClock {

  private final SparseLongArray trackTypeToTimeUs;
  private long minTrackTimeUs;

  public TransformerMediaClock() {
    trackTypeToTimeUs = new SparseLongArray();
  }

  /**
   * Updates the time for a given track type. The clock time is computed based on the different
   * track times.
   */
  public void updateTimeForTrackType(int trackType, long timeUs) {
    long previousTimeUs = trackTypeToTimeUs.get(trackType, /* valueIfKeyNotFound= */ C.TIME_UNSET);
    if (previousTimeUs != C.TIME_UNSET && timeUs <= previousTimeUs) {
      // Make sure that the track times are increasing and therefore that the clock time is
      // increasing. This is necessary for progress updates.
      return;
    }
    trackTypeToTimeUs.put(trackType, timeUs);
    if (previousTimeUs == C.TIME_UNSET || previousTimeUs == minTrackTimeUs) {
      minTrackTimeUs = minValue(trackTypeToTimeUs);
    }
  }

  @Override
  public long getPositionUs() {
    // Use minimum position among tracks as position to ensure that the buffered duration is
    // positive. This is also useful for controlling samples interleaving.
    return minTrackTimeUs;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {}

  @Override
  public PlaybackParameters getPlaybackParameters() {
    // Playback parameters are unknown. Set default value.
    return PlaybackParameters.DEFAULT;
  }
}

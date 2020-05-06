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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;

/**
 * @deprecated Use {@link Player#setPlaybackSpeed(float)} and {@link
 *     Player.AudioComponent#setSkipSilenceEnabled(boolean)} instead.
 */
@SuppressWarnings("deprecation")
@Deprecated
public final class PlaybackParameters {

  /** The default playback parameters: real-time playback with no silence skipping. */
  public static final PlaybackParameters DEFAULT = new PlaybackParameters(/* speed= */ 1f);

  /** The factor by which playback will be sped up. */
  public final float speed;

  private final int scaledUsPerMs;

  /**
   * Creates new playback parameters that set the playback speed.
   *
   * @param speed The factor by which playback will be sped up. Must be greater than zero.
   */
  public PlaybackParameters(float speed) {
    Assertions.checkArgument(speed > 0);
    this.speed = speed;
    scaledUsPerMs = Math.round(speed * 1000f);
  }

  /**
   * Returns the media time in microseconds that will elapse in {@code timeMs} milliseconds of
   * wallclock time.
   *
   * @param timeMs The time to scale, in milliseconds.
   * @return The scaled time, in microseconds.
   */
  public long getMediaTimeUsForPlayoutTimeMs(long timeMs) {
    return timeMs * scaledUsPerMs;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PlaybackParameters other = (PlaybackParameters) obj;
    return this.speed == other.speed;
  }

  @Override
  public int hashCode() {
    return Float.floatToRawIntBits(speed);
  }
}

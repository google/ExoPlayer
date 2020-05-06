/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;

/**
 * A {@link MediaClock} whose position advances with real time based on the playback parameters when
 * started.
 */
public final class StandaloneMediaClock implements MediaClock {

  private final Clock clock;

  private boolean started;
  private long baseUs;
  private long baseElapsedMs;
  private float playbackSpeed;
  private int scaledUsPerMs;

  /**
   * Creates a new standalone media clock using the given {@link Clock} implementation.
   *
   * @param clock A {@link Clock}.
   */
  public StandaloneMediaClock(Clock clock) {
    this.clock = clock;
    playbackSpeed = Player.DEFAULT_PLAYBACK_SPEED;
    scaledUsPerMs = getScaledUsPerMs(playbackSpeed);
  }

  /**
   * Starts the clock. Does nothing if the clock is already started.
   */
  public void start() {
    if (!started) {
      baseElapsedMs = clock.elapsedRealtime();
      started = true;
    }
  }

  /**
   * Stops the clock. Does nothing if the clock is already stopped.
   */
  public void stop() {
    if (started) {
      resetPosition(getPositionUs());
      started = false;
    }
  }

  /**
   * Resets the clock's position.
   *
   * @param positionUs The position to set in microseconds.
   */
  public void resetPosition(long positionUs) {
    baseUs = positionUs;
    if (started) {
      baseElapsedMs = clock.elapsedRealtime();
    }
  }

  @Override
  public long getPositionUs() {
    long positionUs = baseUs;
    if (started) {
      long elapsedSinceBaseMs = clock.elapsedRealtime() - baseElapsedMs;
      if (playbackSpeed == 1f) {
        positionUs += C.msToUs(elapsedSinceBaseMs);
      } else {
        // Add the media time in microseconds that will elapse in elapsedSinceBaseMs milliseconds of
        // wallclock time
        positionUs += elapsedSinceBaseMs * scaledUsPerMs;
      }
    }
    return positionUs;
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    // Store the current position as the new base, in case the playback speed has changed.
    if (started) {
      resetPosition(getPositionUs());
    }
    this.playbackSpeed = playbackSpeed;
    scaledUsPerMs = getScaledUsPerMs(playbackSpeed);
  }

  @Override
  public float getPlaybackSpeed() {
    return playbackSpeed;
  }

  private static int getScaledUsPerMs(float playbackSpeed) {
    return Math.round(playbackSpeed * 1000f);
  }
}

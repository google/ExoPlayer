/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import android.os.SystemClock;

/**
 * A simple clock for tracking the progression of media time. The clock can be started, stopped and
 * its time can be set and retrieved. When started, this clock is based on
 * {@link SystemClock#elapsedRealtime()}.
 */
/* package */ class MediaClock {

  private boolean started;

  /**
   * The media time when the clock was last set or stopped.
   */
  private long timeUs;

  /**
   * The difference between {@link SystemClock#elapsedRealtime()} and {@link #timeUs}
   * when the clock was last set or started.
   */
  private long deltaUs;

  /**
   * Starts the clock. Does nothing if the clock is already started.
   */
  public void start() {
    if (!started) {
      started = true;
      deltaUs = elapsedRealtimeMinus(timeUs);
    }
  }

  /**
   * Stops the clock. Does nothing if the clock is already stopped.
   */
  public void stop() {
    if (started) {
      timeUs = elapsedRealtimeMinus(deltaUs);
      started = false;
    }
  }

  /**
   * @param timeUs The time to set in microseconds.
   */
  public void setTimeUs(long timeUs) {
    this.timeUs = timeUs;
    deltaUs = elapsedRealtimeMinus(timeUs);
  }

  /**
   * @return The current time in microseconds.
   */
  public long getTimeUs() {
    return started ? elapsedRealtimeMinus(deltaUs) : timeUs;
  }

  private long elapsedRealtimeMinus(long microSeconds) {
    return SystemClock.elapsedRealtime() * 1000 - microSeconds;
  }

}

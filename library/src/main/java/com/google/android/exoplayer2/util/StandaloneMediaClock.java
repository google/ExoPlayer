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

import android.os.SystemClock;

/**
 * A standalone {@link MediaClock}. The clock can be started, stopped and its time can be set and
 * retrieved. When started, this clock is based on {@link SystemClock#elapsedRealtime()}.
 */
public final class StandaloneMediaClock implements MediaClock {

  private boolean started;

  /**
   * The media time(ms) on last sync.
   */
  private double lastMediaTime;

  /**
   * The {@link SystemClock#elapsedRealtime()} (ms) on last sync.
   */
  private long lastRealTime;

  /*
   * speed ratio between media time and real time
   */
  private float speed = 1.0f;

  /**
   * Starts the clock. Does nothing if the clock is already started.
   */
  public void start() {
    if (!started) {
      started = true;
      lastRealTime = SystemClock.elapsedRealtime();
    }
  }

  /**
   * Stops the clock. Does nothing if the clock is already stopped.
   */
  public void stop() {
    if (started) {
      updateMediaTime();
      started = false;
    }
  }

  public float getPlaybackSpeed() { return speed; }

  public void setPlaybackSpeed(float newSpeed) {
    updateMediaTime();
    speed = newSpeed;
  }

  /**
   * @param timeUs The position to set in microseconds.
   */
  public void setPositionUs(long timeUs) {
    lastRealTime = SystemClock.elapsedRealtime();
    lastMediaTime = timeUs / 1000.0;
  }

  @Override
  public long getPositionUs() {
    updateMediaTime();
    return (long)(lastMediaTime * 1000);
  }

  private void updateMediaTime() {
    if (!started) return;
    long realTime = SystemClock.elapsedRealtime();
    lastMediaTime = lastMediaTime + (realTime - lastRealTime) * speed;
    lastRealTime = realTime;
  }
}

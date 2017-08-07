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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.util.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake {@link Clock} implementation independent of {@link android.os.SystemClock}.
 */
public final class FakeClock implements Clock {

  private long currentTimeMs;
  private final List<Long> wakeUpTimes;

  /**
   * Create {@link FakeClock} with an arbitrary initial timestamp.
   *
   * @param initialTimeMs Initial timestamp in milliseconds.
   */
  public FakeClock(long initialTimeMs) {
    this.currentTimeMs = initialTimeMs;
    this.wakeUpTimes = new ArrayList<>();
  }

  /**
   * Advance timestamp of {@link FakeClock} by the specified duration.
   *
   * @param timeDiffMs The amount of time to add to the timestamp in milliseconds.
   */
  public synchronized void advanceTime(long timeDiffMs) {
    currentTimeMs += timeDiffMs;
    for (Long wakeUpTime : wakeUpTimes) {
      if (wakeUpTime <= currentTimeMs) {
        notifyAll();
        break;
      }
    }
  }

  @Override
  public long elapsedRealtime() {
    return currentTimeMs;
  }

  @Override
  public synchronized void sleep(long sleepTimeMs) {
    if (sleepTimeMs <= 0) {
      return;
    }
    Long wakeUpTimeMs = currentTimeMs + sleepTimeMs;
    wakeUpTimes.add(wakeUpTimeMs);
    while (currentTimeMs < wakeUpTimeMs) {
      try {
        wait();
      } catch (InterruptedException e) {
        // Ignore InterruptedException as SystemClock.sleep does too.
      }
    }
    wakeUpTimes.remove(wakeUpTimeMs);
  }

}


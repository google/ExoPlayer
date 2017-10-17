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

import android.os.Handler;
import com.google.android.exoplayer2.util.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake {@link Clock} implementation independent of {@link android.os.SystemClock}.
 */
public final class FakeClock implements Clock {

  private long currentTimeMs;
  private final List<Long> wakeUpTimes;
  private final List<HandlerPostData> handlerPosts;

  /**
   * Create {@link FakeClock} with an arbitrary initial timestamp.
   *
   * @param initialTimeMs Initial timestamp in milliseconds.
   */
  public FakeClock(long initialTimeMs) {
    this.currentTimeMs = initialTimeMs;
    this.wakeUpTimes = new ArrayList<>();
    this.handlerPosts = new ArrayList<>();
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
    for (int i = handlerPosts.size() - 1; i >= 0; i--) {
      if (handlerPosts.get(i).postTime <= currentTimeMs) {
        HandlerPostData postData = handlerPosts.remove(i);
        postData.handler.post(postData.runnable);
      }
    }
  }

  @Override
  public synchronized long elapsedRealtime() {
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

  @Override
  public synchronized void postDelayed(Handler handler, Runnable runnable, long delayMs) {
    if (delayMs <= 0) {
      handler.post(runnable);
    } else {
      handlerPosts.add(new HandlerPostData(currentTimeMs + delayMs, handler, runnable));
    }
  }

  private static final class HandlerPostData {

    public final long postTime;
    public final Handler handler;
    public final Runnable runnable;

    public HandlerPostData(long postTime, Handler handler, Runnable runnable) {
      this.postTime = postTime;
      this.handler = handler;
      this.runnable = runnable;
    }

  }

}


/*
 * Copyright (C) 2018 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.HandlerWrapper;

/**
 * {@link FakeClock} extension which automatically advances time whenever an empty message is
 * enqueued at a future time.
 *
 * <p>The clock time is advanced to the time of enqueued empty messages. The first Handler sending
 * messages at a future time will be allowed to advance time to ensure there is only one primary
 * time source at a time. This should usually be the Handler of the internal playback loop. You can
 * {@link #resetHandler() reset the handler} so that the next Handler that sends messages at a
 * future time becomes the primary time source.
 */
public final class AutoAdvancingFakeClock extends FakeClock {

  @Nullable private HandlerWrapper autoAdvancingHandler;

  /** Creates the auto-advancing clock with an initial time of 0. */
  public AutoAdvancingFakeClock() {
    this(/* initialTimeMs= */ 0);
  }

  /**
   * Creates the auto-advancing clock.
   *
   * @param initialTimeMs The initial time of the clock in milliseconds.
   */
  public AutoAdvancingFakeClock(long initialTimeMs) {
    super(initialTimeMs);
  }

  @Override
  protected synchronized boolean addHandlerMessageAtTime(
      HandlerWrapper handler, int message, long timeMs) {
    boolean result = super.addHandlerMessageAtTime(handler, message, timeMs);
    if (autoAdvancingHandler == null || autoAdvancingHandler == handler) {
      autoAdvancingHandler = handler;
      long currentTimeMs = elapsedRealtime();
      if (currentTimeMs < timeMs) {
        advanceTime(timeMs - currentTimeMs);
      }
    }
    return result;
  }

  /** Resets the internal handler, so that this clock can later be used with another handler. */
  public void resetHandler() {
    autoAdvancingHandler = null;
  }
}

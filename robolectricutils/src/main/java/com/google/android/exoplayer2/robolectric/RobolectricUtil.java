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
package com.google.android.exoplayer2.robolectric;

import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.common.base.Supplier;
import java.util.concurrent.TimeoutException;
import org.robolectric.shadows.ShadowLooper;

/** Utility methods for Robolectric-based tests. */
public final class RobolectricUtil {

  private RobolectricUtil() {}

  /**
   * The default timeout applied when calling {@link #runMainLooperUntil(Supplier)}. This timeout
   * should be sufficient for any condition using a Robolectric test.
   */
  public static final long DEFAULT_TIMEOUT_MS = 10_000;

  /**
   * Creates a {@link ConditionVariable} whose {@link ConditionVariable#block(long)} method times
   * out according to wallclock time when used in Robolectric tests.
   */
  public static ConditionVariable createRobolectricConditionVariable() {
    return new ConditionVariable(
        new SystemClock() {
          @Override
          public long elapsedRealtime() {
            // elapsedRealtime() does not advance during Robolectric test execution, so use
            // currentTimeMillis() instead. This is technically unsafe because this clock is not
            // guaranteed to be monotonic, but in practice it will work provided the clock of the
            // host machine does not change during test execution.
            return Clock.DEFAULT.currentTimeMillis();
          }
        });
  }

  /**
   * Runs tasks of the main Robolectric {@link Looper} until the {@code condition} returns {@code
   * true}.
   *
   * <p>Must be called on the main test thread.
   *
   * @param condition The condition.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS} is exceeded.
   */
  public static void runMainLooperUntil(Supplier<Boolean> condition) throws TimeoutException {
    runMainLooperUntil(condition, DEFAULT_TIMEOUT_MS, Clock.DEFAULT);
  }

  /**
   * Runs tasks of the main Robolectric {@link Looper} until the {@code condition} returns {@code
   * true}.
   *
   * <p>Must be called on the main test thread.
   *
   * @param condition The condition.
   * @param timeoutMs The timeout in milliseconds.
   * @param clock The {@link Clock} to measure the timeout.
   * @throws TimeoutException If the {@code timeoutMs timeout} is exceeded.
   */
  public static void runMainLooperUntil(Supplier<Boolean> condition, long timeoutMs, Clock clock)
      throws TimeoutException {
    runLooperUntil(Looper.getMainLooper(), condition, timeoutMs, clock);
  }

  /**
   * Runs tasks of the {@code looper} until the {@code condition} returns {@code true}.
   *
   * <p>Must be called on the thread corresponding to the {@code looper}.
   *
   * @param looper The {@link Looper}.
   * @param condition The condition.
   * @throws TimeoutException If the {@link #DEFAULT_TIMEOUT_MS} is exceeded.
   */
  public static void runLooperUntil(Looper looper, Supplier<Boolean> condition)
      throws TimeoutException {
    runLooperUntil(looper, condition, DEFAULT_TIMEOUT_MS, Clock.DEFAULT);
  }

  /**
   * Runs tasks of the {@code looper} until the {@code condition} returns {@code true}.
   *
   * <p>Must be called on the thread corresponding to the {@code looper}.
   *
   * @param looper The {@link Looper}.
   * @param condition The condition.
   * @param timeoutMs The timeout in milliseconds.
   * @param clock The {@link Clock} to measure the timeout.
   * @throws TimeoutException If the {@code timeoutMs timeout} is exceeded.
   */
  public static void runLooperUntil(
      Looper looper, Supplier<Boolean> condition, long timeoutMs, Clock clock)
      throws TimeoutException {
    if (Looper.myLooper() != looper) {
      throw new IllegalStateException();
    }
    ShadowLooper shadowLooper = shadowOf(looper);
    long timeoutTimeMs = clock.currentTimeMillis() + timeoutMs;
    while (!condition.get()) {
      if (clock.currentTimeMillis() >= timeoutTimeMs) {
        throw new TimeoutException();
      }
      shadowLooper.runOneTask();
    }
  }
}

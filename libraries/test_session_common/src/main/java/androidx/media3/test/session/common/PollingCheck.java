/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.test.session.common;

import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Utility used for testing that allows to poll for a certain condition to happen within a timeout.
 */
// It's forked from androidx.testutils.PollingCheck.
public abstract class PollingCheck {
  private static final long TIME_SLICE_MS = 50;
  private final long timeoutMs;

  /** The condition that the PollingCheck should use to proceed successfully. */
  public interface PollingCheckCondition {
    /**
     * @return Whether the polling condition has been met.
     */
    boolean canProceed() throws Exception;
  }

  private PollingCheck(long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  protected abstract boolean check() throws Exception;

  /** Start running the polling check. */
  public void run() throws Exception {
    if (check()) {
      return;
    }

    long timeoutMs = this.timeoutMs;
    while (timeoutMs > 0) {
      try {
        Thread.sleep(TIME_SLICE_MS);
      } catch (InterruptedException e) {
        throw new AssertionError("unexpected InterruptedException");
      }

      if (check()) {
        return;
      }

      timeoutMs -= TIME_SLICE_MS;
    }

    assertWithMessage("unexpected timeout").fail();
  }

  /**
   * Instantiate and start polling for a given condition.
   *
   * @param timeoutMs Timeout in milliseconds.
   * @param condition The condition to check for success.
   */
  public static void waitFor(long timeoutMs, PollingCheckCondition condition) throws Exception {
    new PollingCheck(timeoutMs) {
      @Override
      protected boolean check() throws Exception {
        return condition.canProceed();
      }
    }.run();
  }
}

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

/**
 * {@link FakeClock} extension which automatically advances time whenever an empty message is
 * enqueued at a future time.
 */
public final class AutoAdvancingFakeClock extends FakeClock {

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
    super(initialTimeMs, /* isAutoAdvancing= */ true);
  }
}

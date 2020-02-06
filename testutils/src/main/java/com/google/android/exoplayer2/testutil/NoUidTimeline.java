/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ForwardingTimeline;

/**
 * A timeline which wraps another timeline and overrides all window and period uids to 0. This is
 * useful for testing timeline equality without taking uids into account.
 */
/* package */ class NoUidTimeline extends ForwardingTimeline {

  /**
   * Creates an instance.
   *
   * @param timeline The underlying timeline.
   */
  public NoUidTimeline(Timeline timeline) {
    super(timeline);
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    timeline.getWindow(windowIndex, window, defaultPositionProjectionUs);
    window.uid = 0;
    return window;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    timeline.getPeriod(periodIndex, period, setIds);
    period.uid = 0;
    return period;
  }
}

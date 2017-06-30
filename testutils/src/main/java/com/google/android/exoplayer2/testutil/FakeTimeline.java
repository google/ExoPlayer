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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;

/**
 * Fake {@link Timeline} which can be setup to return custom {@link TimelineWindowDefinition}s.
 */
public final class FakeTimeline extends Timeline {

  /**
   * Definition used to define a {@link FakeTimeline}.
   */
  public static final class TimelineWindowDefinition {

    public final boolean isSeekable;
    public final boolean isDynamic;
    public final long durationUs;

    public TimelineWindowDefinition(boolean isSeekable, boolean isDynamic, long durationUs) {
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.durationUs = durationUs;
    }

  }

  private final TimelineWindowDefinition[] windowDefinitions;

  public FakeTimeline(TimelineWindowDefinition... windowDefinitions) {
    this.windowDefinitions = windowDefinitions;
  }

  @Override
  public int getWindowCount() {
    return windowDefinitions.length;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs) {
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    Object id = setIds ? windowIndex : null;
    return window.set(id, C.TIME_UNSET, C.TIME_UNSET, windowDefinition.isSeekable,
        windowDefinition.isDynamic, 0, windowDefinition.durationUs, windowIndex, windowIndex, 0);
  }

  @Override
  public int getPeriodCount() {
    return windowDefinitions.length;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    TimelineWindowDefinition windowDefinition = windowDefinitions[periodIndex];
    Object id = setIds ? periodIndex : null;
    return period.set(id, id, periodIndex, windowDefinition.durationUs, 0);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Integer)) {
      return C.INDEX_UNSET;
    }
    int index = (Integer) uid;
    return index >= 0 && index < windowDefinitions.length ? index : C.INDEX_UNSET;
  }

}

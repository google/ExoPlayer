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
package com.google.android.exoplayer.text.ttml;

import com.google.android.exoplayer.text.Subtitle;

import java.util.Arrays;

/**
 * A representation of a TTML subtitle.
 */
public final class TtmlSubtitle implements Subtitle {

  private final TtmlNode root;
  private final long startTimeUs;
  private final long[] eventTimesUs;

  public TtmlSubtitle(TtmlNode root, long startTimeUs) {
    this.root = root;
    this.startTimeUs = startTimeUs;
    this.eventTimesUs = root.getEventTimesUs();
  }

  @Override
  public long getStartTime() {
    return startTimeUs;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index = Arrays.binarySearch(eventTimesUs, timeUs - startTimeUs);
    index = index >= 0 ? index + 1 : ~index;
    return index < eventTimesUs.length ? index : -1;
  }

  @Override
  public int getEventTimeCount() {
    return eventTimesUs.length;
  }

  @Override
  public long getEventTime(int index) {
    return eventTimesUs[index] + startTimeUs;
  }

  @Override
  public long getLastEventTime() {
    return (eventTimesUs.length == 0 ? -1 : eventTimesUs[eventTimesUs.length - 1]) + startTimeUs;
  }

  @Override
  public String getText(long timeUs) {
    return root.getText(timeUs - startTimeUs);
  }

}

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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import java.util.Arrays;

/**
 * A representation of a WebVTT subtitle.
 */
public class WebvttSubtitle implements Subtitle {

  private final String[] cueText;
  private final long startTimeUs;
  private final long[] cueTimesUs;
  private final long[] sortedCueTimesUs;

  /**
   * @param cueText Text to be displayed during each cue.
   * @param startTimeUs The start time of the subtitle.
   * @param cueTimesUs Cue event times, where cueTimesUs[2 * i] and cueTimesUs[(2 * i) + 1] are
   *     the start and end times, respectively, corresponding to cueText[i].
   */
  public WebvttSubtitle(String[] cueText, long startTimeUs, long[] cueTimesUs) {
    this.cueText = cueText;
    this.startTimeUs = startTimeUs;
    this.cueTimesUs = cueTimesUs;
    this.sortedCueTimesUs = Arrays.copyOf(cueTimesUs, cueTimesUs.length);
    Arrays.sort(sortedCueTimesUs);
  }

  @Override
  public long getStartTime() {
    return startTimeUs;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    Assertions.checkArgument(timeUs >= 0);
    int index = Util.binarySearchCeil(sortedCueTimesUs, timeUs, false, false);
    return index < sortedCueTimesUs.length ? index : -1;
  }

  @Override
  public int getEventTimeCount() {
    return sortedCueTimesUs.length;
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkArgument(index < sortedCueTimesUs.length);
    return sortedCueTimesUs[index];
  }

  @Override
  public long getLastEventTime() {
    if (getEventTimeCount() == 0) {
      return -1;
    }
    return sortedCueTimesUs[sortedCueTimesUs.length - 1];
  }

  @Override
  public String getText(long timeUs) {
    StringBuilder stringBuilder = new StringBuilder();

    for (int i = 0; i < cueTimesUs.length; i += 2) {
      if ((cueTimesUs[i] <= timeUs) && (timeUs < cueTimesUs[i + 1])) {
        stringBuilder.append(cueText[i / 2]);
      }
    }

    int stringLength = stringBuilder.length();
    if (stringLength > 0 && stringBuilder.charAt(stringLength - 1) == '\n') {
      // Adjust the length to remove the trailing newline character.
      stringLength -= 1;
    }

    return stringLength == 0 ? null : stringBuilder.substring(0, stringLength);
  }

}

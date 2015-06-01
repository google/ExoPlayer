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
package com.google.android.exoplayer.text.tx3g;

import java.util.ArrayList;
import java.util.List;

import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.Subtitle;

/**
 * A representation of a TTML subtitle.
 */
public final class TextSubtitle implements Subtitle {
  static String TAG = "TextSubtitle";
  private final List<SubtitleData> text;

  public TextSubtitle(List<SubtitleData> text) {
    this.text = text;
  }

  @Override
  public long getStartTime() {
    return text.get(0).getStartTimePos();
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {

    int index = findTheClosed(timeUs);
    int next = (index ) < text.size() ? (index ) : -1;
    return  next;
  }

  @Override
  public int getEventTimeCount() {
    //LOG.I(TAG,"getEventTimeCount() = " + text.size());
    return text.size();
  }

  @Override
  public long getEventTime(int index) {
    if (index > text.size() - 1) return -1;

    //LOG.I(TAG,"getEventTime(" + index + ") = " + text.get(index).getStartTimePos());
    return text.get(index).getStartTimePos();
  }

  @Override
  public long getLastEventTime() {
    return text.get(0).getStartTimePos();
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    int index = findTheClosed(timeUs);
    List<Cue> list = new ArrayList<>();
    if (index == -1) return null;

    String str = text.get(index).getsubtitleText();

    list.add(new Cue(str));
    return list;
  }

  private int findTheClosed(long timeUs) {

    int length = text.size();
    for (int i = 0; i < length ; i++) {
      SubtitleData data = text.get(i);
      boolean bCheckFront = data.getStartTimePos() <= timeUs ;
      boolean bCheckEnd = false;
      if (i + 1  < length) {
        bCheckEnd = text.get(i + 1).getStartTimePos() > timeUs ;
      } else if (i + 1 == length) {
        bCheckEnd = true;
      }

      if (bCheckFront && bCheckEnd)
        return i;
    }
    return -1;
  }
}

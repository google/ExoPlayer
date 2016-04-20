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
package com.google.android.exoplayer.text;

import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.util.extensions.OutputBuffer;

import java.util.List;

/**
 * A {@link Subtitle} output from a {@link SubtitleParser}.
 */
/* package */ final class SubtitleOutputBuffer extends OutputBuffer implements Subtitle {

  private final SubtitleParser owner;

  private Subtitle subtitle;
  private long offsetUs;

  public SubtitleOutputBuffer(SubtitleParser owner) {
    this.owner = owner;
  }

  public void setOutput(long timestampUs, Subtitle subtitle, long subsampleOffsetUs) {
    this.timestampUs = timestampUs;
    this.subtitle = subtitle;
    this.offsetUs = subsampleOffsetUs == Format.OFFSET_SAMPLE_RELATIVE ? timestampUs
        : subsampleOffsetUs;
  }

  @Override
  public int getEventTimeCount() {
    return subtitle.getEventTimeCount();
  }

  @Override
  public long getEventTime(int index) {
    return subtitle.getEventTime(index) + offsetUs;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return subtitle.getNextEventTimeIndex(timeUs - offsetUs);
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return subtitle.getCues(timeUs - offsetUs);
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

  @Override
  public void clear() {
    super.clear();
    subtitle = null;
  }

}

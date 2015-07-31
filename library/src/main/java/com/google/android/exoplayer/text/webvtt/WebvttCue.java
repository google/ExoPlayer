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

import com.google.android.exoplayer.text.Cue;

import android.text.Layout.Alignment;

/**
 * A representation of a WebVTT cue.
 */
/* package */ final class WebvttCue extends Cue {

  public final long startTime;
  public final long endTime;

  public WebvttCue(CharSequence text) {
    this(Cue.UNSET_VALUE, Cue.UNSET_VALUE, text);
  }

  public WebvttCue(long startTime, long endTime, CharSequence text) {
    this(startTime, endTime, text, Cue.UNSET_VALUE, Cue.UNSET_VALUE, null, Cue.UNSET_VALUE);
  }

  public WebvttCue(long startTime, long endTime, CharSequence text, int line, int position,
      Alignment alignment, int size) {
    super(text, line, position, alignment, size);
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /**
   * Returns whether or not this cue should be placed in the default position and rolled-up with
   * the other "normal" cues.
   *
   * @return True if this cue should be placed in the default position; false otherwise.
   */
  public boolean isNormalCue() {
    return (line == UNSET_VALUE && position == UNSET_VALUE);
  }

}

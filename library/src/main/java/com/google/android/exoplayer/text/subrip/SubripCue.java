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
package com.google.android.exoplayer.text.subrip;

import com.google.android.exoplayer.text.Cue;

/**
 * A representation of a SubRip cue.
 */
/* package */ final class SubripCue extends Cue {

  public final long startTime;
  public final long endTime;

  public SubripCue(long startTime, long endTime, CharSequence text) {
    super(text);
    this.startTime = startTime;
    this.endTime = endTime;
  }

}

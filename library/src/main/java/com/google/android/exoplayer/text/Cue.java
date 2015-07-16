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

import android.text.Layout.Alignment;

/**
 * Contains information about a specific cue, including textual content and formatting data.
 */
public class Cue {

  /**
   * Used by some methods to indicate that no value is set.
   */
  public static final int UNSET_VALUE = -1;

  public final CharSequence text;

  public final int line;
  public final int position;
  public final Alignment alignment;
  public final int size;

  public Cue() {
    this(null);
  }

  public Cue(CharSequence text) {
    this(text, UNSET_VALUE, UNSET_VALUE, null, UNSET_VALUE);
  }

  public Cue(CharSequence text, int line, int position, Alignment alignment, int size) {
    this.text = text;
    this.line = line;
    this.position = position;
    this.alignment = alignment;
    this.size = size;
  }

}

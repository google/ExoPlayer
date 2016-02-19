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
package com.google.android.exoplayer.text.eia608;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import com.google.android.exoplayer.text.Cue;

public class Eia608CueBuilder {
  private SpannableStringBuilder text;
  private float position;
  private float line;
  private int rowIndex;

  public Eia608CueBuilder() {
    reset();
  }

  public Eia608CueBuilder(Eia608CueBuilder other) {
    text = other.text;
    position = other.position;
    line = other.line;
    rowIndex = other.rowIndex;
  }

  public void reset() {
    text = null;
    position = Cue.DIMEN_UNSET;
    line = Cue.DIMEN_UNSET;
    rowIndex = 15; // bottom row is the default
  }

  public Cue build() {
    return new Cue(text, Layout.Alignment.ALIGN_NORMAL,
            line, Cue.LINE_TYPE_FRACTION, Cue.ANCHOR_TYPE_START,
            position, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  public boolean isEmpty() {
    return (text == null) || (text.length() == 0);
  }

  public Eia608CueBuilder setText(SpannableStringBuilder aText) {
    text = aText;
    return this;
  }

  public void setRow(int rowIdx) {
    if ((rowIdx < 1) || (15 < rowIdx)) {
      this.line = 0.9f;
      return;
    }

    rowIndex = rowIdx; // saved for roll-up feature

    // 10% of screen is left of for safety reasons (analog overscan)
    // the leftover 80% is divided into 15 equal rows. The problem is that the font size
    // must match the row line height for this, so at the moment, I scale to 90% instead, to avoid
    // overlap of the borders around the rows.
    // -1 as the row and column indices are 1 based in the spec
    this.line = (rowIdx - 1) / 15f * 0.9f + 0.05f;
  }

  /**
   * Decrease the current row and reposition the captions to the new location
   * @return true if rolling was possible
     */
  public boolean rollUp() {
    if (rowIndex <= 1) {
      return false;
    }

    setRow(rowIndex - 1);
    return true;
  }

  public void setColumn(int columnIdx, int additionalTabs) {
    // the original standard defines 32 columns for the safe area of the screen (middle 80%)
    // but it also mentions that widescreen displays should use 42 columns. As the incoming data
    // does not know about the display size, maybe if the content uses widescreen aspect ratio,
    // than it will contain 42 columns. I never met such, but we should not forget about it...
    if (columnIdx < 1 || 32 < columnIdx) {
      this.position = 0.1f;
      return;
    }

    // -1 as the row and column indices are 1 based in the spec
    this.position = (((columnIdx - 1 + additionalTabs) / 32f) * 0.8f) + 0.1f;
  }
}

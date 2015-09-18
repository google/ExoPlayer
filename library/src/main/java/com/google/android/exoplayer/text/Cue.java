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
   * An unset position or width.
   */
  public static final float DIMEN_UNSET = -1f;
  /**
   * An unset anchor.
   */
  public static final int ANCHOR_UNSET = -1;
  /**
   * Anchors the start of an element.
   */
  public static final int ANCHOR_START = 0;
  /**
   * Anchors the middle of an element.
   */
  public static final int ANCHOR_MIDDLE = 1;
  /**
   * Anchors the end of an element.
   */
  public static final int ANCHOR_END = 2;

  /**
   * The cue text. Note the {@link CharSequence} may be decorated with styling spans.
   */
  public final CharSequence text;
  /**
   * The alignment of the cue text within the cue box.
   */
  public final Alignment textAlignment;
  /**
   * The fractional position of the {@link #lineAnchor} of the cue box in the direction orthogonal
   * to the writing direction, or {@link #DIMEN_UNSET}.
   * <p>
   * For horizontal text, this is the vertical position relative to the top of the window.
   */
  public final float line;
  /**
   * The cue box anchor positioned by {@link #line}. One of {@link #ANCHOR_START},
   * {@link #ANCHOR_MIDDLE}, {@link #ANCHOR_END} and {@link #ANCHOR_UNSET}.
   * <p>
   * For the normal case of horizontal text, {@link #ANCHOR_START}, {@link #ANCHOR_MIDDLE} and
   * {@link #ANCHOR_END} correspond to the top, middle and bottom of the cue box respectively.
   */
  public final int lineAnchor;
  /**
   * The fractional position of the {@link #positionAnchor} of the cue box in the direction
   * orthogonal to {@link #line}, or {@link #DIMEN_UNSET}.
   * <p>
   * For horizontal text, this is the horizontal position relative to the left of the window. Note
   * that positioning is relative to the left of the window even in the case of right-to-left text.
   */
  public final float position;
  /**
   * The cue box anchor positioned by {@link #position}. One of {@link #ANCHOR_START},
   * {@link #ANCHOR_MIDDLE}, {@link #ANCHOR_END} and {@link #ANCHOR_UNSET}.
   * <p>
   * For the normal case of horizontal text, {@link #ANCHOR_START}, {@link #ANCHOR_MIDDLE} and
   * {@link #ANCHOR_END} correspond to the left, middle and right of the cue box respectively.
   */
  public final int positionAnchor;
  /**
   * The fractional size of the cue box in the writing direction, or {@link #DIMEN_UNSET}.
   */
  public final float size;

  public Cue() {
    this(null);
  }

  public Cue(CharSequence text) {
    this(text, null, DIMEN_UNSET, ANCHOR_UNSET, DIMEN_UNSET, ANCHOR_UNSET, DIMEN_UNSET);
  }

  public Cue(CharSequence text, Alignment textAlignment, float line, int lineAlignment,
      float position, int positionAlignment, float size) {
    this.text = text;
    this.textAlignment = textAlignment;
    this.line = line;
    this.lineAnchor = lineAlignment;
    this.position = position;
    this.positionAnchor = positionAlignment;
    this.size = size;
  }

}

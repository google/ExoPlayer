/*
 * Copyright (C) 2021 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.text.span;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

public final class TextEmphasisSpan {
  // Bits [1:0] are used for typical mark types
  public static final int MARK_FLAG_CIRCLE = 1;
  public static final int MARK_FLAG_DOT = 2;
  public static final int MARK_FLAG_SESAME = 3;

  // Bit 2 is used for filled/open
  public static final int MARK_FLAG_FILLED = 0;
  public static final int MARK_FLAG_OPEN = 4;

  // Below are the mark style constants
  public static final int MARK_FILLED_CIRCLE = MARK_FLAG_CIRCLE | MARK_FLAG_FILLED;
  public static final int MARK_FILLED_DOT = MARK_FLAG_DOT | MARK_FLAG_FILLED;
  public static final int MARK_FILLED_SESAME = MARK_FLAG_SESAME | MARK_FLAG_FILLED;
  public static final int MARK_OPEN_CIRCLE = MARK_FLAG_CIRCLE | MARK_FLAG_OPEN;
  public static final int MARK_OPEN_DOT = MARK_FLAG_DOT | MARK_FLAG_OPEN;
  public static final int MARK_OPEN_SESAME = MARK_FLAG_SESAME | MARK_FLAG_OPEN;

  /**
   * The possible types of annotations used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #MARK_FILLED_CIRCLE}
   *   <li>{@link #MARK_FILLED_DOT}
   *   <li>{@link #MARK_FILLED_SESAME}
   *   <li>{@link #MARK_OPEN_CIRCLE}
   *   <li>{@link #MARK_OPEN_DOT}
   *   <li>{@link #MARK_OPEN_SESAME}
   * </ul>
   *
   * Note: We are intentionally excluding MARK_AUTO here since the auto value should
   * be resolved
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({MARK_FILLED_CIRCLE, MARK_FILLED_DOT, MARK_FILLED_SESAME,
      MARK_OPEN_CIRCLE, MARK_OPEN_DOT, MARK_OPEN_SESAME})
  public @interface Mark {
  }

  /**
   * The mark used to emphasis text
   */
  public @TextEmphasisSpan.Mark int mark;

  /**
   * The position of the text emphasis relative to the base text
   */
  @TextAnnotation.Position
  public final int position;


  public TextEmphasisSpan(@TextEmphasisSpan.Mark int mark,
      @TextAnnotation.Position int position) {
    this.mark = mark;
    this.position = position;
  }
}

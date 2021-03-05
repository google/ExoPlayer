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
  public static final int MARK_SHAPE_NONE = 0;
  public static final int MARK_SHAPE_CIRCLE = 1;
  public static final int MARK_SHAPE_DOT = 2;
  public static final int MARK_SHAPE_SESAME = 3;

  /**
   * The possible mark shapes that can be used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #MARK_SHAPE_NONE}
   *   <li>{@link #MARK_SHAPE_CIRCLE}
   *   <li>{@link #MARK_SHAPE_DOT}
   *   <li>{@link #MARK_SHAPE_SESAME}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({MARK_SHAPE_NONE, MARK_SHAPE_CIRCLE, MARK_SHAPE_DOT, MARK_SHAPE_SESAME})
  public @interface MarkShape {
  }
  public static final int MARK_FILL_UNSPECIFIED = 0;
  public static final int MARK_FILL_FILLED = 1;
  public static final int MARK_FILL_OPEN = 2;

  /**
   * The possible mark fills that can be used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #MARK_FILL_UNSPECIFIED}
   *   <li>{@link #MARK_FILL_FILLED}
   *   <li>{@link #MARK_FILL_OPEN}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({MARK_FILL_UNSPECIFIED, MARK_FILL_FILLED, MARK_FILL_OPEN})
  public @interface MarkFill {
  }


  /**
   * The mark shape used for text emphasis
   */
  public @MarkShape int markShape;

  /**
   * The mark fill for the text emphasis mark
   */
  public @MarkShape int markFill;


  /**
   * The position of the text emphasis relative to the base text
   */
  @TextAnnotation.Position
  public final int position;


  public TextEmphasisSpan(@MarkShape int shape, @MarkFill int fill,
      @TextAnnotation.Position int position) {
    this.markShape = shape;
    this.markFill = fill;
    this.position = position;
  }
}

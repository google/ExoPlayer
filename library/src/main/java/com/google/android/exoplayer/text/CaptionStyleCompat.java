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

import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;

/**
 * A compatibility wrapper for {@link CaptionStyle}.
 */
public final class CaptionStyleCompat {

  /**
   * Edge type value specifying no character edges.
   */
  public static final int EDGE_TYPE_NONE = 0;

  /**
   * Edge type value specifying uniformly outlined character edges.
   */
  public static final int EDGE_TYPE_OUTLINE = 1;

  /**
   * Edge type value specifying drop-shadowed character edges.
   */
  public static final int EDGE_TYPE_DROP_SHADOW = 2;

  /**
   * Edge type value specifying raised bevel character edges.
   */
  public static final int EDGE_TYPE_RAISED = 3;

  /**
   * Edge type value specifying depressed bevel character edges.
   */
  public static final int EDGE_TYPE_DEPRESSED = 4;

  /**
   * Use color setting specified by the track and fallback to default caption style.
   */
  public static final int USE_TRACK_COLOR_SETTINGS = 1;

  /**
   * Default caption style.
   */
  public static final CaptionStyleCompat DEFAULT = new CaptionStyleCompat(
      Color.WHITE, Color.BLACK, Color.TRANSPARENT, EDGE_TYPE_NONE, Color.WHITE, null);

  /**
   * The preferred foreground color.
   */
  public final int foregroundColor;

  /**
   * The preferred background color.
   */
  public final int backgroundColor;

  /**
   * The preferred window color.
   */
  public final int windowColor;

  /**
   * The preferred edge type. One of:
   * <ul>
   * <li>{@link #EDGE_TYPE_NONE}
   * <li>{@link #EDGE_TYPE_OUTLINE}
   * <li>{@link #EDGE_TYPE_DROP_SHADOW}
   * <li>{@link #EDGE_TYPE_RAISED}
   * <li>{@link #EDGE_TYPE_DEPRESSED}
   * </ul>
   */
  public final int edgeType;

  /**
   * The preferred edge color, if using an edge type other than {@link #EDGE_TYPE_NONE}.
   */
  public final int edgeColor;

  /**
   * The preferred typeface.
   */
  public final Typeface typeface;

  /**
   * Creates a {@link CaptionStyleCompat} equivalent to a provided {@link CaptionStyle}.
   *
   * @param style A {@link CaptionStyle}.
   * @return The equivalent {@link CaptionStyleCompat}.
   */
  @TargetApi(19)
  public static CaptionStyleCompat createFromCaptionStyle(CaptionStyle style) {
    int windowColor = Util.SDK_INT >= 21 ? getWindowColorV21(style) : Color.TRANSPARENT;
    return new CaptionStyleCompat(style.foregroundColor, style.backgroundColor, windowColor,
        style.edgeType, style.edgeColor, style.getTypeface());
  }

  /**
   * @param foregroundColor See {@link #foregroundColor}.
   * @param backgroundColor See {@link #backgroundColor}.
   * @param windowColor See {@link #windowColor}.
   * @param edgeType See {@link #edgeType}.
   * @param edgeColor See {@link #edgeColor}.
   * @param typeface See {@link #typeface}.
   */
  public CaptionStyleCompat(int foregroundColor, int backgroundColor, int windowColor, int edgeType,
      int edgeColor, Typeface typeface) {
    this.foregroundColor = foregroundColor;
    this.backgroundColor = backgroundColor;
    this.windowColor = windowColor;
    this.edgeType = edgeType;
    this.edgeColor = edgeColor;
    this.typeface = typeface;
  }

  @SuppressWarnings("unused")
  @TargetApi(21)
  private static int getWindowColorV21(CaptioningManager.CaptionStyle captionStyle) {
    // TODO: Uncomment when building against API level 21.
    return Color.TRANSPARENT; //captionStyle.windowColor;
  }

}

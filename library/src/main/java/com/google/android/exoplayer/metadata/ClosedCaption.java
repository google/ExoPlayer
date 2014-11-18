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
package com.google.android.exoplayer.metadata;

/**
 * A Closed Caption that contains textual data associated with time indices.
 */
public final class ClosedCaption {

  /**
   * Identifies closed captions with control characters.
   */
  public static final int TYPE_CTRL = 0;
  /**
   * Identifies closed captions with textual information.
   */
  public static final int TYPE_TEXT = 1;

  /**
   * The type of the closed caption data. If equals to {@link #TYPE_TEXT} the {@link #text} field
   * has the textual data, if equals to {@link #TYPE_CTRL} the {@link #text} field has two control
   * characters (C1, C2).
   */
  public final int type;
  /**
   * Contains text or two control characters.
   */
  public final String text;

  public ClosedCaption(int type, String text) {
    this.type = type;
    this.text = text;
  }

}

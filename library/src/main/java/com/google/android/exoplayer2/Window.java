/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2;

/**
 * A window of available media. Instances are immutable.
 */
public final class Window {

  /**
   * Creates a new {@link Window} consisting of a single period with the specified duration. The
   * default start position is zero.
   *
   * @param durationUs The duration of the window, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether this seek window may change when the timeline is updated.
   */
  public static Window createWindowFromZero(long durationUs, boolean isSeekable,
      boolean isDynamic) {
    return new Window(durationUs, isSeekable, isDynamic, 0);
  }

  /**
   * The default position relative to the start of the window at which to start playback, in
   * microseconds.
   */
  public final long defaultStartPositionUs;
  /**
   * The duration of the window in microseconds, or {@link C#UNSET_TIME_US} if unknown.
   */
  public final long durationUs;
  /**
   * Whether it's possible to seek within the window.
   */
  public final boolean isSeekable;
  /**
   * Whether this seek window may change when the timeline is updated.
   */
  public final boolean isDynamic;

  /**
   * @param durationUs The duration of the window in microseconds, or {@link C#UNSET_TIME_US} if
   *     unknown.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether this seek window may change when the timeline is updated.
   * @param defaultStartPositionUs The default position relative to the start of the window at which
   *     to start playback, in microseconds.
   */
  public Window(long durationUs, boolean isSeekable, boolean isDynamic,
      long defaultStartPositionUs) {
    this.durationUs = durationUs;
    this.isSeekable = isSeekable;
    this.isDynamic = isDynamic;
    this.defaultStartPositionUs = defaultStartPositionUs;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (isSeekable ? 1 : 2);
    result = 31 * result + (isDynamic ? 1 : 2);
    result = 31 * result + (int) defaultStartPositionUs;
    result = 31 * result + (int) durationUs;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Window other = (Window) obj;
    return other.durationUs == durationUs
        && other.isSeekable == isSeekable
        && other.isDynamic == isDynamic
        && other.defaultStartPositionUs == defaultStartPositionUs;
  }

  @Override
  public String toString() {
    return "Window[" + durationUs + ", " + defaultStartPositionUs + ", " + isSeekable + ", "
        + isDynamic + "]";
  }

}

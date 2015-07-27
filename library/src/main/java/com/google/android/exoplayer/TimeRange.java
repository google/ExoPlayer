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
package com.google.android.exoplayer;

/**
 * A container to store a start and end time in microseconds.
 */
public final class TimeRange {

  /**
   * Represents a range of time whose bounds change in bulk increments rather than smoothly over
   * time.
   */
  public static final int TYPE_SNAPSHOT = 0;

  /**
   * The type of this time range.
   */
  public final int type;

  private final long startTimeUs;
  private final long endTimeUs;

  /**
   * Create a new {@link TimeRange} of the appropriate type.
   *
   * @param type The type of the TimeRange.
   * @param startTimeUs The beginning of the TimeRange.
   * @param endTimeUs The end of the TimeRange.
   */
  public TimeRange(int type, long startTimeUs, long endTimeUs) {
    this.type = type;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
  }

  /**
   * Returns the start and end times (in milliseconds) of the TimeRange in the provided array,
   * or creates a new one.
   *
   * @param out An array to store the start and end times; can be null.
   * @return An array containing the start time (index 0) and end time (index 1) in milliseconds.
   */
  public long[] getCurrentBoundsMs(long[] out) {
    out = getCurrentBoundsUs(out);
    out[0] /= 1000;
    out[1] /= 1000;
    return out;
  }

  /**
   * Returns the start and end times (in microseconds) of the TimeRange in the provided array,
   * or creates a new one.
   *
   * @param out An array to store the start and end times; can be null.
   * @return An array containing the start time (index 0) and end time (index 1) in microseconds.
   */
  public long[] getCurrentBoundsUs(long[] out) {
    if (out == null || out.length < 2) {
      out = new long[2];
    }
    out[0] = startTimeUs;
    out[1] = endTimeUs;
    return out;
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    hashCode |= type << 30;
    hashCode |= (((startTimeUs + endTimeUs) / 1000) & 0x3FFFFFFF);
    return hashCode;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other instanceof TimeRange) {
      TimeRange otherTimeRange = (TimeRange) other;
      return (otherTimeRange.type == type) && (otherTimeRange.startTimeUs == startTimeUs)
          && (otherTimeRange.endTimeUs == endTimeUs);
    } else {
      return false;
    }
  }

}

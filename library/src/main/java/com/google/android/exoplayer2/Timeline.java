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
 * A media timeline. Instances are immutable.
 */
public abstract class Timeline {

  /**
   * Returns the number of windows in the timeline.
   */
  public abstract int getWindowCount();

  /**
   * Populates a {@link Window} with data for the window at the specified index. Does not populate
   * {@link Window#id}.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window) {
    return getWindow(windowIndex, window, false);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param setIds Whether {@link Window#id} should be populated. If false, the field will be set to
   * null. The caller should pass false for efficiency reasons unless the field is required.
   * @return The populated {@link Window}, for convenience.
   */
  public abstract Window getWindow(int windowIndex, Window window, boolean setIds);

  /**
   * Returns the number of periods in the timeline.
   */
  public abstract int getPeriodCount();

  /**
   * Populates a {@link Period} with data for the period at the specified index. Does not populate
   * {@link Period#id} and {@link Period#uid}.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public final Period getPeriod(int periodIndex, Period period) {
    return getPeriod(periodIndex, period, false);
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @param setIds Whether {@link Period#id} and {@link Period#uid} should be populated. If false,
   *     the fields will be set to null. The caller should pass false for efficiency reasons unless
   *     the fields are required.
   * @return The populated {@link Period}, for convenience.
   */
  public abstract Period getPeriod(int periodIndex, Period period, boolean setIds);

  /**
   * Returns the index of the period identified by its unique {@code id}, or {@link C#INDEX_UNSET}
   * if the period is not in the timeline.
   *
   * @param uid A unique identifier for a period.
   * @return The index of the period, or {@link C#INDEX_UNSET} if the period was not found.
   */
  public abstract int getIndexOfPeriod(Object uid);

  /**
   * Holds information about a window of available media.
   */
  public static final class Window {

    /**
     * An identifier for the window. Not necessarily unique.
     */
    public Object id;

    /**
     * The start time of the presentation to which this window belongs in milliseconds since the
     * epoch, or {@link C#TIME_UNSET} if unknown or not applicable. For informational purposes only.
     */
    public long presentationStartTimeMs;

    /**
     * The windows start time in milliseconds since the epoch, or {@link C#TIME_UNSET} if unknown or
     * not applicable. For informational purposes only.
     */
    public long windowStartTimeMs;

    /**
     * Whether it's possible to seek within this window.
     */
    public boolean isSeekable;

    /**
     * Whether this window may change when the timeline is updated.
     */
    public boolean isDynamic;

    /**
     * The index of the first period that belongs to this window.
     */
    public int firstPeriodIndex;

    /**
     * The index of the last period that belongs to this window.
     */
    public int lastPeriodIndex;

    private long defaultStartPositionUs;
    private long durationUs;
    private long positionInFirstPeriodUs;

    /**
     * Sets the data held by this window.
     */
    public Window set(Object id, long presentationStartTimeMs, long windowStartTimeMs,
        boolean isSeekable, boolean isDynamic, long defaultStartPositionUs, long durationUs,
        int firstPeriodIndex, int lastPeriodIndex, long positionInFirstPeriodUs) {
      this.id = id;
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.defaultStartPositionUs = defaultStartPositionUs;
      this.durationUs = durationUs;
      this.firstPeriodIndex = firstPeriodIndex;
      this.lastPeriodIndex = lastPeriodIndex;
      this.positionInFirstPeriodUs = positionInFirstPeriodUs;
      return this;
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in milliseconds.
     */
    public long getDefaultStartPositionMs() {
      return C.usToMs(defaultStartPositionUs);
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in microseconds.
     */
    public long getDefaultStartPositionUs() {
      return defaultStartPositionUs;
    }

    /**
     * Returns the duration of the window in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in milliseconds.
     */
    public long getPositionInFirstPeriodMs() {
      return C.usToMs(positionInFirstPeriodUs);
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public long getPositionInFirstPeriodUs() {
      return positionInFirstPeriodUs;
    }

  }

  /**
   * Holds information about a media period.
   */
  public static final class Period {

    /**
     * An identifier for the period. Not necessarily unique.
     */
    public Object id;

    /**
     * A unique identifier for the period.
     */
    public Object uid;

    /**
     * The index of the window to which this period belongs.
     */
    public int windowIndex;

    private long durationUs;
    private long positionInWindowUs;

    /**
     * Sets the data held by this period.
     */
    public Period set(Object id, Object uid, int windowIndex, long durationUs,
        long positionInWindowUs) {
      this.id = id;
      this.uid = uid;
      this.windowIndex = windowIndex;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      return this;
    }

    /**
     * Returns the duration of the period in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in milliseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowMs() {
      return C.usToMs(positionInWindowUs);
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in microseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowUs() {
      return positionInWindowUs;
    }

  }

}

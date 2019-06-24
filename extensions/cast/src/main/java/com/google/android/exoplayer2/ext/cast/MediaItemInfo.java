/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import java.util.List;

// TODO (Internal b/119293631): Add ad playback state info.
/**
 * Holds dynamic information for a {@link MediaItem}.
 *
 * <p>Holds information related to preparation for a specific {@link MediaItem}. Unprepared items
 * are associated with an {@link #EMPTY} info object until prepared.
 */
public final class MediaItemInfo {

  /** Placeholder information for media items that have not yet been prepared by the player. */
  public static final MediaItemInfo EMPTY =
      new MediaItemInfo(
          /* windowDurationUs= */ C.TIME_UNSET,
          /* defaultStartPositionUs= */ 0L,
          Collections.singletonList(
              new Period(
                  /* id= */ new Object(),
                  /* durationUs= */ C.TIME_UNSET,
                  /* positionInWindowUs= */ 0L)),
          /* positionInFirstPeriodUs= */ 0L,
          /* isSeekable= */ false,
          /* isDynamic= */ true);

  /** Holds the information of one of the periods of a {@link MediaItem}. */
  public static final class Period {

    /**
     * The id of the period. Must be unique within the {@link MediaItem} but may match with periods
     * in other items.
     */
    public final Object id;
    /** The duration of the period in microseconds. */
    public final long durationUs;
    /** The position of this period in the window in microseconds. */
    public final long positionInWindowUs;
    // TODO: Add track information.

    public Period(Object id, long durationUs, long positionInWindowUs) {
      this.id = id;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      Period period = (Period) other;
      return durationUs == period.durationUs
          && positionInWindowUs == period.positionInWindowUs
          && id.equals(period.id);
    }

    @Override
    public int hashCode() {
      int result = id.hashCode();
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInWindowUs ^ (positionInWindowUs >>> 32));
      return result;
    }
  }

  /** The duration of the window in microseconds. */
  public final long windowDurationUs;
  /** The default start position relative to the start of the window, in microseconds. */
  public final long defaultStartPositionUs;
  /** The periods conforming the media item. */
  public final List<Period> periods;
  /** The position of the window in the first period in microseconds. */
  public final long positionInFirstPeriodUs;
  /** Whether it is possible to seek within the window. */
  public final boolean isSeekable;
  /** Whether the window may change when the timeline is updated. */
  public final boolean isDynamic;

  public MediaItemInfo(
      long windowDurationUs,
      long defaultStartPositionUs,
      List<Period> periods,
      long positionInFirstPeriodUs,
      boolean isSeekable,
      boolean isDynamic) {
    this.windowDurationUs = windowDurationUs;
    this.defaultStartPositionUs = defaultStartPositionUs;
    this.periods = Collections.unmodifiableList(periods);
    this.positionInFirstPeriodUs = positionInFirstPeriodUs;
    this.isSeekable = isSeekable;
    this.isDynamic = isDynamic;
  }

  /**
   * Returns the index of the period with {@link Period#id} equal to {@code periodId}, or {@link
   * C#INDEX_UNSET} if none of the periods has the given id.
   */
  public int getIndexOfPeriod(Object periodId) {
    for (int i = 0; i < periods.size(); i++) {
      if (Util.areEqual(periods.get(i).id, periodId)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    MediaItemInfo that = (MediaItemInfo) other;
    return windowDurationUs == that.windowDurationUs
        && defaultStartPositionUs == that.defaultStartPositionUs
        && positionInFirstPeriodUs == that.positionInFirstPeriodUs
        && isSeekable == that.isSeekable
        && isDynamic == that.isDynamic
        && periods.equals(that.periods);
  }

  @Override
  public int hashCode() {
    int result = (int) (windowDurationUs ^ (windowDurationUs >>> 32));
    result = 31 * result + (int) (defaultStartPositionUs ^ (defaultStartPositionUs >>> 32));
    result = 31 * result + periods.hashCode();
    result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
    result = 31 * result + (isSeekable ? 1 : 0);
    result = 31 * result + (isDynamic ? 1 : 0);
    return result;
  }
}

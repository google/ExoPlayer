/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.mp4;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;

/** Holds information about the segments of slow motion playback within a track. */
public final class SlowMotionData implements Metadata.Entry {

  /** Holds information about a single segment of slow motion playback within a track. */
  public static final class Segment implements Parcelable {

    /** The start time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final int startTimeMs;
    /** The end time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final int endTimeMs;
    /**
     * The speed reduction factor.
     *
     * <p>For example, 4 would mean the segment should be played at a quarter (1/4) of the normal
     * speed.
     */
    public final int speedDivisor;

    /**
     * Creates an instance.
     *
     * @param startTimeMs See {@link #startTimeMs}.
     * @param endTimeMs See {@link #endTimeMs}.
     * @param speedDivisor See {@link #speedDivisor}.
     */
    public Segment(int startTimeMs, int endTimeMs, int speedDivisor) {
      this.startTimeMs = startTimeMs;
      this.endTimeMs = endTimeMs;
      this.speedDivisor = speedDivisor;
    }

    @Override
    public String toString() {
      return Util.formatInvariant(
          "Segment: startTimeMs=%d, endTimeMs=%d, speedDivisor=%d",
          startTimeMs, endTimeMs, speedDivisor);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Segment segment = (Segment) o;
      return startTimeMs == segment.startTimeMs
          && endTimeMs == segment.endTimeMs
          && speedDivisor == segment.speedDivisor;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(startTimeMs, endTimeMs, speedDivisor);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(startTimeMs);
      dest.writeInt(endTimeMs);
      dest.writeInt(speedDivisor);
    }

    public static final Creator<Segment> CREATOR =
        new Creator<Segment>() {

          @Override
          public Segment createFromParcel(Parcel in) {
            int startTimeMs = in.readInt();
            int endTimeMs = in.readInt();
            int speedDivisor = in.readInt();
            return new Segment(startTimeMs, endTimeMs, speedDivisor);
          }

          @Override
          public Segment[] newArray(int size) {
            return new Segment[size];
          }
        };
  }

  public final List<Segment> segments;

  /** Creates an instance with a list of {@link Segment}s. */
  public SlowMotionData(List<Segment> segments) {
    this.segments = segments;
  }

  @Override
  public String toString() {
    return "SlowMotion: segments=" + segments;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SlowMotionData that = (SlowMotionData) o;
    return segments.equals(that.segments);
  }

  @Override
  public int hashCode() {
    return segments.hashCode();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeList(segments);
  }

  public static final Creator<SlowMotionData> CREATOR =
      new Creator<SlowMotionData>() {
        @Override
        public SlowMotionData createFromParcel(Parcel in) {
          List<Segment> slowMotionSegments = new ArrayList<>();
          in.readList(slowMotionSegments, Segment.class.getClassLoader());
          return new SlowMotionData(slowMotionSegments);
        }

        @Override
        public SlowMotionData[] newArray(int size) {
          return new SlowMotionData[size];
        }
      };
}

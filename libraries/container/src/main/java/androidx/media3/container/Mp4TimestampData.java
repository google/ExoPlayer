/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.container;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.primitives.Longs;

/** Stores MP4 timestamp data. */
@UnstableApi
public final class Mp4TimestampData implements Metadata.Entry {
  /** Represents an unset or unknown timescale. */
  public static final int TIMESCALE_UNSET = -1;

  /**
   * The delta between a Unix epoch timestamp (in milliseconds since midnight, January 1, 1970) and
   * an MP4 timestamp (in seconds since midnight, January 1, 1904).
   */
  private static final int UNIX_EPOCH_TO_MP4_TIME_DELTA_SECONDS =
      ((1970 - 1904) * 365 + 17 /* leap year */) * (24 * 60 * 60);

  /** The creation timestamp. */
  public final long creationTimestampSeconds;

  /** The modification timestamp. */
  public final long modificationTimestampSeconds;

  /** The timescale of the movie. */
  public final long timescale;

  /**
   * Creates an instance.
   *
   * <p>The {@link #timescale} is set to {@link Mp4TimestampData#TIMESCALE_UNSET}.
   *
   * @param creationTimestampSeconds The creation time UTC in seconds since midnight, January 1,
   *     1904.
   * @param modificationTimestampSeconds The modification time UTC in seconds since midnight,
   *     January 1, 1904.
   */
  public Mp4TimestampData(long creationTimestampSeconds, long modificationTimestampSeconds) {
    this.creationTimestampSeconds = creationTimestampSeconds;
    this.modificationTimestampSeconds = modificationTimestampSeconds;
    this.timescale = TIMESCALE_UNSET;
  }

  /**
   * Creates an instance.
   *
   * @param creationTimestampSeconds The creation time UTC in seconds since midnight, January 1,
   *     1904.
   * @param modificationTimestampSeconds The modification time UTC in seconds since midnight,
   *     January 1, 1904.
   * @param timescale The timescale of the movie.
   */
  public Mp4TimestampData(
      long creationTimestampSeconds, long modificationTimestampSeconds, long timescale) {
    this.creationTimestampSeconds = creationTimestampSeconds;
    this.modificationTimestampSeconds = modificationTimestampSeconds;
    this.timescale = timescale;
  }

  private Mp4TimestampData(Parcel in) {
    this.creationTimestampSeconds = in.readLong();
    this.modificationTimestampSeconds = in.readLong();
    this.timescale = in.readLong();
  }

  /**
   * Returns an MP4 timestamp (in seconds since midnight, January 1, 1904) from a Unix epoch
   * timestamp (in milliseconds since midnight, January 1, 1970).
   */
  public static long unixTimeToMp4TimeSeconds(long unixTimestampMs) {
    return (unixTimestampMs / 1_000L) + UNIX_EPOCH_TO_MP4_TIME_DELTA_SECONDS;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Mp4TimestampData)) {
      return false;
    }

    Mp4TimestampData other = (Mp4TimestampData) obj;

    return creationTimestampSeconds == other.creationTimestampSeconds
        && modificationTimestampSeconds == other.modificationTimestampSeconds
        && timescale == other.timescale;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Longs.hashCode(creationTimestampSeconds);
    result = 31 * result + Longs.hashCode(modificationTimestampSeconds);
    result = 31 * result + Longs.hashCode(timescale);
    return result;
  }

  @Override
  public String toString() {
    return "Mp4Timestamp: creation time="
        + creationTimestampSeconds
        + ", modification time="
        + modificationTimestampSeconds
        + ", timescale="
        + timescale;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(creationTimestampSeconds);
    dest.writeLong(modificationTimestampSeconds);
    dest.writeLong(timescale);
  }

  public static final Parcelable.Creator<Mp4TimestampData> CREATOR =
      new Parcelable.Creator<Mp4TimestampData>() {

        @Override
        public Mp4TimestampData createFromParcel(Parcel in) {
          return new Mp4TimestampData(in);
        }

        @Override
        public Mp4TimestampData[] newArray(int size) {
          return new Mp4TimestampData[size];
        }
      };
}

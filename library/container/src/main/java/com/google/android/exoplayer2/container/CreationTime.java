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
package com.google.android.exoplayer2.container;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.common.primitives.Longs;

/** Stores creation time. */
public final class CreationTime implements Metadata.Entry {
  public final long timestampMs;

  /**
   * Creates an instance.
   *
   * @param timestampMs The creation time UTC in milliseconds since the Unix epoch.
   */
  public CreationTime(long timestampMs) {
    this.timestampMs = timestampMs;
  }

  private CreationTime(Parcel in) {
    this.timestampMs = in.readLong();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CreationTime)) {
      return false;
    }

    return timestampMs == ((CreationTime) obj).timestampMs;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(timestampMs);
  }

  @Override
  public String toString() {
    long unsetCreationTime = -2_082_844_800_000L;
    return "Creation time: " + (timestampMs == unsetCreationTime ? "unset" : timestampMs);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(timestampMs);
  }

  public static final Parcelable.Creator<CreationTime> CREATOR =
      new Parcelable.Creator<CreationTime>() {

        @Override
        public CreationTime createFromParcel(Parcel in) {
          return new CreationTime(in);
        }

        @Override
        public CreationTime[] newArray(int size) {
          return new CreationTime[size];
        }
      };
}

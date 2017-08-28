/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Uniquely identifies a track in a {@link SsManifest}.
 */
public final class TrackKey implements Parcelable, Comparable<TrackKey> {

  public final int streamElementIndex;
  public final int trackIndex;

  public TrackKey(int streamElementIndex, int trackIndex) {
    this.streamElementIndex = streamElementIndex;
    this.trackIndex = trackIndex;
  }

  @Override
  public String toString() {
    return streamElementIndex + "." + trackIndex;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(streamElementIndex);
    dest.writeInt(trackIndex);
  }

  public static final Creator<TrackKey> CREATOR = new Creator<TrackKey>() {
    @Override
    public TrackKey createFromParcel(Parcel in) {
      return new TrackKey(in.readInt(), in.readInt());
    }

    @Override
    public TrackKey[] newArray(int size) {
      return new TrackKey[size];
    }
  };

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull TrackKey o) {
    int result = streamElementIndex - o.streamElementIndex;
    if (result == 0) {
      result = trackIndex - o.trackIndex;
    }
    return result;
  }

}

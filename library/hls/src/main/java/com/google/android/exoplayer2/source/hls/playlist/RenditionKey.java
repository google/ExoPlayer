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
package com.google.android.exoplayer2.source.hls.playlist;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/** Uniquely identifies a rendition in an {@link HlsMasterPlaylist}. */
public final class RenditionKey implements Parcelable, Comparable<RenditionKey> {

  public static final int GROUP_VARIANTS = 0;
  public static final int GROUP_AUDIOS = 1;
  public static final int GROUP_SUBTITLES = 2;

  public final int renditionGroup;
  public final int trackIndex;

  public RenditionKey(int renditionGroup, int trackIndex) {
    this.renditionGroup = renditionGroup;
    this.trackIndex = trackIndex;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(renditionGroup);
    dest.writeInt(trackIndex);
  }

  public static final Creator<RenditionKey> CREATOR =
      new Creator<RenditionKey>() {
        @Override
        public RenditionKey createFromParcel(Parcel in) {
          return new RenditionKey(in.readInt(), in.readInt());
        }

        @Override
        public RenditionKey[] newArray(int size) {
          return new RenditionKey[size];
        }
      };

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull RenditionKey other) {
    int result = renditionGroup - other.renditionGroup;
    if (result == 0) {
      result = trackIndex - other.trackIndex;
    }
    return result;
  }
}

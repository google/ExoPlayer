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

  public final String url;

  public RenditionKey(String url) {
    this.url = url;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(url);
  }

  public static final Creator<RenditionKey> CREATOR =
      new Creator<RenditionKey>() {
        @Override
        public RenditionKey createFromParcel(Parcel in) {
          return new RenditionKey(in.readString());
        }

        @Override
        public RenditionKey[] newArray(int size) {
          return new RenditionKey[size];
        }
      };

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull RenditionKey o) {
    return url.compareTo(o.url);
  }
}

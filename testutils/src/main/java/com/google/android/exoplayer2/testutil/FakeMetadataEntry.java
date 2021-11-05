/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;

/** A fake {@link Metadata.Entry}. */
public final class FakeMetadataEntry implements Metadata.Entry {

  public final String data;

  public FakeMetadataEntry(String data) {
    this.data = data;
  }

  /* package */ FakeMetadataEntry(Parcel in) {
    data = castNonNull(in.readString());
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    FakeMetadataEntry other = (FakeMetadataEntry) obj;
    return data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(data);
  }

  public static final Parcelable.Creator<FakeMetadataEntry> CREATOR =
      new Parcelable.Creator<FakeMetadataEntry>() {

        @Override
        public FakeMetadataEntry createFromParcel(Parcel in) {
          return new FakeMetadataEntry(in);
        }

        @Override
        public FakeMetadataEntry[] newArray(int size) {
          return new FakeMetadataEntry[size];
        }
      };
}

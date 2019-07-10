/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.vorbis;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;

/** Base class for Vorbis Comment Frames. */
public final class VorbisCommentFrame implements Metadata.Entry {

  /** The key for this vorbis comment */
  public final String key;

  /** The value corresponding to this vorbis comment's key */
  public final String value;

  /**
   * @param key The key
   * @param value Value corresponding to the key
   */
  public VorbisCommentFrame(String key, String value) {
    this.key = key;
    this.value = value;
  }

  /* package */ VorbisCommentFrame(Parcel in) {
    this.key = castNonNull(in.readString());
    this.value = castNonNull(in.readString());
  }

  @Override
  public String toString() {
    return key;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  // Parcelable implementation.
  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(key);
    dest.writeString(value);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if ((obj != null) && (obj.getClass() == this.getClass())) {
      if (this == obj) {
        return true;
      } else {
        VorbisCommentFrame compareFrame = (VorbisCommentFrame) obj;
        if (this.key.equals(compareFrame.key) && this.value.equals(compareFrame.value)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = 17;

    result = 31 * result + key.hashCode();
    result = 31 * result + value.hashCode();

    return result;
  }

  public static final Parcelable.Creator<VorbisCommentFrame> CREATOR =
      new Parcelable.Creator<VorbisCommentFrame>() {

        @Override
        public VorbisCommentFrame createFromParcel(Parcel in) {
          return new VorbisCommentFrame(in);
        }

        @Override
        public VorbisCommentFrame[] newArray(int size) {
          return new VorbisCommentFrame[size];
        }
      };
}

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

/** Metadata of a motion photo file. */
public final class MotionPhoto implements Metadata.Entry {

  /** The start offset of the photo data, in bytes. */
  public final int photoStartPosition;
  /** The size of the photo data, in bytes. */
  public final int photoSize;
  /** The start offset of the video data, in bytes. */
  public final int videoStartPosition;
  /** The size of the video data, in bytes. */
  public final int videoSize;

  /** Creates an instance. */
  public MotionPhoto(int photoStartPosition, int photoSize, int videoStartPosition, int videoSize) {
    this.photoStartPosition = photoStartPosition;
    this.photoSize = photoSize;
    this.videoStartPosition = videoStartPosition;
    this.videoSize = videoSize;
  }

  private MotionPhoto(Parcel in) {
    photoStartPosition = in.readInt();
    photoSize = in.readInt();
    videoStartPosition = in.readInt();
    videoSize = in.readInt();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MotionPhoto other = (MotionPhoto) obj;
    return photoStartPosition == other.photoStartPosition
        && photoSize == other.photoSize
        && videoStartPosition == other.videoStartPosition
        && videoSize == other.videoSize;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + photoStartPosition;
    result = 31 * result + photoSize;
    result = 31 * result + videoStartPosition;
    result = 31 * result + videoSize;
    return result;
  }

  @Override
  public String toString() {
    return "Motion photo: photoStartPosition="
        + photoStartPosition
        + ", photoSize="
        + photoSize
        + ", videoStartPosition="
        + videoStartPosition
        + ", videoSize="
        + videoSize;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(photoStartPosition);
    dest.writeInt(photoSize);
    dest.writeInt(videoStartPosition);
    dest.writeInt(videoSize);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<MotionPhoto> CREATOR =
      new Parcelable.Creator<MotionPhoto>() {

        @Override
        public MotionPhoto createFromParcel(Parcel in) {
          return new MotionPhoto(in);
        }

        @Override
        public MotionPhoto[] newArray(int size) {
          return new MotionPhoto[size];
        }
      };
}

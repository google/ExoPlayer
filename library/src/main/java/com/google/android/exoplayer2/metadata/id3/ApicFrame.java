/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.id3;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

/**
 * APIC (Attached Picture) ID3 frame.
 */
public final class ApicFrame extends Id3Frame {

  public static final String ID = "APIC";

  public final String mimeType;
  public final String description;
  public final int pictureType;
  public final byte[] pictureData;

  public ApicFrame(String mimeType, String description, int pictureType, byte[] pictureData) {
    super(ID);
    this.mimeType = mimeType;
    this.description = description;
    this.pictureType = pictureType;
    this.pictureData = pictureData;
  }

  public ApicFrame(Parcel in) {
    super(in);
    mimeType = in.readString();
    description = in.readString();
    pictureType = in.readInt();
    pictureData = in.createByteArray();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ApicFrame that = (ApicFrame) o;

    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (pictureType != that.pictureType) return false;
    if (mimeType != null ? !mimeType.equals(that.mimeType) : that.mimeType != null)
      return false;
    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    return Arrays.equals(pictureData, that.pictureData);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + pictureType;
    result = 31 * result + Arrays.hashCode(pictureData);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(mimeType);
    dest.writeString(description);
    dest.writeInt(pictureType);
    dest.writeByteArray(pictureData);
  }

  public static final Parcelable.Creator<ApicFrame> CREATOR =
      new Parcelable.Creator<ApicFrame>() {

        @Override
        public ApicFrame createFromParcel(Parcel in) {
          return new ApicFrame(in);
        }

        @Override
        public ApicFrame[] newArray(int size) {
          return new ApicFrame[size];
        }

      };

}

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
 * GEOB (General Encapsulated Object) ID3 frame.
 */
public final class GeobFrame extends Id3Frame {

  public static final String ID = "GEOB";

  public final String mimeType;
  public final String filename;
  public final String description;
  public final byte[] data;

  public GeobFrame(String mimeType, String filename, String description, byte[] data) {
    super(ID);
    this.mimeType = mimeType;
    this.filename = filename;
    this.description = description;
    this.data = data;
  }

  public GeobFrame(Parcel in) {
    super(in);
    mimeType = in.readString();
    filename = in.readString();
    description = in.readString();
    data = in.createByteArray();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GeobFrame that = (GeobFrame) o;

    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (mimeType != null ? !mimeType.equals(that.mimeType) : that.mimeType != null)
      return false;
    if (filename != null ? !filename.equals(that.filename) : that.filename != null)
      return false;
    if (description != null ? !description.equals(that.description) : that.description != null)
      return false;
    return Arrays.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + (filename != null ? filename.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(mimeType);
    dest.writeString(filename);
    dest.writeString(description);
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<GeobFrame> CREATOR =
      new Parcelable.Creator<GeobFrame>() {

        @Override
        public GeobFrame createFromParcel(Parcel in) {
          return new GeobFrame(in);
        }

        @Override
        public GeobFrame[] newArray(int size) {
          return new GeobFrame[size];
        }

      };

}

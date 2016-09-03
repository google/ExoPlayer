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
 * Binary ID3 frame.
 */
public final class BinaryFrame extends Id3Frame {

  public final byte[] data;

  public BinaryFrame(String type, byte[] data) {
    super(type);
    this.data = data;
  }

  public BinaryFrame(Parcel in) {
    super(in);
    data = in.createByteArray();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BinaryFrame that = (BinaryFrame) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    return Arrays.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeByteArray(data);
  }

  public static final Parcelable.Creator<BinaryFrame> CREATOR =
      new Parcelable.Creator<BinaryFrame>() {

        @Override
        public BinaryFrame createFromParcel(Parcel in) {
          return new BinaryFrame(in);
        }

        @Override
        public BinaryFrame[] newArray(int size) {
          return new BinaryFrame[size];
        }

      };

}

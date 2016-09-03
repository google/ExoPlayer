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
 * PRIV (Private) ID3 frame.
 */
public final class PrivFrame extends Id3Frame {

  public static final String ID = "PRIV";

  public final String owner;
  public final byte[] privateData;

  public PrivFrame(String owner, byte[] privateData) {
    super(ID);
    this.owner = owner;
    this.privateData = privateData;
  }

  public PrivFrame(Parcel in) {
    super(in);
    owner = in.readString();
    privateData = in.createByteArray();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PrivFrame that = (PrivFrame) o;

    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
    return Arrays.equals(privateData, that.privateData);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (owner != null ? owner.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(privateData);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(owner);
    dest.writeByteArray(privateData);
  }

  public static final Parcelable.Creator<PrivFrame> CREATOR =
      new Parcelable.Creator<PrivFrame>() {

        @Override
        public PrivFrame createFromParcel(Parcel in) {
          return new PrivFrame(in);
        }

        @Override
        public PrivFrame[] newArray(int size) {
          return new PrivFrame[size];
        }

      };

}

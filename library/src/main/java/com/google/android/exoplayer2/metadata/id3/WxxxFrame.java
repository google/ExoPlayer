/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.android.exoplayer2.util.Util;

/**
 * Url Frame "WXX" ID3 frame.
 */
public final class WxxxFrame extends Id3Frame {

  public static final String ID = "WXXX";

  public final String description;
  public final String url;

  public WxxxFrame(String description, String url) {
    super(ID);
    this.description = description;
    this.url = url;
  }

  /* package */ WxxxFrame(Parcel in) {
    super(ID);
    description = in.readString();
    url = in.readString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    WxxxFrame other = (WxxxFrame) obj;
    return Util.areEqual(description, other.description)
        && Util.areEqual(url, other.url);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(description);
    dest.writeString(url);
  }

  public static final Parcelable.Creator<WxxxFrame> CREATOR =
      new Parcelable.Creator<WxxxFrame>() {

      @Override
      public WxxxFrame createFromParcel(Parcel in) {
        return new WxxxFrame(in);
      }

      @Override
      public WxxxFrame[] newArray(int size) {
        return new WxxxFrame[size];
      }

    };

}

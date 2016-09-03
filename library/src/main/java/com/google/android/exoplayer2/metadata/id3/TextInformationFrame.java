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

/**
 * Text information ("T000" - "TZZZ", excluding "TXXX") ID3 frame.
 */
public final class TextInformationFrame extends Id3Frame {

  public final String description;

  public TextInformationFrame(String id, String description) {
    super(id);
    this.description = description;
  }

  public TextInformationFrame(Parcel in) {
    super(in);
    description = in.readString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextInformationFrame that = (TextInformationFrame) o;

    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    return description != null ? description.equals(that.description) : that.description == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(description);
  }

  public static final Parcelable.Creator<TextInformationFrame> CREATOR =
      new Parcelable.Creator<TextInformationFrame>() {

        @Override
        public TextInformationFrame createFromParcel(Parcel in) {
          return new TextInformationFrame(in);
        }

        @Override
        public TextInformationFrame[] newArray(int size) {
          return new TextInformationFrame[size];
        }

      };

}

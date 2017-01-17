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

import com.google.android.exoplayer2.util.Util;

/**
 * Chapter information "CHAP" ID3 frame.
 */
public final class ChapFrame extends Id3Frame {

  public static final String ID = "CHAP";

  public final String chapterId;
  public final int startTime;
  public final int endTime;
  public final int startOffset;
  public final int endOffset;
  public final String title;
  public final String url;
  public final ApicFrame image;

  public ChapFrame(String chapterId, int startTime, int endTime, int startOffset, int endOffset,
                   String title, String url, ApicFrame image) {
    super(ID);
    this.chapterId = chapterId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.title = title;
    this.url = url;
    this.image = image;
  }

  /* package */ ChapFrame(Parcel in) {
    super(ID);
    this.chapterId = in.readString();
    this.startTime = in.readInt();
    this.endTime = in.readInt();
    this.startOffset = in.readInt();
    this.endOffset = in.readInt();
    this.title = in.readString();
    this.url = in.readString();
    this.image = in.readParcelable(ApicFrame.class.getClassLoader());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ChapFrame other = (ChapFrame) obj;
    return startTime == other.startTime
      && endTime == other.endTime
      && startOffset == other.startOffset
      && endOffset == other.endOffset
      && Util.areEqual(chapterId, other.chapterId)
      && Util.areEqual(title, other.title)
      && Util.areEqual(url, other.url)
      && Util.areEqual(image, other.image);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (chapterId != null ? chapterId.hashCode() : 0);
    result = 31 * result + startTime;
    result = 31 * result + endTime;
    result = 31 * result + startOffset;
    result = 31 * result + endOffset;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    result = 31 * result + (image != null ? image.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(chapterId);
    dest.writeInt(startTime);
    dest.writeInt(endTime);
    dest.writeInt(startOffset);
    dest.writeInt(endOffset);
    dest.writeString(title);
    dest.writeString(url);
    dest.writeString(title);
    dest.writeParcelable(image, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<ChapFrame> CREATOR = new Creator<ChapFrame>() {
    @Override
    public ChapFrame createFromParcel(Parcel in) {
      return new ChapFrame(in);
    }

    @Override
    public ChapFrame[] newArray(int size) {
      return new ChapFrame[size];
    }
  };
}

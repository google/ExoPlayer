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

import java.util.Arrays;

/**
 * Chapter table of contents information "CTOC" ID3 frame.
 */
public class ChapterTOCFrame extends Id3Frame {

  public static final String ID = "CTOC";

  public final String elementId;
  public final boolean isRoot;
  public final boolean isOrdered;
  public final String[] children;
  public final String title;

  public ChapterTOCFrame(String elementId, boolean isRoot, boolean isOrdered, String[] children, String title) {
    super(ID);
    this.elementId = elementId;
    this.isRoot = isRoot;
    this.isOrdered = isOrdered;
    this.children = children;
    this.title = title;
  }

  /* package */ ChapterTOCFrame(Parcel in) {
    super(ID);
    this.elementId = in.readString();
    this.isRoot = in.readByte() != 0;
    this.isOrdered = in.readByte() != 0;
    this.children = in.createStringArray();
    this.title = in.readString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ChapterTOCFrame other = (ChapterTOCFrame) obj;
    return elementId != null ? elementId.equals(other.elementId) : other.elementId == null
      && isRoot == other.isRoot
      && isOrdered == other.isOrdered
      && Arrays.equals(children, other.children)
      && title != null ? title.equals(other.title) : other.title == null;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + (isRoot ? 1 : 0);
    result = 31 * result + (isOrdered ? 1 : 0);
    result = 31 * result + Arrays.hashCode(children);
    result = 31 * result + (title != null ? title.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(this.elementId);
    dest.writeByte((byte)(this.isRoot ? 1 : 0));
    dest.writeByte((byte)(this.isOrdered ? 1 : 0));
    dest.writeStringArray(this.children);
    dest.writeString(this.title);
  }

  public static final Creator<ChapterTOCFrame> CREATOR = new Creator<ChapterTOCFrame>() {
    @Override
    public ChapterTOCFrame createFromParcel(Parcel in) {
      return new ChapterTOCFrame(in);
    }

    @Override
    public ChapterTOCFrame[] newArray(int size) {
      return new ChapterTOCFrame[size];
    }
  };
}

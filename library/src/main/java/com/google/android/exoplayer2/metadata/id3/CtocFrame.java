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

import java.util.Arrays;

/**
 * Chapter table of contents information "CTOC" ID3 frame.
 */
public final class CtocFrame extends Id3Frame {

  public static final String ID = "CTOC";

  public final String elementId;
  public final boolean isRoot;
  public final boolean isOrdered;
  public final String[] children;
  public final String title;

  public CtocFrame(String elementId, boolean isRoot, boolean isOrdered, String[] children, String title) {
    super(ID);
    this.elementId = elementId;
    this.isRoot = isRoot;
    this.isOrdered = isOrdered;
    this.children = children;
    this.title = title;
  }

  /* package */ CtocFrame(Parcel in) {
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
    CtocFrame other = (CtocFrame) obj;
    return isRoot == other.isRoot
      && isOrdered == other.isOrdered
      && Util.areEqual(elementId, other.elementId)
      && Util.areEqual(title, other.title)
      && Arrays.equals(children, other.children);
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
    dest.writeString(elementId);
    dest.writeByte((byte)(isRoot ? 1 : 0));
    dest.writeByte((byte)(isOrdered ? 1 : 0));
    dest.writeStringArray(children);
    dest.writeString(title);
  }

  public static final Creator<CtocFrame> CREATOR = new Creator<CtocFrame>() {
    @Override
    public CtocFrame createFromParcel(Parcel in) {
      return new CtocFrame(in);
    }

    @Override
    public CtocFrame[] newArray(int size) {
      return new CtocFrame[size];
    }
  };
}

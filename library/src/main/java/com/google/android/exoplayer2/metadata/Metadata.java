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
package com.google.android.exoplayer2.metadata;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.exoplayer2.extractor.GaplessInfo;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ID3 style metadata, with convenient access to gapless playback information.
 */
public class Metadata implements Parcelable {

  private final List<Id3Frame> frames;
  private final GaplessInfo gaplessInfo;

  public Metadata(List<Id3Frame> frames, GaplessInfo gaplessInfo) {
    List<Id3Frame> theFrames = frames != null ? new ArrayList<>(frames) : new ArrayList<Id3Frame>();
    this.frames = Collections.unmodifiableList(theFrames);
    this.gaplessInfo = gaplessInfo;
  }

  public Metadata(Parcel in) {
    int encoderDelay = in.readInt();
    int encoderPadding = in.readInt();
    gaplessInfo = encoderDelay > 0 || encoderPadding > 0 ?
        new GaplessInfo(encoderDelay, encoderPadding) : null;
    frames = Arrays.asList((Id3Frame[]) in.readArray(Id3Frame.class.getClassLoader()));
  }

  public Metadata withGaplessInfo(GaplessInfo info) {
    return new Metadata(frames, info);
  }

  public List<Id3Frame> getFrames() {
    return frames;
  }

  public GaplessInfo getGaplessInfo() {
    return gaplessInfo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Metadata that = (Metadata) o;

    if (!frames.equals(that.frames)) return false;
    return gaplessInfo != null ? gaplessInfo.equals(that.gaplessInfo) : that.gaplessInfo == null;
  }

  @Override
  public int hashCode() {
    int result = frames.hashCode();
    result = 31 * result + (gaplessInfo != null ? gaplessInfo.hashCode() : 0);
    return result;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(gaplessInfo != null ? gaplessInfo.encoderDelay : -1);
    dest.writeInt(gaplessInfo != null ? gaplessInfo.encoderPadding : -1);
    dest.writeArray(frames.toArray(new Id3Frame[frames.size()]));
  }

  public static final Parcelable.Creator<Metadata> CREATOR =
      new Parcelable.Creator<Metadata>() {
        @Override
        public Metadata createFromParcel(Parcel in) {
          return new Metadata(in);
        }

        @Override
        public Metadata[] newArray(int size) {
          return new Metadata[0];
        }
      };
}

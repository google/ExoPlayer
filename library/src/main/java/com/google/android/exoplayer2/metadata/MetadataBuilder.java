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

import com.google.android.exoplayer2.extractor.GaplessInfo;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for ID3 style metadata.
 */
public class MetadataBuilder {
  private List<Id3Frame> frames = new ArrayList<>();
  private GaplessInfo gaplessInfo;

  public void add(Id3Frame frame) {
    frames.add(frame);
  }

  public void setGaplessInfo(GaplessInfo info) {
    this.gaplessInfo = info;
  }

  public Metadata build() {
    return !frames.isEmpty() || gaplessInfo != null ? new Metadata(frames, gaplessInfo): null;
  }
}

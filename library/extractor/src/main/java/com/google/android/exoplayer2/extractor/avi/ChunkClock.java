/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

/**
 * A clock that is linearly derived from the current chunk index of a given stream
 */
public class ChunkClock {
  long durationUs;
  int chunks;

  int index;

  public ChunkClock(long durationUs, int chunks) {
    this.durationUs = durationUs;
    this.chunks = chunks;
  }

  public void setDuration(long durationUs) {
    this.durationUs = durationUs;
  }

  public void setChunks(int length) {
    this.chunks = length;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public void advance() {
    index++;
  }

  public long getUs() {
    return getUs(index);
  }

  long getUs(int index) {
    //Doing this the hard way lessens round errors
    return durationUs * index / chunks;
  }
}

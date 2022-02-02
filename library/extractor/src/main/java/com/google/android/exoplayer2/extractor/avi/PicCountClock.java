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

import androidx.annotation.VisibleForTesting;

/**
 * Properly calculates the frame time for H264 frames using PicCount
 */
public class PicCountClock extends LinearClock {
  //The frame as a calculated from the picCount
  private int picIndex;
  private int lastPicCount;
  //Largest picFrame, used when we hit an I frame
  private int maxPicIndex =-1;
  private int maxPicCount;
  private int step = 2;
  private int posHalf;
  private int negHalf;

  public PicCountClock(long durationUs, int length) {
    super(durationUs, length);
  }

  public void setMaxPicCount(int maxPicCount, int step) {
    this.maxPicCount = maxPicCount;
    this.step = step;
    posHalf = maxPicCount / step;
    negHalf = -posHalf;
  }

  /**
   * Used primarily on seek.  May cause issues if not a key frame
   */
  @Override
  public void setIndex(int index) {
    super.setIndex(index);
    syncIndexes();
  }

  public void setPicCount(int picCount) {
    int delta = picCount - lastPicCount;
    if (delta < negHalf) {
      delta += maxPicCount;
    } else if (delta > posHalf) {
      delta -= maxPicCount;
    }
    picIndex += delta / step;
    lastPicCount = picCount;
    if (maxPicIndex < picIndex) {
      maxPicIndex = picIndex;
    }
  }

  /**
   * Handle key frame
   */
  public void syncIndexes() {
    lastPicCount = 0;
    maxPicIndex = picIndex = getIndex();
  }

  @Override
  public long getUs() {
    return getUs(picIndex);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  int getMaxPicCount() {
    return maxPicCount;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  int getLastPicCount() {
    return lastPicCount;
  }
}

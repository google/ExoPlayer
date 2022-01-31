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

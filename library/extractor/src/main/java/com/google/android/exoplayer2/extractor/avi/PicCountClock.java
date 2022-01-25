package com.google.android.exoplayer2.extractor.avi;

/**
 * Properly calculates the frame time for H264 frames using PicCount
 */
public class PicCountClock extends LinearClock {
  //I believe this is 2 because there is a bottom pic order and a top pic order
  private static final int STEP = 2;
  //The frame as a calculated from the picCount
  private int picIndex;
  private int lastPicCount;
  //Largest picFrame, used when we hit an I frame
  private int maxPicIndex =-1;
  private int maxPicCount;
  private int posHalf;
  private int negHalf;

  public PicCountClock(long usPerFrame) {
    super(usPerFrame);
  }

  public void setMaxPicCount(int maxPicCount) {
    this.maxPicCount = maxPicCount;
    posHalf = maxPicCount / STEP;
    negHalf = -posHalf;
  }

  /**
   * Done on seek.  May cause sync issues if frame picCount != 0 (I frames are always 0)
   * @param index
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
    picIndex += delta / STEP;
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
    return picIndex * usPerChunk;
  }
}

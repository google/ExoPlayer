package com.google.android.exoplayer2.extractor.avi;

public class LinearClock {
  long usPerChunk;

  int index;

  public LinearClock(long usPerChunk) {
    this.usPerChunk = usPerChunk;
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
    return index * usPerChunk;
  }
}

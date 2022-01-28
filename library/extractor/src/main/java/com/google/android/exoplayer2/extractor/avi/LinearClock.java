package com.google.android.exoplayer2.extractor.avi;

public class LinearClock {
  long durationUs;
  int length;

  int index;

  public LinearClock(long durationUs, int length) {
    this.durationUs = durationUs;
    this.length = length;
  }

  public void setDuration(long durationUs) {
    this.durationUs = durationUs;
  }

  public void setLength(int length) {
    this.length = length;
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
    return durationUs * index / length;
  }
}

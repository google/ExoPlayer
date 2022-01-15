package com.google.android.exoplayer2.extractor.avi;

public class Box {
  private final long size;
  private final int type;

  Box(int type, long size) {
    this.type = type;
    this.size = size;
  }

  public long getSize() {
    return size;
  }

  public int getType() {
    return type;
  }

  boolean simpleAssert(final int expected) {
     return getType() == expected;
  }

  boolean assertType() {
    //Generic box, nothing to assert
    return true;
  }
}

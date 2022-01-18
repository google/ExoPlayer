package com.google.android.exoplayer2.extractor.avi;

/**
 * This is referred to as a Chunk in the MS spec, but that gets confusing with AV chunks
 */
public class Box {
  private final int size;
  private final int type;

  Box(int type, int size) {
    this.type = type;
    this.size = size;
  }

  public long getSize() {
    return size & AviUtil.UINT_MASK;
  }

  public int getSizeInt() {
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

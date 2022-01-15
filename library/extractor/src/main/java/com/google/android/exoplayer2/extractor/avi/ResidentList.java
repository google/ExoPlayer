package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

/**
 * An AVI LIST box, memory resident
 */
public class ResidentList extends ResidentBox implements IAviList {
  private final int listType;

  ResidentList(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
    listType = byteBuffer.getInt(0);
  }

  @Override
  public int getListType() {
    return listType;
  }

  @Override
  boolean assertType() {
    return simpleAssert(LIST);
  }
}

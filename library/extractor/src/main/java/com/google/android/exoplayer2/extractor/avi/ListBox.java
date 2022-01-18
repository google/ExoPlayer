package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

/**
 * An AVI LIST box, memory resident
 */
public class ListBox extends ResidentBox {
  public static final int LIST = 'L' | ('I' << 8) | ('S' << 16) | ('T' << 24);
  //Header List
  public static final int TYPE_HDRL = 'h' | ('d' << 8) | ('r' << 16) | ('l' << 24);

  private final int listType;

  ListBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
    listType = byteBuffer.getInt(0);
  }

  public int getListType() {
    return listType;
  }

  @Override
  boolean assertType() {
    return simpleAssert(LIST);
  }
}

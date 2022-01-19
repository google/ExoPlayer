package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class AviHeaderBox extends ResidentBox {
  public static final int AVIF_HASINDEX = 0x10;
  static final int AVIH = 'a' | ('v' << 8) | ('i' << 16) | ('h' << 24);

  //AVIMAINHEADER

  AviHeaderBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  boolean hasIndex() {
    return (getFlags() & AVIF_HASINDEX) > 0;
  }

  int getMicroSecPerFrame() {
    return byteBuffer.getInt(0);
  }

  //4 = dwMaxBytesPerSec
  //Always 0, but should be 2
//  int getPaddingGranularity() {
//    return byteBuffer.getInt(8);
//  }

  int getFlags() {
    return byteBuffer.getInt(12);
  }

  int getFrames() {
    return byteBuffer.getInt(16);
  }
  //20 = dwInitialFrames

  int getSuggestedBufferSize() {
    return byteBuffer.getInt(24);
  }

  int getWidth() {
    return byteBuffer.getInt(28);
  }

  int getHeight() {
    return byteBuffer.getInt(32);
  }
}

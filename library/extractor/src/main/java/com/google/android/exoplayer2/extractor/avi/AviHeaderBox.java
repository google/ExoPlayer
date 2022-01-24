package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class AviHeaderBox extends ResidentBox {
  private static final int AVIF_HASINDEX = 0x10;
  private static final int AVIF_MUSTUSEINDEX = 0x20;
  static final int AVIH = 'a' | ('v' << 8) | ('i' << 16) | ('h' << 24);

  //AVIMAINHEADER

  AviHeaderBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  int getMicroSecPerFrame() {
    return byteBuffer.getInt(0);
  }

  //4 = dwMaxBytesPerSec
  //8 = dwPaddingGranularity - Always 0, but should be 2

  public boolean hasIndex() {
    return (getFlags() & AVIF_HASINDEX) == AVIF_HASINDEX;
  }

  public boolean mustUseIndex() {
    return (getFlags() & AVIF_MUSTUSEINDEX) == AVIF_MUSTUSEINDEX;
  }

  int getFlags() {
    return byteBuffer.getInt(12);
  }

  int getTotalFrames() {
    return byteBuffer.getInt(16);
  }

  // 20 - dwInitialFrames
//  int getInitialFrames() {
//    return byteBuffer.getInt(20);
//  }

  int getStreams() {
    return byteBuffer.getInt(24);
  }

  // 28 - dwSuggestedBufferSize
//  int getSuggestedBufferSize() {
//    return byteBuffer.getInt(28);
//  }
//
//  int getWidth() {
//    return byteBuffer.getInt(32);
//  }
//
//  int getHeight() {
//    return byteBuffer.getInt(36);
//  }
}

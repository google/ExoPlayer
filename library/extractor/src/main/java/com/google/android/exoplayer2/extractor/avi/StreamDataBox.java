package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class StreamDataBox extends ResidentBox {
  //Stream CODEC data
  static final int STRD = 's' | ('t' << 8) | ('r' << 16) | ('d' << 24);

  StreamDataBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }
  byte[] getData() {
    byte[] data = new byte[byteBuffer.capacity()];
    System.arraycopy(byteBuffer.array(), 0, data, 0, data.length);
    return data;
  }
}

package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class StreamNameBox extends ResidentBox {
  public static final int STRN = 's' | ('t' << 8) | ('r' << 16) | ('n' << 24);

  StreamNameBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  public String getName() {
    int len = byteBuffer.capacity();
    if (byteBuffer.get(len - 1) == 0) {
      len -= 1;
    }
    final byte[] bytes = new byte[len];
    byteBuffer.position(0);
    byteBuffer.get(bytes);
    return new String(bytes);
  }
}

package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class VideoFormat {
  private final ByteBuffer byteBuffer;

  public VideoFormat(final ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  //biSize - (uint)

  public int getWidth() {
    return byteBuffer.getInt(4);
  }
  public int getHeight() {
    return byteBuffer.getInt(8);
  }

}

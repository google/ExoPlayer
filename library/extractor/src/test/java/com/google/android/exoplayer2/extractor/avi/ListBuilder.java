package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

public class ListBuilder {
  private ByteBuffer byteBuffer;

  public ListBuilder(int listType) {
    byteBuffer = AviExtractor.allocate(12);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(12);
    byteBuffer.putInt(listType);
  }

  public void addBox(final ResidentBox box) {
    long boxLen = 4 + 4 + box.getSize();
    if ((boxLen & 1) == 1) {
      boxLen++;
    }
    final ByteBuffer boxBuffer = AviExtractor.allocate(byteBuffer.capacity() + (int)boxLen);
    byteBuffer.clear();
    boxBuffer.put(byteBuffer);
    boxBuffer.putInt(box.getType());
    boxBuffer.putInt((int)box.getSize());
    boxBuffer.put(box.getByteBuffer());
    byteBuffer = boxBuffer;
  }
  public ByteBuffer build() {
    byteBuffer.putInt(4, byteBuffer.capacity() - 8);
    return byteBuffer;
  }
}

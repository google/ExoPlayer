package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import java.nio.ByteBuffer;

public class BoxFactory {
  @NonNull
  public ResidentBox createBox(final int type, final int size, final ByteBuffer byteBuffer) {
    final ByteBuffer boxBuffer = AviExtractor.allocate(size);
    AviUtil.copy(byteBuffer, boxBuffer, size);

    switch (type) {
      case AviHeaderBox.AVIH:
        return new AviHeaderBox(type, size, boxBuffer);
      case ListBox.LIST:
        return new ListBox(type, size, boxBuffer);
      case StreamHeaderBox.STRH:
        return new StreamHeaderBox(type, size, boxBuffer);
      case StreamFormatBox.STRF:
        return new StreamFormatBox(type, size, boxBuffer);
      default:
        return new ResidentBox(type, size, boxBuffer);
    }
  }
}

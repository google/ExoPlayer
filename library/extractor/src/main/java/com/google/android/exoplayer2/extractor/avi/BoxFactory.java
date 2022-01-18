package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class BoxFactory {
  static int[] types = {AviHeaderBox.AVIH, StreamHeaderBox.STRH, StreamFormatBox.STRF, StreamDataBox.STRD};
  static {
    Arrays.sort(types);
  }

  public boolean isUnknown(final int type) {
    return Arrays.binarySearch(types, type) < 0;
  }

  @Nullable
  public ResidentBox createBox(final int type, final int size, final ByteBuffer byteBuffer) {
    final ByteBuffer boxBuffer = AviExtractor.allocate(size);
    AviUtil.copy(byteBuffer, boxBuffer, size);
    //TODO: Deal with list
    switch (type) {
      case AviHeaderBox.AVIH:
        return new AviHeaderBox(type, size, boxBuffer);
      case StreamHeaderBox.STRH:
        return new StreamHeaderBox(type, size, boxBuffer);
      case StreamFormatBox.STRF:
        return new StreamFormatBox(type, size, boxBuffer);
      case StreamDataBox.STRD:
        return new StreamDataBox(type, size, boxBuffer);
      default:
        return null;
    }
  }

  private ResidentBox createBoxImpl(final int type, final int size, final ByteBuffer boxBuffer) {
    switch (type) {
      case AviHeaderBox.AVIH:
        return new AviHeaderBox(type, size, boxBuffer);
      case StreamHeaderBox.STRH:
        return new StreamHeaderBox(type, size, boxBuffer);
      case StreamFormatBox.STRF:
        return new StreamFormatBox(type, size, boxBuffer);
      default:
        return null;
    }
  }

  public ResidentBox createBox(final int type, final int size, ExtractorInput input) throws IOException {
    if (isUnknown(type)) {
      input.skipFully(size);
      return null;
    }
    final ByteBuffer boxBuffer = AviExtractor.allocate(size);
    input.readFully(boxBuffer.array(),0,size);
    return createBoxImpl(type, size, boxBuffer);
  }
}

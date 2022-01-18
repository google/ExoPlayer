package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class AviUtil {

  static final long UINT_MASK = 0xffffffffL;

  static int toInt(byte[] bytes) {
    int i = 0;
    for (int b=bytes.length - 1;b>=0;b--) {
      i <<=8;
      i |= bytes[b];
    }
    return i;
  }

  static long getUInt(ByteBuffer byteBuffer) {
    return byteBuffer.getInt() & UINT_MASK;
  }

  static void copy(ByteBuffer source, ByteBuffer dest, int bytes) {
    final int inLimit = source.limit();
    source.limit(source.position() + bytes);
    dest.put(source);
    source.limit(inLimit);
  }

  static ByteBuffer getByteBuffer(final ByteBuffer source, final int size,
      final ExtractorInput input) throws IOException {
    final ByteBuffer byteBuffer = AviExtractor.allocate(size);
    if (size < source.remaining()) {
      copy(source, byteBuffer, size);
    } else {
      final int copy = source.remaining();
      copy(source, byteBuffer, copy);
      int remaining = size - copy;
      final int offset = byteBuffer.position() + byteBuffer.arrayOffset();
      input.readFully(byteBuffer.array(), offset, remaining, false);
    }
    return byteBuffer;
  }

  @NonNull
  static String toString(int tag) {
    final StringBuilder sb = new StringBuilder(4);
    for (int i=0;i<4;i++) {
      sb.append((char)(tag & 0xff));
      tag >>=8;
    }
    return sb.toString();
  }

  @Nullable
  static <T extends Box> T getBox(List<? extends Box> list, Class<T> clazz) {
    for (Box box : list) {
      if (box.getClass() == clazz) {
        return (T)box;
      }
    }
    return null;
  }
}

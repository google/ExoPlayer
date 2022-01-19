package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A box that is resident in memory
 */
public class ResidentBox extends Box {
  private static final String TAG = AviExtractor.TAG;
  final private static int MAX_RESIDENT = 1024;
  final ByteBuffer byteBuffer;

  ResidentBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size);
    this.byteBuffer = byteBuffer;
  }

  /**
   * Returns shallow copy of this ByteBuffer with the position at 0
   * @return
   */
  @NonNull
  public ByteBuffer getByteBuffer() {
    final ByteBuffer clone = byteBuffer.duplicate();
    clone.order(ByteOrder.LITTLE_ENDIAN);
    clone.clear();
    return clone;
  }
}

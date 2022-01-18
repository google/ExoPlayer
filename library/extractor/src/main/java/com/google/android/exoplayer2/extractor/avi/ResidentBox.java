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
   * List is not yet populated
   * @param byteBuffer
   * @return
   * @throws IOException
   */
  @Nullable
  public static <T extends ResidentBox> T getInstance(final ByteBuffer byteBuffer,
      ExtractorInput input, Class<T> boxClass) throws IOException  {
    if (byteBuffer.remaining() < 8) {
      //Should not happen
      throw new BufferUnderflowException();
    }
    final int type = byteBuffer.getInt();
    final long size = AviUtil.getUInt(byteBuffer);
    if (size > MAX_RESIDENT) {
      throw new BufferOverflowException();
    }
    final ByteBuffer boxBuffer = AviUtil.getByteBuffer(byteBuffer, (int)size, input);
    return newInstance(type, (int)size, boxBuffer, boxClass);
  }

  @Nullable
  private static <T extends ResidentBox> T newInstance(int type, int size, ByteBuffer boxBuffer,
      Class<T> boxClass) {
    try {
      final Constructor<T> constructor =
          boxClass.getDeclaredConstructor(int.class, int.class, ByteBuffer.class);
      T box = constructor.newInstance(type, size, boxBuffer);
      if (!box.assertType()) {
        Log.e(TAG, "Expected " + AviUtil.toString(type) + " got " + AviUtil.toString(box.getType()));
        return null;
      }
      return box;
    } catch (Exception e) {
      Log.e(TAG, "Create box failed " + AviUtil.toString(type));
      return null;
    }
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

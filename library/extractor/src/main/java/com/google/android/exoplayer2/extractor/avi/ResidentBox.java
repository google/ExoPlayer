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
import java.util.ArrayList;
import java.util.List;

public class ResidentBox extends Box {
  private static final String TAG = AviExtractor.TAG;
  final private static int MAX_RESIDENT = 2*1024;
  final ByteBuffer byteBuffer;

  private Class<? extends ResidentBox> getClass(final int type) {
    switch (type) {
      case AviHeader.AVIH:
        return AviHeader.class;
      case IAviList.LIST:
        return ResidentList.class;
      case StreamHeader.STRH:
        return StreamHeader.class;
      case StreamFormat.STRF:
        return StreamFormat.class;
      default:
        return ResidentBox.class;
    }
  }

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

  @NonNull
  public List<ResidentBox> getBoxList() {
    final ByteBuffer temp = getByteBuffer();
    temp.position(4);
    final List<ResidentBox> list = new ArrayList<>();
    while (temp.hasRemaining()) {
      final int type = temp.getInt();
      final int size = temp.getInt();
      final Class<? extends ResidentBox> clazz = getClass(type);
      final ByteBuffer boxBuffer = AviExtractor.allocate(size);
      AviUtil.copy(temp, boxBuffer, size);
      final ResidentBox residentBox = newInstance(type, size, boxBuffer, clazz);
      if (residentBox == null) {
        break;
      }
      list.add(residentBox);
    }
    return list;
  }
}

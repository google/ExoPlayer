/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.muxer;

import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.List;

/** Utilities for dealing with MP4 boxes. */
/* package */ final class BoxUtils {
  private static final int BOX_TYPE_BYTES = 4;
  private static final int BOX_SIZE_BYTES = 4;

  private BoxUtils() {}

  /** Wraps content into a box, prefixing it with a length and a box type. */
  public static ByteBuffer wrapIntoBox(String boxType, ByteBuffer contents) {
    byte[] typeByteArray = boxType.getBytes(Charsets.UTF_8);
    return wrapIntoBox(typeByteArray, contents);
  }

  /**
   * Wraps content into a box, prefixing it with a length and a box type.
   *
   * <p>Use this method for box types with special characters. For example location box, which has a
   * copyright symbol in the beginning.
   */
  public static ByteBuffer wrapIntoBox(byte[] boxType, ByteBuffer contents) {
    ByteBuffer box = ByteBuffer.allocate(contents.remaining() + BOX_TYPE_BYTES + BOX_SIZE_BYTES);
    box.putInt(contents.remaining() + BOX_TYPE_BYTES + BOX_SIZE_BYTES);
    box.put(boxType, 0, BOX_SIZE_BYTES);
    box.put(contents);
    box.flip();
    return box;
  }

  /** Concatenate multiple boxes into a box, prefixing it with a length and a box type. */
  public static ByteBuffer wrapBoxesIntoBox(String boxType, List<ByteBuffer> boxes) {
    int totalSize = BOX_TYPE_BYTES + BOX_SIZE_BYTES;
    for (int i = 0; i < boxes.size(); i++) {
      totalSize += boxes.get(i).limit();
    }

    ByteBuffer result = ByteBuffer.allocate(totalSize);
    result.putInt(totalSize);
    result.put(boxType.getBytes(Charsets.UTF_8), 0, BOX_TYPE_BYTES);

    for (int i = 0; i < boxes.size(); i++) {
      result.put(boxes.get(i));
    }

    result.flip();
    return result;
  }

  /**
   * Concatenates multiple {@linkplain ByteBuffer byte buffers} into a single {@link ByteBuffer}.
   */
  public static ByteBuffer concatenateBuffers(ByteBuffer... buffers) {
    int totalSize = 0;
    for (ByteBuffer buffer : buffers) {
      totalSize += buffer.limit();
    }

    ByteBuffer result = ByteBuffer.allocate(totalSize);
    for (ByteBuffer buffer : buffers) {
      result.put(buffer);
    }

    result.flip();
    return result;
  }
}

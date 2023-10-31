/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.decoder;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Buffer for {@link SimpleDecoder} output. */
@UnstableApi
public class SimpleDecoderOutputBuffer extends DecoderOutputBuffer {

  private final Owner<SimpleDecoderOutputBuffer> owner;

  @Nullable public ByteBuffer data;

  public SimpleDecoderOutputBuffer(Owner<SimpleDecoderOutputBuffer> owner) {
    this.owner = owner;
  }

  /**
   * Initializes the buffer.
   *
   * @param timeUs The presentation timestamp for the buffer, in microseconds.
   * @param size An upper bound on the size of the data that will be written to the buffer.
   * @return The {@link #data} buffer, for convenience.
   */
  public ByteBuffer init(long timeUs, int size) {
    this.timeUs = timeUs;
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
    data.position(0);
    data.limit(size);
    return data;
  }

  /**
   * Grows the buffer to a new size.
   *
   * <p>Existing data is copied to the new buffer, and {@link ByteBuffer#position} is preserved.
   *
   * @param newSize New size of the buffer.
   * @return The {@link #data} buffer, for convenience.
   */
  public ByteBuffer grow(int newSize) {
    ByteBuffer oldData = Assertions.checkNotNull(this.data);
    Assertions.checkArgument(newSize >= oldData.limit());
    ByteBuffer newData = ByteBuffer.allocateDirect(newSize).order(ByteOrder.nativeOrder());
    int restorePosition = oldData.position();
    oldData.position(0);
    newData.put(oldData);
    newData.position(restorePosition);
    newData.limit(newSize);
    this.data = newData;
    return newData;
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }
}

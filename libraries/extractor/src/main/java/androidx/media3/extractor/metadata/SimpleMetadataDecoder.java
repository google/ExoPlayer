/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.extractor.metadata;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** A {@link MetadataDecoder} base class that validates input buffers. */
@UnstableApi
public abstract class SimpleMetadataDecoder implements MetadataDecoder {

  @Override
  @Nullable
  public final Metadata decode(MetadataInputBuffer inputBuffer) {
    ByteBuffer buffer = Assertions.checkNotNull(inputBuffer.data);
    Assertions.checkArgument(
        buffer.position() == 0 && buffer.hasArray() && buffer.arrayOffset() == 0);
    return decode(inputBuffer, buffer);
  }

  /**
   * Called by {@link #decode(MetadataInputBuffer)} after input buffer validation has been
   * performed.
   *
   * @param inputBuffer The input buffer to decode.
   * @param buffer The input buffer's {@link MetadataInputBuffer#data data buffer}, for convenience.
   *     Validation by {@link #decode} guarantees that {@link ByteBuffer#hasArray()}, {@link
   *     ByteBuffer#position()} and {@link ByteBuffer#arrayOffset()} are {@code true}, {@code 0} and
   *     {@code 0} respectively.
   * @return The decoded metadata object, or {@code null} if the metadata could not be decoded.
   */
  @Nullable
  protected abstract Metadata decode(MetadataInputBuffer inputBuffer, ByteBuffer buffer);
}

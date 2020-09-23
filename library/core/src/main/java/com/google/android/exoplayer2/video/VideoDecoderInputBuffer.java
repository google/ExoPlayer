/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/** Input buffer to a video decoder. */
public class VideoDecoderInputBuffer extends DecoderInputBuffer {

  @Nullable public Format format;

  /**
   * Creates a new instance.
   *
   * @param bufferReplacementMode Determines the behavior of {@link #ensureSpaceForWrite(int)}. One
   *     of {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
   *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  public VideoDecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
    super(bufferReplacementMode);
  }

  /**
   * Creates a new instance.
   *
   * @param bufferReplacementMode Determines the behavior of {@link #ensureSpaceForWrite(int)}. One
   *     of {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
   *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
   * @param paddingSize If non-zero, {@link #ensureSpaceForWrite(int)} will ensure that the buffer
   *     is this number of bytes larger than the requested length. This can be useful for decoders
   *     that consume data in fixed size blocks, for efficiency. Setting the padding size to the
   *     decoder's fixed read size is necessary to prevent such a decoder from trying to read beyond
   *     the end of the buffer.
   */
  public VideoDecoderInputBuffer(
      @BufferReplacementMode int bufferReplacementMode, int paddingSize) {
    super(bufferReplacementMode, paddingSize);
  }
}

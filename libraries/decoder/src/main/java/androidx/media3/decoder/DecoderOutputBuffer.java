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

import androidx.annotation.CallSuper;
import androidx.media3.common.util.UnstableApi;

/** Output buffer decoded by a {@link Decoder}. */
@UnstableApi
public abstract class DecoderOutputBuffer extends Buffer {

  /** Buffer owner. */
  public interface Owner<S extends DecoderOutputBuffer> {

    /**
     * Releases the buffer.
     *
     * @param outputBuffer Output buffer.
     */
    void releaseOutputBuffer(S outputBuffer);
  }

  /** The presentation timestamp for the buffer, in microseconds. */
  public long timeUs;

  /**
   * The number of buffers immediately prior to this one that were skipped in the {@link Decoder}.
   */
  public int skippedOutputBufferCount;

  /**
   * Whether this buffer should be skipped, usually because the decoding process generated no data
   * or invalid data.
   */
  public boolean shouldBeSkipped;

  /** Releases the output buffer for reuse. Must be called when the buffer is no longer needed. */
  public abstract void release();

  @Override
  @CallSuper
  public void clear() {
    super.clear();
    timeUs = 0;
    skippedOutputBufferCount = 0;
    shouldBeSkipped = false;
  }
}

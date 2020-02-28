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

package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import com.google.android.exoplayer2.decoder.CryptoInfo;

/**
 * A {@link MediaCodecInputBufferEnqueuer} that forwards queueing methods directly to {@link
 * MediaCodec}.
 */
class SynchronousMediaCodecBufferEnqueuer implements MediaCodecInputBufferEnqueuer {
  private final MediaCodec codec;

  /**
   * Creates an instance that queues input buffers on the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to submit input buffers to.
   */
  SynchronousMediaCodecBufferEnqueuer(MediaCodec codec) {
    this.codec = codec;
  }

  @Override
  public void start() {}

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    codec.queueSecureInputBuffer(
        index, offset, info.getFrameworkCryptoInfo(), presentationTimeUs, flags);
  }

  @Override
  public void flush() {}

  @Override
  public void shutdown() {}
}

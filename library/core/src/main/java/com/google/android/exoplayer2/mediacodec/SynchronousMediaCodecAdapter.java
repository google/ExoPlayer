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

package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in synchronous mode.
 */
/* package */ final class SynchronousMediaCodecAdapter implements MediaCodecAdapter {
  private final MediaCodec codec;
  private final long dequeueOutputBufferTimeoutMs;

  public SynchronousMediaCodecAdapter(MediaCodec mediaCodec, long dequeueOutputBufferTimeoutMs) {
    this.codec = mediaCodec;
    this.dequeueOutputBufferTimeoutMs = dequeueOutputBufferTimeoutMs;
  }

  @Override
  public int dequeueInputBufferIndex() {
    return codec.dequeueInputBuffer(0);
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    return codec.dequeueOutputBuffer(bufferInfo, dequeueOutputBufferTimeoutMs);
  }

  @Override
  public MediaFormat getOutputFormat() {
    return codec.getOutputFormat();
  }

  @Override
  public void flush() {
    codec.flush();
  }

  @Override
  public void shutdown() {}
}

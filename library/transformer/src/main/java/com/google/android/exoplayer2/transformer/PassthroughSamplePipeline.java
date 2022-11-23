/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/** Pipeline that passes through the samples without any re-encoding or transformation. */
/* package */ final class PassthroughSamplePipeline extends BaseSamplePipeline {

  private final DecoderInputBuffer buffer;
  private final Format format;

  private boolean hasPendingBuffer;

  public PassthroughSamplePipeline(
      Format format,
      long streamOffsetUs,
      long streamStartPositionUs,
      TransformationRequest transformationRequest,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener) {
    super(
        format,
        streamOffsetUs,
        streamStartPositionUs,
        transformationRequest.flattenForSlowMotion,
        muxerWrapper);
    this.format = format;
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    fallbackListener.onTransformationRequestFinalized(transformationRequest);
  }

  @Override
  public void release() {}

  @Override
  @Nullable
  protected DecoderInputBuffer dequeueInputBufferInternal() {
    return hasPendingBuffer ? null : buffer;
  }

  @Override
  protected void queueInputBufferInternal() {
    if (buffer.data != null && buffer.data.hasRemaining()) {
      hasPendingBuffer = true;
    }
  }

  @Override
  protected boolean processDataUpToMuxer() {
    return false;
  }

  @Override
  protected Format getMuxerInputFormat() {
    return format;
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() {
    return hasPendingBuffer ? buffer : null;
  }

  @Override
  protected void releaseMuxerInputBuffer() {
    buffer.clear();
    hasPendingBuffer = false;
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return buffer.isEndOfStream();
  }
}

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

package androidx.media3.transformer;

import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** Muxes encoded samples without any transcoding or transformation. */
/* package */ final class EncodedSampleExporter extends SampleExporter implements GraphInput {

  private static final int MAX_INPUT_BUFFER_COUNT = 10;

  private final Format format;
  private final AtomicLong nextMediaItemOffsetUs;
  private final Queue<DecoderInputBuffer> availableInputBuffers;
  private final Queue<DecoderInputBuffer> pendingInputBuffers;

  private long mediaItemOffsetUs;

  private volatile boolean inputEnded;

  public EncodedSampleExporter(
      Format format,
      TransformationRequest transformationRequest,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener) {
    super(format, muxerWrapper);
    this.format = format;
    nextMediaItemOffsetUs = new AtomicLong();
    availableInputBuffers = new ConcurrentLinkedDeque<>();
    ByteBuffer emptyBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT; i++) {
      DecoderInputBuffer inputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
      inputBuffer.data = emptyBuffer;
      availableInputBuffers.add(inputBuffer);
    }
    pendingInputBuffers = new ConcurrentLinkedDeque<>();
    fallbackListener.onTransformationRequestFinalized(transformationRequest);
  }

  @Override
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format trackFormat,
      boolean isLast) {
    mediaItemOffsetUs = nextMediaItemOffsetUs.get();
    nextMediaItemOffsetUs.addAndGet(durationUs);
  }

  @Override
  @Nullable
  public DecoderInputBuffer getInputBuffer() {
    return availableInputBuffers.peek();
  }

  @Override
  public boolean queueInputBuffer() {
    DecoderInputBuffer inputBuffer = availableInputBuffers.remove();
    if (inputBuffer.isEndOfStream()) {
      inputEnded = true;
    } else {
      inputBuffer.timeUs += mediaItemOffsetUs;
      pendingInputBuffers.add(inputBuffer);
    }
    return true;
  }

  @Override
  public GraphInput getInput(EditedMediaItem item, Format format) {
    return this;
  }

  @Override
  public void release() {}

  @Override
  protected Format getMuxerInputFormat() {
    return format;
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() {
    return pendingInputBuffers.peek();
  }

  @Override
  protected void releaseMuxerInputBuffer() {
    DecoderInputBuffer inputBuffer = pendingInputBuffers.remove();
    inputBuffer.clear();
    inputBuffer.timeUs = 0;
    availableInputBuffers.add(inputBuffer);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return inputEnded && pendingInputBuffers.isEmpty();
  }
}

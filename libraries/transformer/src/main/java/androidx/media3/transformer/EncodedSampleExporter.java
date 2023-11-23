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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Muxes encoded samples without any transcoding or transformation. */
/* package */ final class EncodedSampleExporter extends SampleExporter implements GraphInput {

  // These constants limit the number of buffers used to pass input data. More buffers can avoid the
  // producer/consumer having to wait, but can increase allocation size (determined by the producer
  // side) so we constrain the number of buffers to be in this range, and prevent allocating more
  // buffers (above the minimum number) once a target size has been reached. Once the target has
  // been reached, no new buffers will be created but the producer can still increase the size of
  // existing buffers.
  @VisibleForTesting /* package */ static final int MIN_INPUT_BUFFER_COUNT = 10;
  @VisibleForTesting /* package */ static final int MAX_INPUT_BUFFER_COUNT = 200;
  @VisibleForTesting /* package */ static final long ALLOCATION_SIZE_TARGET_BYTES = 2 * 1024 * 1024;

  /** An empty, direct {@link ByteBuffer}. */
  private static final ByteBuffer EMPTY_BUFFER =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  private final Format format;
  private final long initialTimestampOffsetUs;
  private final AtomicLong nextMediaItemOffsetUs;
  private final Queue<DecoderInputBuffer> availableInputBuffers;
  private final Queue<DecoderInputBuffer> pendingInputBuffers;

  // Accessed on the producer and consumer threads.

  private volatile boolean inputEnded;

  // Accessed only on the producer thread.

  private long mediaItemOffsetUs;
  private boolean hasReachedAllocationTarget;
  private long totalBufferSizeBytes;
  @Nullable private DecoderInputBuffer nextInputBuffer;

  public EncodedSampleExporter(
      Format format,
      TransformationRequest transformationRequest,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener,
      long initialTimestampOffsetUs) {
    super(format, muxerWrapper);
    this.format = format;
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
    nextMediaItemOffsetUs = new AtomicLong();
    availableInputBuffers = new ConcurrentLinkedQueue<>();
    pendingInputBuffers = new ConcurrentLinkedQueue<>();
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
    if (nextInputBuffer == null) {
      nextInputBuffer = availableInputBuffers.poll();
      if (!hasReachedAllocationTarget) {
        if (nextInputBuffer == null) {
          nextInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
          nextInputBuffer.data = EMPTY_BUFFER;
        } else {
          // The size of this buffer has already been accounted for but the producer may reallocate
          // it so remove it from the total and add it back when it's queued again.
          totalBufferSizeBytes -= checkNotNull(nextInputBuffer.data).capacity();
        }
      }
    }
    return nextInputBuffer;
  }

  @Override
  public boolean queueInputBuffer() {
    DecoderInputBuffer inputBuffer = checkNotNull(nextInputBuffer);
    nextInputBuffer = null;
    if (inputBuffer.isEndOfStream()) {
      inputEnded = true;
    } else {
      inputBuffer.timeUs += mediaItemOffsetUs + initialTimestampOffsetUs;
      pendingInputBuffers.add(inputBuffer);
    }
    if (!hasReachedAllocationTarget) {
      int bufferCount = availableInputBuffers.size() + pendingInputBuffers.size();
      totalBufferSizeBytes += checkNotNull(inputBuffer.data).capacity();
      hasReachedAllocationTarget =
          bufferCount >= MIN_INPUT_BUFFER_COUNT
              && (bufferCount >= MAX_INPUT_BUFFER_COUNT
                  || totalBufferSizeBytes >= ALLOCATION_SIZE_TARGET_BYTES);
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

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor;

import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * A {@link TrackOutput} that buffers extracted samples in a queue, and allows for consumption from
 * that queue.
 */
public class DefaultTrackOutput implements TrackOutput {

  private final RollingSampleBuffer rollingBuffer;
  private final DecoderInputBuffer sampleBuffer;

  // Accessed only by the consuming thread.
  private boolean needKeyframe;
  private long lastReadTimeUs;
  private long spliceOutTimeUs;

  // Accessed by both the loading and consuming threads.
  private volatile long largestParsedTimestampUs;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public DefaultTrackOutput(Allocator allocator) {
    rollingBuffer = new RollingSampleBuffer(allocator);
    sampleBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Clears the queue, returning all allocations to the allocator.
   */
  public void clear() {
    rollingBuffer.clear();
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return rollingBuffer.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    rollingBuffer.discardUpstreamSamples(discardFromIndex);
    largestParsedTimestampUs = rollingBuffer.peekSample(sampleBuffer) ? sampleBuffer.timeUs
        : Long.MIN_VALUE;
  }

  // Called by the consuming thread.

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return rollingBuffer.getReadIndex();
  }

  /**
   * The format most recently received by the output, or null if a format has yet to be received.
   */
  public Format getFormat() {
    return rollingBuffer.getUpstreamFormat();
  }

  /**
   * The largest timestamp of any sample received by the output, or {@link Long#MIN_VALUE} if a
   * sample has yet to be received.
   */
  public long getLargestParsedTimestampUs() {
    return largestParsedTimestampUs;
  }

  /**
   * True if at least one sample can be read from the queue. False otherwise.
   */
  public boolean isEmpty() {
    return !advanceToEligibleSample();
  }

  /**
   * Removes the next sample from the head of the queue, writing it into the provided buffer.
   * <p>
   * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
   * queued prior to the first keyframe are discarded.
   *
   * @param buffer A {@link DecoderInputBuffer} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(DecoderInputBuffer buffer) {
    boolean foundEligibleSample = advanceToEligibleSample();
    if (!foundEligibleSample) {
      return false;
    }
    // Write the sample into the buffer.
    rollingBuffer.readSample(buffer);
    needKeyframe = false;
    lastReadTimeUs = buffer.timeUs;
    return true;
  }

  /**
   * Skips all currently buffered samples.
   */
  public void skipAllSamples() {
    rollingBuffer.skipAllSamples();
  }

  /**
   * Attempts to skip to the keyframe before the specified time, if it's present in the buffer.
   *
   * @param timeUs The seek time.
   * @return True if the skip was successful. False otherwise.
   */
  public boolean skipToKeyframeBefore(long timeUs) {
    return rollingBuffer.skipToKeyframeBefore(timeUs);
  }

  /**
   * Attempts to configure a splice from this queue to the next.
   *
   * @param nextQueue The queue being spliced to.
   * @return Whether the splice was configured successfully.
   */
  public boolean configureSpliceTo(DefaultTrackOutput nextQueue) {
    if (spliceOutTimeUs != Long.MIN_VALUE) {
      // We've already configured the splice.
      return true;
    }
    long firstPossibleSpliceTime;
    if (rollingBuffer.peekSample(sampleBuffer)) {
      firstPossibleSpliceTime = sampleBuffer.timeUs;
    } else {
      firstPossibleSpliceTime = lastReadTimeUs + 1;
    }
    RollingSampleBuffer nextRollingBuffer = nextQueue.rollingBuffer;
    while (nextRollingBuffer.peekSample(sampleBuffer)
        && (sampleBuffer.timeUs < firstPossibleSpliceTime || !sampleBuffer.isKeyFrame())) {
      // Discard samples from the next queue for as long as they are before the earliest possible
      // splice time, or not keyframes.
      nextRollingBuffer.skipSample();
    }
    if (nextRollingBuffer.peekSample(sampleBuffer)) {
      // We've found a keyframe in the next queue that can serve as the splice point. Set the
      // splice point now.
      spliceOutTimeUs = sampleBuffer.timeUs;
      return true;
    }
    return false;
  }

  /**
   * Advances the underlying buffer to the next sample that is eligible to be returned.
   *
   * @return True if an eligible sample was found. False otherwise, in which case the underlying
   *     buffer has been emptied.
   */
  private boolean advanceToEligibleSample() {
    boolean haveNext = rollingBuffer.peekSample(sampleBuffer);
    if (needKeyframe) {
      while (haveNext && !sampleBuffer.isKeyFrame()) {
        rollingBuffer.skipSample();
        haveNext = rollingBuffer.peekSample(sampleBuffer);
      }
    }
    if (!haveNext) {
      return false;
    }
    if (spliceOutTimeUs != Long.MIN_VALUE && sampleBuffer.timeUs >= spliceOutTimeUs) {
      return false;
    }
    return true;
  }

  // Called by the loading thread.

  /**
   * Sets an offset that will be added to the timestamps passed to
   * {@link #sampleMetadata(long, int, int, int, byte[])}.
   *
   * @param sampleOffsetUs The offset in microseconds.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    rollingBuffer.setSampleOffsetUs(sampleOffsetUs);
  }

  @Override
  public void format(Format format) {
    rollingBuffer.format(format);
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return rollingBuffer.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    rollingBuffer.sampleData(buffer, length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    largestParsedTimestampUs = Math.max(largestParsedTimestampUs, timeUs);
    rollingBuffer.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
  }

}

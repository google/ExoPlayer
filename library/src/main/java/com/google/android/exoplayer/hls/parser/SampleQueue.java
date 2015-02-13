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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Wraps a {@link RollingSampleBuffer}, adding higher level functionality such as enforcing that
 * the first sample returned from the queue is a keyframe, allowing splicing to another queue, and
 * so on.
 */
/* package */ abstract class SampleQueue {

  private final RollingSampleBuffer rollingBuffer;
  private final SampleHolder sampleInfoHolder;

  // Accessed only by the consuming thread.
  private boolean needKeyframe;
  private long lastReadTimeUs;
  private long spliceOutTimeUs;

  // Accessed only by the loading thread.
  private boolean writingSample;

  // Accessed by both the loading and consuming threads.
  private volatile MediaFormat mediaFormat;
  private volatile long largestParsedTimestampUs;

  protected SampleQueue(BufferPool bufferPool) {
    rollingBuffer = new RollingSampleBuffer(bufferPool);
    sampleInfoHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  public void release() {
    rollingBuffer.release();
  }

  // Called by the consuming thread.

  public long getLargestParsedTimestampUs() {
    return largestParsedTimestampUs;
  }

  public boolean hasMediaFormat() {
    return mediaFormat != null;
  }

  public MediaFormat getMediaFormat() {
    return mediaFormat;
  }

  public boolean isEmpty() {
    return !advanceToEligibleSample();
  }

  /**
   * Removes the next sample from the head of the queue, writing it into the provided holder.
   * <p>
   * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
   * queued prior to the first keyframe are discarded.
   *
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(SampleHolder holder) {
    boolean foundEligibleSample = advanceToEligibleSample();
    if (!foundEligibleSample) {
      return false;
    }
    // Write the sample into the holder.
    rollingBuffer.readSample(holder);
    needKeyframe = false;
    lastReadTimeUs = holder.timeUs;
    return true;
  }

  /**
   * Discards samples from the queue up to the specified time.
   *
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(long timeUs) {
    while (rollingBuffer.peekSample(sampleInfoHolder) && sampleInfoHolder.timeUs < timeUs) {
      rollingBuffer.skipSample();
      // We're discarding one or more samples. A subsequent read will need to start at a keyframe.
      needKeyframe = true;
    }
    lastReadTimeUs = Long.MIN_VALUE;
  }

  /**
   * Attempts to configure a splice from this queue to the next.
   *
   * @param nextQueue The queue being spliced to.
   * @return Whether the splice was configured successfully.
   */
  public boolean configureSpliceTo(SampleQueue nextQueue) {
    if (spliceOutTimeUs != Long.MIN_VALUE) {
      // We've already configured the splice.
      return true;
    }
    long firstPossibleSpliceTime;
    if (rollingBuffer.peekSample(sampleInfoHolder)) {
      firstPossibleSpliceTime = sampleInfoHolder.timeUs;
    } else {
      firstPossibleSpliceTime = lastReadTimeUs + 1;
    }
    RollingSampleBuffer nextRollingBuffer = nextQueue.rollingBuffer;
    while (nextRollingBuffer.peekSample(sampleInfoHolder)
        && (sampleInfoHolder.timeUs < firstPossibleSpliceTime
            || (sampleInfoHolder.flags & C.SAMPLE_FLAG_SYNC) == 0)) {
      // Discard samples from the next queue for as long as they are before the earliest possible
      // splice time, or not keyframes.
      nextRollingBuffer.skipSample();
    }
    if (nextRollingBuffer.peekSample(sampleInfoHolder)) {
      // We've found a keyframe in the next queue that can serve as the splice point. Set the
      // splice point now.
      spliceOutTimeUs = sampleInfoHolder.timeUs;
      return true;
    }
    return false;
  }

  /**
   * Advances the underlying buffer to the next sample that is eligible to be returned.
   *
   * @boolean True if an eligible sample was found. False otherwise, in which case the underlying
   *     buffer has been emptied.
   */
  private boolean advanceToEligibleSample() {
    boolean haveNext = rollingBuffer.peekSample(sampleInfoHolder);
    if (needKeyframe) {
      while (haveNext && (sampleInfoHolder.flags & C.SAMPLE_FLAG_SYNC) == 0) {
        rollingBuffer.skipSample();
        haveNext = rollingBuffer.peekSample(sampleInfoHolder);
      }
    }
    if (!haveNext) {
      return false;
    }
    if (spliceOutTimeUs != Long.MIN_VALUE && sampleInfoHolder.timeUs >= spliceOutTimeUs) {
      return false;
    }
    return true;
  }

  // Called by the loading thread.

  protected boolean writingSample() {
    return writingSample;
  }

  protected void setMediaFormat(MediaFormat mediaFormat) {
    this.mediaFormat = mediaFormat;
  }

  protected void startSample(long sampleTimeUs) {
    startSample(sampleTimeUs, 0);
  }

  protected void startSample(long sampleTimeUs, int offset) {
    writingSample = true;
    largestParsedTimestampUs = Math.max(largestParsedTimestampUs, sampleTimeUs);
    rollingBuffer.startSample(sampleTimeUs, offset);
  }

  protected void appendData(ParsableByteArray buffer, int length) {
    rollingBuffer.appendData(buffer, length);
  }

  protected void commitSample(boolean isKeyframe) {
    commitSample(isKeyframe, 0);
  }

  protected void commitSample(boolean isKeyframe, int offset) {
    rollingBuffer.commitSample(isKeyframe, offset);
    writingSample = false;
  }

}

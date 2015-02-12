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

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.util.ParsableByteArray;

/* package */ abstract class SampleQueue {

  private final SamplePool samplePool;

  // Accessed only by the consuming thread.
  private boolean needKeyframe;
  private long lastReadTimeUs;
  private long spliceOutTimeUs;

  // Accessed by both the loading and consuming threads.
  private volatile MediaFormat mediaFormat;
  private volatile long largestParsedTimestampUs;

  protected SampleQueue(SamplePool samplePool) {
    this.samplePool = samplePool;
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  public long getLargestParsedTimestampUs() {
    return largestParsedTimestampUs;
  }

  public boolean hasMediaFormat() {
    return mediaFormat != null;
  }

  public MediaFormat getMediaFormat() {
    return mediaFormat;
  }

  protected void setMediaFormat(MediaFormat mediaFormat) {
    this.mediaFormat = mediaFormat;
  }

  /**
   * Removes and returns the next sample from the queue.
   * <p>
   * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
   * queued prior to the first keyframe are discarded.
   *
   * @return The next sample from the queue, or null if a sample isn't available.
   */
  public Sample poll() {
    Sample head = peek();
    if (head != null) {
      internalPollSample();
      needKeyframe = false;
      lastReadTimeUs = head.timeUs;
    }
    return head;
  }

  /**
   * Like {@link #poll()}, except the returned sample is not removed from the queue.
   *
   * @return The next sample from the queue, or null if a sample isn't available.
   */
  public Sample peek() {
    Sample head = internalPeekSample();
    if (needKeyframe) {
      // Peeking discard of samples until we find a keyframe or run out of available samples.
      while (head != null && !head.isKeyframe) {
        recycle(head);
        internalPollSample();
        head = internalPeekSample();
      }
    }
    if (head == null) {
      return null;
    }
    if (spliceOutTimeUs != Long.MIN_VALUE && head.timeUs >= spliceOutTimeUs) {
      // The sample is later than the time this queue is spliced out.
      recycle(head);
      internalPollSample();
      return null;
    }
    return head;
  }

  /**
   * Discards samples from the queue up to the specified time.
   *
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(long timeUs) {
    Sample head = peek();
    while (head != null && head.timeUs < timeUs) {
      recycle(head);
      internalPollSample();
      head = internalPeekSample();
      // We're discarding at least one sample, so any subsequent read will need to start at
      // a keyframe.
      needKeyframe = true;
    }
    lastReadTimeUs = Long.MIN_VALUE;
  }

  /**
   * Clears the queue.
   */
  public void release() {
    Sample toRecycle = internalPollSample();
    while (toRecycle != null) {
      recycle(toRecycle);
      toRecycle = internalPollSample();
    }
  }

  /**
   * Recycles a sample.
   *
   * @param sample The sample to recycle.
   */
  public void recycle(Sample sample) {
    samplePool.recycle(sample);
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
    Sample nextSample = internalPeekSample();
    if (nextSample != null) {
      firstPossibleSpliceTime = nextSample.timeUs;
    } else {
      firstPossibleSpliceTime = lastReadTimeUs + 1;
    }
    Sample nextQueueSample = nextQueue.internalPeekSample();
    while (nextQueueSample != null
        && (nextQueueSample.timeUs < firstPossibleSpliceTime || !nextQueueSample.isKeyframe)) {
      // Discard samples from the next queue for as long as they are before the earliest possible
      // splice time, or not keyframes.
      nextQueue.internalPollSample();
      nextQueueSample = nextQueue.internalPeekSample();
    }
    if (nextQueueSample != null) {
      // We've found a keyframe in the next queue that can serve as the splice point. Set the
      // splice point now.
      spliceOutTimeUs = nextQueueSample.timeUs;
      return true;
    }
    return false;
  }

  /**
   * Obtains a Sample object to use.
   *
   * @param type The type of the sample.
   * @return The sample.
   */
  protected Sample getSample(int type) {
    return samplePool.get(type);
  }

  /**
   * Creates a new Sample and adds it to the queue.
   *
   * @param type The type of the sample.
   * @param buffer The buffer to read sample data.
   * @param sampleSize The size of the sample data.
   * @param sampleTimeUs The sample time stamp.
   * @param isKeyframe True if the sample is a keyframe. False otherwise.
   */
  protected void addSample(int type, ParsableByteArray buffer, int sampleSize, long sampleTimeUs,
      boolean isKeyframe) {
    Sample sample = getSample(type);
    addToSample(sample, buffer, sampleSize);
    sample.isKeyframe = isKeyframe;
    sample.timeUs = sampleTimeUs;
    addSample(sample);
  }

  protected void addSample(Sample sample) {
    largestParsedTimestampUs = Math.max(largestParsedTimestampUs, sample.timeUs);
    internalQueueSample(sample);
  }

  protected void addToSample(Sample sample, ParsableByteArray buffer, int size) {
    if (sample.data.length - sample.size < size) {
      sample.expand(size - sample.data.length + sample.size);
    }
    buffer.readBytes(sample.data, sample.size, size);
    sample.size += size;
  }

  protected abstract Sample internalPeekSample();
  protected abstract Sample internalPollSample();
  protected abstract void internalQueueSample(Sample sample);

}

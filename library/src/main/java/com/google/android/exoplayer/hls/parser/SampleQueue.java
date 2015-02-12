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
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.annotation.SuppressLint;
import android.media.MediaExtractor;

import java.util.concurrent.ConcurrentLinkedQueue;

/* package */ abstract class SampleQueue {

  private final SamplePool samplePool;
  private final ConcurrentLinkedQueue<Sample> internalQueue;

  // Accessed only by the consuming thread.
  private boolean needKeyframe;
  private long lastReadTimeUs;
  private long spliceOutTimeUs;

  // Accessed by both the loading and consuming threads.
  private volatile MediaFormat mediaFormat;
  private volatile long largestParsedTimestampUs;

  // Accessed by only the loading thread (except on release, which shouldn't happen until the
  // loading thread has been terminated).
  private Sample pendingSample;

  protected SampleQueue(SamplePool samplePool) {
    this.samplePool = samplePool;
    internalQueue = new ConcurrentLinkedQueue<Sample>();
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  public boolean isEmpty() {
    return peek() == null;
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
   * Removes the next sample from the head of the queue, writing it into the provided holder.
   * <p>
   * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
   * queued prior to the first keyframe are discarded.
   *
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  @SuppressLint("InlinedApi")
  public boolean getSample(SampleHolder holder) {
    Sample sample = peek();
    if (sample == null) {
      return false;
    }
    // Write the sample into the holder.
    if (holder.data == null || holder.data.capacity() < sample.size) {
      holder.replaceBuffer(sample.size);
    }
    if (holder.data != null) {
      holder.data.put(sample.data, 0, sample.size);
    }
    holder.size = sample.size;
    holder.flags = sample.isKeyframe ? MediaExtractor.SAMPLE_FLAG_SYNC : 0;
    holder.timeUs = sample.timeUs;
    // Pop and recycle the sample, and update state.
    needKeyframe = false;
    lastReadTimeUs = sample.timeUs;
    internalQueue.poll();
    samplePool.recycle(sample);
    return true;
  }

  /**
   * Returns (but does not remove) the next sample in the queue.
   *
   * @return The next sample from the queue, or null if a sample isn't available.
   */
  private Sample peek() {
    Sample head = internalQueue.peek();
    if (needKeyframe) {
      // Peeking discard of samples until we find a keyframe or run out of available samples.
      while (head != null && !head.isKeyframe) {
        samplePool.recycle(head);
        internalQueue.poll();
        head = internalQueue.peek();
      }
    }
    if (head == null) {
      return null;
    }
    if (spliceOutTimeUs != Long.MIN_VALUE && head.timeUs >= spliceOutTimeUs) {
      // The sample is later than the time this queue is spliced out.
      samplePool.recycle(head);
      internalQueue.poll();
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
      samplePool.recycle(head);
      internalQueue.poll();
      head = internalQueue.peek();
      // We're discarding at least one sample, so any subsequent read will need to start at
      // a keyframe.
      needKeyframe = true;
    }
    lastReadTimeUs = Long.MIN_VALUE;
  }

  /**
   * Clears the queue.
   */
  public final void release() {
    Sample toRecycle = internalQueue.poll();
    while (toRecycle != null) {
      samplePool.recycle(toRecycle);
      toRecycle = internalQueue.poll();
    }
    if (pendingSample != null) {
      samplePool.recycle(pendingSample);
      pendingSample = null;
    }
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
    Sample nextSample = internalQueue.peek();
    if (nextSample != null) {
      firstPossibleSpliceTime = nextSample.timeUs;
    } else {
      firstPossibleSpliceTime = lastReadTimeUs + 1;
    }
    Sample nextQueueSample = nextQueue.internalQueue.peek();
    while (nextQueueSample != null
        && (nextQueueSample.timeUs < firstPossibleSpliceTime || !nextQueueSample.isKeyframe)) {
      // Discard samples from the next queue for as long as they are before the earliest possible
      // splice time, or not keyframes.
      nextQueue.internalQueue.poll();
      nextQueueSample = nextQueue.internalQueue.peek();
    }
    if (nextQueueSample != null) {
      // We've found a keyframe in the next queue that can serve as the splice point. Set the
      // splice point now.
      spliceOutTimeUs = nextQueueSample.timeUs;
      return true;
    }
    return false;
  }

  // Writing side.

  protected final boolean havePendingSample() {
    return pendingSample != null;
  }

  protected final Sample getPendingSample() {
    return pendingSample;
  }

  protected final void startSample(int type, long timeUs) {
    pendingSample = samplePool.get(type);
    pendingSample.timeUs = timeUs;
  }

  protected final void appendSampleData(ParsableByteArray buffer, int size) {
    if (pendingSample.data.length - pendingSample.size < size) {
      pendingSample.expand(size - pendingSample.data.length + pendingSample.size);
    }
    buffer.readBytes(pendingSample.data, pendingSample.size, size);
    pendingSample.size += size;
  }

  protected final void commitSample(boolean isKeyframe) {
    pendingSample.isKeyframe = isKeyframe;
    internalQueue.add(pendingSample);
    largestParsedTimestampUs = Math.max(largestParsedTimestampUs, pendingSample.timeUs);
    pendingSample = null;
  }

}

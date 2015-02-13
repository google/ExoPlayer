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
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A rolling buffer of sample data and corresponding sample information.
 */
/* package */ final class RollingSampleBuffer {

  private final BufferPool fragmentPool;
  private final int fragmentLength;

  private final InfoQueue infoQueue;
  private final ConcurrentLinkedQueue<byte[]> dataQueue;
  private final long[] dataOffsetHolder;

  // Accessed only by the consuming thread.
  private long totalBytesDropped;

  // Accessed only by the loading thread.
  private long totalBytesWritten;
  private byte[] lastFragment;
  private int lastFragmentOffset;
  private long pendingSampleTimeUs;
  private long pendingSampleOffset;

  public RollingSampleBuffer(BufferPool bufferPool) {
    this.fragmentPool = bufferPool;
    fragmentLength = bufferPool.bufferLength;
    infoQueue = new InfoQueue();
    dataQueue = new ConcurrentLinkedQueue<byte[]>();
    dataOffsetHolder = new long[1];
  }

  public void release() {
    while (!dataQueue.isEmpty()) {
      fragmentPool.releaseDirect(dataQueue.remove());
    }
  }

  // Called by the consuming thread.

  /**
   * Fills {@code holder} with information about the current sample, but does not write its data.
   * <p>
   * The fields set are {SampleHolder#size}, {SampleHolder#timeUs} and {SampleHolder#flags}.
   *
   * @param holder The holder into which the current sample information should be written.
   * @return True if the holder was filled. False if there is no current sample.
   */
  public boolean peekSample(SampleHolder holder) {
    return infoQueue.peekSample(holder, dataOffsetHolder);
  }

  /**
   * Skips the current sample.
   */
  public void skipSample() {
    long nextOffset = infoQueue.moveToNextSample();
    dropFragmentsTo(nextOffset);
  }

  /**
   * Reads the current sample, advancing the read index to the next sample.
   *
   * @param holder The holder into which the current sample should be written.
   */
  public void readSample(SampleHolder holder) {
    // Write the sample information into the holder.
    infoQueue.peekSample(holder, dataOffsetHolder);
    // Write the sample data into the holder.
    if (holder.data == null || holder.data.capacity() < holder.size) {
      holder.replaceBuffer(holder.size);
    }
    if (holder.data != null) {
      readData(dataOffsetHolder[0], holder.data, holder.size);
    }
    // Advance the read head.
    long nextOffset = infoQueue.moveToNextSample();
    dropFragmentsTo(nextOffset);
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The buffer into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, ByteBuffer target, int length) {
    int remaining = length;
    while (remaining > 0) {
      dropFragmentsTo(absolutePosition);
      int positionInFragment = (int) (absolutePosition - totalBytesDropped);
      int toCopy = Math.min(remaining, fragmentLength - positionInFragment);
      target.put(dataQueue.peek(), positionInFragment, toCopy);
      absolutePosition += toCopy;
      remaining -= toCopy;
    }
  }

  /**
   * Discard any fragments that hold data prior to the specified absolute position, returning
   * them to the pool.
   *
   * @param absolutePosition The absolute position up to which fragments can be discarded.
   */
  private void dropFragmentsTo(long absolutePosition) {
    int relativePosition = (int) (absolutePosition - totalBytesDropped);
    int fragmentIndex = relativePosition / fragmentLength;
    for (int i = 0; i < fragmentIndex; i++) {
      fragmentPool.releaseDirect(dataQueue.remove());
      totalBytesDropped += fragmentLength;
    }
  }

  // Called by the loading thread.

  /**
   * Indicates the start point for the next sample.
   *
   * @param sampleTimeUs The sample timestamp.
   * @param offset The offset of the sample's data, relative to the total number of bytes written
   *     to the buffer. Must be negative or zero.
   */
  public void startSample(long sampleTimeUs, int offset) {
    Assertions.checkState(offset <= 0);
    pendingSampleTimeUs = sampleTimeUs;
    pendingSampleOffset = totalBytesWritten + offset;
  }

  /**
   * Appends data to the rolling buffer.
   *
   * @param buffer A buffer containing the data to append.
   * @param length The length of the data to append.
   */
  public void appendData(ParsableByteArray buffer, int length) {
    int remainingWriteLength = length;
    while (remainingWriteLength > 0) {
      if (dataQueue.isEmpty() || lastFragmentOffset == fragmentLength) {
        lastFragmentOffset = 0;
        lastFragment = fragmentPool.allocateDirect();
        dataQueue.add(lastFragment);
      }
      int thisWriteLength = Math.min(remainingWriteLength, fragmentLength - lastFragmentOffset);
      buffer.readBytes(lastFragment, lastFragmentOffset, thisWriteLength);
      lastFragmentOffset += thisWriteLength;
      remainingWriteLength -= thisWriteLength;
    }
    totalBytesWritten += length;
  }

  /**
   * Indicates the end point for the current sample, making it available for consumption.
   *
   * @param isKeyframe True if the sample being committed is a keyframe. False otherwise.
   * @param offset The offset of the first byte after the end of the sample's data, relative to
   *     the total number of bytes written to the buffer. Must be negative or zero.
   */
  public void commitSample(boolean isKeyframe, int offset) {
    Assertions.checkState(offset <= 0);
    int sampleSize = (int) (totalBytesWritten + offset - pendingSampleOffset);
    infoQueue.commitSample(pendingSampleTimeUs, pendingSampleOffset, sampleSize,
        isKeyframe ? C.SAMPLE_FLAG_SYNC : 0);
  }

  /**
   * Holds information about the samples in the rolling buffer.
   */
  private static class InfoQueue {

    private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

    private int capacity;

    private long[] offsets;
    private int[] sizes;
    private int[] flags;
    private long[] timesUs;

    private int queueSize;
    private int readIndex;
    private int writeIndex;

    public InfoQueue() {
      capacity = SAMPLE_CAPACITY_INCREMENT;
      offsets = new long[capacity];
      timesUs = new long[capacity];
      flags = new int[capacity];
      sizes = new int[capacity];
    }

    // Called by the consuming thread.

    /**
     * Fills {@code holder} with information about the current sample, but does not write its data.
     * The first entry in {@code offsetHolder} is filled with the absolute position of the sample's
     * data in the rolling buffer.
     * <p>
     * The fields set are {SampleHolder#size}, {SampleHolder#timeUs}, {SampleHolder#flags} and
     * {@code offsetHolder[0]}.
     *
     * @param holder The holder into which the current sample information should be written.
     * @param offsetHolder The holder into which the absolute position of the sample's data should
     *     be written.
     * @return True if the holders were filled. False if there is no current sample.
     */
    public synchronized boolean peekSample(SampleHolder holder, long[] offsetHolder) {
      if (queueSize == 0) {
        return false;
      }
      holder.timeUs = timesUs[readIndex];
      holder.size = sizes[readIndex];
      holder.flags = flags[readIndex];
      offsetHolder[0] = offsets[readIndex];
      return true;
    }

    /**
     * Advances the read index to the next sample.
     *
     * @return The absolute position of the first byte in the rolling buffer that may still be
     *     required after advancing the index. Data prior to this position can be dropped.
     */
    public synchronized long moveToNextSample() {
      queueSize--;
      int lastReadIndex = readIndex++;
      if (readIndex == capacity) {
        // Wrap around.
        readIndex = 0;
      }
      return queueSize > 0 ? offsets[readIndex] : (sizes[lastReadIndex] + offsets[lastReadIndex]);
    }

    // Called by the loading thread.

    public synchronized void commitSample(long timeUs, long offset, int size, int sampleFlags) {
      timesUs[writeIndex] = timeUs;
      offsets[writeIndex] = offset;
      sizes[writeIndex] = size;
      flags[writeIndex] = sampleFlags;
      // Increment the write index.
      queueSize++;
      if (queueSize == capacity) {
        // Increase the capacity.
        int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
        long[] newOffsets = new long[newCapacity];
        long[] newTimesUs = new long[newCapacity];
        int[] newFlags = new int[newCapacity];
        int[] newSizes = new int[newCapacity];
        int beforeWrap = capacity - readIndex;
        System.arraycopy(offsets, readIndex, newOffsets, 0, beforeWrap);
        System.arraycopy(timesUs, readIndex, newTimesUs, 0, beforeWrap);
        System.arraycopy(flags, readIndex, newFlags, 0, beforeWrap);
        System.arraycopy(sizes, readIndex, newSizes, 0, beforeWrap);
        int afterWrap = readIndex;
        System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
        System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
        System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
        System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
        offsets = newOffsets;
        timesUs = newTimesUs;
        flags = newFlags;
        sizes = newSizes;
        readIndex = 0;
        writeIndex = capacity;
        queueSize = capacity;
        capacity = newCapacity;
      } else {
        writeIndex++;
        if (writeIndex == capacity) {
          // Wrap around.
          writeIndex = 0;
        }
      }
    }

  }

}

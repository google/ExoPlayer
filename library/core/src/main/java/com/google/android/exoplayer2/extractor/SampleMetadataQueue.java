/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.TrackOutput.CryptoData;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * A queue of metadata describing the contents of a media buffer.
 */
/* package */ final class SampleMetadataQueue {

  /**
   * A holder for sample metadata not held by {@link DecoderInputBuffer}.
   */
  public static final class SampleExtrasHolder {

    public int size;
    public long offset;
    public long nextOffset;
    public CryptoData cryptoData;

  }

  private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

  private int capacity;
  private int[] sourceIds;
  private long[] offsets;
  private int[] sizes;
  private int[] flags;
  private long[] timesUs;
  private CryptoData[] cryptoDatas;
  private Format[] formats;

  private int queueSize;
  private int absoluteReadIndex;
  private int relativeReadIndex;
  private int relativeWriteIndex;

  private long largestDequeuedTimestampUs;
  private long largestQueuedTimestampUs;
  private boolean upstreamKeyframeRequired;
  private boolean upstreamFormatRequired;
  private Format upstreamFormat;
  private int upstreamSourceId;

  public SampleMetadataQueue() {
    capacity = SAMPLE_CAPACITY_INCREMENT;
    sourceIds = new int[capacity];
    offsets = new long[capacity];
    timesUs = new long[capacity];
    flags = new int[capacity];
    sizes = new int[capacity];
    cryptoDatas = new CryptoData[capacity];
    formats = new Format[capacity];
    largestDequeuedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
    upstreamFormatRequired = true;
    upstreamKeyframeRequired = true;
  }

  public void clearSampleData() {
    absoluteReadIndex = 0;
    relativeReadIndex = 0;
    relativeWriteIndex = 0;
    queueSize = 0;
    upstreamKeyframeRequired = true;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  public void resetLargestParsedTimestamps() {
    largestDequeuedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return absoluteReadIndex + queueSize;
  }

  /**
   * Discards samples from the write side of the buffer.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   * @return The reduced total number of bytes written, after the samples have been discarded.
   */
  public long discardUpstreamSamples(int discardFromIndex) {
    int discardCount = getWriteIndex() - discardFromIndex;
    Assertions.checkArgument(0 <= discardCount && discardCount <= queueSize);

    if (discardCount == 0) {
      if (absoluteReadIndex == 0) {
        // queueSize == absoluteReadIndex == 0, so nothing has been written to the queue.
        return 0;
      }
      int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
      return offsets[lastWriteIndex] + sizes[lastWriteIndex];
    }

    queueSize -= discardCount;
    relativeWriteIndex = (relativeWriteIndex + capacity - discardCount) % capacity;
    // Update the largest queued timestamp, assuming that the timestamps prior to a keyframe are
    // always less than the timestamp of the keyframe itself, and of subsequent frames.
    largestQueuedTimestampUs = Long.MIN_VALUE;
    for (int i = queueSize - 1; i >= 0; i--) {
      int sampleIndex = (relativeReadIndex + i) % capacity;
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timesUs[sampleIndex]);
      if ((flags[sampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        break;
      }
    }
    return offsets[relativeWriteIndex];
  }

  public void sourceId(int sourceId) {
    upstreamSourceId = sourceId;
  }

  // Called by the consuming thread.

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return absoluteReadIndex;
  }

  /**
   * Peeks the source id of the next sample, or the current upstream source id if the queue is
   * empty.
   */
  public int peekSourceId() {
    return queueSize == 0 ? upstreamSourceId : sourceIds[relativeReadIndex];
  }

  /**
   * Returns whether the queue is empty.
   */
  public synchronized boolean isEmpty() {
    return queueSize == 0;
  }

  /**
   * Returns the upstream {@link Format} in which samples are being queued.
   */
  public synchronized Format getUpstreamFormat() {
    return upstreamFormatRequired ? null : upstreamFormat;
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last call to
   * {@link #resetLargestParsedTimestamps()}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public synchronized long getLargestQueuedTimestampUs() {
    return Math.max(largestDequeuedTimestampUs, largestQueuedTimestampUs);
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If a sample is read then the buffer is populated with information
   *     about the sample, but not its data. The size and absolute position of the data in the
   *     rolling buffer is stored in {@code extrasHolder}, along with an encryption id if present
   *     and the absolute position of the first byte that may still be required after the current
   *     sample has been read. May be null if the caller requires that the format of the stream be
   *     read even if it's not changing.
   * @param formatRequired Whether the caller requires that the format of the stream be read even
   *     if it's not changing. A sample will never be read if set to true, however it is still
   *     possible for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param downstreamFormat The current downstream {@link Format}. If the format of the next
   *     sample is different to the current downstream format then a format will be read.
   * @param extrasHolder The holder into which extra sample information should be written.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ}
   *     or {@link C#RESULT_BUFFER_READ}.
   */
  @SuppressWarnings("ReferenceEquality")
  public synchronized int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired, boolean loadingFinished, Format downstreamFormat,
      SampleExtrasHolder extrasHolder) {
    if (queueSize == 0) {
      if (loadingFinished) {
        buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      } else if (upstreamFormat != null
          && (formatRequired || upstreamFormat != downstreamFormat)) {
        formatHolder.format = upstreamFormat;
        return C.RESULT_FORMAT_READ;
      } else {
        return C.RESULT_NOTHING_READ;
      }
    }

    if (formatRequired || formats[relativeReadIndex] != downstreamFormat) {
      formatHolder.format = formats[relativeReadIndex];
      return C.RESULT_FORMAT_READ;
    }

    if (buffer.isFlagsOnly()) {
      return C.RESULT_NOTHING_READ;
    }

    buffer.timeUs = timesUs[relativeReadIndex];
    buffer.setFlags(flags[relativeReadIndex]);
    extrasHolder.size = sizes[relativeReadIndex];
    extrasHolder.offset = offsets[relativeReadIndex];
    extrasHolder.cryptoData = cryptoDatas[relativeReadIndex];

    largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, buffer.timeUs);
    queueSize--;
    relativeReadIndex++;
    absoluteReadIndex++;
    if (relativeReadIndex == capacity) {
      // Wrap around.
      relativeReadIndex = 0;
    }

    extrasHolder.nextOffset = queueSize > 0 ? offsets[relativeReadIndex]
        : extrasHolder.offset + extrasHolder.size;
    return C.RESULT_BUFFER_READ;
  }

  /**
   * Skips all samples in the buffer.
   *
   * @return The offset up to which data should be dropped, or {@link C#POSITION_UNSET} if no
   *     dropping of data is required.
   */
  public synchronized long skipAll() {
    if (queueSize == 0) {
      return C.POSITION_UNSET;
    }

    int lastSampleIndex = (relativeReadIndex + queueSize - 1) % capacity;
    relativeReadIndex = (relativeReadIndex + queueSize) % capacity;
    absoluteReadIndex += queueSize;
    queueSize = 0;
    return offsets[lastSampleIndex] + sizes[lastSampleIndex];
  }

  /**
   * Attempts to locate the keyframe before or at the specified time. If
   * {@code allowTimeBeyondBuffer} is {@code false} then it is also required that {@code timeUs}
   * falls within the buffer.
   *
   * @param timeUs The seek time.
   * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
   *     of the buffer.
   * @return The offset of the keyframe's data if the keyframe was present.
   *     {@link C#POSITION_UNSET} otherwise.
   */
  public synchronized long skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
    if (queueSize == 0 || timeUs < timesUs[relativeReadIndex]) {
      return C.POSITION_UNSET;
    }

    if (timeUs > largestQueuedTimestampUs && !allowTimeBeyondBuffer) {
      return C.POSITION_UNSET;
    }

    // This could be optimized to use a binary search, however in practice callers to this method
    // often pass times near to the start of the buffer. Hence it's unclear whether switching to
    // a binary search would yield any real benefit.
    int sampleCount = 0;
    int sampleCountToKeyframe = -1;
    int searchIndex = relativeReadIndex;
    while (searchIndex != relativeWriteIndex) {
      if (timesUs[searchIndex] > timeUs) {
        // We've gone too far.
        break;
      } else if ((flags[searchIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        // We've found a keyframe, and we're still before the seek position.
        sampleCountToKeyframe = sampleCount;
      }
      searchIndex = (searchIndex + 1) % capacity;
      sampleCount++;
    }

    if (sampleCountToKeyframe == -1) {
      return C.POSITION_UNSET;
    }

    relativeReadIndex = (relativeReadIndex + sampleCountToKeyframe) % capacity;
    absoluteReadIndex += sampleCountToKeyframe;
    queueSize -= sampleCountToKeyframe;
    return offsets[relativeReadIndex];
  }

  // Called by the loading thread.

  public synchronized boolean format(Format format) {
    if (format == null) {
      upstreamFormatRequired = true;
      return false;
    }
    upstreamFormatRequired = false;
    if (Util.areEqual(format, upstreamFormat)) {
      // Suppress changes between equal formats so we can use referential equality in readData.
      return false;
    } else {
      upstreamFormat = format;
      return true;
    }
  }

  public synchronized void commitSample(long timeUs, @C.BufferFlags int sampleFlags, long offset,
      int size, CryptoData cryptoData) {
    if (upstreamKeyframeRequired) {
      if ((sampleFlags & C.BUFFER_FLAG_KEY_FRAME) == 0) {
        return;
      }
      upstreamKeyframeRequired = false;
    }
    Assertions.checkState(!upstreamFormatRequired);
    commitSampleTimestamp(timeUs);
    timesUs[relativeWriteIndex] = timeUs;
    offsets[relativeWriteIndex] = offset;
    sizes[relativeWriteIndex] = size;
    flags[relativeWriteIndex] = sampleFlags;
    cryptoDatas[relativeWriteIndex] = cryptoData;
    formats[relativeWriteIndex] = upstreamFormat;
    sourceIds[relativeWriteIndex] = upstreamSourceId;
    // Increment the write index.
    queueSize++;
    if (queueSize == capacity) {
      // Increase the capacity.
      int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
      int[] newSourceIds = new int[newCapacity];
      long[] newOffsets = new long[newCapacity];
      long[] newTimesUs = new long[newCapacity];
      int[] newFlags = new int[newCapacity];
      int[] newSizes = new int[newCapacity];
      CryptoData[] newCryptoDatas = new CryptoData[newCapacity];
      Format[] newFormats = new Format[newCapacity];
      int beforeWrap = capacity - relativeReadIndex;
      System.arraycopy(offsets, relativeReadIndex, newOffsets, 0, beforeWrap);
      System.arraycopy(timesUs, relativeReadIndex, newTimesUs, 0, beforeWrap);
      System.arraycopy(flags, relativeReadIndex, newFlags, 0, beforeWrap);
      System.arraycopy(sizes, relativeReadIndex, newSizes, 0, beforeWrap);
      System.arraycopy(cryptoDatas, relativeReadIndex, newCryptoDatas, 0, beforeWrap);
      System.arraycopy(formats, relativeReadIndex, newFormats, 0, beforeWrap);
      System.arraycopy(sourceIds, relativeReadIndex, newSourceIds, 0, beforeWrap);
      int afterWrap = relativeReadIndex;
      System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
      System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
      System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
      System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
      System.arraycopy(cryptoDatas, 0, newCryptoDatas, beforeWrap, afterWrap);
      System.arraycopy(formats, 0, newFormats, beforeWrap, afterWrap);
      System.arraycopy(sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
      offsets = newOffsets;
      timesUs = newTimesUs;
      flags = newFlags;
      sizes = newSizes;
      cryptoDatas = newCryptoDatas;
      formats = newFormats;
      sourceIds = newSourceIds;
      relativeReadIndex = 0;
      relativeWriteIndex = capacity;
      queueSize = capacity;
      capacity = newCapacity;
    } else {
      relativeWriteIndex++;
      if (relativeWriteIndex == capacity) {
        // Wrap around.
        relativeWriteIndex = 0;
      }
    }
  }

  public synchronized void commitSampleTimestamp(long timeUs) {
    largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timeUs);
  }

  /**
   * Attempts to discard samples from the tail of the queue to allow samples starting from the
   * specified timestamp to be spliced in.
   *
   * @param timeUs The timestamp at which the splice occurs.
   * @return Whether the splice was successful.
   */
  public synchronized boolean attemptSplice(long timeUs) {
    if (largestDequeuedTimestampUs >= timeUs) {
      return false;
    }
    int retainCount = queueSize;
    while (retainCount > 0
        && timesUs[(relativeReadIndex + retainCount - 1) % capacity] >= timeUs) {
      retainCount--;
    }
    discardUpstreamSamples(absoluteReadIndex + retainCount);
    return true;
  }

}

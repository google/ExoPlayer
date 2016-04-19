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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.upstream.Allocation;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A rolling buffer of sample data and corresponding sample information.
 */
public final class RollingSampleBuffer implements TrackOutput {

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private final Allocator allocator;
  private final int allocationLength;

  private final InfoQueue infoQueue;
  private final LinkedBlockingDeque<Allocation> dataQueue;
  private final BufferExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;

  // Accessed only by the consuming thread.
  private long totalBytesDropped;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private long sampleOffsetUs;
  private long totalBytesWritten;
  private Allocation lastAllocation;
  private int lastAllocationOffset;
  private boolean needKeyframe;
  private boolean pendingSplice;

  // Accessed by both the loading and consuming threads.
  private volatile Format upstreamFormat;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public RollingSampleBuffer(Allocator allocator) {
    this.allocator = allocator;
    allocationLength = allocator.getIndividualAllocationLength();
    infoQueue = new InfoQueue();
    dataQueue = new LinkedBlockingDeque<>();
    extrasHolder = new BufferExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    lastAllocationOffset = allocationLength;
    needKeyframe = true;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Clears the buffer, returning all allocations to the allocator.
   */
  public void clear() {
    infoQueue.clear();
    while (!dataQueue.isEmpty()) {
      allocator.release(dataQueue.remove());
    }
    totalBytesDropped = 0;
    totalBytesWritten = 0;
    lastAllocation = null;
    lastAllocationOffset = allocationLength;
    needKeyframe = true;
  }

  /**
   * Indicates that samples subsequently queued to the buffer should be spliced into those already
   * queued.
   */
  public void splice() {
    pendingSplice = true;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return infoQueue.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the buffer.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    totalBytesWritten = infoQueue.discardUpstreamSamples(discardFromIndex);
    dropUpstreamFrom(totalBytesWritten);
  }

  /**
   * Discards data from the write side of the buffer. Data is discarded from the specified absolute
   * position. Any allocations that are fully discarded are returned to the allocator.
   *
   * @param absolutePosition The absolute position (inclusive) from which to discard data.
   */
  private void dropUpstreamFrom(long absolutePosition) {
    int relativePosition = (int) (absolutePosition - totalBytesDropped);
    // Calculate the index of the allocation containing the position, and the offset within it.
    int allocationIndex = relativePosition / allocationLength;
    int allocationOffset = relativePosition % allocationLength;
    // We want to discard any allocations after the one at allocationIdnex.
    int allocationDiscardCount = dataQueue.size() - allocationIndex - 1;
    if (allocationOffset == 0) {
      // If the allocation at allocationIndex is empty, we should discard that one too.
      allocationDiscardCount++;
    }
    // Discard the allocations.
    for (int i = 0; i < allocationDiscardCount; i++) {
      allocator.release(dataQueue.removeLast());
    }
    // Update lastAllocation and lastAllocationOffset to reflect the new position.
    lastAllocation = dataQueue.peekLast();
    lastAllocationOffset = allocationOffset == 0 ? allocationLength : allocationOffset;
  }

  // Called by the consuming thread.

  /**
   * Returns whether the buffer is empty.
   */
  public boolean isEmpty() {
    return infoQueue.isEmpty();
  }

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return infoQueue.getReadIndex();
  }

  /**
   * Returns the current upstream {@link Format}.
   */
  public Format getUpstreamFormat() {
    return upstreamFormat;
  }

  /**
   * Returns the current downstream {@link Format}.
   */
  public Format getDownstreamFormat() {
    Format nextSampleFormat = infoQueue.peekFormat();
    return nextSampleFormat != null ? nextSampleFormat : upstreamFormat;
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last {@link #clear()}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
     * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
     *     samples have been queued.
   */
  public long getLargestQueuedTimestampUs() {
    return infoQueue.getLargestQueuedTimestampUs();
  }

  /**
   * Skips all currently buffered samples.
   */
  public void skipAllSamples() {
    long nextOffset = infoQueue.skipAllSamples();
    if (nextOffset == -1) {
      return;
    }
    dropDownstreamTo(nextOffset);
  }

  /**
   * Attempts to skip to the keyframe before the specified time, if it's present in the buffer.
   *
   * @param timeUs The seek time.
   * @return True if the skip was successful. False otherwise.
   */
  public boolean skipToKeyframeBefore(long timeUs) {
    long nextOffset = infoQueue.skipToKeyframeBefore(timeUs);
    if (nextOffset == -1) {
      return false;
    }
    dropDownstreamTo(nextOffset);
    return true;
  }

  /**
   * Reads the current sample, advancing the read index to the next sample.
   *
   * @param buffer The buffer into which the current sample should be written.
   * @return True if a sample was read. False if there is no current sample.
   */
  public boolean readSample(DecoderInputBuffer buffer) {
    // Write the sample information into the buffer and extrasHolder.
    boolean haveSample = infoQueue.readSample(buffer, extrasHolder);
    if (!haveSample) {
      return false;
    }

    // Read encryption data if the sample is encrypted.
    if (buffer.isEncrypted()) {
      readEncryptionData(buffer, extrasHolder);
    }
    // Write the sample data into the holder.
    buffer.ensureSpaceForWrite(buffer.size);
    readData(extrasHolder.offset, buffer.data, buffer.size);
    // Advance the read head.
    dropDownstreamTo(extrasHolder.nextOffset);
    return true;
  }

  /**
   * Reads encryption data for the current sample.
   * <p>
   * The encryption data is written into {@link DecoderInputBuffer#cryptoInfo}, and
   * {@link DecoderInputBuffer#size} is adjusted to subtract the number of bytes that were read. The
   * same value is added to {@link BufferExtrasHolder#offset}.
   *
   * @param buffer The buffer into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   */
  private void readEncryptionData(DecoderInputBuffer buffer, BufferExtrasHolder extrasHolder) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    readData(offset, scratch.data, 1);
    offset++;
    byte signalByte = scratch.data[0];
    boolean subsampleEncryption = (signalByte & 0x80) != 0;
    int ivSize = signalByte & 0x7F;

    // Read the initialization vector.
    if (buffer.cryptoInfo.iv == null) {
      buffer.cryptoInfo.iv = new byte[16];
    }
    readData(offset, buffer.cryptoInfo.iv, ivSize);
    offset += ivSize;

    // Read the subsample count, if present.
    int subsampleCount;
    if (subsampleEncryption) {
      readData(offset, scratch.data, 2);
      offset += 2;
      scratch.setPosition(0);
      subsampleCount = scratch.readUnsignedShort();
    } else {
      subsampleCount = 1;
    }

    // Write the clear and encrypted subsample sizes.
    int[] clearDataSizes = buffer.cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    int[] encryptedDataSizes = buffer.cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      int subsampleDataLength = 6 * subsampleCount;
      ensureCapacity(scratch, subsampleDataLength);
      readData(offset, scratch.data, subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = buffer.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
        extrasHolder.encryptionKeyId, buffer.cryptoInfo.iv, C.CRYPTO_MODE_AES_CTR);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    buffer.size -= bytesRead;
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
      dropDownstreamTo(absolutePosition);
      int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
      int toCopy = Math.min(remaining, allocationLength - positionInAllocation);
      Allocation allocation = dataQueue.peek();
      target.put(allocation.data, allocation.translateOffset(positionInAllocation), toCopy);
      absolutePosition += toCopy;
      remaining -= toCopy;
    }
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The array into which data should be written.
   * @param length The number of bytes to read.
   */
  // TODO: Consider reducing duplication of this method and the one above.
  private void readData(long absolutePosition, byte[] target, int length) {
    int bytesRead = 0;
    while (bytesRead < length) {
      dropDownstreamTo(absolutePosition);
      int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
      int toCopy = Math.min(length - bytesRead, allocationLength - positionInAllocation);
      Allocation allocation = dataQueue.peek();
      System.arraycopy(allocation.data, allocation.translateOffset(positionInAllocation), target,
          bytesRead, toCopy);
      absolutePosition += toCopy;
      bytesRead += toCopy;
    }
  }

  /**
   * Discard any allocations that hold data prior to the specified absolute position, returning
   * them to the allocator.
   *
   * @param absolutePosition The absolute position up to which allocations can be discarded.
   */
  private void dropDownstreamTo(long absolutePosition) {
    int relativePosition = (int) (absolutePosition - totalBytesDropped);
    int allocationIndex = relativePosition / allocationLength;
    for (int i = 0; i < allocationIndex; i++) {
      allocator.release(dataQueue.remove());
      totalBytesDropped += allocationLength;
    }
  }

  /**
   * Ensure that the passed {@link ParsableByteArray} is of at least the specified limit.
   */
  private static void ensureCapacity(ParsableByteArray byteArray, int limit) {
    if (byteArray.limit() < limit) {
      byteArray.reset(new byte[limit], limit);
    }
  }

  // Called by the loading thread.

  /**
   * Like {@link #format(Format)}, but with an offset that will be added to the timestamps of
   * samples subsequently queued to the buffer. The offset is also used to adjust
   * {@link Format#subsampleOffsetUs} for both the {@link Format} passed and those subsequently
   * passed to {@link #format(Format)}.
   *
   * @param format The format.
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public void formatWithOffset(Format format, long sampleOffsetUs) {
    this.sampleOffsetUs = sampleOffsetUs;
    format(format);
  }

  @Override
  public void format(Format format) {
    upstreamFormat = getAdjustedSampleFormat(format, sampleOffsetUs);
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    length = prepareForAppend(length);
    int bytesAppended = input.read(lastAllocation.data,
        lastAllocation.translateOffset(lastAllocationOffset), length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    lastAllocationOffset += bytesAppended;
    totalBytesWritten += bytesAppended;
    return bytesAppended;
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    while (length > 0) {
      int thisAppendLength = prepareForAppend(length);
      buffer.readBytes(lastAllocation.data, lastAllocation.translateOffset(lastAllocationOffset),
          thisAppendLength);
      lastAllocationOffset += thisAppendLength;
      totalBytesWritten += thisAppendLength;
      length -= thisAppendLength;
    }
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    if (pendingSplice) {
      if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0 || !infoQueue.attemptSplice(timeUs)) {
        return;
      }
      // TODO - We should be able to actually remove the data from the rolling buffer after a splice
      // succeeds, but doing so is a little bit tricky; it requires moving data written after the
      // last committed sample.
      pendingSplice = false;
    }
    if (needKeyframe) {
      if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0) {
        // TODO - As above, although this case is probably less worthwhile.
        return;
      }
      needKeyframe = false;
    }
    timeUs += sampleOffsetUs;
    long absoluteOffset = totalBytesWritten - size - offset;
    infoQueue.commitSample(timeUs, flags, absoluteOffset, size, encryptionKey, upstreamFormat);
  }

  /**
   * Prepares the rolling sample buffer for an append of up to {@code length} bytes, returning the
   * number of bytes that can actually be appended.
   */
  private int prepareForAppend(int length) {
    if (lastAllocationOffset == allocationLength) {
      lastAllocationOffset = 0;
      lastAllocation = allocator.allocate();
      dataQueue.add(lastAllocation);
    }
    return Math.min(length, allocationLength - lastAllocationOffset);
  }

  /**
   * Adjusts a {@link Format} to incorporate a sample offset into {@link Format#subsampleOffsetUs}.
   *
   * @param format The {@link Format} to adjust.
   * @param sampleOffsetUs The offset to apply.
   * @return The adjusted {@link Format}.
   */
  private static Format getAdjustedSampleFormat(Format format, long sampleOffsetUs) {
    if (format == null) {
      return null;
    }
    if (sampleOffsetUs != 0 && format.subsampleOffsetUs != Format.OFFSET_SAMPLE_RELATIVE) {
      format = format.copyWithSubsampleOffsetUs(format.subsampleOffsetUs + sampleOffsetUs);
    }
    return format;
  }

  /**
   * Holds information about the samples in the rolling buffer.
   */
  private static final class InfoQueue {

    private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

    private int capacity;

    private long[] offsets;
    private int[] sizes;
    private int[] flags;
    private long[] timesUs;
    private byte[][] encryptionKeys;
    private Format[] formats;

    private int queueSize;
    private int absoluteReadIndex;
    private int relativeReadIndex;
    private int relativeWriteIndex;

    private long largestDequeuedTimestampUs;
    private long largestQueuedTimestampUs;

    public InfoQueue() {
      capacity = SAMPLE_CAPACITY_INCREMENT;
      offsets = new long[capacity];
      timesUs = new long[capacity];
      flags = new int[capacity];
      sizes = new int[capacity];
      encryptionKeys = new byte[capacity][];
      formats = new Format[capacity];
      largestDequeuedTimestampUs = Long.MIN_VALUE;
      largestQueuedTimestampUs = Long.MIN_VALUE;
    }

    // Called by the consuming thread, but only when there is no loading thread.

    /**
     * Clears the queue.
     */
    public void clear() {
      absoluteReadIndex = 0;
      relativeReadIndex = 0;
      relativeWriteIndex = 0;
      queueSize = 0;
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

    // Called by the consuming thread.

    /**
     * Returns the current absolute read index.
     */
    public int getReadIndex() {
      return absoluteReadIndex;
    }

    public synchronized boolean isEmpty() {
      return queueSize == 0;
    }

    /**
     * Returns the {@link Format} of the next sample, or null of the queue is empty.
     */
    public synchronized Format peekFormat() {
      if (queueSize == 0) {
        return null;
      }
      return formats[relativeReadIndex];
    }

    /**
     * Returns the largest sample timestamp that has been queued since the last {@link #clear()}.
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
     * Fills {@code buffer} with information about the current sample, but does not write its data.
     * The absolute position of the sample's data in the rolling buffer is stored in
     * {@code extrasHolder}, along with an encryption id if present, and the absolute position of
     * the first byte that may still be required after the current sample has been read.
     * <p>
     * Populates {@link DecoderInputBuffer#size}, {@link DecoderInputBuffer#timeUs}, the buffer
     * flags and {@code extrasHolder}.
     *
     * @param buffer The buffer into which the current sample information should be written.
     * @param extrasHolder The holder into which extra sample information should be written.
     * @return True if the buffer and extras were filled. False if there is no current sample.
     */
    public synchronized boolean readSample(DecoderInputBuffer buffer,
        BufferExtrasHolder extrasHolder) {
      if (queueSize == 0) {
        return false;
      }
      buffer.timeUs = timesUs[relativeReadIndex];
      buffer.size = sizes[relativeReadIndex];
      buffer.setFlags(flags[relativeReadIndex]);
      extrasHolder.offset = offsets[relativeReadIndex];
      extrasHolder.encryptionKeyId = encryptionKeys[relativeReadIndex];

      largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, buffer.timeUs);
      queueSize--;
      relativeReadIndex++;
      absoluteReadIndex++;
      if (relativeReadIndex == capacity) {
        // Wrap around.
        relativeReadIndex = 0;
      }

      extrasHolder.nextOffset = queueSize > 0 ? offsets[relativeReadIndex]
          : extrasHolder.offset + buffer.size;
      return true;
    }

    /**
     * Skips all currently buffered samples.
     *
     * @return The absolute position of the first byte in the rolling buffer that may still be
     *     required after skipping the samples, or -1 if no samples were skipped.
     */
    public synchronized long skipAllSamples() {
      if (queueSize == 0) {
        return -1;
      }

      relativeReadIndex = (relativeReadIndex + queueSize) % capacity;
      absoluteReadIndex += queueSize;
      queueSize = 0;

      int lastReadIndex = (relativeReadIndex == 0 ? capacity : relativeReadIndex) - 1;
      return sizes[lastReadIndex] + offsets[lastReadIndex];
    }

    /**
     * Attempts to locate the keyframe before the specified time, if it's present in the buffer.
     *
     * @param timeUs The seek time.
     * @return The offset of the keyframe's data if the keyframe was present. -1 otherwise.
     */
    public synchronized long skipToKeyframeBefore(long timeUs) {
      if (queueSize == 0 || timeUs < timesUs[relativeReadIndex]) {
        return -1;
      }

      int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
      long lastTimeUs = timesUs[lastWriteIndex];
      if (timeUs > lastTimeUs) {
        return -1;
      }

      // TODO: This can be optimized further using binary search, although the fact that the array
      // is cyclic means we'd need to implement the binary search ourselves.
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
        return -1;
      }

      queueSize -= sampleCountToKeyframe;
      relativeReadIndex = (relativeReadIndex + sampleCountToKeyframe) % capacity;
      absoluteReadIndex += sampleCountToKeyframe;
      return offsets[relativeReadIndex];
    }

    // Called by the loading thread.

    public synchronized void commitSample(long timeUs, int sampleFlags, long offset, int size,
        byte[] encryptionKey, Format format) {
      timesUs[relativeWriteIndex] = timeUs;
      offsets[relativeWriteIndex] = offset;
      sizes[relativeWriteIndex] = size;
      flags[relativeWriteIndex] = sampleFlags;
      encryptionKeys[relativeWriteIndex] = encryptionKey;
      formats[relativeWriteIndex] = format;
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, timeUs);
      // Increment the write index.
      queueSize++;
      if (queueSize == capacity) {
        // Increase the capacity.
        int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
        long[] newOffsets = new long[newCapacity];
        long[] newTimesUs = new long[newCapacity];
        int[] newFlags = new int[newCapacity];
        int[] newSizes = new int[newCapacity];
        byte[][] newEncryptionKeys = new byte[newCapacity][];
        Format[] newFormats = new Format[newCapacity];
        int beforeWrap = capacity - relativeReadIndex;
        System.arraycopy(offsets, relativeReadIndex, newOffsets, 0, beforeWrap);
        System.arraycopy(timesUs, relativeReadIndex, newTimesUs, 0, beforeWrap);
        System.arraycopy(flags, relativeReadIndex, newFlags, 0, beforeWrap);
        System.arraycopy(sizes, relativeReadIndex, newSizes, 0, beforeWrap);
        System.arraycopy(encryptionKeys, relativeReadIndex, newEncryptionKeys, 0, beforeWrap);
        System.arraycopy(formats, relativeReadIndex, newFormats, 0, beforeWrap);
        int afterWrap = relativeReadIndex;
        System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
        System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
        System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
        System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
        System.arraycopy(encryptionKeys, 0, newEncryptionKeys, beforeWrap, afterWrap);
        System.arraycopy(formats, 0, newFormats, beforeWrap, afterWrap);
        offsets = newOffsets;
        timesUs = newTimesUs;
        flags = newFlags;
        sizes = newSizes;
        encryptionKeys = newEncryptionKeys;
        formats = newFormats;
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

  /**
   * Holds additional buffer information not held by {@link DecoderInputBuffer}.
   */
  private static final class BufferExtrasHolder {

    public long offset;
    public long nextOffset;
    public byte[] encryptionKeyId;

  }

}

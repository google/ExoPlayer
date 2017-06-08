/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.google.android.exoplayer2.extractor.SampleMetadataQueue.SampleExtrasHolder;
import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link TrackOutput} that buffers extracted samples in a queue and allows for consumption from
 * that queue.
 */
public final class DefaultTrackOutput implements TrackOutput {

  /**
   * A listener for changes to the upstream format.
   */
  public interface UpstreamFormatChangedListener {

    /**
     * Called on the loading thread when an upstream format change occurs.
     *
     * @param format The new upstream format.
     */
    void onUpstreamFormatChanged(Format format);

  }

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private static final int STATE_ENABLED = 0;
  private static final int STATE_ENABLED_WRITING = 1;
  private static final int STATE_DISABLED = 2;

  private final Allocator allocator;
  private final int allocationLength;

  private final SampleMetadataQueue metadataQueue;
  private final LinkedBlockingDeque<Allocation> dataQueue;
  private final SampleExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;
  private final AtomicInteger state;

  // Accessed only by the consuming thread.
  private long totalBytesDropped;
  private Format downstreamFormat;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private boolean pendingFormatAdjustment;
  private Format lastUnadjustedFormat;
  private long sampleOffsetUs;
  private long totalBytesWritten;
  private Allocation lastAllocation;
  private int lastAllocationOffset;
  private boolean pendingSplice;
  private UpstreamFormatChangedListener upstreamFormatChangeListener;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public DefaultTrackOutput(Allocator allocator) {
    this.allocator = allocator;
    allocationLength = allocator.getIndividualAllocationLength();
    metadataQueue = new SampleMetadataQueue();
    dataQueue = new LinkedBlockingDeque<>();
    extrasHolder = new SampleExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    state = new AtomicInteger();
    lastAllocationOffset = allocationLength;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Resets the output.
   *
   * @param enable Whether the output should be enabled. False if it should be disabled.
   */
  public void reset(boolean enable) {
    int previousState = state.getAndSet(enable ? STATE_ENABLED : STATE_DISABLED);
    clearSampleData();
    metadataQueue.resetLargestParsedTimestamps();
    if (previousState == STATE_DISABLED) {
      downstreamFormat = null;
    }
  }

  /**
   * Sets a source identifier for subsequent samples.
   *
   * @param sourceId The source identifier.
   */
  public void sourceId(int sourceId) {
    metadataQueue.sourceId(sourceId);
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
    return metadataQueue.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the buffer.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    totalBytesWritten = metadataQueue.discardUpstreamSamples(discardFromIndex);
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
   * Disables buffering of sample data and metadata.
   */
  public void disable() {
    if (state.getAndSet(STATE_DISABLED) == STATE_ENABLED) {
      clearSampleData();
    }
  }

  /**
   * Returns whether the buffer is empty.
   */
  public boolean isEmpty() {
    return metadataQueue.isEmpty();
  }

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return metadataQueue.getReadIndex();
  }

  /**
   * Peeks the source id of the next sample, or the current upstream source id if the buffer is
   * empty.
   *
   * @return The source id.
   */
  public int peekSourceId() {
    return metadataQueue.peekSourceId();
  }

  /**
   * Returns the upstream {@link Format} in which samples are being queued.
   */
  public Format getUpstreamFormat() {
    return metadataQueue.getUpstreamFormat();
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
   * <p>
   * Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public long getLargestQueuedTimestampUs() {
    return metadataQueue.getLargestQueuedTimestampUs();
  }

  /**
   * Skips all samples currently in the buffer.
   */
  public void skipAll() {
    long nextOffset = metadataQueue.skipAll();
    if (nextOffset != C.POSITION_UNSET) {
      dropDownstreamTo(nextOffset);
    }
  }

  /**
   * Attempts to skip to the keyframe before or at the specified time. Succeeds only if the buffer
   * contains a keyframe with a timestamp of {@code timeUs} or earlier. If
   * {@code allowTimeBeyondBuffer} is {@code false} then it is also required that {@code timeUs}
   * falls within the buffer.
   *
   * @param timeUs The seek time.
   * @param allowTimeBeyondBuffer Whether the skip can succeed if {@code timeUs} is beyond the end
   *     of the buffer.
   * @return Whether the skip was successful.
   */
  public boolean skipToKeyframeBefore(long timeUs, boolean allowTimeBeyondBuffer) {
    long nextOffset = metadataQueue.skipToKeyframeBefore(timeUs, allowTimeBeyondBuffer);
    if (nextOffset == C.POSITION_UNSET) {
      return false;
    }
    dropDownstreamTo(nextOffset);
    return true;
  }

  /**
   * Attempts to read from the queue.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the
   *     {@link C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @param formatRequired Whether the caller requires that the format of the stream be read even if
   *     it's not changing. A sample will never be read if set to true, however it is still possible
   *     for the end of stream or nothing to be read.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @param decodeOnlyUntilUs If a buffer is read, the {@link C#BUFFER_FLAG_DECODE_ONLY} flag will
   *     be set if the buffer's timestamp is less than this value.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired,
      boolean loadingFinished, long decodeOnlyUntilUs) {
    int result = metadataQueue.readData(formatHolder, buffer, formatRequired, loadingFinished,
        downstreamFormat, extrasHolder);
    switch (result) {
      case C.RESULT_FORMAT_READ:
        downstreamFormat = formatHolder.format;
        return C.RESULT_FORMAT_READ;
      case C.RESULT_BUFFER_READ:
        if (!buffer.isEndOfStream()) {
          if (buffer.timeUs < decodeOnlyUntilUs) {
            buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
          }
          // Read encryption data if the sample is encrypted.
          if (buffer.isEncrypted()) {
            readEncryptionData(buffer, extrasHolder);
          }
          // Write the sample data into the holder.
          buffer.ensureSpaceForWrite(extrasHolder.size);
          readData(extrasHolder.offset, buffer.data, extrasHolder.size);
          // Advance the read head.
          dropDownstreamTo(extrasHolder.nextOffset);
        }
        return C.RESULT_BUFFER_READ;
      case C.RESULT_NOTHING_READ:
        return C.RESULT_NOTHING_READ;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Reads encryption data for the current sample.
   * <p>
   * The encryption data is written into {@link DecoderInputBuffer#cryptoInfo}, and
   * {@link SampleExtrasHolder#size} is adjusted to subtract the number of bytes that were read. The
   * same value is added to {@link SampleExtrasHolder#offset}.
   *
   * @param buffer The buffer into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   */
  private void readEncryptionData(DecoderInputBuffer buffer, SampleExtrasHolder extrasHolder) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    scratch.reset(1);
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
      scratch.reset(2);
      readData(offset, scratch.data, 2);
      offset += 2;
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
      scratch.reset(subsampleDataLength);
      readData(offset, scratch.data, subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = extrasHolder.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    CryptoData cryptoData = extrasHolder.cryptoData;
    buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
        cryptoData.encryptionKey, buffer.cryptoInfo.iv, cryptoData.cryptoMode);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    extrasHolder.size -= bytesRead;
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

  // Called by the loading thread.

  /**
   * Sets a listener to be notified of changes to the upstream format.
   *
   * @param listener The listener.
   */
  public void setUpstreamFormatChangeListener(UpstreamFormatChangedListener listener) {
    upstreamFormatChangeListener = listener;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * subsequently queued to the buffer.
   *
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      pendingFormatAdjustment = true;
    }
  }

  @Override
  public void format(Format format) {
    Format adjustedFormat = getAdjustedSampleFormat(format, sampleOffsetUs);
    boolean formatChanged = metadataQueue.format(adjustedFormat);
    lastUnadjustedFormat = format;
    pendingFormatAdjustment = false;
    if (upstreamFormatChangeListener != null && formatChanged) {
      upstreamFormatChangeListener.onUpstreamFormatChanged(adjustedFormat);
    }
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    if (!startWriteOperation()) {
      int bytesSkipped = input.skip(length);
      if (bytesSkipped == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        }
        throw new EOFException();
      }
      return bytesSkipped;
    }
    try {
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
    } finally {
      endWriteOperation();
    }
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    if (!startWriteOperation()) {
      buffer.skipBytes(length);
      return;
    }
    while (length > 0) {
      int thisAppendLength = prepareForAppend(length);
      buffer.readBytes(lastAllocation.data, lastAllocation.translateOffset(lastAllocationOffset),
          thisAppendLength);
      lastAllocationOffset += thisAppendLength;
      totalBytesWritten += thisAppendLength;
      length -= thisAppendLength;
    }
    endWriteOperation();
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
      CryptoData cryptoData) {
    if (pendingFormatAdjustment) {
      format(lastUnadjustedFormat);
    }
    if (!startWriteOperation()) {
      metadataQueue.commitSampleTimestamp(timeUs);
      return;
    }
    try {
      if (pendingSplice) {
        if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0 || !metadataQueue.attemptSplice(timeUs)) {
          return;
        }
        pendingSplice = false;
      }
      timeUs += sampleOffsetUs;
      long absoluteOffset = totalBytesWritten - size - offset;
      metadataQueue.commitSample(timeUs, flags, absoluteOffset, size, cryptoData);
    } finally {
      endWriteOperation();
    }
  }

  // Private methods.

  private boolean startWriteOperation() {
    return state.compareAndSet(STATE_ENABLED, STATE_ENABLED_WRITING);
  }

  private void endWriteOperation() {
    if (!state.compareAndSet(STATE_ENABLED_WRITING, STATE_ENABLED)) {
      clearSampleData();
    }
  }

  private void clearSampleData() {
    metadataQueue.clearSampleData();
    allocator.release(dataQueue.toArray(new Allocation[dataQueue.size()]));
    dataQueue.clear();
    allocator.trim();
    totalBytesDropped = 0;
    totalBytesWritten = 0;
    lastAllocation = null;
    lastAllocationOffset = allocationLength;
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

}

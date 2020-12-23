/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;

/** Buffer that stores multiple encoded access units to allow batch processing. */
/* package */ final class BatchBuffer extends DecoderInputBuffer {
  /** Arbitrary limit to the number of access unit in a full batch buffer. */
  public static final int DEFAULT_BATCH_SIZE_ACCESS_UNITS = 32;
  /**
   * Arbitrary limit to the memory used by a full batch buffer to avoid using too much memory for
   * very high bitrate. Equivalent of 75s of mp3 at highest bitrate (320kb/s) and 30s of AAC LC at
   * highest bitrate (800kb/s). That limit is ignored for the first access unit to avoid stalling
   * stream with huge access units.
   */
  private static final int BATCH_SIZE_BYTES = 3 * 1000 * 1024;

  private final DecoderInputBuffer nextAccessUnitBuffer;
  private boolean hasPendingAccessUnit;

  private long firstAccessUnitTimeUs;
  private int accessUnitCount;
  private int maxAccessUnitCount;

  public BatchBuffer() {
    super(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    nextAccessUnitBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    clear();
  }

  /** Sets the maximum number of access units the buffer can contain before being full. */
  public void setMaxAccessUnitCount(@IntRange(from = 1) int maxAccessUnitCount) {
    Assertions.checkArgument(maxAccessUnitCount > 0);
    this.maxAccessUnitCount = maxAccessUnitCount;
  }

  /** Gets the maximum number of access units the buffer can contain before being full. */
  public int getMaxAccessUnitCount() {
    return maxAccessUnitCount;
  }

  /** Resets the state of this object to what it was after construction. */
  @Override
  public void clear() {
    flush();
    maxAccessUnitCount = DEFAULT_BATCH_SIZE_ACCESS_UNITS;
  }

  /** Clear all access units from the BatchBuffer to empty it. */
  public void flush() {
    clearMainBuffer();
    nextAccessUnitBuffer.clear();
    hasPendingAccessUnit = false;
  }

  /** Clears the state of the batch buffer to be ready to receive a new sequence of access units. */
  public void batchWasConsumed() {
    clearMainBuffer();
    if (hasPendingAccessUnit) {
      putAccessUnit(nextAccessUnitBuffer);
      hasPendingAccessUnit = false;
    }
  }

  /**
   * Gets the buffer to fill-out that will then be append to the batch buffer with {@link
   * #commitNextAccessUnit()}.
   */
  public DecoderInputBuffer getNextAccessUnitBuffer() {
    return nextAccessUnitBuffer;
  }

  /** Gets the timestamp of the first access unit in the buffer. */
  public long getFirstAccessUnitTimeUs() {
    return firstAccessUnitTimeUs;
  }

  /** Gets the timestamp of the last access unit in the buffer. */
  public long getLastAccessUnitTimeUs() {
    return timeUs;
  }

  /** Gets the number of access units contained in this batch buffer. */
  public int getAccessUnitCount() {
    return accessUnitCount;
  }

  /** If the buffer contains no access units. */
  public boolean isEmpty() {
    return accessUnitCount == 0;
  }

  /** If more access units should be added to the batch buffer. */
  public boolean isFull() {
    return accessUnitCount >= maxAccessUnitCount
        || (data != null && data.position() >= BATCH_SIZE_BYTES)
        || hasPendingAccessUnit;
  }

  /**
   * Appends the staged access unit in this batch buffer.
   *
   * @throws IllegalStateException If calling this method on a full or end of stream batch buffer.
   * @throws IllegalArgumentException If the {@code accessUnit} is encrypted or has
   *     supplementalData, as batching of those state has not been implemented.
   */
  public void commitNextAccessUnit() {
    DecoderInputBuffer accessUnit = nextAccessUnitBuffer;
    Assertions.checkState(!isFull() && !isEndOfStream());
    Assertions.checkArgument(!accessUnit.isEncrypted() && !accessUnit.hasSupplementalData());
    if (!canBatch(accessUnit)) {
      hasPendingAccessUnit = true; // Delay the putAccessUnit until the batch buffer is empty.
      return;
    }
    putAccessUnit(accessUnit);
  }

  private boolean canBatch(DecoderInputBuffer accessUnit) {
    if (isEmpty()) {
      return true; // Batching with an empty batch must always succeed or the stream will stall.
    }
    if (accessUnit.isDecodeOnly() != isDecodeOnly()) {
      return false; // Decode only and non decode only access units can not be batched together.
    }

    @Nullable ByteBuffer accessUnitData = accessUnit.data;
    if (accessUnitData != null
        && this.data != null
        && this.data.position() + accessUnitData.limit() >= BATCH_SIZE_BYTES) {
      return false; // The batch buffer does not have the capacity to add this access unit.
    }
    return true;
  }

  private void putAccessUnit(DecoderInputBuffer accessUnit) {
    if (accessUnit.isEndOfStream()) {
      setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    } else {
      timeUs = accessUnit.timeUs;
      if (accessUnit.isDecodeOnly()) {
        setFlags(C.BUFFER_FLAG_DECODE_ONLY);
      }
      if (accessUnit.isKeyFrame()) {
        setFlags(C.BUFFER_FLAG_KEY_FRAME);
      }
      @Nullable ByteBuffer accessUnitData = accessUnit.data;
      if (accessUnitData != null) {
        accessUnit.flip();
        ensureSpaceForWrite(accessUnitData.remaining());
        this.data.put(accessUnitData);
      }
      accessUnitCount++;
      if (accessUnitCount == 1) {
        firstAccessUnitTimeUs = timeUs;
      }
    }
    accessUnit.clear();
  }

  private void clearMainBuffer() {
    super.clear();
    accessUnitCount = 0;
    firstAccessUnitTimeUs = C.TIME_UNSET;
    timeUs = C.TIME_UNSET;
  }
}

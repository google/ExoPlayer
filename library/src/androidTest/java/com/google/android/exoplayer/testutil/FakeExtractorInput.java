/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.testutil;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.ExtractorInput;

import android.util.SparseBooleanArray;

import junit.framework.Assert;

import java.io.EOFException;
import java.io.IOException;

/**
 * A fake {@link ExtractorInput} capable of simulating various scenarios.
 * <p>
 * Read, skip and peek errors can be simulated using {@link Builder#setSimulateIOErrors}. When
 * enabled each read and skip will throw a {@link SimulatedIOException} unless one has already been
 * thrown from the current position. Each peek will throw {@link SimulatedIOException} unless one
 * has already been thrown from the current peek position. When a {@link SimulatedIOException} is
 * thrown the read position is left unchanged and the peek position is reset back to the read
 * position.
 * <p>
 * Partial reads and skips can be simulated using {@link Builder#setSimulatePartialReads}. When
 * enabled, {@link #read(byte[], int, int)} and {@link #skip(int)} calls will only read or skip a
 * single byte unless a partial read or skip has already been performed that had the same target
 * position. For example, a first read request for 10 bytes will be partially satisfied by reading
 * a single byte and advancing the position to 1. If the following read request attempts to read 9
 * bytes then it will be fully satisfied, since it has the same target position of 10.
 * <p>
 * Unknown data length can be simulated using {@link Builder#setSimulateUnknownLength}. When enabled
 * {@link #getLength()} will return {@link C#LENGTH_UNBOUNDED} rather than the length of the data.
 */
public final class FakeExtractorInput implements ExtractorInput {

  /**
   * Thrown when simulating an {@link IOException}.
   */
  public static final class SimulatedIOException extends IOException {

    public SimulatedIOException(String message) {
      super(message);
    }

  }

  private final byte[] data;
  private final boolean simulateUnknownLength;
  private final boolean simulatePartialReads;
  private final boolean simulateIOErrors;

  private int readPosition;
  private int peekPosition;

  private final SparseBooleanArray partiallySatisfiedTargetPositions;
  private final SparseBooleanArray failedReadPositions;
  private final SparseBooleanArray failedPeekPositions;

  private FakeExtractorInput(byte[] data, boolean simulateUnknownLength,
      boolean simulatePartialReads, boolean simulateIOErrors) {
    this.data = data;
    this.simulateUnknownLength = simulateUnknownLength;
    this.simulatePartialReads = simulatePartialReads;
    this.simulateIOErrors = simulateIOErrors;
    partiallySatisfiedTargetPositions = new SparseBooleanArray();
    failedReadPositions = new SparseBooleanArray();
    failedPeekPositions = new SparseBooleanArray();
  }

  /**
   * Sets the read and peek positions.
   *
   * @param position The position to set.
   */
  public void setPosition(int position) {
    Assert.assertTrue(0 <= position && position < data.length);
    readPosition = position;
    peekPosition = position;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    length = getReadLength(length);
    if (readFully(target, offset, length, true)) {
      return length;
    }
    return C.RESULT_END_OF_INPUT;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    if (!checkXFully(allowEndOfInput, readPosition, length, failedReadPositions)) {
      return false;
    }
    System.arraycopy(data, readPosition, target, offset, length);
    readPosition += length;
    peekPosition = readPosition;
    return true;
  }

  @Override
  public void readFully(byte[] target, int offset, int length) throws IOException {
    readFully(target, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException {
    length = getReadLength(length);
    if (skipFully(length, true)) {
      return length;
    }
    return C.RESULT_END_OF_INPUT;
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
    if (!checkXFully(allowEndOfInput, readPosition, length, failedReadPositions)) {
      return false;
    }
    readPosition += length;
    peekPosition = readPosition;
    return true;
  }

  @Override
  public void skipFully(int length) throws IOException {
    skipFully(length, false);
  }

  @Override
  public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    if (!checkXFully(allowEndOfInput, peekPosition, length, failedPeekPositions)) {
      return false;
    }
    System.arraycopy(data, peekPosition, target, offset, length);
    peekPosition += length;
    return true;
  }

  @Override
  public void peekFully(byte[] target, int offset, int length) throws IOException {
    peekFully(target, offset, length, false);
  }

  @Override
  public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
    if (!checkXFully(allowEndOfInput, peekPosition, length, failedPeekPositions)) {
      return false;
    }
    peekPosition += length;
    return true;
  }

  @Override
  public void advancePeekPosition(int length) throws IOException {
    advancePeekPosition(length, false);
  }

  @Override
  public void resetPeekPosition() {
    peekPosition = readPosition;
  }

  @Override
  public long getPeekPosition() {
    return peekPosition;
  }

  @Override
  public long getPosition() {
    return readPosition;
  }

  @Override
  public long getLength() {
    return simulateUnknownLength ? C.LENGTH_UNBOUNDED : data.length;
  }

  private boolean checkXFully(boolean allowEndOfInput, int position, int length,
      SparseBooleanArray failedPositions) throws IOException {
    if (simulateIOErrors && !failedPositions.get(position)) {
      failedPositions.put(position, true);
      peekPosition = readPosition;
      throw new SimulatedIOException("Simulated IO error at position: " + position);
    }
    if (isEof()) {
      if (allowEndOfInput) {
        return false;
      }
      throw new EOFException();
    }
    if (position + length > data.length) {
      throw new IOException("Attempted to move past end of data: (" + position + " + "
          + length + ") > " + data.length);
    }
    return true;
  }

  private int getReadLength(int requestedLength) {
    int targetPosition = readPosition + requestedLength;
    if (simulatePartialReads && requestedLength > 1
        && !partiallySatisfiedTargetPositions.get(targetPosition)) {
      partiallySatisfiedTargetPositions.put(targetPosition, true);
      return 1;
    }
    return Math.min(requestedLength, data.length - readPosition);
  }

  private boolean isEof() {
    return readPosition == data.length;
  }

  /**
   * Builder of {@link FakeExtractorInput} instances.
   */
  public static final class Builder {

    private byte[] data;
    private boolean simulateUnknownLength;
    private boolean simulatePartialReads;
    private boolean simulateIOErrors;

    public Builder() {
      data = new byte[0];
    }

    public Builder setData(byte[] data) {
      this.data = data;
      return this;
    }

    public Builder setSimulateUnknownLength(boolean simulateUnknownLength) {
      this.simulateUnknownLength = simulateUnknownLength;
      return this;
    }

    public Builder setSimulatePartialReads(boolean simulatePartialReads) {
      this.simulatePartialReads = simulatePartialReads;
      return this;
    }

    public Builder setSimulateIOErrors(boolean simulateIOErrors) {
      this.simulateIOErrors = simulateIOErrors;
      return this;
    }

    public FakeExtractorInput build() {
      return new FakeExtractorInput(data, simulateUnknownLength, simulatePartialReads,
          simulateIOErrors);
    }

  }

}

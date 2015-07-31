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
import com.google.android.exoplayer.upstream.DataSource;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * An {@link ExtractorInput} that wraps a {@link DataSource}.
 */
public final class DefaultExtractorInput implements ExtractorInput {

  private static final byte[] SCRATCH_SPACE = new byte[4096];

  private final DataSource dataSource;
  private final long streamLength;

  private long position;
  private byte[] peekBuffer;
  private int peekBufferPosition;
  private int peekBufferLength;

  /**
   * @param dataSource The wrapped {@link DataSource}.
   * @param position The initial position in the stream.
   * @param length The length of the stream, or {@link C#LENGTH_UNBOUNDED} if it is unknown.
   */
  public DefaultExtractorInput(DataSource dataSource, long position, long length) {
    this.dataSource = dataSource;
    this.position = position;
    this.streamLength = length;
    peekBuffer = new byte[8 * 1024];
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    int peekBytes = Math.min(peekBufferLength, length);
    System.arraycopy(peekBuffer, 0, target, offset, peekBytes);
    offset += peekBytes;
    length -= peekBytes;
    int bytesRead = length != 0 ? dataSource.read(target, offset, length) : 0;
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return C.RESULT_END_OF_INPUT;
    }
    updatePeekBuffer(peekBytes);
    bytesRead += peekBytes;
    position += bytesRead;
    return bytesRead;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    int peekBytes = Math.min(peekBufferLength, length);
    System.arraycopy(peekBuffer, 0, target, offset, peekBytes);
    offset += peekBytes;
    int remaining = length - peekBytes;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(target, offset, remaining);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput && remaining == length) {
          return false;
        }
        throw new EOFException();
      }
      offset += bytesRead;
      remaining -= bytesRead;
    }
    updatePeekBuffer(peekBytes);
    position += length;
    return true;
  }

  @Override
  public void readFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    readFully(target, offset, length, false);
  }

  @Override
  public void skipFully(int length) throws IOException, InterruptedException {
    int peekBytes = Math.min(peekBufferLength, length);
    int remaining = length - peekBytes;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(SCRATCH_SPACE, 0, Math.min(SCRATCH_SPACE.length, remaining));
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        throw new EOFException();
      }
      remaining -= bytesRead;
    }
    updatePeekBuffer(peekBytes);
    position += length;
  }

  @Override
  public void peekFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    ensureSpaceForPeek(length);
    int peekBytes = Math.min(peekBufferLength - peekBufferPosition, length);
    System.arraycopy(peekBuffer, peekBufferPosition, target, offset, peekBytes);
    offset += peekBytes;
    int fillBytes = length - peekBytes;
    int remaining = fillBytes;
    int writePosition = peekBufferLength;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(peekBuffer, writePosition, remaining);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        throw new EOFException();
      }
      System.arraycopy(peekBuffer, writePosition, target, offset, bytesRead);
      remaining -= bytesRead;
      writePosition += bytesRead;
      offset += bytesRead;
    }
    peekBufferPosition += length;
    peekBufferLength += fillBytes;
  }

  @Override
  public void advancePeekPosition(int length) throws IOException, InterruptedException {
    ensureSpaceForPeek(length);
    int peekBytes = Math.min(peekBufferLength - peekBufferPosition, length);
    int fillBytes = length - peekBytes;
    int remaining = fillBytes;
    int writePosition = peekBufferLength;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(peekBuffer, writePosition, remaining);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        throw new EOFException();
      }
      remaining -= bytesRead;
      writePosition += bytesRead;
    }
    peekBufferPosition += length;
    peekBufferLength += fillBytes;
  }

  @Override
  public void resetPeekPosition() {
    peekBufferPosition = 0;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getLength() {
    return streamLength;
  }

  /**
   * Ensures {@code peekBuffer} is large enough to store at least {@code length} bytes from the
   * current peek position.
   */
  private void ensureSpaceForPeek(int length) {
    int requiredLength = peekBufferPosition + length;
    if (requiredLength > peekBuffer.length) {
      peekBuffer = Arrays.copyOf(peekBuffer, Math.max(peekBuffer.length * 2, requiredLength));
    }
  }

  /**
   * Updates the peek buffer's length, position and contents after consuming data.
   *
   * @param bytesConsumed The number of bytes consumed from the peek buffer.
   */
  private void updatePeekBuffer(int bytesConsumed) {
    peekBufferLength -= bytesConsumed;
    peekBufferPosition = 0;
    System.arraycopy(peekBuffer, bytesConsumed, peekBuffer, 0, peekBufferLength);
  }

}

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
    return internalRead(target, offset, length, true, false, false);
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return internalReadFully(target, offset, length, allowEndOfInput, false);
  }

  @Override
  public void readFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    readFully(target, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException, InterruptedException {
    return internalRead(null, 0, Math.min(SCRATCH_SPACE.length + peekBufferLength, length), true,
        true, false);
  }

  @Override
  public boolean skipFully(final int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return internalReadFully(null, 0, length, allowEndOfInput, true);
  }

  @Override
  public void skipFully(int length) throws IOException, InterruptedException {
    skipFully(length, false);
  }

  @Override
  public void peekFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    advancePeekPosition(length);
    System.arraycopy(peekBuffer, peekBufferPosition - length, target, offset, length);
  }

  @Override
  public void advancePeekPosition(int length) throws IOException, InterruptedException {
    ensureSpaceForPeek(length);
    peekBufferPosition += length;
    if (peekBufferPosition > peekBufferLength) {
      readFromDataSource(peekBuffer, peekBufferLength, peekBufferPosition - peekBufferLength,
          false, false, 0, true);
      peekBufferLength = peekBufferPosition;
    }
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

  /**
   * Internal read method.
   *
   * @see #internalRead(byte[], int, int, boolean, boolean, boolean)
   */
  private boolean internalReadFully(byte[] target, int offset, int length, boolean allowEndOfInput,
      boolean skip) throws InterruptedException, IOException {
    return internalRead(target, offset, length, allowEndOfInput, skip, true)
        != C.RESULT_END_OF_INPUT;
  }

  /**
   * Internal read method.
   *
   * @see #readFromPeekBuffer(byte[], int, int, boolean)
   * @see #readFromDataSource(byte[], int, int, boolean, boolean, int, boolean)
   */
  private int internalRead(byte[] target, int offset, int length, boolean allowEndOfInput,
      boolean skip, boolean readFully) throws InterruptedException, IOException {
    int totalBytesRead = readFromPeekBuffer(target, offset, length, skip);
    totalBytesRead = readFromDataSource(target, offset, length, allowEndOfInput, skip,
        totalBytesRead, readFully);
    if (totalBytesRead != C.RESULT_END_OF_INPUT) {
      position += totalBytesRead;
    }
    return totalBytesRead;
  }

  /**
   * Reads from the peek buffer
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @param skip If true, instead of reading skip the data
   * @return The number of bytes read
   */
  private int readFromPeekBuffer(byte[] target, int offset, int length, boolean skip) {
    if (peekBufferLength == 0) {
      return 0;
    }

    int peekBytes = Math.min(peekBufferLength, length);
    if (!skip) {
      System.arraycopy(peekBuffer, 0, target, offset, peekBytes);
    }
    updatePeekBuffer(peekBytes);
    return peekBytes;
  }

  /**
   * Reads from the data source
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @param returnEOIifNoReadBytes If true on encountering the end of the input having read no data
   * should result in {@link C#RESULT_END_OF_INPUT} being returned.
   * @param skip If true, instead of reading skip the data
   * @param totalBytesRead Number of bytes read until now for the external request
   * @param readFully If true reads the requested {@code length} in full.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private int readFromDataSource(byte[] target, int offset, int length,
      boolean returnEOIifNoReadBytes, boolean skip, int totalBytesRead, boolean readFully)
      throws InterruptedException, IOException {
    while (totalBytesRead < length) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }

      int bytesRead = !skip
          ? dataSource.read(target, offset + totalBytesRead, length - totalBytesRead)
          : dataSource.read(SCRATCH_SPACE, 0,
              Math.min(SCRATCH_SPACE.length, length - totalBytesRead));

      if (bytesRead == C.RESULT_END_OF_INPUT) {
        if (returnEOIifNoReadBytes && totalBytesRead == 0) {
          return C.RESULT_END_OF_INPUT;
        }
        if (readFully) {
          throw new EOFException();
        }
      } else {
        totalBytesRead += bytesRead;
      }

      if (!readFully) {
        break;
      }
    }
    return totalBytesRead;
  }

}

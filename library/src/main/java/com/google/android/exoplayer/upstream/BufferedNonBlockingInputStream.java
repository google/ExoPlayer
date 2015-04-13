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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.util.Assertions;

import java.nio.ByteBuffer;

/**
 * Input stream with non-blocking reading/skipping that also stores read/skipped data in a buffer.
 * Call {@link #mark} to discard any buffered data before the current reading position. Call
 * {@link #returnToMark} to move the current reading position back to the marked position, which is
 * initially the start of the input stream.
 */
public final class BufferedNonBlockingInputStream implements NonBlockingInputStream {

  private final NonBlockingInputStream inputStream;
  private final byte[] bufferedBytes;

  private long inputStreamPosition;

  private int readPosition;
  private int writePosition;

  /**
   * Wraps the specified {@code nonBlockingInputStream} for buffered reading using a buffer of size
   * {@code bufferSize} bytes.
   */
  public BufferedNonBlockingInputStream(
      NonBlockingInputStream nonBlockingInputStream, int bufferSize) {
    inputStream = Assertions.checkNotNull(nonBlockingInputStream);
    bufferedBytes = new byte[bufferSize];
  }

  @Override
  public int skip(int length) {
    return consumeStream(null, null, 0, length);
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    return consumeStream(null, buffer, offset, length);
  }

  @Override
  public int read(ByteBuffer buffer, int length) {
    return consumeStream(buffer, null, 0, length);
  }

  @Override
  public long getAvailableByteCount() {
    // The amount that can be read from the input stream is limited by how much can be buffered.
    return (writePosition - readPosition)
        + Math.min(inputStream.getAvailableByteCount(), bufferedBytes.length - writePosition);
  }

  @Override
  public boolean isEndOfStream() {
    return writePosition == readPosition && inputStream.isEndOfStream();
  }

  @Override
  public void close() {
    inputStream.close();
    inputStreamPosition = -1;
  }

  /** Returns the current position in the stream. */
  public long getReadPosition() {
    return inputStreamPosition - (writePosition - readPosition);
  }

  /**
   * Moves the mark to be at the current position. Any data before the current position is
   * discarded. After calling this method, calling {@link #returnToMark} will move the reading
   * position back to the mark position.
   */
  public void mark() {
    System.arraycopy(bufferedBytes, readPosition, bufferedBytes, 0, writePosition - readPosition);
    writePosition -= readPosition;
    readPosition = 0;
  }

  /** Moves the current position back to the mark position. */
  public void returnToMark() {
    readPosition = 0;
  }

  /**
   * Reads or skips data from the input stream. If {@code byteBuffer} is non-{@code null}, reads
   * {@code length} bytes into {@code byteBuffer} (other arguments are ignored). If
   * {@code byteArray} is non-{@code null}, reads {@code length} bytes into {@code byteArray} at
   * {@code offset} (other arguments are ignored). Otherwise, skips {@code length} bytes.
   *
   * @param byteBuffer {@link ByteBuffer} to read into, or {@code null} to read into
   *     {@code byteArray} or skip.
   * @param byteArray Byte array to read into, or {@code null} to read into {@code byteBuffer} or
   *     skip.
   * @param offset Offset in {@code byteArray} to write to, if it is non-{@code null}.
   * @param length Number of bytes to read or skip.
   * @return The number of bytes consumed, or -1 if nothing was consumed and the end of stream was
   *     reached.
   */
  private int consumeStream(ByteBuffer byteBuffer, byte[] byteArray, int offset, int length) {
    // If necessary, reduce length so that we do not need to write past the end of the array.
    int pendingBytes = writePosition - readPosition;
    length = Math.min(length, bufferedBytes.length - writePosition + pendingBytes);

    // If reading past the end of buffered data, request more and populate the buffer.
    int streamBytesRead = 0;
    if (length - pendingBytes > 0) {
      streamBytesRead = inputStream.read(bufferedBytes, writePosition, length - pendingBytes);
      if (streamBytesRead > 0) {
        inputStreamPosition += streamBytesRead;

        writePosition += streamBytesRead;
        pendingBytes += streamBytesRead;
      }
    }

    // Signal the end of the stream if nothing more will be read.
    if (streamBytesRead == -1 && pendingBytes == 0) {
      return -1;
    }

    // Fill the buffer using buffered data if reading, or just skip otherwise.
    length = Math.min(pendingBytes, length);
    if (byteBuffer != null) {
      byteBuffer.put(bufferedBytes, readPosition, length);
    } else if (byteArray != null) {
      System.arraycopy(bufferedBytes, readPosition, byteArray, offset, length);
    }
    readPosition += length;
    return length;
  }

}

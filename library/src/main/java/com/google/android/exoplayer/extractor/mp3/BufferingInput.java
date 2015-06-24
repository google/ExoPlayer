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
package com.google.android.exoplayer.extractor.mp3;

import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferOverflowException;

/**
 * Buffers bytes read from an {@link ExtractorInput} to allow re-reading buffered bytes within a
 * window starting at a marked position.
 */
/* package */ final class BufferingInput {

  private final ParsableByteArray buffer;
  private final int capacity;

  private int readPosition;
  private int writePosition;
  private int markPosition;

  /**
   * Constructs a new buffer for reading from extractor inputs that can store up to {@code capacity}
   * bytes.
   *
   * @param capacity Number of bytes that can be stored in the buffer.
   */
  public BufferingInput(int capacity) {
    this.capacity = capacity;
    buffer = new ParsableByteArray(capacity * 2);
  }

  /** Discards any pending data in the buffer and returns the writing position to zero. */
  public void reset() {
    readPosition = 0;
    writePosition = 0;
    markPosition = 0;
  }

  /**
   * Moves the mark to be at the reading position. Any data before the reading position is
   * discarded. After calling this method, calling {@link #returnToMark} will move the reading
   * position back to the mark position.
   */
  public void mark() {
    if (readPosition > capacity) {
      System.arraycopy(buffer.data, readPosition, buffer.data, 0, writePosition - readPosition);
      writePosition -= readPosition;
      readPosition = 0;
    }
    markPosition = readPosition;
  }

  /** Moves the reading position back to the mark position. */
  public void returnToMark() {
    readPosition = markPosition;
  }

  /** Returns the number of bytes available for reading from the current position. */
  public int getAvailableByteCount() {
    return writePosition - readPosition;
  }

  /**
   * Buffers any more data required to read {@code length} bytes from the reading position, and
   * returns a {@link ParsableByteArray} that wraps the buffer's byte array, with its position set
   * to the current reading position. The read position is then updated for having read
   * {@code length} bytes.
   *
   * @param extractorInput {@link ExtractorInput} from which additional data should be read.
   * @param length Number of bytes that will be readable in the returned array.
   * @return {@link ParsableByteArray} from which {@code length} bytes can be read.
   * @throws IOException Thrown if there was an error reading from the stream.
   * @throws InterruptedException Thrown if reading from the stream was interrupted.
   */
  public ParsableByteArray getParsableByteArray(ExtractorInput extractorInput, int length)
      throws IOException, InterruptedException {
    if (!ensureLoaded(extractorInput, length)) {
      throw new EOFException();
    }
    ParsableByteArray parsableByteArray = new ParsableByteArray(buffer.data, writePosition);
    parsableByteArray.setPosition(readPosition);
    readPosition += length;
    return parsableByteArray;
  }

  /**
   * Drains as much buffered data as possible up to {@code length} bytes to {@code trackOutput}.
   *
   * @param trackOutput Track output to populate with up to {@code length} bytes of sample data.
   * @param length Number of bytes to try to read from the buffer.
   * @return The number of buffered bytes written.
   */
  public int drainToOutput(TrackOutput trackOutput, int length) {
    if (length == 0) {
      return 0;
    }
    buffer.setPosition(readPosition);
    int bytesToDrain = Math.min(writePosition - readPosition, length);
    trackOutput.sampleData(buffer, bytesToDrain);
    readPosition += bytesToDrain;
    return bytesToDrain;
  }

  /**
   * Skips {@code length} bytes from the reading position, reading from {@code extractorInput} to
   * populate the buffer if required.
   *
   * @param extractorInput {@link ExtractorInput} from which additional data should be read.
   * @param length Number of bytes to skip.
   * @throws IOException Thrown if there was an error reading from the stream.
   * @throws InterruptedException Thrown if reading from the stream was interrupted.
   */
  public void skip(ExtractorInput extractorInput, int length)
      throws IOException, InterruptedException {
    if (!readInternal(extractorInput, null, 0, length)) {
      throw new EOFException();
    }
  }

  /**
   * Reads {@code length} bytes from the reading position, reading from {@code extractorInput} to
   * populate the buffer if required.
   *
   * @param extractorInput {@link ExtractorInput} from which additional data should be read.
   * @param length Number of bytes to read.
   * @throws IOException Thrown if there was an error reading from the stream.
   * @throws InterruptedException Thrown if reading from the stream was interrupted.
   * @throws EOFException Thrown if the end of the file was reached.
   */
  public void read(ExtractorInput extractorInput, byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    if (!readInternal(extractorInput, target, offset, length)) {
      throw new EOFException();
    }
  }

  /**
   * Reads {@code length} bytes from the reading position, reading from {@code extractorInput} to
   * populate the buffer if required.
   *
   * <p>Returns {@code false} if the end of the stream has been reached. Throws {@link EOFException}
   * if the read request could only be partially satisfied. Returns {@code true} otherwise.
   *
   * @param extractorInput {@link ExtractorInput} from which additional data should be read.
   * @param length Number of bytes to read.
   * @return Whether the extractor input is at the end of the stream.
   * @throws IOException Thrown if there was an error reading from the stream.
   * @throws InterruptedException Thrown if reading from the stream was interrupted.
   * @throws EOFException Thrown if the end of the file was reached.
   */
  public boolean readAllowingEndOfInput(ExtractorInput extractorInput, byte[] target, int offset,
      int length) throws IOException, InterruptedException {
    return readInternal(extractorInput, target, offset, length);
  }

  private boolean readInternal(ExtractorInput extractorInput, byte[] target, int offset, int length)
      throws InterruptedException, IOException {
    if (!ensureLoaded(extractorInput, length)) {
      return false;
    }
    if (target != null) {
      System.arraycopy(buffer.data, readPosition, target, offset, length);
    }
    readPosition += length;
    return true;
  }

  /** Ensures the buffer contains enough data to read {@code length} bytes. */
  private boolean ensureLoaded(ExtractorInput extractorInput, int length)
      throws InterruptedException, IOException {
    if (length + readPosition - markPosition > capacity) {
      throw new BufferOverflowException();
    }

    int bytesToLoad = length - (writePosition - readPosition);
    if (bytesToLoad > 0) {
      if (!extractorInput.readFully(buffer.data, writePosition, bytesToLoad, true)) {
        return false;
      }
      writePosition += bytesToLoad;
    }
    return true;
  }

}

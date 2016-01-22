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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.ExtractorInput;

import java.io.EOFException;
import java.io.IOException;

/**
 * Implementation of {@link ExtractorInput} for testing purpose.
 */
/* package */ class RecordableExtractorInput implements ExtractorInput {
  protected static final byte STREAM_REVISION = 0x00;

  private byte[] data;
  private int readOffset;
  private int writeOffset;
  private int peekOffset;

  private boolean throwExceptionsAtRead = false;
  private boolean throwExceptionsAtPeek = false;
  private int numberOfReadsUntilException = 1;
  private int numberOfPeeksUntilException = 1;
  private int readCounter;
  private int peekCounter;
  private int maxReadExceptions = Integer.MAX_VALUE;
  private int maxPeekExceptions = Integer.MAX_VALUE;
  private int readExceptionCounter;
  private int peekExceptionCounter;


  /**
   * Constructs an instance with a initial array of bytes.
   *
   * @param data the initial data.
   * @param writeOffset the {@code writeOffset} from where to start recording.
   */
  public RecordableExtractorInput(byte[] data, int writeOffset) {
    this.data = data;
    this.writeOffset = writeOffset;
  }

  /**
   * Constructs an instance with an empty data array with length {@code maxBytes}.
   *
   * @param maxBytes the maximal number of bytes this {@code ExtractorInput} can store.
   */
  public RecordableExtractorInput(int maxBytes) {
    this(new byte[maxBytes], 0);
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
    readFully(target, offset, length);
    return isEOF() ? C.RESULT_END_OF_INPUT : length;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    readCounter++;
    if (throwExceptionsAtRead
        && readExceptionCounter < maxReadExceptions
        && readCounter % numberOfReadsUntilException == 0) {
      readCounter = 0;
      numberOfReadsUntilException++;
      readExceptionCounter++;
      throw new IOException("deliberately thrown an exception for testing");
    }
    if (readOffset + length > writeOffset) {
      if (!allowEndOfInput) {
        throw new EOFException();
      }
      return false;
    }
    System.arraycopy(data, readOffset, target, offset, length);
    readOffset += length;
    peekOffset = readOffset;
    return true;
  }

  @Override
  public void readFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    readFully(target, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException, InterruptedException {
    skipFully(length);
    return isEOF() ? C.RESULT_END_OF_INPUT : length;
  }

  private boolean isEOF() {
    return readOffset == writeOffset;
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    if (readOffset + length >= writeOffset) {
      if (!allowEndOfInput) {
        throw new EOFException();
      }
      return false;
    }
    readOffset += length;
    peekOffset = readOffset;
    return true;
  }

  @Override
  public void skipFully(int length) throws IOException, InterruptedException {
    skipFully(length, false);
  }

  @Override
  public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    peekCounter++;
    if (throwExceptionsAtPeek
        && peekExceptionCounter < maxPeekExceptions
        && peekCounter % numberOfPeeksUntilException == 0) {
      peekCounter = 0;
      numberOfPeeksUntilException++;
      peekExceptionCounter++;
      throw new IOException("deliberately thrown an exception for testing");
    }
    if (peekOffset + length > writeOffset) {
      if (!allowEndOfInput) {
        throw new EOFException();
      }
      return false;
    }
    System.arraycopy(data, peekOffset, target, offset, length);
    peekOffset += length;
    return true;
  }

  @Override
  public void peekFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    peekFully(target, offset, length, false);
  }

  @Override
  public boolean advancePeekPosition(int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    if (peekOffset + length >= writeOffset) {
      if (!allowEndOfInput) {
        throw new EOFException();
      }
      return false;
    }
    peekOffset += length;
    return true;
  }

  @Override
  public void advancePeekPosition(int length) throws IOException, InterruptedException {
    advancePeekPosition(length, false);
  }

  @Override
  public void resetPeekPosition() {
    peekOffset = readOffset;
  }

  @Override
  public long getPosition() {
    return readOffset;
  }

  @Override
  public long getLength() {
    return writeOffset;
  }

  /**
   * Records the {@code bytes}.
   *
   * @param bytes the bytes to record.
   */
  public void record(final byte[] bytes) {
    System.arraycopy(bytes, 0, data, writeOffset, bytes.length);
    writeOffset += bytes.length;
  }

  /** Records a single byte. **/
  public void record(byte b) {
    record(new byte[]{b});
  }

  /**
   * Gets a byte array with length {@code length} with ascending values starting from 0 (zero).
   *
   * @param length the length of the array.
   * @return an array of bytes with ascending values.
   */
  public static byte[] getBytesGrowingValues(int length) {
    return fillBytesGrowingValues(new byte[length], length, (byte) 0);
  }

  /**
   * Gets a byte array with length {@code length} with ascending values starting
   * from {@code startValue}.
   *
   * @param length the length of the array.
   * @param startValue the value from which to start.
   * @return an array of bytes with ascending values starting from {@code startValue}.
   */
  public static byte[] getBytesGrowingValues(int length, byte startValue) {
    return fillBytesGrowingValues(new byte[length], length, startValue);
  }

  /**
   * Fills the byte array passed as argument with ascending values.
   *
   * @param bytes the byte array to fill with values.
   * @param limit the number of bytes to set in the array.
   * @param startValue the startValue from which the values in the array have to start.
   */
  public static byte[] fillBytesGrowingValues(byte[] bytes, int limit, byte startValue) {
    for (int i = 0; i < bytes.length; i++) {
      if (i < limit) {
        bytes[i] = (byte) ((i + startValue) % 255);
      } else {
        bytes[i] = 0;
      }
    }
    return bytes;
  }

  public void setMaxReadExceptions(int maxReadExceptions) {
    this.maxReadExceptions = maxReadExceptions;
  }

  public void setMaxPeekExceptions(int maxPeekExceptions) {
    this.maxPeekExceptions = maxPeekExceptions;
  }

  public void setNumberOfReadsUntilException(int numberOfReadsUntilException) {
    this.numberOfReadsUntilException = numberOfReadsUntilException;
  }

  public void setNumberOfPeeksUntilException(int numberOfPeeksUntilException) {
    this.numberOfPeeksUntilException = numberOfPeeksUntilException;
  }

  public void doThrowExceptionsAtRead(boolean throwExceptionsAtRead) {
    this.throwExceptionsAtRead = throwExceptionsAtRead;
  }

  public void doThrowExceptionsAtPeek(boolean throwExceptionsAtPeek) {
    this.throwExceptionsAtPeek = throwExceptionsAtPeek;
  }

}

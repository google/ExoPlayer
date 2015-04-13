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

import java.io.EOFException;
import java.io.IOException;

/**
 * Provides data to be consumed by an {@link Extractor}.
 */
public interface ExtractorInput {

  /**
   * Reads up to {@code length} bytes from the input.
   * <p>
   * This method blocks until at least one byte of data can be read, the end of the input is
   * detected, or an exception is thrown.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  int read(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Like {@link #read(byte[], int, int)}, but reads the requested {@code length} in full.
   * <p>
   * If the end of the input is found having read no data, then behavior is dependent on
   * {@code allowEndOfInput}. If {@code allowEndOfInput == true} then {@code false} is returned.
   * Otherwise an {@link EOFException} is thrown.
   * <p>
   * Encountering the end of input having partially satisfied the read is always considered an
   * error, and will result in an {@link EOFException} being thrown.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@code false} being returned. False if it should be
   *     considered an error, causing an {@link EOFException} to be thrown.
   * @return True if the read was successful. False if the end of the input was encountered having
   *     read no data.
   * @throws EOFException If the end of input was encountered having partially satisfied the read
   *     (i.e. having read at least one byte, but fewer than {@code length}), or if no bytes were
   *     read and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Equivalent to {@code readFully(target, offset, length, false)}.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The number of bytes to read from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException;

  /**
   * Like {@link #readFully(byte[], int, int)}, except the data is skipped instead of read.
   * <p>
   * Encountering the end of input is always considered an error, and will result in an
   * {@link EOFException} being thrown.
   *
   * @param length The number of bytes to skip from the input.
   * @throws EOFException If the end of input was encountered.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  void skipFully(int length) throws IOException, InterruptedException;

  /**
   * The current position (byte offset) in the stream.
   *
   * @return The position (byte offset) in the stream.
   */
  long getPosition();

  /**
   * Returns the length of the source stream, or {@link C#LENGTH_UNBOUNDED} if it is unknown.
   *
   * @return The length of the source stream, or {@link C#LENGTH_UNBOUNDED}.
   */
  long getLength();

}

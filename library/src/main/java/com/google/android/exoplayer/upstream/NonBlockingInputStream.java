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

import java.nio.ByteBuffer;

/**
 * Represents a source of bytes that can be consumed by downstream components.
 * <p>
 * The read and skip methods are non-blocking, and hence return 0 (indicating that no data has
 * been read) in the case that data is not yet available to be consumed.
 */
public interface NonBlockingInputStream {

  /**
   * Skips over and discards up to {@code length} bytes of data. This method may skip over some
   * smaller number of bytes, possibly 0.
   *
   * @param length The maximum number of bytes to skip.
   * @return The actual number of bytes skipped, or -1 if the end of the data is reached.
   */
  int skip(int length);

  /**
   * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
   * index {@code offset}. This method may read fewer bytes, possibly 0.
   *
   * @param buffer The buffer into which the read data should be stored.
   * @param offset The start offset into {@code buffer} at which data should be written.
   * @param length The maximum number of bytes to read.
   * @return The actual number of bytes read, or -1 if the end of the data is reached.
   */
  int read(byte[] buffer, int offset, int length);

  /**
   * Reads up to {@code length} bytes of data and stores them into {@code buffer}. This method may
   * read fewer bytes, possibly 0.
   *
   * @param buffer The buffer into which the read data should be stored.
   * @param length The maximum number of bytes to read.
   * @return The actual number of bytes read, or -1 if the end of the data is reached.
   */
  int read(ByteBuffer buffer, int length);

  /**
   * Returns the number of bytes currently available for reading or skipping. Calls to the read()
   * and skip() methods are guaranteed to be satisfied in full if they request less than or
   * equal to the value returned.
   *
   * @return The number of bytes currently available.
   */
  long getAvailableByteCount();

  /**
   * Whether the end of the data has been reached.
   *
   * @return True if the end of the data has been reached, false otherwise.
   */
  boolean isEndOfStream();

  /**
   * Closes the input stream.
   */
  void close();

}

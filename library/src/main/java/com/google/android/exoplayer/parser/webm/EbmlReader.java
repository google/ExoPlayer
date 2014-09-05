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
package com.google.android.exoplayer.parser.webm;

import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import java.nio.ByteBuffer;

/**
 * Basic event-driven incremental EBML parser which needs an {@link EbmlEventHandler} to
 * define IDs/types and react to events.
 *
 * <p>EBML can be summarized as a binary XML format somewhat similar to Protocol Buffers.
 * It was originally designed for the Matroska container format. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 */
/* package */ interface EbmlReader {

  // Element Types
  /** Undefined element. */
  public static final int TYPE_UNKNOWN = 0;
  /** Contains child elements. */
  public static final int TYPE_MASTER = 1;
  /** Unsigned integer value of up to 8 bytes. */
  public static final int TYPE_UNSIGNED_INT = 2;
  public static final int TYPE_STRING = 3;
  public static final int TYPE_BINARY = 4;
  /** IEEE floating point value of either 4 or 8 bytes. */
  public static final int TYPE_FLOAT = 5;

  // Return values for reading methods.
  public static final int READ_RESULT_CONTINUE = 0;
  public static final int READ_RESULT_NEED_MORE_DATA = 1;
  public static final int READ_RESULT_END_OF_STREAM = 2;

  public void setEventHandler(EbmlEventHandler eventHandler);

  /**
   * Reads from a {@link NonBlockingInputStream}, invoking an event callback if possible.
   *
   * @param inputStream The input stream from which data should be read
   * @return One of the {@code RESULT_*} flags defined in this interface
   */
  public int read(NonBlockingInputStream inputStream);

  /**
   * The total number of bytes consumed by the reader since first created or last {@link #reset()}.
   */
  public long getBytesRead();

  /**
   * Resets the entire state of the reader so that it will read a new EBML structure from scratch.
   *
   * <p>This includes resetting the value returned from {@link #getBytesRead()} to 0 and discarding
   * all pending {@link EbmlEventHandler#onMasterElementEnd(int)} events.
   */
  public void reset();

  /**
   * Reads, parses, and returns an EBML variable-length integer (varint) from the contents
   * of a binary element.
   *
   * @param inputStream The input stream from which data should be read
   * @return The varint value at the current position of the contents of a binary element
   */
  public long readVarint(NonBlockingInputStream inputStream);

  /**
   * Reads a fixed number of bytes from the contents of a binary element into a {@link ByteBuffer}.
   *
   * @param inputStream The input stream from which data should be read
   * @param byteBuffer The {@link ByteBuffer} to which data should be written
   * @param totalBytes The fixed number of bytes to be read and written
   */
  public void readBytes(NonBlockingInputStream inputStream, ByteBuffer byteBuffer, int totalBytes);

  /**
   * Reads a fixed number of bytes from the contents of a binary element into a {@code byte[]}.
   *
   * @param inputStream The input stream from which data should be read
   * @param byteArray The byte array to which data should be written
   * @param totalBytes The fixed number of bytes to be read and written
   */
  public void readBytes(NonBlockingInputStream inputStream, byte[] byteArray, int totalBytes);

  /**
   * Skips a fixed number of bytes from the contents of a binary element.
   *
   * @param inputStream The input stream from which data should be skipped
   * @param totalBytes The fixed number of bytes to be skipped
   */
  public void skipBytes(NonBlockingInputStream inputStream, int totalBytes);

}

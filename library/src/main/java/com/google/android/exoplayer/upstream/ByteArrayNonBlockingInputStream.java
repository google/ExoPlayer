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
 * An implementation of {@link NonBlockingInputStream} for reading data from a byte array.
 */
public final class ByteArrayNonBlockingInputStream implements NonBlockingInputStream {

  private final byte[] data;

  private int position;

  public ByteArrayNonBlockingInputStream(byte[] data) {
    this.data = Assertions.checkNotNull(data);
  }

  @Override
  public int skip(int length) {
    int skipLength = getReadLength(length);
    position += skipLength;
    return skipLength;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    if (isEndOfStream()) {
      return -1;
    }
    int readLength = getReadLength(length);
    System.arraycopy(data, position, buffer, offset, readLength);
    position += readLength;
    return readLength;
  }

  @Override
  public int read(ByteBuffer buffer, int length) {
    if (isEndOfStream()) {
      return -1;
    }
    int readLength = getReadLength(length);
    buffer.put(data, position, readLength);
    position += readLength;
    return readLength;
  }

  @Override
  public long getAvailableByteCount() {
    return data.length - position;
  }

  @Override
  public boolean isEndOfStream() {
    return position == data.length;
  }

  @Override
  public void close() {
    // Do nothing.
  }

  private int getReadLength(int requestedLength) {
    return Math.min(requestedLength, data.length - position);
  }

}

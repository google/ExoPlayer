/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.util.Log;
/**
 * Wraps a byte array, providing methods that allows it to be used as a circular queue
 */
public final class CircularByteQueue {
  // circular array
  private byte[] data;
  // maximum capacity of the queue
  private int capacity;
  // current size of the queue
  private int availableCount;
  // offset to write into
  private int writeOffset;
  // offset to read into
  private int readOffset;

  /**
   * Creates a new instance.
   */
  public CircularByteQueue(int capacity) {
    // allocate one extra element to handle the queue wrap-arounds
    this.data = new byte[capacity + 1];
    this.capacity = capacity + 1;
  }

  /**
   * clears the queue and resets read/write offsets
   *
   */
  public void reset() {
    readOffset = 0;
    availableCount = 0;
    writeOffset = 0;
  }

  public boolean canWrite() {
    if (availableCount == capacity - 1) {
      return false;
    }
    return true;
  }

  public boolean canRead(int count) {
    if (availableCount < count) {
      return false;
    }
    return true;
  }

  public boolean write(byte value) {
    // check for space and early return
    if (!canWrite()) {
      return false;
    }
    data[writeOffset] = value;
    availableCount++;
    writeOffset = (writeOffset + 1) % capacity;
    return true;
  }

  public int read() {
    // check for space and early return
    if (!canRead(1)) {
      return 0;
    }
    // byte to unsigned int
    int value = data[readOffset] & 0xff;
    availableCount--;
    readOffset = (readOffset + 1) % capacity;
    return value;
  }

  public int size() {
    return availableCount;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("capacity = " + capacity + " ,availableCount = " + availableCount + ", readOffset = " +readOffset +
        ", writeOffset = " + writeOffset);

    for (int i= 0; i < data.length; i++) {
      builder.append(" data["+ i +"] = " + data[i]);
    }
    return builder.toString();
  }

}
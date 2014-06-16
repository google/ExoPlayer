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
package com.google.android.exoplayer.parser.mp4;

import java.nio.ByteBuffer;

/**
 * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
 * parsed with the assumption that their constituent bytes are in big endian order.
 */
/* package */ final class ParsableByteArray {

  private final byte[] data;
  private int position;

  public ParsableByteArray(int length) {
    this.data = new byte[length];
  }

  public byte[] getData() {
    return data;
  }

  public int length() {
    return data.length;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public void skip(int bytes) {
    position += bytes;
  }

  public void rewind(int bytes) {
    position -= bytes;
  }

  public void readBytes(byte[] buffer, int offset, int length) {
    System.arraycopy(data, position, buffer, offset, length);
    position += length;
  }

  public void readBytes(ByteBuffer buffer, int length) {
    buffer.put(data, position, length);
    position += length;
  }

  public int readUnsignedByte() {
    int result = shiftIntoInt(data, position, 1);
    position += 1;
    return result;
  }

  public int readUnsignedShort() {
    int result = shiftIntoInt(data, position, 2);
    position += 2;
    return result;
  }

  public long readUnsignedInt() {
    long result = shiftIntoLong(data, position, 4);
    position += 4;
    return result;
  }

  public int readInt() {
    int result = shiftIntoInt(data, position, 4);
    position += 4;
    return result;
  }

  public long readLong() {
    long result = shiftIntoLong(data, position, 8);
    position += 8;
    return result;
  }

  /**
   * @return The integer portion of a fixed point 16.16.
   */
  public int readUnsignedFixedPoint1616() {
    int result = shiftIntoInt(data, position, 2);
    position += 4;
    return result;
  }

  /**
   * Reads an unsigned integer into an integer. This method is suitable for use when it can be
   * assumed that the top bit will always be set to zero.
   *
   * @throws IllegalArgumentException If the top bit of the input data is set.
   */
  public int readUnsignedIntToInt() {
    int result = shiftIntoInt(data, position, 4);
    position += 4;
    if (result < 0) {
      throw new IllegalArgumentException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Reads an unsigned long into a long. This method is suitable for use when it can be
   * assumed that the top bit will always be set to zero.
   *
   * @throws IllegalArgumentException If the top bit of the input data is set.
   */
  public long readUnsignedLongToLong() {
    long result = shiftIntoLong(data, position, 8);
    position += 8;
    if (result < 0) {
      throw new IllegalArgumentException("Top bit not zero: " + result);
    }
    return result;
  }

  private static int shiftIntoInt(byte[] bytes, int offset, int length) {
    int result = 0xFF & bytes[offset];
    for (int i = offset + 1; i < offset + length; i++) {
      result <<= 8;
      result |= 0xFF & bytes[i];
    }
    return result;
  }

  private static long shiftIntoLong(byte[] bytes, int offset, int length) {
    long result = 0xFF & bytes[offset];
    for (int i = offset + 1; i < offset + length; i++) {
      result <<= 8;
      result |= 0xFF & bytes[i];
    }
    return result;
  }

}

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
package com.google.android.exoplayer.util;

import java.nio.ByteBuffer;

/**
 * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
 * parsed with the assumption that their constituent bytes are in big endian order.
 */
public final class ParsableByteArray {

  public final byte[] data;

  private int position;

  /** Creates a new parsable array with {@code length} bytes. */
  public ParsableByteArray(int length) {
    this.data = new byte[length];
  }

  /** Returns the number of bytes in the array. */
  public int length() {
    return data.length;
  }

  /** Returns the current offset in the array, in bytes. */
  public int getPosition() {
    return position;
  }

  /**
   * Sets the reading offset in the array.
   *
   * @param position Byte offset in the array from which to read.
   * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
   *     array.
   */
  public void setPosition(int position) {
    // It is fine for position to be at the end of the array.
    Assertions.checkArgument(position >= 0 && position <= data.length);
    this.position = position;
  }

  /**
   * Moves the reading offset by {@code bytes}.
   *
   * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
   *     array.
   */
  public void skip(int bytes) {
    setPosition(position + bytes);
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer} at {@code offset}.
   *
   * @see System#arraycopy
   */
  public void readBytes(byte[] buffer, int offset, int length) {
    System.arraycopy(data, position, buffer, offset, length);
    position += length;
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer}.
   *
   * @see ByteBuffer#put(byte[], int, int)
   */
  public void readBytes(ByteBuffer buffer, int length) {
    buffer.put(data, position, length);
    position += length;
  }

  /** Reads the next byte as an unsigned value. */
  public int readUnsignedByte() {
    int result = shiftIntoInt(data, position, 1);
    position += 1;
    return result;
  }

  /** Reads the next two bytes as an unsigned value. */
  public int readUnsignedShort() {
    int result = shiftIntoInt(data, position, 2);
    position += 2;
    return result;
  }

  /** Reads the next four bytes as an unsigned value. */
  public long readUnsignedInt() {
    long result = shiftIntoLong(data, position, 4);
    position += 4;
    return result;
  }

  /** Reads the next four bytes as a signed value. */
  public int readInt() {
    int result = shiftIntoInt(data, position, 4);
    position += 4;
    return result;
  }

  /** Reads the next eight bytes as a signed value. */
  public long readLong() {
    long result = shiftIntoLong(data, position, 8);
    position += 8;
    return result;
  }

  /** Reads the next four bytes, returning the integer portion of the fixed point 16.16 integer. */
  public int readUnsignedFixedPoint1616() {
    int result = shiftIntoInt(data, position, 2);
    position += 4;
    return result;
  }

  /**
   * Reads the next four bytes as an unsigned integer into an integer, if the top bit is a zero.
   *
   * @throws IllegalArgumentException Thrown if the top bit of the input data is set.
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
   * Reads the next eight bytes as an unsigned long into a long, if the top bit is a zero.
   *
   * @throws IllegalArgumentException Thrown if the top bit of the input data is set.
   */
  public long readUnsignedLongToLong() {
    long result = shiftIntoLong(data, position, 8);
    position += 8;
    if (result < 0) {
      throw new IllegalArgumentException("Top bit not zero: " + result);
    }
    return result;
  }

  /** Reads {@code length} bytes into an int at {@code offset} in {@code bytes}. */
  private static int shiftIntoInt(byte[] bytes, int offset, int length) {
    int result = 0xFF & bytes[offset];
    for (int i = offset + 1; i < offset + length; i++) {
      result <<= 8;
      result |= 0xFF & bytes[i];
    }
    return result;
  }

  /** Reads {@code length} bytes into a long at {@code offset} in {@code bytes}. */
  private static long shiftIntoLong(byte[] bytes, int offset, int length) {
    long result = 0xFF & bytes[offset];
    for (int i = offset + 1; i < offset + length; i++) {
      result <<= 8;
      result |= 0xFF & bytes[i];
    }
    return result;
  }

}

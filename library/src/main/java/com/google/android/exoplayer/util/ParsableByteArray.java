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

  public byte[] data;

  private int position;
  private int limit;

  /** Creates a new instance that initially has no backing data. */
  public ParsableByteArray() {}

  /** Creates a new instance with {@code length} bytes. */
  public ParsableByteArray(int length) {
    this.data = new byte[length];
    limit = data.length;
  }

  /** Creates a new instance wrapping {@code data}. */
  public ParsableByteArray(byte[] data) {
    this.data = data;
    limit = data.length;
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   * @param limit The limit.
   */
  public ParsableByteArray(byte[] data, int limit) {
    this.data = data;
    this.limit = limit;
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   * @param limit The limit.
   */
  public void reset(byte[] data, int limit) {
    this.data = data;
    this.limit = limit;
    position = 0;
  }

  /**
   * Sets the position and limit to zero.
   */
  public void reset() {
    position = 0;
    limit = 0;
  }

  /** Returns the number of bytes yet to be read. */
  public int bytesLeft() {
    return limit - position;
  }

  /** Returns the limit. */
  public int limit() {
    return limit;
  }

  /**
   * Sets the limit.
   *
   * @param limit The limit to set.
   */
  public void setLimit(int limit) {
    Assertions.checkArgument(limit >= 0 && limit <= data.length);
    this.limit = limit;
  }

  /** Returns the current offset in the array, in bytes. */
  public int getPosition() {
    return position;
  }

  /** Returns the capacity of the array, which may be larger than the limit. */
  public int capacity() {
    return data == null ? 0 : data.length;
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
    Assertions.checkArgument(position >= 0 && position <= limit);
    this.position = position;
  }

  /**
   * Moves the reading offset by {@code bytes}.
   *
   * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
   *     array.
   */
  public void skipBytes(int bytes) {
    setPosition(position + bytes);
  }

  /**
   * Reads the next {@code length} bytes into {@code bitArray}, and resets the position of
   * {@code bitArray} to zero.
   *
   * @param bitArray The {@link ParsableBitArray} into which the bytes should be read.
   * @param length The number of bytes to write.
   */
  public void readBytes(ParsableBitArray bitArray, int length) {
    readBytes(bitArray.data, 0, length);
    bitArray.setPosition(0);
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
    return (data[position++] & 0xFF);
  }

  /** Reads the next two bytes as an unsigned value. */
  public int readUnsignedShort() {
    return (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /** Reads the next three bytes as an unsigned value. */
  public int readUnsignedInt24() {
    return (data[position++] & 0xFF) << 16
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /** Reads the next four bytes as an unsigned value. */
  public long readUnsignedInt() {
    return (data[position++] & 0xFFL) << 24
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL);
  }

  /** Reads the next four bytes as a signed value. */
  public int readInt() {
    return (data[position++] & 0xFF) << 24
        | (data[position++] & 0xFF) << 16
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /** Reads the next eight bytes as a signed value. */
  public long readLong() {
    return (data[position++] & 0xFFL) << 56
        | (data[position++] & 0xFFL) << 48
        | (data[position++] & 0xFFL) << 40
        | (data[position++] & 0xFFL) << 32
        | (data[position++] & 0xFFL) << 24
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL);
  }

  /** Reads the next four bytes, returning the integer portion of the fixed point 16.16 integer. */
  public int readUnsignedFixedPoint1616() {
    int result = (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
    position += 2; // Skip the non-integer portion.
    return result;
  }

  /**
   * Reads a Synchsafe integer.
   * <p>
   * Synchsafe integers keep the highest bit of every byte zeroed. A 32 bit synchsafe integer can
   * store 28 bits of information.
   *
   * @return The parsed value.
   */
  public int readSynchSafeInt() {
    int b1 = readUnsignedByte();
    int b2 = readUnsignedByte();
    int b3 = readUnsignedByte();
    int b4 = readUnsignedByte();
    return (b1 << 21) | (b2 << 14) | (b3 << 7) | b4;
  }

  /**
   * Reads the next four bytes as an unsigned integer into an integer, if the top bit is a zero.
   *
   * @throws IllegalStateException Thrown if the top bit of the input data is set.
   */
  public int readUnsignedIntToInt() {
    int result = readInt();
    if (result < 0) {
      throw new IllegalStateException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Reads the next eight bytes as an unsigned long into a long, if the top bit is a zero.
   *
   * @throws IllegalStateException Thrown if the top bit of the input data is set.
   */
  public long readUnsignedLongToLong() {
    long result = readLong();
    if (result < 0) {
      throw new IllegalStateException("Top bit not zero: " + result);
    }
    return result;
  }

}

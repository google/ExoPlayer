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

/**
 * Wraps a byte array, providing methods that allow it to be read as a bitstream.
 */
public final class ParsableBitArray {

  private byte[] data;

  // The offset within the data, stored as the current byte offset, and the bit offset within that
  // byte (from 0 to 7).
  private int byteOffset;
  private int bitOffset;

  /** Creates a new instance that initially has no backing data. */
  public ParsableBitArray() {}

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   */
  public ParsableBitArray(byte[] data) {
    this.data = data;
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   */
  public void reset(byte[] data) {
    this.data = data;
    byteOffset = 0;
    bitOffset = 0;
  }

  /**
   * Gets the backing byte array.
   *
   * @return The backing byte array.
   */
  public byte[] getData() {
    return data;
  }

  /**
   * Gets the current bit offset.
   *
   * @return The current bit offset.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
  }

  /**
   * Sets the current bit offset.
   *
   * @param position The position to set.
   */
  public void setPosition(int position) {
    byteOffset = position / 8;
    bitOffset = position - (byteOffset * 8);
  }

  /**
   * Skips bits and moves current reading position forward.
   *
   * @param n The number of bits to skip.
   */
  public void skipBits(int n) {
    byteOffset += (n / 8);
    bitOffset += (n % 8);
    if (bitOffset > 7) {
      byteOffset++;
      bitOffset -= 8;
    }
  }

  /**
   * Reads a single bit.
   *
   * @return True if the bit is set. False otherwise.
   */
  public boolean readBit() {
    return readBits(1) == 1;
  }

  /**
   * Reads up to 32 bits.
   *
   * @param n The number of bits to read.
   * @return An integer whose bottom n bits hold the read data.
   */
  public int readBits(int n) {
    return (int) readBitsLong(n);
  }

  /**
   * Reads up to 64 bits.
   *
   * @param n The number of bits to read.
   * @return A long whose bottom n bits hold the read data.
   */
  public long readBitsLong(int n) {
    if (n == 0) {
      return 0;
    }

    long retval = 0;

    // While n >= 8, read whole bytes.
    while (n >= 8) {
      n -= 8;
      retval |= (readUnsignedByte() << n);
    }

    if (n > 0) {
      int nextBit = bitOffset + n;
      byte writeMask = (byte) (0xFF >> (8 - n));

      if (nextBit > 8) {
        // Combine bits from current byte and next byte.
        retval |= (((getUnsignedByte(byteOffset) << (nextBit - 8)
            | (getUnsignedByte(byteOffset + 1) >> (16 - nextBit))) & writeMask));
        byteOffset++;
      } else {
        // Bits to be read only within current byte.
        retval |= ((getUnsignedByte(byteOffset) >> (8 - nextBit)) & writeMask);
        if (nextBit == 8) {
          byteOffset++;
        }
      }

      bitOffset = nextBit % 8;
    }

    return retval;
  }

  /**
   * Reads an unsigned Exp-Golomb-coded format integer.
   *
   * @return The value of the parsed Exp-Golomb-coded integer.
   */
  public int readUnsignedExpGolombCodedInt() {
    return readExpGolombCodeNum();
  }

  /**
   * Reads an signed Exp-Golomb-coded format integer.
   *
   * @return The value of the parsed Exp-Golomb-coded integer.
   */
  public int readSignedExpGolombCodedInt() {
    int codeNum = readExpGolombCodeNum();
    return ((codeNum % 2) == 0 ? -1 : 1) * ((codeNum + 1) / 2);
  }

  private int readUnsignedByte() {
    int value;
    if (bitOffset != 0) {
      value = ((data[byteOffset] & 0xFF) << bitOffset)
          | ((data[byteOffset + 1] & 0xFF) >>> (8 - bitOffset));
    } else {
      value = data[byteOffset];
    }
    byteOffset++;
    return value & 0xFF;
  }

  private int getUnsignedByte(int offset) {
    return data[offset] & 0xFF;
  }

  private int readExpGolombCodeNum() {
    int leadingZeros = 0;
    while (!readBit()) {
      leadingZeros++;
    }
    return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
  }

}

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
package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

/**
 * Wraps a byte array, providing methods that allow it to be read as a bitstream.
 */
public final class BitsArray {

  private byte[] data;

  // The length of the valid data.
  private int limit;

  // The offset within the data, stored as the current byte offset, and the bit offset within that
  // byte (from 0 to 7).
  private int byteOffset;
  private int bitOffset;

  public BitsArray() {
  }

  public BitsArray(byte[] data, int limit) {
    this.data = data;
    this.limit = limit;
  }

  /**
   * Resets the state.
   */
  public void reset() {
    byteOffset = 0;
    bitOffset = 0;
    limit = 0;
  }

  /**
   * Gets the current byte offset.
   *
   * @return The current byte offset.
   */
  public int getByteOffset() {
    return byteOffset;
  }

  /**
   * Sets the current byte offset.
   *
   * @param byteOffset The byte offset to set.
   */
  public void setByteOffset(int byteOffset) {
    this.byteOffset = byteOffset;
  }

  /**
   * Appends data from a {@link NonBlockingInputStream}.
   *
   * @param inputStream The {@link NonBlockingInputStream} whose data should be appended.
   * @param length The maximum number of bytes to read and append.
   * @return The number of bytes that were read and appended. May be 0 if no data was available
   *     from the stream. -1 is returned if the end of the stream has been reached.
   */
  public int append(NonBlockingInputStream inputStream, int length) {
    expand(length);
    int bytesRead = inputStream.read(data, limit, length);
    if (bytesRead == -1) {
      return -1;
    }
    limit += bytesRead;
    return bytesRead;
  }

  /**
   * Appends data from another {@link BitsArray}.
   *
   * @param bitsArray The {@link BitsArray} whose data should be appended.
   * @param length The number of bytes to read and append.
   */
  public void append(BitsArray bitsArray, int length) {
    expand(length);
    bitsArray.readBytes(data, limit, length);
    limit += length;
  }

  private void expand(int length) {
    if (data == null) {
      data = new byte[length];
      return;
    }
    if (data.length - limit < length) {
      byte[] newBuffer = new byte[limit + length];
      System.arraycopy(data, 0, newBuffer, 0, limit);
      data = newBuffer;
    }
  }

  /**
   * Clears data that has already been read, moving the remaining data to the start of the buffer.
   */
  public void clearReadData() {
    System.arraycopy(data, byteOffset, data, 0, limit - byteOffset);
    limit -= byteOffset;
    byteOffset = 0;
  }

  /**
   * Reads a single unsigned byte.
   *
   * @return The value of the parsed byte.
   */
  public int readUnsignedByte() {
    byte b;
    if (bitOffset != 0) {
      b = (byte) ((data[byteOffset] << bitOffset)
          | (data[byteOffset + 1] >> (8 - bitOffset)));
    } else {
      b = data[byteOffset];
    }
    byteOffset++;
    // Converting a signed byte into unsigned.
    return b & 0xFF;
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

  private int getUnsignedByte(int offset) {
    return data[offset] & 0xFF;
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
   * Skips bytes and moves current reading position forward.
   *
   * @param n The number of bytes to skip.
   */
  public void skipBytes(int n) {
    byteOffset += n;
  }

  /**
   * Reads multiple bytes and copies them into provided byte array.
   * <p>
   * The read position must be at a whole byte boundary for this method to be called.
   *
   * @param out The byte array to copy read data.
   * @param offset The offset in the out byte array.
   * @param length The length of the data to read
   * @throws IllegalStateException If the method is called with the read position not at a whole
   *     byte boundary.
   */
  public void readBytes(byte[] out, int offset, int length) {
    Assertions.checkState(bitOffset == 0);
    System.arraycopy(data, byteOffset, out, offset, length);
    byteOffset += length;
  }

  /**
   * @return The number of whole bytes that are available to read.
   */
  public int bytesLeft() {
    return limit - byteOffset;
  }

  /**
   * @return Whether or not there is any data available.
   */
  public boolean isEmpty() {
    return limit == 0;
  }

  /**
   * Reads a Synchsafe integer.
   * Synchsafe integers are integers that keep the highest bit of every byte zeroed.
   * A 32 bit synchsafe integer can store 28 bits of information.
   *
   * @return The value of the parsed Synchsafe integer.
   */
  public int readSynchSafeInt() {
    int b1 = readUnsignedByte();
    int b2 = readUnsignedByte();
    int b3 = readUnsignedByte();
    int b4 = readUnsignedByte();

    return (b1 << 21) | (b2 << 14) | (b3 << 7) | b4;
  }

  // TODO: Find a better place for this method.
  /**
   * Finds the next Adts sync word.
   *
   * @return The offset from the current position to the start of the next Adts sync word. If an
   *     Adts sync word is not found, then the offset to the end of the data is returned.
   */
  public int findNextAdtsSyncWord() {
    for (int i = byteOffset; i < limit - 1; i++) {
      int syncBits = (getUnsignedByte(i) << 8) | getUnsignedByte(i + 1);
      if ((syncBits & 0xFFF0) == 0xFFF0 && syncBits != 0xFFFF) {
        return i - byteOffset;
      }
    }
    return limit - byteOffset;
  }

  //TODO: Find a better place for this method.
  /**
   * Finds the next NAL unit.
   *
   * @param nalUnitType The type of the NAL unit to search for.
   * @param offset The additional offset in the data to start the search from.
   * @return The offset from the current position to the start of the NAL unit. If a NAL unit is
   *     not found, then the offset to the end of the data is returned.
   */
  public int findNextNalUnit(int nalUnitType, int offset) {
    for (int i = byteOffset + offset; i < limit - 3; i++) {
      // Check for NAL unit start code prefix == 0x000001.
      if ((data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1)
          && (nalUnitType == (data[i + 3] & 0x1F))) {
        return i - byteOffset;
      }
    }
    return limit - byteOffset;
  }

}

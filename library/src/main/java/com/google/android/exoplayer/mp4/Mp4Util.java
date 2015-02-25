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
package com.google.android.exoplayer.mp4;

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.nio.ByteBuffer;

/**
 * Utility methods and constants for parsing fragmented and unfragmented MP4 files.
 */
public final class Mp4Util {

  /** Size of an atom header, in bytes. */
  public static final int ATOM_HEADER_SIZE = 8;

  /** Size of a long atom header, in bytes. */
  public static final int LONG_ATOM_HEADER_SIZE = 16;

  /** Size of a full atom header, in bytes. */
  public static final int FULL_ATOM_HEADER_SIZE = 12;

  /** Value for the first 32 bits of atomSize when the atom size is actually a long value. */
  public static final int LONG_ATOM_SIZE = 1;

  /** Sample index when no sample is available. */
  public static final int NO_SAMPLE = -1;

  /** Track index when no track is selected. */
  public static final int NO_TRACK = -1;

  /** Four initial bytes that must prefix H.264/AVC NAL units for decoding. */
  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  /** Parses the version number out of the additional integer component of a full atom. */
  public static int parseFullAtomVersion(int fullAtomInt) {
    return 0x000000FF & (fullAtomInt >> 24);
  }

  /** Parses the atom flags out of the additional integer component of a full atom. */
  public static int parseFullAtomFlags(int fullAtomInt) {
    return 0x00FFFFFF & fullAtomInt;
  }

  /**
   * Reads an unsigned integer into an integer. This method is suitable for use when it can be
   * assumed that the top bit will always be set to zero.
   *
   * @throws IllegalArgumentException If the top bit of the input data is set.
   */
  public static int readUnsignedIntToInt(ByteBuffer data) {
    int result = 0xFF & data.get();
    for (int i = 1; i < 4; i++) {
      result <<= 8;
      result |= 0xFF & data.get();
    }
    if (result < 0) {
      throw new IllegalArgumentException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Replaces length prefixes of NAL units in {@code buffer} with start code prefixes, within the
   * {@code size} bytes preceding the buffer's position.
   */
  public static void replaceLengthPrefixesWithAvcStartCodes(ByteBuffer buffer, int size) {
    int sampleOffset = buffer.position() - size;
    int position = sampleOffset;
    while (position < sampleOffset + size) {
      buffer.position(position);
      int length = readUnsignedIntToInt(buffer);
      buffer.position(position);
      buffer.put(NAL_START_CODE);
      position += length + 4;
    }
    buffer.position(sampleOffset + size);
  }

  /** Constructs and returns a NAL unit with a start code followed by the data in {@code atom}. */
  public static byte[] parseChildNalUnit(ParsableByteArray atom) {
    int length = atom.readUnsignedShort();
    int offset = atom.getPosition();
    atom.skip(length);
    return CodecSpecificDataUtil.buildNalUnit(atom.data, offset, length);
  }

  /**
   * Finds the first NAL unit in {@code data}.
   * <p>
   * For a NAL unit to be found, its first four bytes must be contained within the part of the
   * array being searched.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param type The type of the NAL unit to search for, or -1 for any NAL unit.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(byte[] data, int startOffset, int endOffset, int type) {
    return findNalUnit(data, startOffset, endOffset, type, null);
  }

  /**
   * Like {@link #findNalUnit(byte[], int, int, int)}, but supports finding of NAL units across
   * array boundaries.
   * <p>
   * To use this method, pass the same {@code prefixFlags} parameter to successive calls where the
   * data passed represents a contiguous stream. The state maintained in this parameter allows the
   * detection of NAL units where the NAL unit prefix spans array boundaries.
   * <p>
   * Note that when using {@code prefixFlags} the return value may be 3, 2 or 1 less than
   * {@code startOffset}, to indicate a NAL unit starting 3, 2 or 1 bytes before the first byte in
   * the current array.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param type The type of the NAL unit to search for, or -1 for any NAL unit.
   * @param prefixFlags A boolean array whose first three elements are used to store the state
   *     required to detect NAL units where the NAL unit prefix spans array boundaries. The array
   *     must be at least 3 elements long.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(byte[] data, int startOffset, int endOffset, int type,
      boolean[] prefixFlags) {
    int length = endOffset - startOffset;

    Assertions.checkState(length >= 0);
    if (length == 0) {
      return endOffset;
    }

    if (prefixFlags != null) {
      if (prefixFlags[0] && matchesType(data, startOffset, type)) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 3;
      } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1
          && matchesType(data, startOffset + 1, type)) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 2;
      } else if (length > 2 && prefixFlags[2] && data[startOffset] == 0
          && data[startOffset + 1] == 1 && matchesType(data, startOffset + 2, type)) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 1;
      }
    }

    int limit = endOffset - 1;
    // We're looking for the NAL unit start code prefix 0x000001, followed by a byte that matches
    // the specified type. The value of i tracks the index of the third byte in the four bytes
    // being examined.
    for (int i = startOffset + 2; i < limit; i += 3) {
      if ((data[i] & 0xFE) != 0) {
        // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
        // loop advance the index by three.
      } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1
          && matchesType(data, i + 1, type)) {
        if (prefixFlags != null) {
          clearPrefixFlags(prefixFlags);
        }
        return i - 2;
      } else {
        // There isn't a NAL prefix here, but there might be at the next position. We should
        // only skip forward by one. The loop will skip forward by three, so subtract two here.
        i -= 2;
      }
    }

    if (prefixFlags != null) {
      // True if the last three bytes in the data seen so far are {0,0,1}.
      prefixFlags[0] = length > 2
          ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
          : length == 2 ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
          : (prefixFlags[1] && data[endOffset - 1] == 1);
      // True if the last three bytes in the data seen so far are {0,0}.
      prefixFlags[1] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
          : prefixFlags[2] && data[endOffset - 1] == 0;
      // True if the last three bytes in the data seen so far are {0}.
      prefixFlags[2] = data[endOffset - 1] == 0;
    }

    return endOffset;
  }

  /**
   * Like {@link #findNalUnit(byte[], int, int, int)} with {@code type == -1}.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(byte[] data, int startOffset, int endOffset) {
    return findNalUnit(data, startOffset, endOffset, null);
  }

  /**
   * Like {@link #findNalUnit(byte[], int, int, int, boolean[])} with {@code type == -1}.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param prefixFlags A boolean array of length at least 3.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(byte[] data, int startOffset, int endOffset,
      boolean[] prefixFlags) {
    return findNalUnit(data, startOffset, endOffset, -1, prefixFlags);
  }

  /**
   * Gets the type of the NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getNalUnitType(byte[] data, int offset) {
    return data[offset + 3] & 0x1F;
  }

  /**
   * Clears prefix flags, as used by {@link #findNalUnit(byte[], int, int, int, boolean[])}.
   *
   * @param prefixFlags The flags to clear.
   */
  private static void clearPrefixFlags(boolean[] prefixFlags) {
    prefixFlags[0] = false;
    prefixFlags[1] = false;
    prefixFlags[2] = false;
  }

  /**
   * Returns true if the type at {@code offset} is equal to {@code type}, or if {@code type == -1}.
   */
  private static boolean matchesType(byte[] data, int offset, int type) {
    return type == -1 || (data[offset] & 0x1F) == type;
  }

}

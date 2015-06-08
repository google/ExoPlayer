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
import java.util.Arrays;

/**
 * Utility methods for handling H.264/AVC and H.265/HEVC NAL units.
 */
public final class NalUnitUtil {

  /** Four initial bytes that must prefix NAL units for decoding. */
  public static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  /** Value for aspect_ratio_idc indicating an extended aspect ratio, in H.264 and H.265 SPSs. */
  public static final int EXTENDED_SAR = 0xFF;
  /** Aspect ratios indexed by aspect_ratio_idc, in H.264 and H.265 SPSs. */
  public static final float[] ASPECT_RATIO_IDC_VALUES = new float[] {
    1f /* Unspecified. Assume square */,
    1f,
    12f / 11f,
    10f / 11f,
    16f / 11f,
    40f / 33f,
    24f / 11f,
    20f / 11f,
    32f / 11f,
    80f / 33f,
    18f / 11f,
    15f / 11f,
    64f / 33f,
    160f / 99f,
    4f / 3f,
    3f / 2f,
    2f
  };

  private static final Object scratchEscapePositionsLock = new Object();

  /**
   * Temporary store for positions of escape codes in {@link #unescapeStream(byte[], int)}. Guarded
   * by {@link #scratchEscapePositionsLock}.
   */
  private static int[] scratchEscapePositions = new int[10];

  /**
   * Unescapes {@code data} up to the specified limit, replacing occurrences of [0, 0, 3] with
   * [0, 0]. The unescaped data is returned in-place, with the return value indicating its length.
   * <p>
   * Executions of this method are mutually exclusive, so it should not be called with very large
   * buffers.
   *
   * @param data The data to unescape.
   * @param limit The limit (exclusive) of the data to unescape.
   * @return The length of the unescaped data.
   */
  public static int unescapeStream(byte[] data, int limit) {
    synchronized (scratchEscapePositionsLock) {
      int position = 0;
      int scratchEscapeCount = 0;
      while (position < limit) {
        position = findNextUnescapeIndex(data, position, limit);
        if (position < limit) {
          if (scratchEscapePositions.length <= scratchEscapeCount) {
            // Grow scratchEscapePositions to hold a larger number of positions.
            scratchEscapePositions = Arrays.copyOf(scratchEscapePositions,
                scratchEscapePositions.length * 2);
          }
          scratchEscapePositions[scratchEscapeCount++] = position;
          position += 3;
        }
      }

      int unescapedLength = limit - scratchEscapeCount;
      int escapedPosition = 0; // The position being read from.
      int unescapedPosition = 0; // The position being written to.
      for (int i = 0; i < scratchEscapeCount; i++) {
        int nextEscapePosition = scratchEscapePositions[i];
        int copyLength = nextEscapePosition - escapedPosition;
        System.arraycopy(data, escapedPosition, data, unescapedPosition, copyLength);
        unescapedPosition += copyLength;
        data[unescapedPosition++] = 0;
        data[unescapedPosition++] = 0;
        escapedPosition += copyLength + 3;
      }

      int remainingLength = unescapedLength - unescapedPosition;
      System.arraycopy(data, escapedPosition, data, unescapedPosition, remainingLength);
      return unescapedLength;
    }
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

  /**
   * Constructs and returns a NAL unit with a start code followed by the data in {@code atom}.
   */
  public static byte[] parseChildNalUnit(ParsableByteArray atom) {
    int length = atom.readUnsignedShort();
    int offset = atom.getPosition();
    atom.skipBytes(length);
    return CodecSpecificDataUtil.buildNalUnit(atom.data, offset, length);
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
   * Gets the type of the H.265 NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getH265NalUnitType(byte[] data, int offset) {
    return (data[offset + 3] & 0x7E) >> 1;
  }

  /**
   * Finds the first NAL unit in {@code data}.
   * <p>
   * If {@code prefixFlags} is null then the first three bytes of a NAL unit must be entirely
   * contained within the part of the array being searched in order for it to be found.
   * <p>
   * When {@code prefixFlags} is non-null, this method supports finding NAL units whose first four
   * bytes span {@code data} arrays passed to successive calls. To use this feature, pass the same
   * {@code prefixFlags} parameter to successive calls. State maintained in this parameter enables
   * the detection of such NAL units. Note that when using this feature, the return value may be 3,
   * 2 or 1 less than {@code startOffset}, to indicate a NAL unit starting 3, 2 or 1 bytes before
   * the first byte in the current array.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param prefixFlags A boolean array whose first three elements are used to store the state
   *     required to detect NAL units where the NAL unit prefix spans array boundaries. The array
   *     must be at least 3 elements long.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(byte[] data, int startOffset, int endOffset,
      boolean[] prefixFlags) {
    int length = endOffset - startOffset;

    Assertions.checkState(length >= 0);
    if (length == 0) {
      return endOffset;
    }

    if (prefixFlags != null) {
      if (prefixFlags[0]) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 3;
      } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 2;
      } else if (length > 2 && prefixFlags[2] && data[startOffset] == 0
          && data[startOffset + 1] == 1) {
        clearPrefixFlags(prefixFlags);
        return startOffset - 1;
      }
    }

    int limit = endOffset - 1;
    // We're looking for the NAL unit start code prefix 0x000001. The value of i tracks the index of
    // the third byte.
    for (int i = startOffset + 2; i < limit; i += 3) {
      if ((data[i] & 0xFE) != 0) {
        // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
        // loop advance the index by three.
      } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
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
      // True if the last two bytes in the data seen so far are {0,0}.
      prefixFlags[1] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
          : prefixFlags[2] && data[endOffset - 1] == 0;
      // True if the last byte in the data seen so far is {0}.
      prefixFlags[2] = data[endOffset - 1] == 0;
    }

    return endOffset;
  }

  /**
   * Clears prefix flags, as used by {@link #findNalUnit(byte[], int, int, boolean[])}.
   *
   * @param prefixFlags The flags to clear.
   */
  public static void clearPrefixFlags(boolean[] prefixFlags) {
    prefixFlags[0] = false;
    prefixFlags[1] = false;
    prefixFlags[2] = false;
  }

  private static int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
    for (int i = offset; i < limit - 2; i++) {
      if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
        return i;
      }
    }
    return limit;
  }

  /**
   * Reads an unsigned integer into an integer. This method is suitable for use when it can be
   * assumed that the top bit will always be set to zero.
   *
   * @throws IllegalArgumentException If the top bit of the input data is set.
   */
  private static int readUnsignedIntToInt(ByteBuffer data) {
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

  private NalUnitUtil() {
    // Prevent instantiation.
  }

}

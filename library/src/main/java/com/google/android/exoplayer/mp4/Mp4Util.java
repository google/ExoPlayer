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

}

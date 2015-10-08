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
package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;

/**
 * Provides methods that peek data from an {@link ExtractorInput} and return whether the input
 * appears to be in MP4 format.
 */
/* package */ final class Sniffer {

  private static final int[] COMPATIBLE_BRANDS = new int[] {
      Util.getIntegerCodeForString("isom"),
      Util.getIntegerCodeForString("iso2"),
      Util.getIntegerCodeForString("avc1"),
      Util.getIntegerCodeForString("hvc1"),
      Util.getIntegerCodeForString("hev1"),
      Util.getIntegerCodeForString("mp41"),
      Util.getIntegerCodeForString("mp42"),
      Util.getIntegerCodeForString("3g2a"),
      Util.getIntegerCodeForString("3g2b"),
      Util.getIntegerCodeForString("3gr6"),
      Util.getIntegerCodeForString("3gs6"),
      Util.getIntegerCodeForString("3ge6"),
      Util.getIntegerCodeForString("3gg6"),
      Util.getIntegerCodeForString("M4V "),
      Util.getIntegerCodeForString("M4A "),
      Util.getIntegerCodeForString("f4v "),
      Util.getIntegerCodeForString("kddi"),
      Util.getIntegerCodeForString("M4VP"),
      Util.getIntegerCodeForString("qt  "), // Apple QuickTime
      Util.getIntegerCodeForString("MSNV"), // Sony PSP
  };

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being a fragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return True if the input appears to be in the fragmented MP4 format. False otherwise.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffFragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, 4 * 1024, true);
  }

  /**
   * Returns whether data peeked from the current position in {@code input} is consistent with the
   * input being an unfragmented MP4 file.
   *
   * @param input The extractor input from which to peek data. The peek position will be modified.
   * @return True if the input appears to be in the unfragmented MP4 format. False otherwise.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static boolean sniffUnfragmented(ExtractorInput input)
      throws IOException, InterruptedException {
    return sniffInternal(input, 128, false);
  }

  private static boolean sniffInternal(ExtractorInput input, int searchLength, boolean fragmented)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    int bytesToSearch = (int) (inputLength == C.LENGTH_UNBOUNDED || inputLength > searchLength
        ? searchLength : inputLength);

    ParsableByteArray buffer = new ParsableByteArray(64);
    int bytesSearched = 0;
    boolean foundGoodFileType = false;
    boolean foundFragment = false;
    while (bytesSearched < bytesToSearch) {
      // Read an atom header.
      int headerSize = Atom.HEADER_SIZE;
      input.peekFully(buffer.data, 0, headerSize);
      buffer.setPosition(0);
      long atomSize = buffer.readUnsignedInt();
      int atomType = buffer.readInt();
      if (atomSize == Atom.LONG_SIZE_PREFIX) {
        input.peekFully(buffer.data, headerSize, Atom.LONG_HEADER_SIZE - headerSize);
        headerSize = Atom.LONG_HEADER_SIZE;
        atomSize = buffer.readLong();
      }
      // Check the atom size is large enough to include its header.
      if (atomSize < headerSize) {
        return false;
      }
      int atomDataSize = (int) atomSize - headerSize;
      if (atomType == Atom.TYPE_ftyp) {
        if (atomDataSize < 8) {
          return false;
        }
        int compatibleBrandsCount = (atomDataSize - 8) / 4;
        input.peekFully(buffer.data, 0, 4 * (compatibleBrandsCount + 2));
        for (int i = 0; i < compatibleBrandsCount + 2; i++) {
          if (i == 1) {
            // This index refers to the minorVersion, not a brand, so skip it.
            continue;
          }
          if (isCompatibleBrand(buffer.readInt())) {
            foundGoodFileType = true;
            break;
          }
        }
        // There is only one ftyp box, so reject the file if the file type in this box was invalid.
        if (!foundGoodFileType) {
          return false;
        }
      } else if (atomType == Atom.TYPE_moof) {
        foundFragment = true;
        break;
      } else if (atomDataSize != 0) {
        // Stop searching if reading this atom would exceed the search limit.
        if (bytesSearched + atomSize >= bytesToSearch) {
          break;
        }
        input.advancePeekPosition(atomDataSize);
      }
      bytesSearched += atomSize;
    }
    return foundGoodFileType && fragmented == foundFragment;
  }

  /**
   * Returns whether {@code brand} is an ftyp atom brand that is compatible with the MP4 extractors.
   */
  private static boolean isCompatibleBrand(int brand) {
    // Accept all brands starting '3gp'.
    if (brand >>> 8 == Util.getIntegerCodeForString("3gp")) {
      return true;
    }
    for (int compatibleBrand : COMPATIBLE_BRANDS) {
      if (compatibleBrand == brand) {
        return true;
      }
    }
    return false;
  }

  private Sniffer() {
    // Prevent instantiation.
  }

}

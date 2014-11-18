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
package com.google.android.exoplayer.metadata;

import com.google.android.exoplayer.util.BitArray;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Facilitates the extraction and parsing of EIA-608 (a.k.a. "line 21 captions" and "CEA-608")
 * Closed Captions from the SEI data block from H.264.
 */
public class Eia608Parser implements MetadataParser<List<ClosedCaption>> {

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE = 0x31;
  private static final int USER_ID = 0x47413934; // "GA94"
  private static final int USER_DATA_TYPE_CODE = 0x3;

  private static final int[] SPECIAL_CHARACTER_SET = new int[] {
    0xAE,    // 30: 174 '®' "Registered Sign" - registered trademark symbol
    0xB0,    // 31: 176 '°' "Degree Sign"
    0xBD,    // 32: 189 '½' "Vulgar Fraction One Half" (1/2 symbol)
    0xBF,    // 33: 191 '¿' "Inverted Question Mark"
    0x2122,  // 34:         "Trade Mark Sign" (tm superscript)
    0xA2,    // 35: 162 '¢' "Cent Sign"
    0xA3,    // 36: 163 '£' "Pound Sign" - pounds sterling
    0x266A,  // 37:         "Eighth Note" - music note
    0xE0,    // 38: 224 'à' "Latin small letter A with grave"
    0x20,    // 39:         TRANSPARENT SPACE - for now use ordinary space
    0xE8,    // 3A: 232 'è' "Latin small letter E with grave"
    0xE2,    // 3B: 226 'â' "Latin small letter A with circumflex"
    0xEA,    // 3C: 234 'ê' "Latin small letter E with circumflex"
    0xEE,    // 3D: 238 'î' "Latin small letter I with circumflex"
    0xF4,    // 3E: 244 'ô' "Latin small letter O with circumflex"
    0xFB     // 3F: 251 'û' "Latin small letter U with circumflex"
  };

  @Override
  public boolean canParse(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_EIA608);
  }

  @Override
  public List<ClosedCaption> parse(byte[] data, int size) throws IOException {
    if (size <= 0) {
      return null;
    }
    BitArray seiBuffer = new BitArray(data, size);
    seiBuffer.skipBits(3); // reserved + process_cc_data_flag + zero_bit
    int ccCount = seiBuffer.readBits(5);
    seiBuffer.skipBytes(1);

    List<ClosedCaption> captions = new ArrayList<ClosedCaption>();

    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < ccCount; i++) {
      seiBuffer.skipBits(5); // one_bit + reserved
      boolean ccValid = seiBuffer.readBit();
      if (!ccValid) {
        seiBuffer.skipBits(18);
        continue;
      }
      int ccType = seiBuffer.readBits(2);
      if (ccType != 0 && ccType != 1) {
        // Not EIA-608 captions.
        seiBuffer.skipBits(16);
        continue;
      }
      seiBuffer.skipBits(1);
      byte ccData1 = (byte) seiBuffer.readBits(7);
      seiBuffer.skipBits(1);
      byte ccData2 = (byte) seiBuffer.readBits(7);

      if ((ccData1 == 0x11) && ((ccData2 & 0x70) == 0x30)) {
        ccData2 &= 0xF;
        stringBuilder.append((char) SPECIAL_CHARACTER_SET[ccData2]);
        continue;
      }

      // Control character.
      if (ccData1 < 0x20) {
        if (stringBuilder.length() > 0) {
          captions.add(new ClosedCaption(ClosedCaption.TYPE_TEXT, stringBuilder.toString()));
          stringBuilder.setLength(0);
        }
        captions.add(new ClosedCaption(ClosedCaption.TYPE_CTRL,
            new String(new char[]{(char) ccData1, (char) ccData2})));
        continue;
      }

      stringBuilder.append((char) ccData1);
      if (ccData2 != 0) {
        stringBuilder.append((char) ccData2);
      }

    }

    if (stringBuilder.length() > 0) {
      captions.add(new ClosedCaption(ClosedCaption.TYPE_TEXT, stringBuilder.toString()));
    }

    return Collections.unmodifiableList(captions);
  }

  /**
   * Parses the beginning of SEI data and returns the size of underlying contains closed captions
   * data following the header. Returns 0 if the SEI doesn't contain any closed captions data.
   *
   * @param seiBuffer The buffer to read from.
   * @return The size of closed captions data.
   */
  public static int parseHeader(BitArray seiBuffer) {
    int b = 0;
    int payloadType = 0;

    do {
      b = seiBuffer.readUnsignedByte();
      payloadType += b;
    } while (b == 0xFF);

    if (payloadType != PAYLOAD_TYPE_CC) {
      return 0;
    }

    int payloadSize = 0;
    do {
      b = seiBuffer.readUnsignedByte();
      payloadSize += b;
    } while (b == 0xFF);

    if (payloadSize <= 0) {
      return 0;
    }

    int countryCode = seiBuffer.readUnsignedByte();
    if (countryCode != COUNTRY_CODE) {
      return 0;
    }
    int providerCode = seiBuffer.readBits(16);
    if (providerCode != PROVIDER_CODE) {
      return 0;
    }
    int userIdentifier = seiBuffer.readBits(32);
    if (userIdentifier != USER_ID) {
      return 0;
    }
    int userDataTypeCode = seiBuffer.readUnsignedByte();
    if (userDataTypeCode != USER_DATA_TYPE_CODE) {
      return 0;
    }
    return payloadSize;
  }

}

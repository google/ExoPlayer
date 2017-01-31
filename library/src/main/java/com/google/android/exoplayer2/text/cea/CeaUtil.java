/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.cea;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Utility methods for handling CEA-608/708 messages.
 */
public final class CeaUtil {

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE = 0x31;
  private static final int USER_ID = 0x47413934; // "GA94"
  private static final int USER_DATA_TYPE_CODE = 0x3;

  /**
   * Consumes the unescaped content of an SEI NAL unit, writing the content of any CEA-608 messages
   * as samples to the provided output.
   *
   * @param presentationTimeUs The presentation time in microseconds for any samples.
   * @param seiBuffer The unescaped SEI NAL unit data, excluding the NAL unit start code and type.
   * @param output The output to which any samples should be written.
   */
  public static void consume(long presentationTimeUs, ParsableByteArray seiBuffer,
      TrackOutput output) {
    int b;
    while (seiBuffer.bytesLeft() > 1 /* last byte will be rbsp_trailing_bits */) {
      // Parse payload type.
      int payloadType = 0;
      do {
        b = seiBuffer.readUnsignedByte();
        payloadType += b;
      } while (b == 0xFF);
      // Parse payload size.
      int payloadSize = 0;
      do {
        b = seiBuffer.readUnsignedByte();
        payloadSize += b;
      } while (b == 0xFF);
      // Process the payload.
      if (isSeiMessageCea608(payloadType, payloadSize, seiBuffer)) {
        // Ignore country_code (1) + provider_code (2) + user_identifier (4)
        // + user_data_type_code (1).
        seiBuffer.skipBytes(8);
        // Ignore first three bits: reserved (1) + process_cc_data_flag (1) + zero_bit (1).
        int ccCount = seiBuffer.readUnsignedByte() & 0x1F;
        // Ignore em_data (1)
        seiBuffer.skipBytes(1);
        // Each data packet consists of 24 bits: marker bits (5) + cc_valid (1) + cc_type (2)
        // + cc_data_1 (8) + cc_data_2 (8).
        int sampleLength = ccCount * 3;
        output.sampleData(seiBuffer, sampleLength);
        output.sampleMetadata(presentationTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleLength, 0, null);
        // Ignore trailing information in SEI, if any.
        seiBuffer.skipBytes(payloadSize - (10 + ccCount * 3));
      } else {
        seiBuffer.skipBytes(payloadSize);
      }
    }
  }

  /**
   * Inspects an sei message to determine whether it contains CEA-608.
   * <p>
   * The position of {@code payload} is left unchanged.
   *
   * @param payloadType The payload type of the message.
   * @param payloadLength The length of the payload.
   * @param payload A {@link ParsableByteArray} containing the payload.
   * @return Whether the sei message contains CEA-608.
   */
  private static boolean isSeiMessageCea608(int payloadType, int payloadLength,
      ParsableByteArray payload) {
    if (payloadType != PAYLOAD_TYPE_CC || payloadLength < 8) {
      return false;
    }
    int startPosition = payload.getPosition();
    int countryCode = payload.readUnsignedByte();
    int providerCode = payload.readUnsignedShort();
    int userIdentifier = payload.readInt();
    int userDataTypeCode = payload.readUnsignedByte();
    payload.setPosition(startPosition);
    return countryCode == COUNTRY_CODE && providerCode == PROVIDER_CODE
        && userIdentifier == USER_ID && userDataTypeCode == USER_DATA_TYPE_CODE;
  }

  private CeaUtil() {}

}

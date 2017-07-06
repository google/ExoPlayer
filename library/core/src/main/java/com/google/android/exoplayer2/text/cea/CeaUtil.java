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

import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Utility methods for handling CEA-608/708 messages.
 */
public final class CeaUtil {

  public static final int MODE_H262 = 1;
  public static final int MODE_H264 = 2;
  public static final int MODE_H265 = MODE_H264;

  private static final String TAG = "CeaUtil";

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE = 0x31;
  private static final int USER_ID = 0x47413934; // "GA94"
  private static final int USER_DATA_TYPE_CODE = 0x3;

  public static void consume(long presentationTimeUs, ParsableByteArray buffer,
      TrackOutput[] outputs, int mode) {
    switch (mode) {
      case MODE_H262:
        consumeUserData(presentationTimeUs, buffer, outputs);
        break;
      case MODE_H264:
        consumeSei(presentationTimeUs, buffer, outputs);
        break;
      default:
        Log.w(TAG, "Unknown mode: " + mode);
        break;
    }
  }

  public static void consume(long presentationTimeUs, ParsableByteArray buffer,
      TrackOutput[] outputs) {
    consume(presentationTimeUs, buffer, outputs, MODE_H264);
  }

  /**
   * Consumes the unescaped content of an SEI NAL unit, writing the content of any CEA-608 messages
   * as samples to all of the provided outputs.
   *
   * @param presentationTimeUs The presentation time in microseconds for any samples.
   * @param seiBuffer The unescaped SEI NAL unit data, excluding the NAL unit start code and type.
   * @param outputs The outputs to which any samples should be written.
   */
  private static void consumeSei(long presentationTimeUs, ParsableByteArray seiBuffer,
      TrackOutput[] outputs) {
    while (seiBuffer.bytesLeft() > 1 /* last byte will be rbsp_trailing_bits */) {
      int payloadType = readNon255TerminatedValue(seiBuffer);
      int payloadSize = readNon255TerminatedValue(seiBuffer);
      // Process the payload.
      if (payloadSize == -1 || payloadSize > seiBuffer.bytesLeft()) {
        // This might occur if we're trying to read an encrypted SEI NAL unit.
        Log.w(TAG, "Skipping remainder of malformed SEI NAL unit.");
        seiBuffer.setPosition(seiBuffer.limit());
      } else if (isSeiMessageCea608(payloadType, payloadSize, seiBuffer)) {
        // Ignore country_code (1) + provider_code (2) + user_identifier (4)
        // + user_data_type_code (1).
        seiBuffer.skipBytes(8);
        // Consume closed-captions data.
        int ccCount = consumeCea608Data(presentationTimeUs, seiBuffer, outputs);
        // Ignore trailing information in SEI, if any.
        seiBuffer.skipBytes(payloadSize - (10 + ccCount * 3));
      } else {
        seiBuffer.skipBytes(payloadSize);
      }
    }
  }

  private static void consumeUserData(long presentationTimeUs, ParsableByteArray userDataBuffer,
      TrackOutput[] outputs) {
    if (isUserDataCea608(userDataBuffer)) {
      userDataBuffer.skipBytes(5);
      consumeCea608Data(presentationTimeUs, userDataBuffer, outputs);
    }
  }

  private static int consumeCea608Data(long presentationTimeUs, ParsableByteArray buffer,
      TrackOutput[] outputs) {
    // Ignore first three bits: reserved (1) + process_cc_data_flag (1) + zero_bit (1).
    int ccCount = buffer.readUnsignedByte() & 0x1F;
    // Ignore em_data (1)
    buffer.skipBytes(1);
    // Each data packet consists of 24 bits: marker bits (5) + cc_valid (1) + cc_type (2)
    // + cc_data_1 (8) + cc_data_2 (8).
    int sampleLength = ccCount * 3;
    int sampleStartPosition = buffer.getPosition();
    for (TrackOutput output : outputs) {
      buffer.setPosition(sampleStartPosition);
      output.sampleData(buffer, sampleLength);
      output.sampleMetadata(presentationTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleLength, 0, null);
    }
    return ccCount;
  }

  /**
   * Reads a value from the provided buffer consisting of zero or more 0xFF bytes followed by a
   * terminating byte not equal to 0xFF. The returned value is ((0xFF * N) + T), where N is the
   * number of 0xFF bytes and T is the value of the terminating byte.
   *
   * @param buffer The buffer from which to read the value.
   * @returns The read value, or -1 if the end of the buffer is reached before a value is read.
   */
  private static int readNon255TerminatedValue(ParsableByteArray buffer) {
    int b;
    int value = 0;
    do {
      if (buffer.bytesLeft() == 0) {
        return -1;
      }
      b = buffer.readUnsignedByte();
      value += b;
    } while (b == 0xFF);
    return value;
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

  private static boolean isUserDataCea608(ParsableByteArray userData) {
    if (userData.bytesLeft() < 5) {
      // Need at least 5 bytes: USER_ID + USER_DATA_TYPE_CODE
      return false;
    }
    int startPosition = userData.getPosition();
    int userIdentifier = userData.readInt();
    int userDataTypeCode = userData.readUnsignedByte();
    userData.setPosition(startPosition);
    return userIdentifier == USER_ID && userDataTypeCode == USER_DATA_TYPE_CODE;
  }

  private CeaUtil() {}

}

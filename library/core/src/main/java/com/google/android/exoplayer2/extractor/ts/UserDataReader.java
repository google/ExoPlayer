/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.List;

/**
 * Consumes user data structure, outputting contained CEA-608/708 messages to a {@link TrackOutput}.
 */
/* package */ final class UserDataReader {
  private final List<Format> closedCaptionFormats;
  private final TrackOutput[] outputs;
  private final int USER_DATA_START_CODE = 0x0001B2;
  private final int USER_DATA_IDENTIFIER_GA94 = 0x47413934;
  private final int USER_DATA_TYPE_CODE_MPEG_CC = 0x03;
  public UserDataReader(List<Format> closedCaptionFormats) {
    this.closedCaptionFormats = closedCaptionFormats;
    outputs = new TrackOutput[closedCaptionFormats.size()];
  }

  public void createTracks(ExtractorOutput extractorOutput,
                           TsPayloadReader.TrackIdGenerator idGenerator) {
    for (int i = 0; i < outputs.length; i++) {
      idGenerator.generateNewId();
      TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
      Format channelFormat = closedCaptionFormats.get(i);
      String channelMimeType = channelFormat.sampleMimeType;
      Assertions.checkArgument(MimeTypes.APPLICATION_CEA608.equals(channelMimeType)
                      || MimeTypes.APPLICATION_CEA708.equals(channelMimeType),
              "Invalid closed caption mime type provided: " + channelMimeType);
      output.format(Format.createTextSampleFormat(idGenerator.getFormatId(), channelMimeType, null,
              Format.NO_VALUE, channelFormat.selectionFlags, channelFormat.language,
              channelFormat.accessibilityChannel, null, channelFormat.params));
      outputs[i] = output;
    }
  }

  public void consume(long pesTimeUs, ParsableByteArray userDataPayload) {
    if (userDataPayload.bytesLeft() < 9) {
      return;
    }
    //check if payload is used_data_type (0x01B2)
    int userDataStartCode = userDataPayload.readInt();
    int userDataIdentifier = userDataPayload.readInt();
    int userDataTypeCode = userDataPayload.readUnsignedByte();

    if (userDataStartCode == USER_DATA_START_CODE && userDataIdentifier == USER_DATA_IDENTIFIER_GA94
            &&  userDataTypeCode == USER_DATA_TYPE_CODE_MPEG_CC) {
      if (userDataPayload.bytesLeft() < 2) {
        return;
      }
      // read cc_count and process_cc_data_flag byte.
      int ccByte = userDataPayload.readUnsignedByte();
      boolean processCCDataFlag = ((ccByte & 0x40) != 0);
      int ccCount = (ccByte & 0x1F);
      // skip reserved em_data byte of MPEG_CC structure
      userDataPayload.skipBytes(1);
      int payLoadSize =  ccCount * 3;
      if (processCCDataFlag && payLoadSize != 0) {
        int ccPos = userDataPayload.getPosition();
        for (TrackOutput output : outputs) {
          output.sampleData(userDataPayload, payLoadSize);
          output.sampleMetadata(pesTimeUs, C.BUFFER_FLAG_KEY_FRAME, payLoadSize, 0, null);
          userDataPayload.setPosition(ccPos);
        }

      }
    }
  }

}

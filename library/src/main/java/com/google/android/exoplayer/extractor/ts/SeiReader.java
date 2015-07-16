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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.text.eia608.Eia608Parser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses a SEI data from H.264 frames and extracts samples with closed captions data.
 *
 * TODO: Technically, we shouldn't allow a sample to be read from the queue until we're sure that
 * a sample with an earlier timestamp won't be added to it.
 */
/* package */ class SeiReader extends ElementaryStreamReader {

  public SeiReader(TrackOutput output) {
    super(output);
    output.format(MediaFormat.createTextFormat(MimeTypes.APPLICATION_EIA608));
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  public void consume(ParsableByteArray seiBuffer, long pesTimeUs, boolean startOfPacket) {
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
      // Process the payload. We only support EIA-608 payloads currently.
      if (Eia608Parser.isSeiMessageEia608(payloadType, payloadSize, seiBuffer)) {
        output.sampleData(seiBuffer, payloadSize);
        output.sampleMetadata(pesTimeUs, C.SAMPLE_FLAG_SYNC, payloadSize, 0, null);
      } else {
        seiBuffer.skipBytes(payloadSize);
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

}

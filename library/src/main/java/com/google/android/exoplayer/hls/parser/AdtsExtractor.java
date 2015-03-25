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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * Facilitates the extraction of AAC samples from elementary audio files formatted as AAC with ADTS
 * headers.
 */
public class AdtsExtractor implements HlsExtractor {

  private static final int MAX_PACKET_SIZE = 200;

  private final long firstSampleTimestamp;
  private final ParsableByteArray packetBuffer;

  // Accessed only by the loading thread.
  private AdtsReader adtsReader;
  private boolean firstPacket;

  public AdtsExtractor(long firstSampleTimestamp) {
    this.firstSampleTimestamp = firstSampleTimestamp;
    packetBuffer = new ParsableByteArray(MAX_PACKET_SIZE);
    firstPacket = true;
  }

  @Override
  public void init(TrackOutputBuilder output) {
    adtsReader = new AdtsReader(output.buildOutput(0));
    output.allOutputsBuilt();
  }

  @Override
  public void read(ExtractorInput input) throws IOException, InterruptedException {
    int bytesRead = input.read(packetBuffer.data, 0, MAX_PACKET_SIZE);
    if (bytesRead == -1) {
      return;
    }

    // Feed whatever data we have to the reader, regardless of whether the read finished or not.
    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesRead);

    // TODO: Make it possible for adtsReader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    adtsReader.consume(packetBuffer, firstSampleTimestamp, firstPacket);
    firstPacket = false;
  }

}

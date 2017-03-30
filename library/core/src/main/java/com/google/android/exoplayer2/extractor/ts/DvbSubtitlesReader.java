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
package com.google.android.exoplayer2.extractor.ts;


import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.Collections;
import java.util.List;


/**
 * Output PES packets to a {@link TrackOutput}.
 */
public final class DvbSubtitlesReader implements ElementaryStreamReader {

  private final String language;
  private List<byte[]> initializationData;

  private long sampleTimeUs;
  private int sampleBytesWritten;
  private boolean writingSample;

  private TrackOutput output;

  public DvbSubtitlesReader(TsPayloadReader.EsInfo esInfo) {
    this.language = esInfo.language;
    initializationData = Collections.singletonList(new byte[] {(byte) 0x00,
        esInfo.descriptorBytes[6], esInfo.descriptorBytes[7],
        esInfo.descriptorBytes[8], esInfo.descriptorBytes[9]});
  }


  @Override
  public void seek() {
    writingSample = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    this.output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
    output.format(Format.createImageSampleFormat(idGenerator.getFormatId(),
        MimeTypes.APPLICATION_DVBSUBS, null, Format.NO_VALUE, initializationData, language, null));
  }


  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    if (!dataAlignmentIndicator) {
      return;
    }
    writingSample = true;
    sampleTimeUs = pesTimeUs;
    sampleBytesWritten = 0;
  }

  @Override
  public void packetFinished() {
    output.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null);
    writingSample = false;
  }

  @Override
  public void consume(ParsableByteArray data) {
    if (writingSample) {
      int bytesAvailable = data.bytesLeft();
      output.sampleData(data, bytesAvailable);
      sampleBytesWritten += bytesAvailable;
    }
  }
}
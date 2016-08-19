/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.rawcc;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Extracts EIA-608 data from a RawCC file
 */
public final class RawCcExtractor implements Extractor {

  private static final int SCRATCH_SIZE = 9;
  private static final int HEADER_SIZE = 8;
  private static final int HEADER_ID = Util.getIntegerCodeForString("RCC\u0001");
  private static final int TIMESTAMP_SIZE_V0 = 4;
  private static final int TIMESTAMP_SIZE_V1 = 8;

  // Parser states.
  private static final int STATE_READING_HEADER = 0;
  private static final int STATE_READING_TIMESTAMP_AND_COUNT = 1;
  private static final int STATE_READING_SAMPLES = 2;

  private final ParsableByteArray dataScratch;

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private int parserState;
  private int version;
  private long timestampUs;
  private int remainingSampleCount;
  private int sampleBytesWritten;

  public RawCcExtractor() {
    dataScratch = new ParsableByteArray(SCRATCH_SIZE);
    parserState = STATE_READING_HEADER;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
    extractorOutput.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    trackOutput = extractorOutput.track(0);
    extractorOutput.endTracks();

    trackOutput.format(Format.createTextSampleFormat(null, MimeTypes.APPLICATION_EIA608,
        null, Format.NO_VALUE, 0, null, null));
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    dataScratch.reset();
    input.peekFully(dataScratch.data, 0, HEADER_SIZE);
    return dataScratch.readInt() == HEADER_ID;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    while (true) {
      switch (parserState) {
        case STATE_READING_HEADER:
          parseHeader(input);
          parserState = STATE_READING_TIMESTAMP_AND_COUNT;
          break;
        case STATE_READING_TIMESTAMP_AND_COUNT:
          if (parseTimestampAndSampleCount(input)) {
            parserState = STATE_READING_SAMPLES;
          } else {
            parserState = STATE_READING_HEADER;
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_SAMPLES:
          parseSamples(input);
          parserState = STATE_READING_TIMESTAMP_AND_COUNT;
          return RESULT_CONTINUE;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void seek(long position) {
    parserState = STATE_READING_HEADER;
  }

  @Override
  public void release() {
    // Do nothing
  }

  private void parseHeader(ExtractorInput input) throws IOException, InterruptedException {
    dataScratch.reset();
    input.readFully(dataScratch.data, 0, HEADER_SIZE);
    if (dataScratch.readInt() != HEADER_ID) {
      throw new IOException("Input not RawCC");
    }
    version = dataScratch.readUnsignedByte();
    // no versions use the flag fields yet
  }

  private boolean parseTimestampAndSampleCount(ExtractorInput input) throws IOException,
      InterruptedException {
    dataScratch.reset();
    if (version == 0) {
      if (!input.readFully(dataScratch.data, 0, TIMESTAMP_SIZE_V0 + 1, true)) {
        return false;
      }
      // version 0 timestamps are 45kHz, so we need to convert them into us
      timestampUs = dataScratch.readUnsignedInt() * 1000 / 45;
    } else if (version == 1) {
      if (!input.readFully(dataScratch.data, 0, TIMESTAMP_SIZE_V1 + 1, true)) {
        return false;
      }
      timestampUs = dataScratch.readLong();
    } else {
      throw new ParserException("Unsupported version number: " + version);
    }

    remainingSampleCount = dataScratch.readUnsignedByte();
    sampleBytesWritten = 0;
    return true;
  }

  private void parseSamples(ExtractorInput input) throws IOException, InterruptedException {
    for (; remainingSampleCount > 0; remainingSampleCount--) {
      dataScratch.reset();
      input.readFully(dataScratch.data, 0, 3);

      // only accept EIA-608 packets which have validity (6th bit) == 1 and
      // type (7-8th bits) == 0; i.e. ccDataPkt[0] == 0bXXXXX100
      int ccValidityAndType = dataScratch.readUnsignedByte() & 0x07;
      if (ccValidityAndType == 0x04) {
        trackOutput.sampleData(dataScratch, 2);
        sampleBytesWritten += 2;
      }
    }

    if (sampleBytesWritten > 0) {
      trackOutput.sampleMetadata(timestampUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null);
    }
  }

}

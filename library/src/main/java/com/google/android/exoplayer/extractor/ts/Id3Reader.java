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
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses ID3 data and extracts individual text information frames.
 */
/* package */ class Id3Reader extends ElementaryStreamReader {

  // State that should be reset on seek.
  private boolean writingSample;

  // Per sample state that gets reset at the start of each sample.
  private long sampleTimeUs;
  private int sampleSize;

  public Id3Reader(TrackOutput output) {
    super(output);
    output.format(MediaFormat.createTextFormat(MimeTypes.APPLICATION_ID3));
  }

  @Override
  public void seek() {
    writingSample = false;
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    if (startOfPacket) {
      writingSample = true;
      sampleTimeUs = pesTimeUs;
      sampleSize = 0;
    }
    if (writingSample) {
      sampleSize += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());
    }
  }

  @Override
  public void packetFinished() {
    output.sampleMetadata(sampleTimeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
    writingSample = false;
  }

}

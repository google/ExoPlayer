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

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.text.eia608.Eia608Parser;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses a SEI data from H.264 frames and extracts samples with closed captions data.
 *
 * TODO: Technically, we shouldn't allow a sample to be read from the queue until we're sure that
 * a sample with an earlier timestamp won't be added to it.
 */
/* package */ class SeiReader extends SampleQueue {

  private final ParsableByteArray seiBuffer;

  public SeiReader(SamplePool samplePool) {
    super(samplePool);
    setMediaFormat(MediaFormat.createEia608Format());
    seiBuffer = new ParsableByteArray();
  }

  public void read(byte[] data, int position, long pesTimeUs) {
    seiBuffer.reset(data, data.length);
    seiBuffer.setPosition(position + 4);
    int ccDataSize = Eia608Parser.parseHeader(seiBuffer);
    if (ccDataSize > 0) {
      startSample(Sample.TYPE_MISC, pesTimeUs);
      appendSampleData(seiBuffer, ccDataSize);
      commitSample(true);
    }
  }

}

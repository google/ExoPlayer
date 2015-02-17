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
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses ID3 data and extracts individual text information frames.
 */
/* package */ class Id3Reader extends ElementaryStreamReader {

  public Id3Reader(BufferPool bufferPool) {
    super(bufferPool);
    setMediaFormat(MediaFormat.createId3Format());
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    if (startOfPacket) {
      startSample(pesTimeUs);
    }
    if (writingSample()) {
      appendData(data, data.bytesLeft());
    }
  }

  @Override
  public void packetFinished() {
    commitSample(true);
  }

}

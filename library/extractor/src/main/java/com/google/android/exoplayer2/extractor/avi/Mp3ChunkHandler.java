/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.MpegAudioUtil;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

public class Mp3ChunkHandler extends ChunkHandler {
  private final MpegAudioUtil.Header header = new MpegAudioUtil.Header();
  private final ParsableByteArray scratch = new ParsableByteArray(0);
  private final int fps;
  private int frameRemaining;
  private long us = 0L;

  Mp3ChunkHandler(int id, @NonNull TrackOutput trackOutput, @NonNull ChunkClock clock, int fps) {
    super(id, TYPE_AUDIO, trackOutput, clock);
    this.fps = fps;
  }

  @Override
  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
    if (size == 0) {
      clock.advance();
      syncUs();
      //Log.d(AviExtractor.TAG, "Blank Frame: us=" + us);
      return true;
    }
    chunkRemaining = size;
    if (process(input)) {
      //If we scratch is the entire buffer, we didn't find a MP3 header, so just dump the chunk
      if (scratch.limit() == size) {
        scratch.setPosition(0);
        trackOutput.sampleData(scratch, size);
        scratch.reset(0);
      }
      clock.advance();
      return true;
    }
    return false;
  }

  @Override
  boolean resume(ExtractorInput input) throws IOException {
    if (process(input)) {
      clock.advance();
      return true;
    }
    return false;
  }

  int readScratch(ExtractorInput input, int bytes) throws IOException {
    final int toRead = Math.min(bytes, chunkRemaining);
    final int read = input.read(scratch.getData(), scratch.limit(), toRead);
    if (read == C.RESULT_END_OF_INPUT) {
      return read;
    }
    chunkRemaining -= read;
    scratch.setLimit(scratch.limit() + read);
    return read;
  }

  private boolean findFrame(ExtractorInput input) throws IOException {
    scratch.reset(0);
    scratch.ensureCapacity(scratch.limit() + chunkRemaining);
    int toRead = 4;
    while (chunkRemaining > 0 && readScratch(input, toRead) != C.RESULT_END_OF_INPUT) {
      readScratch(input, toRead);
      while (scratch.bytesLeft() >= 4) {
        if (header.setForHeaderData(scratch.readInt())) {
          scratch.skipBytes(-4);
          return true;
        }
        scratch.skipBytes(-3);
      }
      toRead = Math.min(chunkRemaining, 128);
    }
    return false;
  }

  private boolean process(ExtractorInput input) throws IOException {
    if (frameRemaining == 0) {
      if (findFrame(input)) {
        final int scratchBytes = scratch.bytesLeft();
        trackOutput.sampleData(scratch, scratchBytes);
        frameRemaining = header.frameSize - scratchBytes;
      } else {
        return chunkRemaining == 0;
      }
    }
    final int bytes = trackOutput.sampleData(input, Math.min(frameRemaining, chunkRemaining), false);
    if (bytes == C.RESULT_END_OF_INPUT) {
      return true;
    }
    frameRemaining -= bytes;
    if (frameRemaining == 0) {
      trackOutput.sampleMetadata(us, C.BUFFER_FLAG_KEY_FRAME, header.frameSize, 0, null);
      //Log.d(AviExtractor.TAG, "MP3: us=" + us);
      us += header.samplesPerFrame * C.MICROS_PER_SECOND / fps;
    }
    chunkRemaining -= bytes;
    return chunkRemaining == 0;
  }

  @Override
  public void setIndex(int index) {
    super.setIndex(index);
    syncUs();
  }

  private void syncUs() {
    us = clock.getUs();
    frameRemaining = 0;
  }
}

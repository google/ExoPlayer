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
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.MpegAudioUtil;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * Resolves several issues with Mpeg Audio
 * 1. That muxers don't always mux MPEG audio on the frame boundary
 * 2. That some codecs can't handle multiple or partial frames (Pixels)
 */
public class MpegAudioChunkHandler extends ChunkHandler {
  private final MpegAudioUtil.Header header = new MpegAudioUtil.Header();
  private final ParsableByteArray scratch = new ParsableByteArray(8);
  private final int samplesPerSecond;
  //Bytes remaining in the Mpeg Audio frame
  private int frameRemaining;
  private long timeUs = 0L;

  MpegAudioChunkHandler(int id, @NonNull TrackOutput trackOutput, @NonNull ChunkClock clock,
      int samplesPerSecond) {
    super(id, TYPE_AUDIO, trackOutput, clock);
    this.samplesPerSecond = samplesPerSecond;
  }

  @Override
  public boolean newChunk(int size, @NonNull ExtractorInput input) throws IOException {
    if (size == 0) {
      //Empty frame, advance the clock and sync
      clock.advance();
      syncTime();
      return true;
    }
    this.size = chunkRemaining = size;
    return resume(input);
  }

  @Override
  boolean resume(@NonNull ExtractorInput input) throws IOException {
    if (process(input)) {
      // Fail Over: If the scratch is the entire chunk, we didn't find a MP3 header.
      // Dump the chunk as is and hope the decoder can handle it.
      if (scratch.limit() == size) {
        scratch.setPosition(0);
        trackOutput.sampleData(scratch, size);
        scratch.reset(0);
        done(size);
      }
      return true;
    }
    return false;
  }

  /**
   * Read from input to scratch
   * @param bytes to attempt to read
   * @return {@link C#RESULT_END_OF_INPUT} or number of bytes read.
   */
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

  /**
   * Attempt to find a frame header in the input
   * @return true if a frame header was found
   */
  @VisibleForTesting
  boolean findFrame(ExtractorInput input) throws IOException {
    scratch.reset(0);
    scratch.ensureCapacity(scratch.limit() + chunkRemaining);
    int toRead = 4;
    while (chunkRemaining > 0 && readScratch(input, toRead) != C.RESULT_END_OF_INPUT) {
      while (scratch.bytesLeft() >= 4) {
        if (header.setForHeaderData(scratch.readInt())) {
          scratch.skipBytes(-4);
          return true;
        }
        scratch.skipBytes(-3);
      }
      // 16 is small, but if we end up reading multiple frames into scratch, things get complicated.
      // We should only loop on seek, so this is the lesser of the evils.
      toRead = Math.min(chunkRemaining, 16);
    }
    return false;
  }

  /**
   * Process the chunk by breaking it in Mpeg audio frames
   * @return true if the chunk has been completely processed
   */
  @VisibleForTesting
  boolean process(ExtractorInput input) throws IOException {
    if (frameRemaining == 0) {
      //Find the next frame
      if (findFrame(input)) {
        final int scratchBytes = scratch.bytesLeft();
        trackOutput.sampleData(scratch, scratchBytes);
        frameRemaining = header.frameSize - scratchBytes;
      } else {
        return true;
      }
    }
    final int bytes = trackOutput.sampleData(input, Math.min(frameRemaining, chunkRemaining), false);
    frameRemaining -= bytes;
    if (frameRemaining == 0) {
      trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, header.frameSize, 0, null);
      timeUs += header.samplesPerFrame * C.MICROS_PER_SECOND / samplesPerSecond;
    }
    chunkRemaining -= bytes;
    return chunkRemaining == 0;
  }

  @Override
  public void setIndex(int index) {
    super.setIndex(index);
    syncTime();
    frameRemaining = 0;
  }

  private void syncTime() {
    timeUs = clock.getUs();
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  long getTimeUs() {
    return timeUs;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  int getFrameRemaining() {
    return frameRemaining;
  }
}

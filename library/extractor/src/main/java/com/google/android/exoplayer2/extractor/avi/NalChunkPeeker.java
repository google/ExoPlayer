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
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Generic base class for NAL (0x00 0x00 0x01) chunk headers
 * Theses are used by AVC and MP4V (XVID)
 */
public abstract class NalChunkPeeker extends ChunkHandler {
  private static final int SEEK_PEEK_SIZE = 256;
  private final int peekSize;

  private transient int remaining;
  transient byte[] buffer;
  transient int pos;

  NalChunkPeeker(int id, @NonNull TrackOutput trackOutput,
      @NonNull ChunkClock clock, int peakSize) {
    super(id, TYPE_VIDEO, trackOutput, clock);
    if (peakSize < 5) {
      throw new IllegalArgumentException("Peak size must at least be 5");
    }
    this.peekSize = peakSize;
  }

  abstract void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException;

  /**
   *
   * @return NAL offset from pos
   */
  private int getNalTypeOffset() {
    if (buffer[pos] == 0 && buffer[pos+1] == 0) {
      if (buffer[pos+2] == 1) {
        return 3;
      } else if (buffer[pos+2] == 0 && buffer[pos+3] == 1) {
        return 4;
      }
    }
    return -1;
  }

  /**
   * Look for the next NAL in buffer, incrementing pos
   * @return offset of the nal from the pos
   */
  private int seekNal() {
    int nalOffset;
    while ((nalOffset = getNalTypeOffset()) < 0 && pos < buffer.length - 5) {
      pos++;
    }
    return nalOffset;
  }

  /**
   * Removes everything before the pos
   */
  void compact() {
    //Compress down to the last NAL
    final byte[] newBuffer = new byte[buffer.length - pos];
    System.arraycopy(buffer, pos, newBuffer, 0, newBuffer.length);
    buffer = newBuffer;
    pos = 0;
  }

  /**
   * @param peekSize number of bytes to append
   */
  void append(final ExtractorInput input, final int peekSize) throws IOException {
    int oldLength = buffer.length;
    buffer = Arrays.copyOf(buffer, oldLength + peekSize);
    input.peekFully(buffer, oldLength, peekSize);
    remaining -= peekSize;
  }

  /**
   *
   * @return NAL offset from pos, -1 if end of input
   */
  int seekNextNal(final ExtractorInput input, int skip) throws IOException {
    pos += skip;
    while (pos + 5 < buffer.length || remaining > 0) {
      if (buffer.length - pos < SEEK_PEEK_SIZE && remaining > 0) {
        append(input, Math.min(SEEK_PEEK_SIZE, remaining));
      }
      final int nalOffset = seekNal();
      if (nalOffset > 0) {
        return nalOffset;
      }
    }
    pos = buffer.length;
    return -1;
  }

  abstract boolean skip(byte nalType);

  public boolean newChunk(int tag, int size, ExtractorInput input) throws IOException {
    peek(input, size);
    return super.newChunk(tag, size, input);
  }

  public void peek(ExtractorInput input, final int size) throws IOException {
    buffer = new byte[peekSize];
    if (!input.peekFully(buffer, 0, peekSize, true)) {
      return;
    }
    pos = 0;
    int nalTypeOffset = getNalTypeOffset();
    if (nalTypeOffset < 0 || skip(buffer[nalTypeOffset])) {
      input.resetPeekPosition();
      return;
    }
    remaining = size - peekSize;
    processChunk(input, nalTypeOffset);
    input.resetPeekPosition();
  }
}

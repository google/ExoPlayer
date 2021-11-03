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
package com.google.android.exoplayer2.extractor.wav;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.WavUtil;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/** Reads a WAV header from an input stream; supports resuming from input failures. */
/* package */ final class WavHeaderReader {

  private static final String TAG = "WavHeaderReader";

  /**
   * Returns whether the given {@code input} starts with a RIFF chunk header, followed by a WAVE
   * tag.
   *
   * @param input The input stream to peek from. The position should point to the start of the
   *     stream.
   * @return Whether the given {@code input} starts with a RIFF chunk header, followed by a WAVE
   *     tag.
   * @throws IOException If peeking from the input fails.
   */
  public static boolean checkFileType(ExtractorInput input) throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(ChunkHeader.SIZE_IN_BYTES);
    // Attempt to read the RIFF chunk.
    ChunkHeader chunkHeader = ChunkHeader.peek(input, scratch);
    if (chunkHeader.id != WavUtil.RIFF_FOURCC) {
      return false;
    }

    input.peekFully(scratch.getData(), 0, 4);
    scratch.setPosition(0);
    int formType = scratch.readInt();
    if (formType != WavUtil.WAVE_FOURCC) {
      Log.e(TAG, "Unsupported form type: " + formType);
      return false;
    }

    return true;
  }

  /**
   * Reads and returns a {@code WavFormat}.
   *
   * @param input Input stream to read the WAV format from. The position should point to the byte
   *     following the WAVE tag.
   * @throws IOException If reading from the input fails.
   * @return A new {@code WavFormat} read from {@code input}.
   */
  public static WavFormat readFormat(ExtractorInput input) throws IOException {
    // Allocate a scratch buffer large enough to store the format chunk.
    ParsableByteArray scratch = new ParsableByteArray(16);

    // Skip chunks until we find the format chunk.
    ChunkHeader chunkHeader = ChunkHeader.peek(input, scratch);
    while (chunkHeader.id != WavUtil.FMT_FOURCC) {
      input.skipFully(ChunkHeader.SIZE_IN_BYTES + (int) chunkHeader.size);
      chunkHeader = ChunkHeader.peek(input, scratch);
    }

    Assertions.checkState(chunkHeader.size >= 16);
    input.peekFully(scratch.getData(), 0, 16);
    scratch.setPosition(0);
    int audioFormatType = scratch.readLittleEndianUnsignedShort();
    int numChannels = scratch.readLittleEndianUnsignedShort();
    int frameRateHz = scratch.readLittleEndianUnsignedIntToInt();
    int averageBytesPerSecond = scratch.readLittleEndianUnsignedIntToInt();
    int blockSize = scratch.readLittleEndianUnsignedShort();
    int bitsPerSample = scratch.readLittleEndianUnsignedShort();

    int bytesLeft = (int) chunkHeader.size - 16;
    byte[] extraData;
    if (bytesLeft > 0) {
      extraData = new byte[bytesLeft];
      input.peekFully(extraData, 0, bytesLeft);
    } else {
      extraData = Util.EMPTY_BYTE_ARRAY;
    }

    input.skipFully((int) (input.getPeekPosition() - input.getPosition()));
    return new WavFormat(
        audioFormatType,
        numChannels,
        frameRateHz,
        averageBytesPerSecond,
        blockSize,
        bitsPerSample,
        extraData);
  }

  /**
   * Skips to the data in the given WAV input stream, and returns its bounds. After calling, the
   * input stream's position will point to the start of sample data in the WAV. If an exception is
   * thrown, the input position will be left pointing to a chunk header.
   *
   * @param input The input stream, whose read position must be pointing to a valid chunk header.
   * @return The byte positions at which the data starts (inclusive) and ends (exclusive).
   * @throws ParserException If an error occurs parsing chunks.
   * @throws IOException If reading from the input fails.
   */
  public static Pair<Long, Long> skipToSampleData(ExtractorInput input) throws IOException {
    // Make sure the peek position is set to the read position before we peek the first header.
    input.resetPeekPosition();

    ParsableByteArray scratch = new ParsableByteArray(ChunkHeader.SIZE_IN_BYTES);
    // Skip all chunks until we find the data header.
    ChunkHeader chunkHeader = ChunkHeader.peek(input, scratch);
    while (chunkHeader.id != WavUtil.DATA_FOURCC) {
      Log.w(TAG, "Ignoring unknown WAV chunk: " + chunkHeader.id);
      long bytesToSkip = ChunkHeader.SIZE_IN_BYTES + chunkHeader.size;
      if (bytesToSkip > Integer.MAX_VALUE) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Chunk is too large (~2GB+) to skip; id: " + chunkHeader.id);
      }
      input.skipFully((int) bytesToSkip);
      chunkHeader = ChunkHeader.peek(input, scratch);
    }
    // Skip past the "data" header.
    input.skipFully(ChunkHeader.SIZE_IN_BYTES);

    long dataStartPosition = input.getPosition();
    long dataEndPosition = dataStartPosition + chunkHeader.size;
    long inputLength = input.getLength();
    if (inputLength != C.LENGTH_UNSET && dataEndPosition > inputLength) {
      Log.w(TAG, "Data exceeds input length: " + dataEndPosition + ", " + inputLength);
      dataEndPosition = inputLength;
    }
    return Pair.create(dataStartPosition, dataEndPosition);
  }

  private WavHeaderReader() {
    // Prevent instantiation.
  }

  /** Container for a WAV chunk header. */
  private static final class ChunkHeader {

    /** Size in bytes of a WAV chunk header. */
    public static final int SIZE_IN_BYTES = 8;

    /** 4-character identifier, stored as an integer, for this chunk. */
    public final int id;
    /** Size of this chunk in bytes. */
    public final long size;

    private ChunkHeader(int id, long size) {
      this.id = id;
      this.size = size;
    }

    /**
     * Peeks and returns a {@link ChunkHeader}.
     *
     * @param input Input stream to peek the chunk header from.
     * @param scratch Buffer for temporary use.
     * @throws IOException If peeking from the input fails.
     * @return A new {@code ChunkHeader} peeked from {@code input}.
     */
    public static ChunkHeader peek(ExtractorInput input, ParsableByteArray scratch)
        throws IOException {
      input.peekFully(scratch.getData(), /* offset= */ 0, /* length= */ SIZE_IN_BYTES);
      scratch.setPosition(0);

      int id = scratch.readInt();
      long size = scratch.readLittleEndianUnsignedInt();

      return new ChunkHeader(id, size);
    }
  }
}

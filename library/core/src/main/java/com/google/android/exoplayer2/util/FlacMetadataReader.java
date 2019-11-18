/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.Id3Peeker;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import java.io.IOException;

/** Reads and peeks FLAC stream metadata elements from an {@link ExtractorInput}. */
public final class FlacMetadataReader {

  /** Holds the metadata extracted from the first frame. */
  public static final class FirstFrameMetadata {
    /** The frame start marker, which should correspond to the 2 first bytes of each frame. */
    public final int frameStartMarker;
    /** The block size in samples. */
    public final int blockSizeSamples;

    public FirstFrameMetadata(int frameStartMarker, int blockSizeSamples) {
      this.frameStartMarker = frameStartMarker;
      this.blockSizeSamples = blockSizeSamples;
    }
  }

  private static final int STREAM_MARKER = 0x664C6143; // ASCII for "fLaC"
  private static final int SYNC_CODE = 0x3FFE;

  /**
   * Peeks ID3 Data.
   *
   * @param input Input stream to peek the ID3 data from.
   * @throws IOException If peeking from the input fails. In this case, there is no guarantee on the
   *     peek position.
   * @throws InterruptedException If interrupted while peeking from input. In this case, there is no
   *     guarantee on the peek position.
   */
  public static void peekId3Data(ExtractorInput input) throws IOException, InterruptedException {
    new Id3Peeker().peekId3Data(input, Id3Decoder.NO_FRAMES_PREDICATE);
  }

  /**
   * Peeks the FLAC stream marker.
   *
   * @param input Input stream to peek the stream marker from.
   * @return Whether the data peeked is the FLAC stream marker.
   * @throws IOException If peeking from the input fails. In this case, the peek position is left
   *     unchanged.
   * @throws InterruptedException If interrupted while peeking from input. In this case, the peek
   *     position is left unchanged.
   */
  public static boolean checkAndPeekStreamMarker(ExtractorInput input)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.STREAM_MARKER_SIZE);
    input.peekFully(scratch.data, /* offset= */ 0, FlacConstants.STREAM_MARKER_SIZE);
    return scratch.readUnsignedInt() == STREAM_MARKER;
  }

  /**
   * Reads ID3 Data.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the ID3 data from.
   * @throws IOException If reading from the input fails. In this case, the read position is left
   *     unchanged and there is no guarantee on the peek position.
   * @throws InterruptedException If interrupted while reading from input. In this case, the read
   *     position is left unchanged and there is no guarantee on the peek position.
   */
  public static void readId3Data(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    long startingPeekPosition = input.getPeekPosition();
    new Id3Peeker().peekId3Data(input, Id3Decoder.NO_FRAMES_PREDICATE);
    int peekedId3Bytes = (int) (input.getPeekPosition() - startingPeekPosition);
    input.skipFully(peekedId3Bytes);
  }

  /**
   * Reads the FLAC stream marker.
   *
   * @param input Input stream to read the stream marker from.
   * @param scratchData The array in which the data read should be copied. This array must have size
   *     at least {@code scratchWriteIndex} + {@link FlacConstants#STREAM_MARKER_SIZE}.
   * @param scratchWriteIndex The index of {@code scratchData} from which to write.
   * @throws ParserException If an error occurs parsing the stream marker. In this case, the
   *     position of {@code input} is advanced by {@link FlacConstants#STREAM_MARKER_SIZE} bytes.
   * @throws IOException If reading from the input fails. In this case, the position is left
   *     unchanged.
   * @throws InterruptedException If interrupted while reading from input. In this case, the
   *     position is left unchanged.
   */
  public static void readStreamMarker(
      ExtractorInput input, byte[] scratchData, int scratchWriteIndex)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(scratchData);
    input.readFully(
        scratch.data,
        /* offset= */ scratchWriteIndex,
        /* length= */ FlacConstants.STREAM_MARKER_SIZE);
    scratch.setPosition(scratchWriteIndex);
    if (scratch.readUnsignedInt() != STREAM_MARKER) {
      throw new ParserException("Failed to read FLAC stream marker.");
    }
  }

  /**
   * Reads the stream info block.
   *
   * @param input Input stream to read the stream info block from.
   * @param scratchData The array in which the data read should be copied. This array must have size
   *     at least {@code scratchWriteIndex} + {@link FlacConstants#STREAM_INFO_BLOCK_SIZE}.
   * @param scratchWriteIndex The index of {@code scratchData} from which to write.
   * @return A new {@link FlacStreamMetadata} read from {@code input}.
   * @throws IOException If reading from the input fails. In this case, the position is left
   *     unchanged.
   * @throws InterruptedException If interrupted while reading from input. In this case, the
   *     position is left unchanged.
   */
  public static FlacStreamMetadata readStreamInfoBlock(
      ExtractorInput input, byte[] scratchData, int scratchWriteIndex)
      throws IOException, InterruptedException {
    input.readFully(
        scratchData,
        /* offset= */ scratchWriteIndex,
        /* length= */ FlacConstants.STREAM_INFO_BLOCK_SIZE);
    return new FlacStreamMetadata(
        scratchData, /* offset= */ scratchWriteIndex + FlacConstants.METADATA_BLOCK_HEADER_SIZE);
  }

  /**
   * Skips the stream metadata blocks.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the metadata blocks from.
   * @throws IOException If reading from the input fails. In this case, the read position will be at
   *     the start of a metadata block and there is no guarantee on the peek position.
   * @throws InterruptedException If interrupted while reading from input. In this case, the read
   *     position will be at the start of a metadata block and there is no guarantee on the peek
   *     position.
   */
  public static void skipMetadataBlocks(ExtractorInput input)
      throws IOException, InterruptedException {
    input.resetPeekPosition();
    ParsableBitArray scratch = new ParsableBitArray(new byte[4]);
    boolean lastMetadataBlock = false;
    while (!lastMetadataBlock) {
      input.peekFully(scratch.data, /* offset= */ 0, /* length= */ 4);
      scratch.setPosition(0);
      lastMetadataBlock = scratch.readBit();
      scratch.skipBits(7);
      int length = scratch.readBits(24);
      input.skipFully(4 + length);
    }
  }

  /**
   * Returns some metadata extracted from the first frame of a FLAC stream.
   *
   * <p>The read position of {@code input} is left unchanged and the peek position is aligned with
   * the read position.
   *
   * @param input Input stream to get the metadata from (starting from the read position).
   * @return A {@link FirstFrameMetadata} containing the frame start marker (which should be the
   *     same for all the frames in the stream) and the block size of the frame.
   * @throws ParserException If an error occurs parsing the frame metadata.
   * @throws IOException If peeking from the input fails.
   * @throws InterruptedException If interrupted while peeking from input.
   */
  public static FirstFrameMetadata getFirstFrameMetadata(ExtractorInput input)
      throws IOException, InterruptedException {
    input.resetPeekPosition();
    ParsableByteArray scratch =
        new ParsableByteArray(new byte[FlacConstants.MAX_FRAME_HEADER_SIZE]);
    input.peekFully(scratch.data, /* offset= */ 0, FlacConstants.MAX_FRAME_HEADER_SIZE);

    int frameStartMarker = scratch.readUnsignedShort();
    int syncCode = frameStartMarker >> 2;
    if (syncCode != SYNC_CODE) {
      input.resetPeekPosition();
      throw new ParserException("First frame does not start with sync code.");
    }

    scratch.setPosition(0);
    int firstFrameBlockSizeSamples = FlacFrameReader.getFrameBlockSizeSamples(scratch);

    input.resetPeekPosition();
    return new FirstFrameMetadata(frameStartMarker, firstFrameBlockSizeSamples);
  }

  private FlacMetadataReader() {}
}

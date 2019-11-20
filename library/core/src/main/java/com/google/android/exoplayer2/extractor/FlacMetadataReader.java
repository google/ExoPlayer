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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.VorbisUtil.CommentHeader;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Reads and peeks FLAC stream metadata elements from an {@link ExtractorInput}. */
public final class FlacMetadataReader {

  /** Holds a {@link FlacStreamMetadata}. */
  public static final class FlacStreamMetadataHolder {
    /** The FLAC stream metadata. */
    @Nullable public FlacStreamMetadata flacStreamMetadata;

    public FlacStreamMetadataHolder(@Nullable FlacStreamMetadata flacStreamMetadata) {
      this.flacStreamMetadata = flacStreamMetadata;
    }
  }

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
  private static final int STREAM_INFO_TYPE = 0;
  private static final int VORBIS_COMMENT_TYPE = 4;
  private static final int PICTURE_TYPE = 6;

  /**
   * Peeks ID3 Data.
   *
   * @param input Input stream to peek the ID3 data from.
   * @param parseData Whether to parse the ID3 frames.
   * @return The parsed ID3 data, or {@code null} if there is no such data or if {@code parseData}
   *     is {@code false}.
   * @throws IOException If peeking from the input fails. In this case, there is no guarantee on the
   *     peek position.
   * @throws InterruptedException If interrupted while peeking from input. In this case, there is no
   *     guarantee on the peek position.
   */
  @Nullable
  public static Metadata peekId3Metadata(ExtractorInput input, boolean parseData)
      throws IOException, InterruptedException {
    @Nullable
    Id3Decoder.FramePredicate id3FramePredicate = parseData ? null : Id3Decoder.NO_FRAMES_PREDICATE;
    return new Id3Peeker().peekId3Data(input, id3FramePredicate);
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
    input.peekFully(scratch.data, 0, FlacConstants.STREAM_MARKER_SIZE);
    return scratch.readUnsignedInt() == STREAM_MARKER;
  }

  /**
   * Reads ID3 Data.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the ID3 data from.
   * @param parseData Whether to parse the ID3 frames.
   * @return The parsed ID3 data, or {@code null} if there is no such data or if {@code parseData}
   *     is {@code false}.
   * @throws IOException If reading from the input fails. In this case, the read position is left
   *     unchanged and there is no guarantee on the peek position.
   * @throws InterruptedException If interrupted while reading from input. In this case, the read
   *     position is left unchanged and there is no guarantee on the peek position.
   */
  @Nullable
  public static Metadata readId3Metadata(ExtractorInput input, boolean parseData)
      throws IOException, InterruptedException {
    input.resetPeekPosition();
    long startingPeekPosition = input.getPeekPosition();
    @Nullable Metadata id3Metadata = peekId3Metadata(input, parseData);
    int peekedId3Bytes = (int) (input.getPeekPosition() - startingPeekPosition);
    input.skipFully(peekedId3Bytes);
    return id3Metadata;
  }

  /**
   * Reads the FLAC stream marker.
   *
   * @param input Input stream to read the stream marker from.
   * @throws ParserException If an error occurs parsing the stream marker. In this case, the
   *     position of {@code input} is advanced by {@link FlacConstants#STREAM_MARKER_SIZE} bytes.
   * @throws IOException If reading from the input fails. In this case, the position is left
   *     unchanged.
   * @throws InterruptedException If interrupted while reading from input. In this case, the
   *     position is left unchanged.
   */
  public static void readStreamMarker(ExtractorInput input)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.STREAM_MARKER_SIZE);
    input.readFully(scratch.data, 0, FlacConstants.STREAM_MARKER_SIZE);
    if (scratch.readUnsignedInt() != STREAM_MARKER) {
      throw new ParserException("Failed to read FLAC stream marker.");
    }
  }

  /**
   * Reads one FLAC metadata block.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the metadata block from (header included).
   * @param metadataHolder A holder for the metadata read. If the stream info block (which must be
   *     the first metadata block) is read, the holder contains a new instance representing the
   *     stream info data. If the block read is a Vorbis comment block or a picture block, the
   *     holder contains a copy of the existing stream metadata with the corresponding metadata
   *     added. Otherwise, the metadata in the holder is unchanged.
   * @return Whether the block read is the last metadata block.
   * @throws IllegalArgumentException If the block read is not a stream info block and the metadata
   *     in {@code metadataHolder} is {@code null}. In this case, the read position will be at the
   *     start of a metadata block and there is no guarantee on the peek position.
   * @throws IOException If reading from the input fails. In this case, the read position will be at
   *     the start of a metadata block and there is no guarantee on the peek position.
   * @throws InterruptedException If interrupted while reading from input. In this case, the read
   *     position will be at the start of a metadata block and there is no guarantee on the peek
   *     position.
   */
  public static boolean readMetadataBlock(
      ExtractorInput input, FlacStreamMetadataHolder metadataHolder)
      throws IOException, InterruptedException {
    input.resetPeekPosition();
    ParsableBitArray scratch = new ParsableBitArray(new byte[4]);
    input.peekFully(scratch.data, 0, FlacConstants.METADATA_BLOCK_HEADER_SIZE);

    boolean isLastMetadataBlock = scratch.readBit();
    int type = scratch.readBits(7);
    int length = FlacConstants.METADATA_BLOCK_HEADER_SIZE + scratch.readBits(24);
    if (type == STREAM_INFO_TYPE) {
      metadataHolder.flacStreamMetadata = readStreamInfoBlock(input);
    } else {
      FlacStreamMetadata flacStreamMetadata = metadataHolder.flacStreamMetadata;
      if (flacStreamMetadata == null) {
        throw new IllegalArgumentException();
      }
      if (type == VORBIS_COMMENT_TYPE) {
        List<String> vorbisComments = readVorbisCommentMetadataBlock(input, length);
        metadataHolder.flacStreamMetadata =
            flacStreamMetadata.copyWithVorbisComments(vorbisComments);
      } else if (type == PICTURE_TYPE) {
        PictureFrame pictureFrame = readPictureMetadataBlock(input, length);
        metadataHolder.flacStreamMetadata =
            flacStreamMetadata.copyWithPictureFrames(Collections.singletonList(pictureFrame));
      } else {
        input.skipFully(length);
      }
    }

    return isLastMetadataBlock;
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
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    input.peekFully(scratch.data, 0, FlacConstants.MAX_FRAME_HEADER_SIZE);

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

  private static FlacStreamMetadata readStreamInfoBlock(ExtractorInput input)
      throws IOException, InterruptedException {
    byte[] scratchData = new byte[FlacConstants.STREAM_INFO_BLOCK_SIZE];
    input.readFully(scratchData, 0, FlacConstants.STREAM_INFO_BLOCK_SIZE);
    return new FlacStreamMetadata(
        scratchData, /* offset= */ FlacConstants.METADATA_BLOCK_HEADER_SIZE);
  }

  private static List<String> readVorbisCommentMetadataBlock(ExtractorInput input, int length)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(length);
    input.readFully(scratch.data, 0, length);
    scratch.skipBytes(FlacConstants.METADATA_BLOCK_HEADER_SIZE);
    CommentHeader commentHeader =
        VorbisUtil.readVorbisCommentHeader(
            scratch, /* hasMetadataHeader= */ false, /* hasFramingBit= */ false);
    return Arrays.asList(commentHeader.comments);
  }

  private static PictureFrame readPictureMetadataBlock(ExtractorInput input, int length)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(length);
    input.readFully(scratch.data, 0, length);
    scratch.skipBytes(FlacConstants.METADATA_BLOCK_HEADER_SIZE);

    int pictureType = scratch.readInt();
    int mimeTypeLength = scratch.readInt();
    String mimeType = scratch.readString(mimeTypeLength, Charset.forName(C.ASCII_NAME));
    int descriptionLength = scratch.readInt();
    String description = scratch.readString(descriptionLength);
    int width = scratch.readInt();
    int height = scratch.readInt();
    int depth = scratch.readInt();
    int colors = scratch.readInt();
    int pictureDataLength = scratch.readInt();
    byte[] pictureData = new byte[pictureDataLength];
    scratch.readBytes(pictureData, 0, pictureDataLength);

    return new PictureFrame(
        pictureType, mimeType, description, width, height, depth, colors, pictureData);
  }

  private FlacMetadataReader() {}
}

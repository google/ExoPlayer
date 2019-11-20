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

import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/** Reads and peeks FLAC frame elements. */
public final class FlacFrameReader {

  /** Holds a frame block size. */
  public static final class BlockSizeHolder {
    /** The block size in samples. */
    public int blockSizeSamples;
  }

  /**
   * Checks whether the given FLAC frame header is valid and, if so, reads it and writes the frame
   * block size in {@code blockSizeHolder}.
   *
   * <p>If the header is valid, the position of {@code scratch} is moved to the byte following it.
   * Otherwise, there is no guarantee on the position.
   *
   * @param scratch The array to read the data from, whose position must correspond to the frame
   *     header.
   * @param flacStreamMetadata The stream metadata.
   * @param frameStartMarker The frame start marker of the stream.
   * @param blockSizeHolder The holder used to contain the block size.
   * @return Whether the frame header is valid.
   */
  public static boolean checkAndReadFrameHeader(
      ParsableByteArray scratch,
      FlacStreamMetadata flacStreamMetadata,
      int frameStartMarker,
      BlockSizeHolder blockSizeHolder) {
    int frameStartPosition = scratch.getPosition();

    long frameHeaderBytes = scratch.readUnsignedInt();
    if (frameHeaderBytes >>> 16 != frameStartMarker) {
      return false;
    }

    int blockSizeKey = (int) (frameHeaderBytes >> 12 & 0xF);
    int sampleRateKey = (int) (frameHeaderBytes >> 8 & 0xF);
    int channelAssignmentKey = (int) (frameHeaderBytes >> 4 & 0xF);
    int bitsPerSampleKey = (int) (frameHeaderBytes >> 1 & 0x7);
    boolean reservedBit = (frameHeaderBytes & 1) == 1;
    return checkChannelAssignment(channelAssignmentKey, flacStreamMetadata)
        && checkBitsPerSample(bitsPerSampleKey, flacStreamMetadata)
        && !reservedBit
        && checkAndReadUtf8Data(scratch)
        && checkAndReadBlockSizeSamples(scratch, flacStreamMetadata, blockSizeKey, blockSizeHolder)
        && checkAndReadSampleRate(scratch, flacStreamMetadata, sampleRateKey)
        && checkAndReadCrc(scratch, frameStartPosition);
  }

  /**
   * Returns the block size of the given frame.
   *
   * <p>If no exception is thrown, the position of {@code scratch} is left unchanged. Otherwise,
   * there is no guarantee on the position.
   *
   * @param scratch The array to get the data from, whose position must correspond to the start of a
   *     frame.
   * @return The block size in samples, or -1 if the block size is invalid.
   */
  public static int getFrameBlockSizeSamples(ParsableByteArray scratch) {
    int blockSizeKey = (scratch.data[2] & 0xFF) >> 4;
    if (blockSizeKey < 6 || blockSizeKey > 7) {
      return readFrameBlockSizeSamplesFromKey(scratch, blockSizeKey);
    }
    scratch.skipBytes(4);
    scratch.readUtf8EncodedLong();
    int blockSizeSamples = readFrameBlockSizeSamplesFromKey(scratch, blockSizeKey);
    scratch.setPosition(0);
    return blockSizeSamples;
  }

  /**
   * Reads the given block size.
   *
   * @param scratch The array to read the data from, whose position must correspond to the block
   *     size bits.
   * @param blockSizeKey The key in the block size lookup table.
   * @return The block size in samples.
   */
  public static int readFrameBlockSizeSamplesFromKey(ParsableByteArray scratch, int blockSizeKey) {
    switch (blockSizeKey) {
      case 1:
        return 192;
      case 2:
      case 3:
      case 4:
      case 5:
        return 576 << (blockSizeKey - 2);
      case 6:
        return scratch.readUnsignedByte() + 1;
      case 7:
        return scratch.readUnsignedShort() + 1;
      case 8:
      case 9:
      case 10:
      case 11:
      case 12:
      case 13:
      case 14:
      case 15:
        return 256 << (blockSizeKey - 8);
      default:
        return -1;
    }
  }

  /**
   * Checks whether the given channel assignment is valid.
   *
   * @param channelAssignmentKey The channel assignment lookup key.
   * @param flacStreamMetadata The stream metadata.
   * @return Whether the channel assignment is valid.
   */
  private static boolean checkChannelAssignment(
      int channelAssignmentKey, FlacStreamMetadata flacStreamMetadata) {
    if (channelAssignmentKey <= 7) {
      return channelAssignmentKey == flacStreamMetadata.channels - 1;
    } else if (channelAssignmentKey <= 10) {
      return flacStreamMetadata.channels == 2;
    } else {
      return false;
    }
  }

  /**
   * Checks whether the given number of bits per sample is valid.
   *
   * @param bitsPerSampleKey The bits per sample lookup key.
   * @param flacStreamMetadata The stream metadata.
   * @return Whether the number of bits per sample is valid.
   */
  private static boolean checkBitsPerSample(
      int bitsPerSampleKey, FlacStreamMetadata flacStreamMetadata) {
    if (bitsPerSampleKey == 0) {
      return true;
    }
    return bitsPerSampleKey == flacStreamMetadata.bitsPerSampleLookupKey;
  }

  /**
   * Checks whether the given UTF-8 data is valid and, if so, reads it.
   *
   * <p>If the UTF-8 data is valid, the position of {@code scratch} is moved to the byte following
   * it. Otherwise, there is no guarantee on the position.
   *
   * @param scratch The array to read the data from, whose position must correspond to the UTF-8
   *     data.
   * @return Whether the UTF-8 data is valid.
   */
  private static boolean checkAndReadUtf8Data(ParsableByteArray scratch) {
    try {
      scratch.readUtf8EncodedLong();
    } catch (NumberFormatException e) {
      return false;
    }
    return true;
  }

  /**
   * Checks whether the given frame block size key and block size bits are valid and, if so, reads
   * the block size bits and writes the block size in {@code blockSizeHolder}.
   *
   * <p>If the block size is valid, the position of {@code scratch} is moved to the byte following
   * the block size bits. Otherwise, there is no guarantee on the position.
   *
   * @param scratch The array to read the data from, whose position must correspond to the block
   *     size bits.
   * @param flacStreamMetadata The stream metadata.
   * @param blockSizeKey The key in the block size lookup table.
   * @param blockSizeHolder The holder used to contain the block size.
   * @return Whether the block size is valid.
   */
  private static boolean checkAndReadBlockSizeSamples(
      ParsableByteArray scratch,
      FlacStreamMetadata flacStreamMetadata,
      int blockSizeKey,
      BlockSizeHolder blockSizeHolder) {
    int blockSizeSamples = readFrameBlockSizeSamplesFromKey(scratch, blockSizeKey);
    if (blockSizeSamples == -1 || blockSizeSamples > flacStreamMetadata.maxBlockSizeSamples) {
      return false;
    }
    blockSizeHolder.blockSizeSamples = blockSizeSamples;
    return true;
  }

  /**
   * Checks whether the given sample rate key and sample rate bits are valid and, if so, reads the
   * sample rate bits.
   *
   * <p>If the sample rate is valid, the position of {@code scratch} is moved to the byte following
   * the sample rate bits. Otherwise, there is no guarantee on the position.
   *
   * @param scratch The array to read the data from, whose position must indicate the sample rate
   *     bits.
   * @param flacStreamMetadata The stream metadata.
   * @param sampleRateKey The key in the sample rate lookup table.
   * @return Whether the sample rate is valid.
   */
  private static boolean checkAndReadSampleRate(
      ParsableByteArray scratch, FlacStreamMetadata flacStreamMetadata, int sampleRateKey) {
    int expectedSampleRate = flacStreamMetadata.sampleRate;
    if (sampleRateKey == 0) {
      return true;
    } else if (sampleRateKey <= 11) {
      return sampleRateKey == flacStreamMetadata.sampleRateLookupKey;
    } else if (sampleRateKey == 12) {
      return scratch.readUnsignedByte() * 1000 == expectedSampleRate;
    } else if (sampleRateKey <= 14) {
      int sampleRate = scratch.readUnsignedShort();
      if (sampleRateKey == 14) {
        sampleRate *= 10;
      }
      return sampleRate == expectedSampleRate;
    } else {
      return false;
    }
  }

  /**
   * Checks whether the given CRC is valid and, if so, reads it.
   *
   * <p>If the CRC is valid, the position of {@code scratch} is moved to the byte following it.
   * Otherwise, there is no guarantee on the position.
   *
   * <p>The {@code scratch} array must contain the whole frame header.
   *
   * @param scratch The array to read the data from, whose position must indicate the CRC.
   * @param frameStartPosition The frame start offset in {@code scratch}.
   * @return Whether the CRC is valid.
   */
  private static boolean checkAndReadCrc(ParsableByteArray scratch, int frameStartPosition) {
    int crc = scratch.readUnsignedByte();
    int frameEndPosition = scratch.getPosition();
    int expectedCrc =
        Util.crc8(scratch.data, frameStartPosition, frameEndPosition - 1, /* initialValue= */ 0);
    return crc == expectedCrc;
  }

  private FlacFrameReader() {}
}

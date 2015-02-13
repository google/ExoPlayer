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
import com.google.android.exoplayer.mp4.Mp4Util;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a continuous H264 byte stream and extracts individual frames.
 */
/* package */ class H264Reader extends PesPayloadReader {

  private static final int NAL_UNIT_TYPE_IDR = 5;
  private static final int NAL_UNIT_TYPE_SEI = 6;
  private static final int NAL_UNIT_TYPE_SPS = 7;
  private static final int NAL_UNIT_TYPE_PPS = 8;
  private static final int NAL_UNIT_TYPE_AUD = 9;

  private final SeiReader seiReader;
  private final ParsableByteArray pendingSampleWrapper;

  // TODO: Ideally we wouldn't need to have a copy step through a byte array here.
  private byte[] pendingSampleData;
  private int pendingSampleSize;
  private long pendingSampleTimeUs;

  public H264Reader(BufferPool bufferPool, SeiReader seiReader) {
    super(bufferPool);
    this.seiReader = seiReader;
    this.pendingSampleData = new byte[1024];
    this.pendingSampleWrapper = new ParsableByteArray();
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    while (data.bytesLeft() > 0) {
      boolean sampleFinished = readToNextAudUnit(data, pesTimeUs);
      if (!sampleFinished) {
        continue;
      }

      // Scan the sample to find relevant NAL units.
      int position = 0;
      int idrNalUnitPosition = Integer.MAX_VALUE;
      while (position < pendingSampleSize) {
        position = Mp4Util.findNalUnit(pendingSampleData, position, pendingSampleSize);
        if (position < pendingSampleSize) {
          int type = Mp4Util.getNalUnitType(pendingSampleData, position);
          if (type == NAL_UNIT_TYPE_IDR) {
            idrNalUnitPosition = position;
          } else if (type == NAL_UNIT_TYPE_SEI) {
            seiReader.read(pendingSampleData, position, pendingSampleTimeUs);
          }
          position += 4;
        }
      }

      // Determine whether the sample is a keyframe.
      boolean isKeyframe = pendingSampleSize > idrNalUnitPosition;
      if (!hasMediaFormat() && isKeyframe) {
        parseMediaFormat(pendingSampleData, pendingSampleSize);
      }

      // Commit the sample to the queue.
      pendingSampleWrapper.reset(pendingSampleData, pendingSampleSize);
      appendSampleData(pendingSampleWrapper, pendingSampleSize);
      commitSample(isKeyframe);
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Reads data up to (but not including) the start of the next AUD unit.
   *
   * @param data The data to consume.
   * @param pesTimeUs The corresponding time.
   * @return True if the current sample is now complete. False otherwise.
   */
  private boolean readToNextAudUnit(ParsableByteArray data, long pesTimeUs) {
    int pesOffset = data.getPosition();
    int pesLimit = data.limit();

    // TODO: We probably need to handle the case where the AUD start code was split across the
    // previous and current data buffers.
    int audOffset = Mp4Util.findNalUnit(data.data, pesOffset, pesLimit, NAL_UNIT_TYPE_AUD);
    int bytesToNextAud = audOffset - pesOffset;
    if (bytesToNextAud == 0) {
      if (!writingSample()) {
        startSample(pesTimeUs);
        pendingSampleSize = 0;
        pendingSampleTimeUs = pesTimeUs;
        appendToSample(data, 4);
        return false;
      } else {
        return true;
      }
    } else if (writingSample()) {
      appendToSample(data, bytesToNextAud);
      return data.bytesLeft() > 0;
    } else {
      data.skip(bytesToNextAud);
      return false;
    }
  }

  private void appendToSample(ParsableByteArray data, int length) {
    int requiredSize = pendingSampleSize + length;
    if (pendingSampleData.length < requiredSize) {
      byte[] newPendingSampleData = new byte[(requiredSize * 3) / 2];
      System.arraycopy(pendingSampleData, 0, newPendingSampleData, 0, pendingSampleSize);
      pendingSampleData = newPendingSampleData;
    }
    data.readBytes(pendingSampleData, pendingSampleSize, length);
    pendingSampleSize += length;
  }

  private void parseMediaFormat(byte[] sampleData, int sampleSize) {
    // Locate the SPS and PPS units.
    int spsOffset = Mp4Util.findNalUnit(sampleData, 0, sampleSize, NAL_UNIT_TYPE_SPS);
    int ppsOffset = Mp4Util.findNalUnit(sampleData, 0, sampleSize, NAL_UNIT_TYPE_PPS);
    if (spsOffset == sampleSize || ppsOffset == sampleSize) {
      return;
    }
    // Determine the length of the units, and copy them to build the initialization data.
    int spsLength = Mp4Util.findNalUnit(sampleData, spsOffset + 3, sampleSize) - spsOffset;
    int ppsLength = Mp4Util.findNalUnit(sampleData, ppsOffset + 3, sampleSize) - ppsOffset;
    byte[] spsData = new byte[spsLength];
    byte[] ppsData = new byte[ppsLength];
    System.arraycopy(sampleData, spsOffset, spsData, 0, spsLength);
    System.arraycopy(sampleData, ppsOffset, ppsData, 0, ppsLength);
    List<byte[]> initializationData = new ArrayList<byte[]>();
    initializationData.add(spsData);
    initializationData.add(ppsData);

    // Unescape and then parse the SPS unit.
    byte[] unescapedSps = unescapeStream(spsData, 0, spsLength);
    ParsableBitArray bitArray = new ParsableBitArray(unescapedSps);
    bitArray.skipBits(32); // NAL header
    int profileIdc = bitArray.readBits(8);
    bitArray.skipBits(16); // constraint bits (6), reserved (2) and level_idc (8)
    bitArray.readUnsignedExpGolombCodedInt(); // seq_parameter_set_id

    int chromaFormatIdc = 1; // Default is 4:2:0
    if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
        || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
        || profileIdc == 128 || profileIdc == 138) {
      chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
      if (chromaFormatIdc == 3) {
        bitArray.skipBits(1); // separate_colour_plane_flag
      }
      bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
      bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
      bitArray.skipBits(1); // qpprime_y_zero_transform_bypass_flag
      boolean seqScalingMatrixPresentFlag = bitArray.readBit();
      if (seqScalingMatrixPresentFlag) {
        int limit = (chromaFormatIdc != 3) ? 8 : 12;
        for (int i = 0; i < limit; i++) {
          boolean seqScalingListPresentFlag = bitArray.readBit();
          if (seqScalingListPresentFlag) {
            skipScalingList(bitArray, i < 6 ? 16 : 64);
          }
        }
      }
    }

    bitArray.readUnsignedExpGolombCodedInt(); // log2_max_frame_num_minus4
    long picOrderCntType = bitArray.readUnsignedExpGolombCodedInt();
    if (picOrderCntType == 0) {
      bitArray.readUnsignedExpGolombCodedInt(); // log2_max_pic_order_cnt_lsb_minus4
    } else if (picOrderCntType == 1) {
      bitArray.skipBits(1); // delta_pic_order_always_zero_flag
      bitArray.readSignedExpGolombCodedInt(); // offset_for_non_ref_pic
      bitArray.readSignedExpGolombCodedInt(); // offset_for_top_to_bottom_field
      long numRefFramesInPicOrderCntCycle = bitArray.readUnsignedExpGolombCodedInt();
      for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
        bitArray.readUnsignedExpGolombCodedInt(); // offset_for_ref_frame[i]
      }
    }
    bitArray.readUnsignedExpGolombCodedInt(); // max_num_ref_frames
    bitArray.skipBits(1); // gaps_in_frame_num_value_allowed_flag

    int picWidthInMbs = bitArray.readUnsignedExpGolombCodedInt() + 1;
    int picHeightInMapUnits = bitArray.readUnsignedExpGolombCodedInt() + 1;
    boolean frameMbsOnlyFlag = bitArray.readBit();
    int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
    if (!frameMbsOnlyFlag) {
      bitArray.skipBits(1); // mb_adaptive_frame_field_flag
    }

    bitArray.skipBits(1); // direct_8x8_inference_flag
    int frameWidth = picWidthInMbs * 16;
    int frameHeight = frameHeightInMbs * 16;
    boolean frameCroppingFlag = bitArray.readBit();
    if (frameCroppingFlag) {
      int frameCropLeftOffset = bitArray.readUnsignedExpGolombCodedInt();
      int frameCropRightOffset = bitArray.readUnsignedExpGolombCodedInt();
      int frameCropTopOffset = bitArray.readUnsignedExpGolombCodedInt();
      int frameCropBottomOffset = bitArray.readUnsignedExpGolombCodedInt();
      int cropUnitX, cropUnitY;
      if (chromaFormatIdc == 0) {
        cropUnitX = 1;
        cropUnitY = 2 - (frameMbsOnlyFlag ? 1 : 0);
      } else {
        int subWidthC = (chromaFormatIdc == 3) ? 1 : 2;
        int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
        cropUnitX = subWidthC;
        cropUnitY = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
      }
      frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * cropUnitX;
      frameHeight -= (frameCropTopOffset + frameCropBottomOffset) * cropUnitY;
    }

    // Set the format.
    setMediaFormat(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        frameWidth, frameHeight, initializationData));
  }

  private void skipScalingList(ParsableBitArray bitArray, int size) {
    int lastScale = 8;
    int nextScale = 8;
    for (int i = 0; i < size; i++) {
      if (nextScale != 0) {
        int deltaScale = bitArray.readSignedExpGolombCodedInt();
        nextScale = (lastScale + deltaScale + 256) % 256;
      }
      lastScale = (nextScale == 0) ? lastScale : nextScale;
    }
  }

  /**
   * Replaces occurrences of [0, 0, 3] with [0, 0].
   * <p>
   * See ISO/IEC 14496-10:2005(E) page 36 for more information.
   */
  private byte[] unescapeStream(byte[] data, int offset, int limit) {
    int position = offset;
    List<Integer> escapePositions = new ArrayList<Integer>();
    while (position < limit) {
      position = findNextUnescapeIndex(data, position, limit);
      if (position < limit) {
        escapePositions.add(position);
        position += 3;
      }
    }

    int escapeCount = escapePositions.size();
    int escapedPosition = offset; // The position being read from.
    int unescapedPosition = 0; // The position being written to.
    byte[] unescapedData = new byte[limit - offset - escapeCount];
    for (int i = 0; i < escapeCount; i++) {
      int nextEscapePosition = escapePositions.get(i);
      int copyLength = nextEscapePosition - escapedPosition;
      System.arraycopy(data, escapedPosition, unescapedData, unescapedPosition, copyLength);
      escapedPosition += copyLength + 3;
      unescapedPosition += copyLength + 2;
    }

    int remainingLength = unescapedData.length - unescapedPosition;
    System.arraycopy(data, escapedPosition, unescapedData, unescapedPosition, remainingLength);
    return unescapedData;
  }

  private int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
    for (int i = offset; i < limit - 2; i++) {
      if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
        return i;
      }
    }
    return limit;
  }

}

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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.H264Util;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a continuous H264 byte stream and extracts individual frames.
 */
/* package */ class H264Reader extends ElementaryStreamReader {

  private static final String TAG = "H264Reader";

  private static final int NAL_UNIT_TYPE_IDR = 5;
  private static final int NAL_UNIT_TYPE_SEI = 6;
  private static final int NAL_UNIT_TYPE_SPS = 7;
  private static final int NAL_UNIT_TYPE_PPS = 8;
  private static final int NAL_UNIT_TYPE_AUD = 9;
  private static final int EXTENDED_SAR = 0xFF;
  private static final float[] ASPECT_RATIO_IDC_VALUES = new float[] {
    1f /* Unspecified. Assume square */,
    1f,
    12f / 11f,
    10f / 11f,
    16f / 11f,
    40f / 33f,
    24f / 11f,
    20f / 11f,
    32f / 11f,
    80f / 33f,
    18f / 11f,
    15f / 11f,
    64f / 33f,
    160f / 99f,
    4f / 3f,
    3f / 2f,
    2f
  };

  private final SeiReader seiReader;
  private final boolean[] prefixFlags;
  private final NalUnitTargetBuffer sps;
  private final NalUnitTargetBuffer pps;
  private final NalUnitTargetBuffer sei;
  private final ParsableByteArray seiWrapper;

  private boolean hasOutputFormat;
  private int scratchEscapeCount;
  private int[] scratchEscapePositions;

  private boolean writingSample;
  private boolean isKeyframe;
  private long samplePosition;
  private long sampleTimeUs;
  private long totalBytesWritten;

  public H264Reader(TrackOutput output, SeiReader seiReader) {
    super(output);
    this.seiReader = seiReader;
    prefixFlags = new boolean[3];
    sps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SPS, 128);
    pps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_PPS, 128);
    sei = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SEI, 128);
    seiWrapper = new ParsableByteArray();
    scratchEscapePositions = new int[10];
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.data;

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      // Scan the appended data, processing NAL units as they are encountered
      while (offset < limit) {
        int nextNalUnitOffset = H264Util.findNalUnit(dataArray, offset, limit, prefixFlags);
        if (nextNalUnitOffset < limit) {
          // We've seen the start of a NAL unit.

          // This is the length to the start of the unit. It may be negative if the NAL unit
          // actually started in previously consumed data.
          int lengthToNalUnit = nextNalUnitOffset - offset;
          if (lengthToNalUnit > 0) {
            feedNalUnitTargetBuffersData(dataArray, offset, nextNalUnitOffset);
          }

          int nalUnitType = H264Util.getNalUnitType(dataArray, nextNalUnitOffset);
          int bytesWrittenPastNalUnit = limit - nextNalUnitOffset;
          if (nalUnitType == NAL_UNIT_TYPE_AUD) {
            if (writingSample) {
              if (isKeyframe && !hasOutputFormat && sps.isCompleted() && pps.isCompleted()) {
                parseMediaFormat(sps, pps);
              }
              int flags = isKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
              int size = (int) (totalBytesWritten - samplePosition) - bytesWrittenPastNalUnit;
              output.sampleMetadata(sampleTimeUs, flags, size, bytesWrittenPastNalUnit, null);
              writingSample = false;
            }
            writingSample = true;
            samplePosition = totalBytesWritten - bytesWrittenPastNalUnit;
            sampleTimeUs = pesTimeUs;
            isKeyframe = false;
          } else if (nalUnitType == NAL_UNIT_TYPE_IDR) {
            isKeyframe = true;
          }

          // If the length to the start of the unit is negative then we wrote too many bytes to the
          // NAL buffers. Discard the excess bytes when notifying that the unit has ended.
          feedNalUnitTargetEnd(pesTimeUs, lengthToNalUnit < 0 ? -lengthToNalUnit : 0);
          // Notify the start of the next NAL unit.
          feedNalUnitTargetBuffersStart(nalUnitType);
          // Continue scanning the data.
          offset = nextNalUnitOffset + 4;
        } else {
          feedNalUnitTargetBuffersData(dataArray, offset, limit);
          offset = limit;
        }
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private void feedNalUnitTargetBuffersStart(int nalUnitType) {
    if (!hasOutputFormat) {
      sps.startNalUnit(nalUnitType);
      pps.startNalUnit(nalUnitType);
    }
    sei.startNalUnit(nalUnitType);
  }

  private void feedNalUnitTargetBuffersData(byte[] dataArray, int offset, int limit) {
    if (!hasOutputFormat) {
      sps.appendToNalUnit(dataArray, offset, limit);
      pps.appendToNalUnit(dataArray, offset, limit);
    }
    sei.appendToNalUnit(dataArray, offset, limit);
  }

  private void feedNalUnitTargetEnd(long pesTimeUs, int discardPadding) {
    sps.endNalUnit(discardPadding);
    pps.endNalUnit(discardPadding);
    if (sei.endNalUnit(discardPadding)) {
      int unescapedLength = unescapeStream(sei.nalData, sei.nalLength);
      seiWrapper.reset(sei.nalData, unescapedLength);
      seiReader.consume(seiWrapper, pesTimeUs, true);
    }
  }

  private void parseMediaFormat(NalUnitTargetBuffer sps, NalUnitTargetBuffer pps) {
    byte[] spsData = new byte[sps.nalLength];
    byte[] ppsData = new byte[pps.nalLength];
    System.arraycopy(sps.nalData, 0, spsData, 0, sps.nalLength);
    System.arraycopy(pps.nalData, 0, ppsData, 0, pps.nalLength);
    List<byte[]> initializationData = new ArrayList<byte[]>();
    initializationData.add(spsData);
    initializationData.add(ppsData);

    // Unescape and then parse the SPS unit.
    unescapeStream(sps.nalData, sps.nalLength);
    ParsableBitArray bitArray = new ParsableBitArray(sps.nalData);
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

    float pixelWidthHeightRatio = 1;
    boolean vuiParametersPresentFlag = bitArray.readBit();
    if (vuiParametersPresentFlag) {
      boolean aspectRatioInfoPresentFlag = bitArray.readBit();
      if (aspectRatioInfoPresentFlag) {
        int aspectRatioIdc = bitArray.readBits(8);
        if (aspectRatioIdc == EXTENDED_SAR) {
          int sarWidth = bitArray.readBits(16);
          int sarHeight = bitArray.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
    }

    output.format(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        C.UNKNOWN_TIME_US, frameWidth, frameHeight, pixelWidthHeightRatio, initializationData));
    hasOutputFormat = true;
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
   * Unescapes {@code data} up to the specified limit, replacing occurrences of [0, 0, 3] with
   * [0, 0]. The unescaped data is returned in-place, with the return value indicating its length.
   * <p>
   * See ISO/IEC 14496-10:2005(E) page 36 for more information.
   *
   * @param data The data to unescape.
   * @param limit The limit (exclusive) of the data to unescape.
   * @return The length of the unescaped data.
   */
  private int unescapeStream(byte[] data, int limit) {
    int position = 0;
    scratchEscapeCount = 0;
    while (position < limit) {
      position = findNextUnescapeIndex(data, position, limit);
      if (position < limit) {
        if (scratchEscapePositions.length <= scratchEscapeCount) {
          // Grow scratchEscapePositions to hold a larger number of positions.
          scratchEscapePositions = Arrays.copyOf(scratchEscapePositions,
              scratchEscapePositions.length * 2);
        }
        scratchEscapePositions[scratchEscapeCount++] = position;
        position += 3;
      }
    }

    int unescapedLength = limit - scratchEscapeCount;
    int escapedPosition = 0; // The position being read from.
    int unescapedPosition = 0; // The position being written to.
    for (int i = 0; i < scratchEscapeCount; i++) {
      int nextEscapePosition = scratchEscapePositions[i];
      int copyLength = nextEscapePosition - escapedPosition;
      System.arraycopy(data, escapedPosition, data, unescapedPosition, copyLength);
      escapedPosition += copyLength + 3;
      unescapedPosition += copyLength + 2;
    }

    int remainingLength = unescapedLength - unescapedPosition;
    System.arraycopy(data, escapedPosition, data, unescapedPosition, remainingLength);
    return unescapedLength;
  }

  private int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
    for (int i = offset; i < limit - 2; i++) {
      if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
        return i;
      }
    }
    return limit;
  }

  /**
   * A buffer that fills itself with data corresponding to a specific NAL unit, as it is
   * encountered in the stream.
   */
  private static final class NalUnitTargetBuffer {

    private final int targetType;

    private boolean isFilling;
    private boolean isCompleted;

    public byte[] nalData;
    public int nalLength;

    public NalUnitTargetBuffer(int targetType, int initialCapacity) {
      this.targetType = targetType;
      // Initialize data, writing the known NAL prefix into the first four bytes.
      nalData = new byte[4 + initialCapacity];
      nalData[2] = 1;
      nalData[3] = (byte) targetType;
    }

    public boolean isCompleted() {
      return isCompleted;
    }

    /**
     * Invoked to indicate that a NAL unit has started.
     *
     * @param type The type of the NAL unit.
     */
    public void startNalUnit(int type) {
      Assertions.checkState(!isFilling);
      isFilling = type == targetType;
      if (isFilling) {
        // Length is initially the length of the NAL prefix.
        nalLength = 4;
        isCompleted = false;
      }
    }

    /**
     * Invoked to pass stream data. The data passed should not include 4 byte NAL unit prefixes.
     *
     * @param data Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void appendToNalUnit(byte[] data, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (nalData.length < nalLength + readLength) {
        nalData = Arrays.copyOf(nalData, (nalLength + readLength) * 2);
      }
      System.arraycopy(data, offset, nalData, nalLength, readLength);
      nalLength += readLength;
    }

    /**
     * Invoked to indicate that a NAL unit has ended.
     *
     * @param discardPadding The number of excess bytes that were passed to
     *     {@link #appendToNalUnit(byte[], int, int)}, which should be discarded.
     * @return True if the ended NAL unit is of the target type. False otherwise.
     */
    public boolean endNalUnit(int discardPadding) {
      if (!isFilling) {
        return false;
      }
      nalLength -= discardPadding;
      isFilling = false;
      isCompleted = true;
      return true;
    }

  }

}

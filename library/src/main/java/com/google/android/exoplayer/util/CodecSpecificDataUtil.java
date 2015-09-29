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
package com.google.android.exoplayer.util;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides static utility methods for manipulating various types of codec specific data.
 */
public final class CodecSpecificDataUtil {

  /**
   * Holds data parsed from a sequence parameter set NAL unit.
   */
  public static final class SpsData {

    public final int width;
    public final int height;
    public final float pixelWidthAspectRatio;

    public SpsData(int width, int height, float pixelWidthAspectRatio) {
      this.width = width;
      this.height = height;
      this.pixelWidthAspectRatio = pixelWidthAspectRatio;
    }

  }

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[] {
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
  };

  private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE = new int[] {
    0, 1, 2, 3, 4, 5, 6, 8
  };

  private static final String TAG = "CodecSpecificDataUtil";

  private CodecSpecificDataUtil() {}

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig The AudioSpecificConfig to parse.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(byte[] audioSpecificConfig) {
    int audioObjectType = (audioSpecificConfig[0] >> 3) & 0x1F;
    int byteOffset = audioObjectType == 5 || audioObjectType == 29 ? 1 : 0;
    int frequencyIndex = (audioSpecificConfig[byteOffset] & 0x7) << 1
        | ((audioSpecificConfig[byteOffset + 1] >> 7) & 0x1);
    Assertions.checkState(frequencyIndex < 13);
    int sampleRate = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
    int channelCount = (audioSpecificConfig[byteOffset + 1] >> 3) & 0xF;
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * Builds a simple AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioObjectType The audio object type.
   * @param sampleRateIndex The sample rate index.
   * @param channelConfig The channel configuration.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacAudioSpecificConfig(int audioObjectType, int sampleRateIndex,
      int channelConfig) {
    byte[] audioSpecificConfig = new byte[2];
    audioSpecificConfig[0] = (byte) ((audioObjectType << 3) & 0xF8 | (sampleRateIndex >> 1) & 0x07);
    audioSpecificConfig[1] = (byte) ((sampleRateIndex << 7) & 0x80 | (channelConfig << 3) & 0x78);
    return audioSpecificConfig;
  }

  /**
   * Builds a simple HE-AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param numChannels The number of channels.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacAudioSpecificConfig(int sampleRate, int numChannels) {
    int sampleRateIndex = -1;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    int channelConfig = -1;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
      if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
        channelConfig = i;
      }
    }
    // The full specification for AudioSpecificConfig is stated in ISO 14496-3 Section 1.6.2.1
    byte[] csd = new byte[2];
    csd[0] = (byte) ((2 /* AAC LC */ << 3) | (sampleRateIndex >> 1));
    csd[1] = (byte) (((sampleRateIndex & 0x1) << 7) | (channelConfig << 3));
    return csd;
  }

  /**
   * Constructs a NAL unit consisting of the NAL start code followed by the specified data.
   *
   * @param data An array containing the data that should follow the NAL start code.
   * @param offset The start offset into {@code data}.
   * @param length The number of bytes to copy from {@code data}
   * @return The constructed NAL unit.
   */
  public static byte[] buildNalUnit(byte[] data, int offset, int length) {
    byte[] nalUnit = new byte[length + NAL_START_CODE.length];
    System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.length);
    System.arraycopy(data, offset, nalUnit, NAL_START_CODE.length, length);
    return nalUnit;
  }

  /**
   * Splits an array of NAL units.
   * <p>
   * If the input consists of NAL start code delimited units, then the returned array consists of
   * the split NAL units, each of which is still prefixed with the NAL start code. For any other
   * input, null is returned.
   *
   * @param data An array of data.
   * @return The individual NAL units, or null if the input did not consist of NAL start code
   *     delimited units.
   */
  public static byte[][] splitNalUnits(byte[] data) {
    if (!isNalStartCode(data, 0)) {
      // data does not consist of NAL start code delimited units.
      return null;
    }
    List<Integer> starts = new ArrayList<>();
    int nalUnitIndex = 0;
    do {
      starts.add(nalUnitIndex);
      nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
    } while (nalUnitIndex != -1);
    byte[][] split = new byte[starts.size()][];
    for (int i = 0; i < starts.size(); i++) {
      int startIndex = starts.get(i);
      int endIndex = i < starts.size() - 1 ? starts.get(i + 1) : data.length;
      byte[] nal = new byte[endIndex - startIndex];
      System.arraycopy(data, startIndex, nal, 0, nal.length);
      split[i] = nal;
    }
    return split;
  }

  /**
   * Finds the next occurrence of the NAL start code from a given index.
   *
   * @param data The data in which to search.
   * @param index The first index to test.
   * @return The index of the first byte of the found start code, or -1.
   */
  private static int findNalStartCode(byte[] data, int index) {
    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Tests whether there exists a NAL start code at a given index.
   *
   * @param data The data.
   * @param index The index to test.
   * @return Whether there exists a start code that begins at {@code index}.
   */
  private static boolean isNalStartCode(byte[] data, int index) {
    if (data.length - index <= NAL_START_CODE.length) {
      return false;
    }
    for (int j = 0; j < NAL_START_CODE.length; j++) {
      if (data[index + j] != NAL_START_CODE[j]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Parses an SPS NAL unit.
   *
   * @param bitArray A {@link ParsableBitArray} containing the SPS data. The position must to set
   *     to the start of the data (i.e. the first bit of the profile_idc field).
   * @return A parsed representation of the SPS data.
   */
  public static SpsData parseSpsNalUnit(ParsableBitArray bitArray) {
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
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = bitArray.readBits(16);
          int sarHeight = bitArray.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
    }

    return new SpsData(frameWidth, frameHeight, pixelWidthHeightRatio);
  }

  private static void skipScalingList(ParsableBitArray bitArray, int size) {
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

}

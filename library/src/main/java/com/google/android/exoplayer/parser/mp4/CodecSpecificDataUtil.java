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
package com.google.android.exoplayer.parser.mp4;

import com.google.android.exoplayer.util.Assertions;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides static utility methods for manipulating various types of codec specific data.
 */
public class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[] {
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
  };

  private static final int SPS_NAL_UNIT_TYPE = 7;

  private CodecSpecificDataUtil() {}

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig
   * @return A pair consisting of the sample rate in Hz and the channel count.
   */
  public static Pair<Integer, Integer> parseAudioSpecificConfig(byte[] audioSpecificConfig) {
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
   * Builds a simple HE-AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param numChannels The number of channels
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAudioSpecificConfig(int sampleRate, int numChannels) {
    int sampleRateIndex = -1;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    // The full specification for AudioSpecificConfig is stated in ISO 14496-3 Section 1.6.2.1
    byte[] csd = new byte[2];
    csd[0] = (byte) ((2 /* AAC LC */ << 3) | (sampleRateIndex >> 1));
    csd[1] = (byte) (((sampleRateIndex & 0x1) << 7) | (numChannels << 3));
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
    List<Integer> starts = new ArrayList<Integer>();
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
   * @param spsNalUnit The NAL unit.
   * @return A pair consisting of AVC profile and level constants, as defined in
   *     {@link CodecProfileLevel}. Null if the input data was not an SPS NAL unit.
   */
  public static Pair<Integer, Integer> parseSpsNalUnit(byte[] spsNalUnit) {
    // SPS NAL unit:
    // - Start prefix (4 bytes)
    // - Forbidden zero bit (1 bit)
    // - NAL ref idx (2 bits)
    // - NAL unit type (5 bits)
    // - Profile idc (8 bits)
    // - Constraint bits (3 bits)
    // - Reserved bits (5 bits)
    // - Level idx (8 bits)
    if (isNalStartCode(spsNalUnit, 0) && spsNalUnit.length == 8
        && (spsNalUnit[5] & 0x1F) == SPS_NAL_UNIT_TYPE) {
      return Pair.create(parseAvcProfile(spsNalUnit), parseAvcLevel(spsNalUnit));
    }
    return null;
  }

  @SuppressLint("InlinedApi")
  private static int parseAvcProfile(byte[] data) {
    int profileIdc = data[6] & 0xFF;
    switch (profileIdc) {
      case 0x42:
        return CodecProfileLevel.AVCProfileBaseline;
      case 0x4d:
        return CodecProfileLevel.AVCProfileMain;
      case 0x58:
        return CodecProfileLevel.AVCProfileExtended;
      case 0x64:
        return CodecProfileLevel.AVCProfileHigh;
      case 0x6e:
        return CodecProfileLevel.AVCProfileHigh10;
      case 0x7a:
        return CodecProfileLevel.AVCProfileHigh422;
      case 0xf4:
        return CodecProfileLevel.AVCProfileHigh444;
      default:
        return 0;
    }
  }

  @SuppressLint("InlinedApi")
  private static int parseAvcLevel(byte[] data) {
    int levelIdc = data[8] & 0xFF;
    switch (levelIdc) {
      case 9:
        return CodecProfileLevel.AVCLevel1b;
      case 10:
        return CodecProfileLevel.AVCLevel1;
      case 11:
        return CodecProfileLevel.AVCLevel11;
      case 12:
        return CodecProfileLevel.AVCLevel12;
      case 13:
        return CodecProfileLevel.AVCLevel13;
      case 20:
        return CodecProfileLevel.AVCLevel2;
      case 21:
        return CodecProfileLevel.AVCLevel21;
      case 22:
        return CodecProfileLevel.AVCLevel22;
      case 30:
        return CodecProfileLevel.AVCLevel3;
      case 31:
        return CodecProfileLevel.AVCLevel31;
      case 32:
        return CodecProfileLevel.AVCLevel32;
      case 40:
        return CodecProfileLevel.AVCLevel4;
      case 41:
        return CodecProfileLevel.AVCLevel41;
      case 42:
        return CodecProfileLevel.AVCLevel42;
      case 50:
        return CodecProfileLevel.AVCLevel5;
      case 51:
        return CodecProfileLevel.AVCLevel51;
      default:
        return 0;
    }
  }

}

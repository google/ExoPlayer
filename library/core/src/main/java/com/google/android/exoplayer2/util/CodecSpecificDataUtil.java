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
package com.google.android.exoplayer2.util;

import android.util.Pair;
import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides static utility methods for manipulating various types of codec specific data.
 */
public final class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  private static final int AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY = 0xF;

  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[] {
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
  };

  private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;
  /**
   * In the channel configurations below, <A> indicates a single channel element; (A, B) indicates a
   * channel pair element; and [A] indicates a low-frequency effects element.
   * The speaker mapping short forms used are:
   * - FC: front center
   * - BC: back center
   * - FL/FR: front left/right
   * - FCL/FCR: front center left/right
   * - FTL/FTR: front top left/right
   * - SL/SR: back surround left/right
   * - BL/BR: back left/right
   * - LFE: low frequency effects
   */
  private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE =
      new int[] {
        0,
        1, /* mono: <FC> */
        2, /* stereo: (FL, FR) */
        3, /* 3.0: <FC>, (FL, FR) */
        4, /* 4.0: <FC>, (FL, FR), <BC> */
        5, /* 5.0 back: <FC>, (FL, FR), (SL, SR) */
        6, /* 5.1 back: <FC>, (FL, FR), (SL, SR), <BC>, [LFE] */
        8, /* 7.1 wide back: <FC>, (FCL, FCR), (FL, FR), (SL, SR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        7, /* 6.1: <FC>, (FL, FR), (SL, SR), <RC>, [LFE] */
        8, /* 7.1: <FC>, (FL, FR), (SL, SR), (BL, BR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        8, /* 7.1 top: <FC>, (FL, FR), (SL, SR), [LFE], (FTL, FTR) */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID
      };

  // Advanced Audio Coding Low-Complexity profile.
  private static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;
  // Spectral Band Replication.
  private static final int AUDIO_OBJECT_TYPE_SBR = 5;
  // Error Resilient Bit-Sliced Arithmetic Coding.
  private static final int AUDIO_OBJECT_TYPE_ER_BSAC = 22;
  // Parametric Stereo.
  private static final int AUDIO_OBJECT_TYPE_PS = 29;
  // Escape code for extended audio object types.
  private static final int AUDIO_OBJECT_TYPE_ESCAPE = 31;


  private static final int VISUAL_OBJECT_LAYER = 1;
  private static final int VISUAL_OBJECT_LAYER_START = 0x20;
  private static final int EXTENDED_PAR = 0x0F;
  private static final int RECTANGULAR = 0x00;
  private static final int FINE_GRANULARITY_SCALABLE = 0x12;

  private CodecSpecificDataUtil() {}

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig A byte array containing the AudioSpecificConfig to parse.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed as it's not supported.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(byte[] audioSpecificConfig)
      throws ParserException {
    return parseAacAudioSpecificConfig(new ParsableBitArray(audioSpecificConfig), false);
  }

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param bitArray A {@link ParsableBitArray} containing the AudioSpecificConfig to parse. The
   *     position is advanced to the end of the AudioSpecificConfig.
   * @param forceReadToEnd Whether the entire AudioSpecificConfig should be read. Required for
   *     knowing the length of the configuration payload.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed as it's not supported.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(ParsableBitArray bitArray,
      boolean forceReadToEnd) throws ParserException {
    int audioObjectType = getAacAudioObjectType(bitArray);
    int sampleRate = getAacSamplingFrequency(bitArray);
    int channelConfiguration = bitArray.readBits(4);
    if (audioObjectType == AUDIO_OBJECT_TYPE_SBR || audioObjectType == AUDIO_OBJECT_TYPE_PS) {
      // For an AAC bitstream using spectral band replication (SBR) or parametric stereo (PS) with
      // explicit signaling, we return the extension sampling frequency as the sample rate of the
      // content; this is identical to the sample rate of the decoded output but may differ from
      // the sample rate set above.
      // Use the extensionSamplingFrequencyIndex.
      sampleRate = getAacSamplingFrequency(bitArray);
      audioObjectType = getAacAudioObjectType(bitArray);
      if (audioObjectType == AUDIO_OBJECT_TYPE_ER_BSAC) {
        // Use the extensionChannelConfiguration.
        channelConfiguration = bitArray.readBits(4);
      }
    }

    if (forceReadToEnd) {
      switch (audioObjectType) {
        case 1:
        case 2:
        case 3:
        case 4:
        case 6:
        case 7:
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          parseGaSpecificConfig(bitArray, audioObjectType, channelConfiguration);
          break;
        default:
          throw new ParserException("Unsupported audio object type: " + audioObjectType);
      }
      switch (audioObjectType) {
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          int epConfig = bitArray.readBits(2);
          if (epConfig == 2 || epConfig == 3) {
            throw new ParserException("Unsupported epConfig: " + epConfig);
          }
          break;
      }
    }
    // For supported containers, bits_to_decode() is always 0.
    int channelCount = AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[channelConfiguration];
    Assertions.checkArgument(channelCount != AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID);
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * Builds a simple HE-AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param numChannels The number of channels.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacLcAudioSpecificConfig(int sampleRate, int numChannels) {
    int sampleRateIndex = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    int channelConfig = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
      if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
        channelConfig = i;
      }
    }
    if (sampleRate == C.INDEX_UNSET || channelConfig == C.INDEX_UNSET) {
      throw new IllegalArgumentException("Invalid sample rate or number of channels: "
          + sampleRate + ", " + numChannels);
    }
    return buildAacAudioSpecificConfig(AUDIO_OBJECT_TYPE_AAC_LC, sampleRateIndex, channelConfig);
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
    byte[] specificConfig = new byte[2];
    specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) & 0x07));
    specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfig << 3) & 0x78));
    return specificConfig;
  }

  /**
   * Parses an MPEG-4 Audio Stream Mux configuration, as defined in ISO/IEC14496-3
   *
   * @param audioStreamMuxConfig A byte array containing the MPEG-4 Audio Stream Mux configuration
   *                to parse.
   * @return A pair consisting of the number of subframes, sample rate in Hz and the channel count.
   * @throws ParserException If the MPEG-4 Audio Stream Mux configuration cannot be parsed as
   *                        it's not supported.
   */
  public static Pair<Integer, Pair<Integer, Integer>> parseMpeg4AudioStreamMuxConfig(byte[] audioStreamMuxConfig)
          throws ParserException {
    ParsableBitArray scdScratchBits = new ParsableBitArray(audioStreamMuxConfig);

    int audioMuxVersion = scdScratchBits.readBits(1);
    if (audioMuxVersion == 0) {
      int allStreamsSameTimeFraming = scdScratchBits.readBits(1);
      Assertions.checkArgument(allStreamsSameTimeFraming == 1);

      int numSubFrames = scdScratchBits.readBits(6);
      int numProgram = scdScratchBits.readBits(4);
      Assertions.checkArgument(numProgram == 0);
      int numLayer = scdScratchBits.readBits(3);
      Assertions.checkArgument(numLayer == 0);

      return Pair.create(numSubFrames, parseAacAudioSpecificConfig(scdScratchBits, true));
    }

    throw new ParserException("audio mux version wrong");
  }

  /**
   * Builds a H.264 configuration information, as defined in RFC 6814 and ISO/IEC 14496-10
   *
   * @param config A representation of an octet string that expresses the H.264 configuration information
   * @return The H.264 configuration information
   */
  public static List<byte[]> buildH264SpecificConfig(String config)
          throws IllegalArgumentException {
    List<byte[]> codecSpecificData = null;

    /* For H.264 MPEG4 Part15, the CodecPrivateData field must contain SPS and PPS in the following
      form, base16-encoded: [start code][SPS][start code][PPS], where [start code] is the following
      four bytes: 0x00, 0x00, 0x00, 0x01 */

    String[] paramSets = config.split(",");
    if (paramSets.length == 2) {
      codecSpecificData = new ArrayList<>();
      for (String s : paramSets) {
        if ((s != null) && (s.length() != 0)) {
          byte[] nal = Base64.decode(s, Base64.DEFAULT);
          if ((nal != null) && (nal.length != 0)) {
            codecSpecificData.add(buildNalUnit(nal, 0, nal.length));
          }
        }
      }
    }

    return codecSpecificData;
  }

  /**
   * Builds a H.265 configuration information, as defined in RFC 7798 and ISO/IEC 23008-2
   *
   * @param vps A representation of an octet string that expresses the H.265 Video Parameter Set
   * @param sps A representation of an octet string that expresses the H.265 Sequence Parameter Set
   * @param pps A representation of an octet string that expresses the H.265 Picture Parameter Set
   * @return The H.265 configuration information
   */
  public static List<byte[]> buildH265SpecificConfig(String vps, String sps, String pps)
      throws IllegalArgumentException {
    /* For H.265, the CodecPrivateData field must contain VPS, SPS and PPS in the following
      form, base16-encoded: [start code][VPS][start code][SPS][start code][PPS], where [start code]
      is the following four bytes: 0x00, 0x00, 0x00, 0x01 */
    byte[] vpsDec = Base64.decode(vps, Base64.DEFAULT);
    byte[] nalVps = buildNalUnit(vpsDec, 0, vpsDec.length);

    byte[] spsDec = Base64.decode(sps, Base64.DEFAULT);
    byte[] nalSps = buildNalUnit(spsDec, 0, spsDec.length);

    byte[] ppsDec = Base64.decode(pps, Base64.DEFAULT);
    byte[] nalPps = buildNalUnit(ppsDec, 0, ppsDec.length);

    byte[] codecSpecificData = new byte[nalVps.length + nalSps.length + nalPps.length];

    System.arraycopy(nalVps, 0, codecSpecificData, 0, nalVps.length);
    System.arraycopy(nalSps, 0, codecSpecificData, nalVps.length, nalSps.length);
    System.arraycopy(nalPps, 0, codecSpecificData, nalVps.length + nalSps.length,
        nalPps.length);

    return Collections.singletonList(codecSpecificData);
  }

  /**
   * Builds a H.265 configuration information, as defined in RFC 7798 and ISO/IEC 23008-2
   *
   * @param vps A byte array containing the H.265 Video Parameter Set
   * @param sps A byte array containing the H.265 Sequence Parameter Set
   * @param pps A byte array containing the H.265 Picture Parameter Set
   * @return The H.265 configuration information
   */
  public static List<byte[]> buildH265SpecificConfig(byte[] vps, byte[] sps, byte[] pps)
      throws IllegalArgumentException {
    /* For H.265, the CodecPrivateData field must contain VPS, SPS and PPS in the following
      form, base16-encoded: [start code][VPS][start code][SPS][start code][PPS], where [start code]
      is the following four bytes: 0x00, 0x00, 0x00, 0x01 */
    byte[] nalVps = buildNalUnit(vps, 0, vps.length);
    byte[] nalSps = buildNalUnit(sps, 0, sps.length);
    byte[] nalPps = buildNalUnit(pps, 0, pps.length);

    byte[] codecSpecificData = new byte[nalVps.length + nalSps.length + nalPps.length];

    System.arraycopy(nalVps, 0, codecSpecificData, 0, nalVps.length);
    System.arraycopy(nalSps, 0, codecSpecificData, nalVps.length, nalSps.length);
    System.arraycopy(nalPps, 0, codecSpecificData, nalVps.length + nalSps.length,
        nalPps.length);

    return Collections.singletonList(codecSpecificData);
  }

  /**
   * Parses an H.264 configuration information, as defined in ISO/IEC 14496-10
   *
   * @param videoSpecificConfig A byte array list containing the H.264 configuration information to parse.
   * @return A pair consisting of the pixel width aspect ratio and the dimensions.
   * @throws ParserException If the H.264 configuration information cannot be parsed as it's not
   *                         supported.
   */
  public static Pair<Float, Pair<Integer, Integer>> parseH264SpecificConfig(
      List<byte[]> videoSpecificConfig) throws ParserException {

    try {
      byte[] sps = videoSpecificConfig.get(0);
      NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(sps, 4, sps.length);
      return Pair.create(spsData.pixelWidthAspectRatio, Pair.create(spsData.width, spsData.height));

    } catch (IllegalStateException | NullPointerException ex) {
      throw new ParserException("H.264 configuration information malformed");
    }
  }

  /**
   * Parses an MPEG-4 Visual configuration information, as defined in ISO/IEC14496-2
   *
   * @param videoSpecificConfig A byte array containing the MPEG-4 Visual configuration
   *                information to parse.
   * @return A pair consisting of the width and the height.
   * @throws ParserException If the MPEG-4 Visual configuration information cannot be parsed as
   *                        it's not supported.
   */
  public static Pair<Integer, Integer> parseMpeg4VideoSpecificConfig(byte[] videoSpecificConfig)
          throws ParserException {
    int offset = 0;
    boolean foundVOL = false;

    ParsableBitArray scdScratchBits = new ParsableBitArray(videoSpecificConfig);
    ParsableByteArray scdScratchBytes = new ParsableByteArray(videoSpecificConfig);

    while (offset + 3 < videoSpecificConfig.length) {
      if (scdScratchBytes.readUnsignedInt24() != VISUAL_OBJECT_LAYER
              || (videoSpecificConfig[offset + 3] & 0xf0) != VISUAL_OBJECT_LAYER_START) {
        scdScratchBytes.setPosition(scdScratchBytes.getPosition() - 2);
        offset++;
        continue;
      }

      foundVOL = true;
      break;
    }

    if (!foundVOL) {
      throw new ParserException("MPEG-4 Visual configuration information malformed");
    }

    scdScratchBits.skipBits((offset + 4) * 8);
    scdScratchBits.skipBits(1);  // random_accessible_vol

    int videoObjectTypeIndication = scdScratchBits.readBits(8);
    Assertions.checkArgument(videoObjectTypeIndication != FINE_GRANULARITY_SCALABLE);

    if (scdScratchBits.readBit()) { // object_layer_identifier
      scdScratchBits.skipBits(4); // video_object_layer_verid
      scdScratchBits.skipBits(3); // video_object_layer_priority
    }

    int aspectRatioInfo = scdScratchBits.readBits(4);
    if (aspectRatioInfo == EXTENDED_PAR) {
      scdScratchBits.skipBits(8);  // par_width
      scdScratchBits.skipBits(8);  // par_height
    }

    if (scdScratchBits.readBit()) {  // vol_control_parameters
      scdScratchBits.skipBits(2);  // chroma_format
      scdScratchBits.skipBits(1);  // low_delay
      if (scdScratchBits.readBit()) {  // vbv_parameters
        throw new ParserException("Should not be here");
      }
    }

    int videoObjectLayerShape = scdScratchBits.readBits(2);
    Assertions.checkArgument(videoObjectLayerShape == RECTANGULAR);

    Assertions.checkArgument(scdScratchBits.readBit());  // marker_bit
    int vopTimeIncrementResolution= scdScratchBits.readBits(16);
    Assertions.checkArgument(scdScratchBits.readBit());  // marker_bit

    if (scdScratchBits.readBit()) {  // fixed_vop_rate
      Assertions.checkArgument(vopTimeIncrementResolution > 0);
      --vopTimeIncrementResolution;

      int numBits = 0;
      while (vopTimeIncrementResolution > 0) {
        ++numBits;
        vopTimeIncrementResolution >>= 1;
      }

      scdScratchBits.skipBits(numBits);  // fixed_vop_time_increment
    }

    Assertions.checkArgument(scdScratchBits.readBit());  // marker_bit
    int videoObjectLayerWidth = scdScratchBits.readBits(13);
    Assertions.checkArgument(scdScratchBits.readBit());  // marker_bit
    int videoObjectLayerHeight = scdScratchBits.readBits(13);
    Assertions.checkArgument(scdScratchBits.readBit());  // marker_bit

    scdScratchBits.skipBits(1); // interlaced

    return Pair.create(videoObjectLayerWidth, videoObjectLayerHeight);
  }

  /**
   * Builds an RFC 6381 AVC codec string using the provided parameters.
   *
   * @param profileIdc The encoding profile.
   * @param constraintsFlagsAndReservedZero2Bits The constraint flags followed by the reserved zero
   *     2 bits, all contained in the least significant byte of the integer.
   * @param levelIdc The encoding level.
   * @return An RFC 6381 AVC codec string built using the provided parameters.
   */
  public static String buildAvcCodecString(
      int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc) {
    return String.format(
        "avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc);
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
   *
   * <p>If the input consists of NAL start code delimited units, then the returned array consists of
   * the split NAL units, each of which is still prefixed with the NAL start code. For any other
   * input, null is returned.
   *
   * @param data An array of data.
   * @return The individual NAL units, or null if the input did not consist of NAL start code
   *     delimited units.
   */
  public static @Nullable byte[][] splitNalUnits(byte[] data) {
    if (!isNalStartCode(data, 0)) {
      // data does not consist of NAL start code delimited units.
      return null;
    }
    List<Integer> starts = new ArrayList<>();
    int nalUnitIndex = 0;
    do {
      starts.add(nalUnitIndex);
      nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
    } while (nalUnitIndex != C.INDEX_UNSET);
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
   * @return The index of the first byte of the found start code, or {@link C#INDEX_UNSET}.
   */
  private static int findNalStartCode(byte[] data, int index) {
    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
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
   * Returns the AAC audio object type as specified in 14496-3 (2005) Table 1.14.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The audio object type.
   */
  private static int getAacAudioObjectType(ParsableBitArray bitArray) {
    int audioObjectType = bitArray.readBits(5);
    if (audioObjectType == AUDIO_OBJECT_TYPE_ESCAPE) {
      audioObjectType = 32 + bitArray.readBits(6);
    }
    return audioObjectType;
  }

  /**
   * Returns the AAC sampling frequency (or extension sampling frequency) as specified in 14496-3
   * (2005) Table 1.13.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The sampling frequency.
   */
  private static int getAacSamplingFrequency(ParsableBitArray bitArray) {
    int samplingFrequency;
    int frequencyIndex = bitArray.readBits(4);
    if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
      samplingFrequency = bitArray.readBits(24);
    } else {
      Assertions.checkArgument(frequencyIndex < 13);
      samplingFrequency = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
    }
    return samplingFrequency;
  }

  private static void parseGaSpecificConfig(ParsableBitArray bitArray, int audioObjectType,
      int channelConfiguration) {
    bitArray.skipBits(1); // frameLengthFlag.
    boolean dependsOnCoreDecoder = bitArray.readBit();
    if (dependsOnCoreDecoder) {
      bitArray.skipBits(14); // coreCoderDelay.
    }
    boolean extensionFlag = bitArray.readBit();
    if (channelConfiguration == 0) {
      throw new UnsupportedOperationException(); // TODO: Implement programConfigElement();
    }
    if (audioObjectType == 6 || audioObjectType == 20) {
      bitArray.skipBits(3); // layerNr.
    }
    if (extensionFlag) {
      if (audioObjectType == 22) {
        bitArray.skipBits(16); // numOfSubFrame (5), layer_length(11).
      }
      if (audioObjectType == 17 || audioObjectType == 19 || audioObjectType == 20
          || audioObjectType == 23) {
        // aacSectionDataResilienceFlag, aacScalefactorDataResilienceFlag,
        // aacSpectralDataResilienceFlag.
        bitArray.skipBits(3);
      }
      bitArray.skipBits(1); // extensionFlag3.
    }
  }

}

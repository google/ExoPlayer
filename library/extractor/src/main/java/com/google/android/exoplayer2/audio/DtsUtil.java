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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Utility methods for parsing DTS frames. */
public final class DtsUtil {

  /** Holds sample format information for DTS audio. */
  public static final class DtsFormatInfo {
    /**
     * The mime type of the DTS bitstream. One of {@link MimeTypes#AUDIO_DTS}, {@link
     * MimeTypes#AUDIO_DTS_EXPRESS} or {@link MimeTypes#AUDIO_DTS_X}.
     */
    public final String mimeType;
    /** The audio sampling rate in Hertz. */
    public final int sampleRate;
    /** The number of channels */
    public final int channelCount;
    /** The size of a DTS frame(compressed). */
    public final int frameSize;
    /** Number of audio samples in a frame. */
    public final int sampleCount;
    /** The bitrate of compressed stream. */
    public final int bitrate;

    private DtsFormatInfo(
        String mimeType,
        int channelCount,
        int sampleRate,
        int frameSize,
        int sampleCount,
        int bitrate) {
      this.mimeType = mimeType;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.sampleCount = sampleCount;
      this.bitrate = bitrate;
    }
  }

  /**
   * Maximum rate for a DTS audio stream, in bytes per second.
   *
   * <p>DTS allows an 'open' bitrate, but we assume the maximum listed value: 1536 kbit/s.
   */
  public static final int DTS_MAX_RATE_BYTES_PER_SECOND = 1536 * 1000 / 8;
  /** Maximum rate for a DTS-HD audio stream, in bytes per second. */
  public static final int DTS_HD_MAX_RATE_BYTES_PER_SECOND = 18000 * 1000 / 8;
  /** DTS UHD Channel count. */
  public static final int DTS_UHD_CHANNEL_COUNT = 2;

  private static final int SYNC_VALUE_BE = 0x7FFE8001;
  private static final int SYNC_VALUE_14B_BE = 0x1FFFE800;
  private static final int SYNC_VALUE_LE = 0xFE7F0180;
  private static final int SYNC_VALUE_14B_LE = 0xFF1F00E8;
  private static final int SYNC_VALUE_EXTSS_BE = 0x64582025;
  private static final int SYNC_VALUE_EXTSS_LE = 0x58642520;
  private static final int SYNC_VALUE_UHD_FTOC_SYNC_BE = 0x40411BF2;
  private static final int SYNC_VALUE_UHD_FTOC_SYNC_LE = 0x4140F21B;
  private static final int SYNC_VALUE_UHD_FTOC_NONSYNC_BE = 0x71C442E8;
  private static final int SYNC_VALUE_UHD_FTOC_NONSYNC_LE = 0xC471E842;
  private static final byte FIRST_BYTE_BE = (byte) (SYNC_VALUE_BE >>> 24);
  private static final byte FIRST_BYTE_14B_BE = (byte) (SYNC_VALUE_14B_BE >>> 24);
  private static final byte FIRST_BYTE_LE = (byte) (SYNC_VALUE_LE >>> 24);
  private static final byte FIRST_BYTE_14B_LE = (byte) (SYNC_VALUE_14B_LE >>> 24);
  private static final byte FIRST_BYTE_EXTSS_BE = (byte) (SYNC_VALUE_EXTSS_BE >>> 24);
  private static final byte FIRST_BYTE_EXTSS_LE = (byte) (SYNC_VALUE_EXTSS_LE >>> 24);
  private static final byte FIRST_BYTE_UHD_FTOC_SYNC_BE =
      (byte) (SYNC_VALUE_UHD_FTOC_SYNC_BE >>> 24);
  private static final byte FIRST_BYTE_UHD_FTOC_SYNC_LE =
      (byte) (SYNC_VALUE_UHD_FTOC_SYNC_LE >>> 24);
  private static final byte FIRST_BYTE_UHD_FTOC_NONSYNC_BE =
      (byte) (SYNC_VALUE_UHD_FTOC_NONSYNC_BE >>> 24);
  private static final byte FIRST_BYTE_UHD_FTOC_NONSYNC_LE =
      (byte) (SYNC_VALUE_UHD_FTOC_NONSYNC_LE >>> 24);

  /** Maps AMODE to the number of channels. See ETSI TS 102 114 table 5.4. */
  private static final int[] CHANNELS_BY_AMODE =
      new int[] {1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 6, 6, 7, 8, 8};

  /** Maps SFREQ to the sampling frequency in Hz. See ETSI TS 102 114 table 5.5. */
  private static final int[] SAMPLE_RATE_BY_SFREQ =
      new int[] {
        -1, 8000, 16000, 32000, -1, -1, 11025, 22050, 44100, -1, -1, 12000, 24000, 48000, -1, -1
      };

  /** Maps RATE to 2 * bitrate in kbit/s. See ETSI TS 102 114 table 5.7. */
  private static final int[] TWICE_BITRATE_KBPS_BY_RATE =
      new int[] {
        64, 112, 128, 192, 224, 256, 384, 448, 512, 640, 768, 896, 1024, 1152, 1280, 1536, 1920,
        2048, 2304, 2560, 2688, 2816, 2823, 2944, 3072, 3840, 4096, 6144, 7680
      };

  /**
   * Maps nMaxSampleRate index to sampling frequency in Hz.
   * See ETSI TS 102 114 V1.6.1 (2019-08) table 7.9.
   * */
  private static final int[] SAMPLE_RATE_EXTSS =
      new int[] {
          8000, 16000, 32000, 64000, 128000, 22050, 44100, 88200, 176400, 352800, 12000, 24000,
          48000, 96000, 192000, 384000
      };

  /**
   * Maps nRefClockCode (Reference Clock Code) to reference clock rate.
   * See ETSI TS 102 114 V1.6.1 (2019-08) table 7.3.
   * */
  private static final int[] REF_CLOCK_TABLE =
      new int[] {
          32000, 44100, 48000, 0
      };

  /**
   * Index look-up table corresponding to the prefix code read from the bitstream.
   * See ETSI TS 103 491 V1.2.1 (2019-05) Table 5-2: ExtractVarLenBitFields.
   * */
  private static final int[] INDEX_TABLE =
      new int[] {
          0, 0, 0, 0, 1, 1, 2, 3
      };

  /**
   * Number of bits used for encode a field.
   * See ETSI TS 103 491 V1.2.1 (2019-05) Table 5-2: ExtractVarLenBitFields.
   * */
  private static final int[] BITS_USED =
      new int[] {
          1, 1, 1, 1, 2, 2, 3, 3
      };

  /**
   * Number of clock cycles in the base duration period.
   * See ETSI TS 103 491 V1.2.1 (2019-05) Table 6-13.
   * */
  private static final int[] BASE_DURATION =
      new int[] {
          512, 480, 384, 0
      };

  /**
   * Returns whether a given integer matches a DTS Core sync word.
   * Synchronization and storage modes are defined in ETSI TS 102 114 V1.1.1 (2002-08), Section 5.3.
   *
   * @param word An integer.
   * @return Whether a given integer matches a DTS Core sync word.
   */
  public static boolean isSyncWord(int word) {
    return word == SYNC_VALUE_BE
        || word == SYNC_VALUE_LE
        || word == SYNC_VALUE_14B_BE
        || word == SYNC_VALUE_14B_LE;
  }

  /**
   * Returns whether a given integer matches a DTS Extension Sub-stream sync word.
   * Synchronization and storage modes are defined in ETSI TS 102 114 V1.6.1 (2019-08), Section 7.5.
   *
   * @param word An integer.
   * @return Whether a given integer matches a DTS Extension Sub-stream sync word.
   */
  public static boolean isExtssSyncWord(int word) {
    return word == SYNC_VALUE_EXTSS_BE
        || word == SYNC_VALUE_EXTSS_LE;
  }

  /**
   * Returns whether a given integer matches a DTS UHD FTOC sync word.
   * Synchronization and storage modes are defined in ETSI TS 103 491 V1.2.1 (2019-05), Section 6.
   *
   * @param word An integer.
   * @return Whether a given integer matches a DTS UHD FTOC sync word.
   */
  public static boolean isUhdFtocSyncWord(int word) {
    return word == SYNC_VALUE_UHD_FTOC_SYNC_BE
        || word == SYNC_VALUE_UHD_FTOC_SYNC_LE;
  }

  /**
   * Returns whether a given integer matches a DTS UHD FTOC non-sync word.
   * Synchronization and storage modes are defined in ETSI TS 103 491 V1.2.1 (2019-05), Section 6.
   *
   * @param word An integer.
   * @return Whether a given integer matches a DTS UHD FTOC non-sync word.
   */
  public static boolean isUhdFtocNonSyncWord(int word) {
    return word == SYNC_VALUE_UHD_FTOC_NONSYNC_BE
        || word == SYNC_VALUE_UHD_FTOC_NONSYNC_LE;
  }

  /**
   * Returns the DTS format given {@code data} containing the DTS Core frame according to
   * ETSI TS 102 114 subsections 5.3/5.4.
   *
   * @param frame The DTS Core frame to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The DTS format parsed from data in the header.
   */
  public static Format parseDtsFormat(
      byte[] frame,
      @Nullable String trackId,
      @Nullable String language,
      @Nullable DrmInitData drmInitData) {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    frameBits.skipBits(32 + 1 + 5 + 1 + 7 + 14); // SYNC, FTYPE, SHORT, CPF, NBLKS, FSIZE
    int amode = frameBits.readBits(6);
    int channelCount = CHANNELS_BY_AMODE[amode];
    int sfreq = frameBits.readBits(4);
    int sampleRate = SAMPLE_RATE_BY_SFREQ[sfreq];
    int rate = frameBits.readBits(5);
    int bitrate =
        rate >= TWICE_BITRATE_KBPS_BY_RATE.length
            ? Format.NO_VALUE
            : TWICE_BITRATE_KBPS_BY_RATE[rate] * 1000 / 2;
    frameBits.skipBits(10); // MIX, DYNF, TIMEF, AUXF, HDCD, EXT_AUDIO_ID, EXT_AUDIO, ASPF
    channelCount += frameBits.readBits(2) > 0 ? 1 : 0; // LFF
    return new Format.Builder()
        .setId(trackId)
        .setSampleMimeType(MimeTypes.AUDIO_DTS)
        .setAverageBitrate(bitrate)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .setDrmInitData(drmInitData)
        .setLanguage(language)
        .build();
  }

  /**
   * Returns the number of audio samples represented by the given DTS Core frame.
   *
   * @param data The frame to parse.
   * @return The number of audio samples represented by the frame.
   */
  public static int parseDtsAudioSampleCount(byte[] data) {
    int nblks;
    switch (data[0]) {
      case FIRST_BYTE_LE:
        nblks = ((data[5] & 0x01) << 6) | ((data[4] & 0xFC) >> 2);
        break;
      case FIRST_BYTE_14B_LE:
        nblks = ((data[4] & 0x07) << 4) | ((data[7] & 0x3C) >> 2);
        break;
      case FIRST_BYTE_14B_BE:
        nblks = ((data[5] & 0x07) << 4) | ((data[6] & 0x3C) >> 2);
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        nblks = ((data[4] & 0x01) << 6) | ((data[5] & 0xFC) >> 2);
    }
    return (nblks + 1) * 32;
  }

  /**
   * Like {@link #parseDtsAudioSampleCount(byte[])} but reads from a {@link ByteBuffer}. The
   * buffer's position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseDtsAudioSampleCount(ByteBuffer buffer) {
    // See ETSI TS 102 114 subsection 5.4.1.
    int position = buffer.position();
    int nblks;
    switch (buffer.get(position)) {
      case FIRST_BYTE_LE:
        nblks = ((buffer.get(position + 5) & 0x01) << 6) | ((buffer.get(position + 4) & 0xFC) >> 2);
        break;
      case FIRST_BYTE_14B_LE:
        nblks = ((buffer.get(position + 4) & 0x07) << 4) | ((buffer.get(position + 7) & 0x3C) >> 2);
        break;
      case FIRST_BYTE_14B_BE:
        nblks = ((buffer.get(position + 5) & 0x07) << 4) | ((buffer.get(position + 6) & 0x3C) >> 2);
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        nblks = ((buffer.get(position + 4) & 0x01) << 6) | ((buffer.get(position + 5) & 0xFC) >> 2);
    }
    return (nblks + 1) * 32;
  }

  /**
   * Returns the size in bytes of the given DTS Core frame.
   *
   * @param data The frame to parse.
   * @return The frame's size in bytes.
   */
  public static int getDtsFrameSize(byte[] data) {
    int fsize;
    boolean uses14BitPerWord = false;
    switch (data[0]) {
      case FIRST_BYTE_14B_BE:
        fsize = (((data[6] & 0x03) << 12) | ((data[7] & 0xFF) << 4) | ((data[8] & 0x3C) >> 2)) + 1;
        uses14BitPerWord = true;
        break;
      case FIRST_BYTE_LE:
        fsize = (((data[4] & 0x03) << 12) | ((data[7] & 0xFF) << 4) | ((data[6] & 0xF0) >> 4)) + 1;
        break;
      case FIRST_BYTE_14B_LE:
        fsize = (((data[7] & 0x03) << 12) | ((data[6] & 0xFF) << 4) | ((data[9] & 0x3C) >> 2)) + 1;
        uses14BitPerWord = true;
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        fsize = (((data[5] & 0x03) << 12) | ((data[6] & 0xFF) << 4) | ((data[7] & 0xF0) >> 4)) + 1;
    }

    // If the frame is stored in 14-bit mode, adjust the frame size to reflect the actual byte size.
    return uses14BitPerWord ? fsize * 16 / 14 : fsize;
  }

  /**
   * Returns the DTS format given {@code data} containing the DTS-HD frame(containing only Extension
   * Sub-stream) according to ETSI TS 102 114 V1.6.1 (2019-08), Section 7.4/7.5.
   *
   * @param frame The DTS-HD frame(containing only Extension Sub-stream) to parse.
   * @return The DTS format parsed from data in the header.
   */
  public static DtsFormatInfo parseDtsHdFormat(byte[] frame) {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    frameBits.skipBits(32 + 8); // SYNC, userDefinedBits
    int extSSIndex = frameBits.readBits(2);
    int headerSizeType = frameBits.readBits(1);
    int nBits4Header, nBits4ExSSFsize;
    if (headerSizeType == 0){
      nBits4Header = 8;
      nBits4ExSSFsize = 16;
    }
    else{
      nBits4Header = 12;
      nBits4ExSSFsize = 20;
    }
    frameBits.skipBits(nBits4Header); // Extension substream header size
    int extFrameSize = frameBits.readBits(nBits4ExSSFsize) + 1; // Extension substream frame size

    int numAudioPresent = 1, numAssets = 1;
    int nRefClockCode = 0, nExSSFrameDurationCode = 0;
    int staticFieldsPresent = frameBits.readBits(1);
    if (staticFieldsPresent == 1) {
      nRefClockCode = frameBits.readBits(2);
      nExSSFrameDurationCode = 512 * (frameBits.readBits(3) + 1);
      if (frameBits.readBits(1) == 1) {
        frameBits.skipBits(32 + 4);
      }

      int[] nuActiveExSSMask = new int[8];
      frameBits.skipBits(3 + 3); // numAudioPresent, numAssets
      for (int nAuPr = 0; nAuPr < numAudioPresent; nAuPr++) {
        nuActiveExSSMask[nAuPr] = frameBits.readBits(extSSIndex + 1);
      }

      for (int nAuPr = 0; nAuPr < numAudioPresent; nAuPr++) {
        for (int nSS = 0; nSS < extSSIndex + 1; nSS++) {
          if (((nuActiveExSSMask[nAuPr] >> nSS) & 0x1) == 1) {
            frameBits.skipBits(8); // nuActiveAssetMask
          }
        }
      }

      if (frameBits.readBits(1) == 1) { // bMixMetadataEnbl
        frameBits.skipBits(2); // nuMixMetadataAdjLevel
        int bits4MixOutMask = (frameBits.readBits(2) + 1) << 2;
        int numMixOutConfigs = frameBits.readBits(2) + 1;
        for (int ns = 0; ns < numMixOutConfigs; ns++) {
          frameBits.skipBits(bits4MixOutMask); // nuMixOutChMask
        }
      }
    }
    for (int nAst = 0; nAst < numAssets; nAst++) {
      int bits4ExSSFsize = (headerSizeType == 0) ? 16 : 20;
      frameBits.skipBits(bits4ExSSFsize);
    }

    // Asset descriptor
    int sampleRate = 0, channelCount = 0;
    for (int nAst = 0; nAst < numAssets; nAst++) {
      frameBits.skipBits(9 + 3); // nuAssetDescriptFsize, nuAssetIndex
      if (staticFieldsPresent == 1) {
        if (frameBits.readBits(1) == 1) { // bAssetTypeDescrPresent
          frameBits.skipBits(4); // nuAssetTypeDescriptor
        }
        if (frameBits.readBits(1) == 1) { // bLanguageDescrPresent
          frameBits.skipBits(24); // LanguageDescriptor
        }
        if (frameBits.readBits(1) == 1) { // bInfoTextPresent
          int nuInfoTextByteSize = frameBits.readBits(10) + 1;
          frameBits.skipBits(nuInfoTextByteSize * 8); // InfoTextString
        }
        frameBits.skipBits(5); // nuBitResolution

        sampleRate = SAMPLE_RATE_EXTSS[frameBits.readBits(4)];
        channelCount = frameBits.readBits(8) + 1;
      }
    }

    // Number of audio sample in a compressed DTS frame
    int sampleCount = nExSSFrameDurationCode * (sampleRate / REF_CLOCK_TABLE[ nRefClockCode]);
    return new DtsFormatInfo(
        MimeTypes.AUDIO_DTS_EXPRESS,
        channelCount,
        sampleRate,
        extFrameSize,
        sampleCount,
        0);
  }

  /**
   * Returns the size of frame header in a DTS-HD frame by parsing a few bytes of data
   * (minimum 8 bytes) from the input bitstream(containing only Extension Sub-stream).
   *
   * @param frame The initial 8 bytes(minimum) of a DTS-HD frame(having only Extension Sub-stream).
   * @return Size of the DTS-HD frame header in bytes.
   */
  public static int parseDtsHdHeaderSize(byte[] frame) {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    int nuBits4Header;
    frameBits.skipBits( 32 + 8 + 2); // SYNC, UserDefinedBits, nExtSSIndex
    // Unpack the num of bits to be used to read header size
    int headerSizeType = frameBits.readBits(1);
    if (headerSizeType == 0){
      nuBits4Header = 8;
    }
    else{
      nuBits4Header = 12;
    }
    // Unpack the substream header size
    return frameBits.readBits(nuBits4Header) + 1;
  }

  /**
   * Returns the DTS format given {@code data} containing the DTS-UHD(Profile 2) frame according to
   * ETSI TS 103 491 V1.2.1 (2019-05), Section 6.4.3.
   *
   * @param frame The DTS-UHD frame to parse.
   * @return The DTS format parsed from data in the header.
   */
  public static DtsFormatInfo parseDtsUhdFormat(byte[] frame) throws ParserException {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    int sampleRate = 0;
    int sampleCount = 0;
    boolean bSyncFrameFlag = false;

    int nSyncWord = frameBits.readBits(32);
    if (nSyncWord == SYNC_VALUE_UHD_FTOC_SYNC_BE) {
      bSyncFrameFlag = true;
    }
    int[] ucTable1 = new int[] { 5, 8, 10, 12 };
    int nFTOCPayloadinBytes = extractVarLenBitFields(frameBits, ucTable1, true) + 1;

    if (bSyncFrameFlag) {
      // fullChannelBasedMixFlag, ETSI TS 103 491 V1.2.1, Section 6.4.6.1
      if(frameBits.readBits(1) != 1) {
        throw ParserException.createForMalformedContainer(
            /* message= */ "Only supports full channel mask-based audio presentation",
            /* cause= */ null);
      }

      int nBaseDurationIndex = frameBits.readBits(2);
      sampleCount = BASE_DURATION[nBaseDurationIndex] * (frameBits.readBits(3) + 1);
      int clockRateIndex = frameBits.readBits(2);
      int clockRateHertz = 0;
      switch (clockRateIndex) {
        case 0:
          clockRateHertz = 32000;
          break;
        case 1:
          clockRateHertz = 44100;
          break;
        case 2:
          clockRateHertz = 48000;
          break;
      }
      // Read time stamp information
      if (frameBits.readBits(1) == 1) {
        frameBits.skipBits(32 + 4);
      }
      int samplRateMultiplier = (1 << frameBits.readBits(2));
      sampleRate = clockRateHertz * samplRateMultiplier;
    }

    int chunkPayloadBytes = 0;
    int numOfMDChunks = bSyncFrameFlag ? 1 : 0; // Metadata chunks
    for (int nmdc = 0; nmdc < numOfMDChunks; nmdc++) {
      int[] ucTable2 = new int[] { 6, 9, 12, 15 };
      int nuMDChunkSize = extractVarLenBitFields(frameBits, ucTable2, true);
      if (nuMDChunkSize > 32767) {
        throw ParserException.createForMalformedContainer(
            /* message= */ "Unsupported metadata chunk size in DTS UHD header: " + nuMDChunkSize,
            /* cause= */ null);
      }
      chunkPayloadBytes += nuMDChunkSize;
    }

    // Ony one audio chunk is supported
    int numAudioChunks = 1;
    for (int nac = 0; nac < numAudioChunks; nac++) {
      int acID = 256, nuAudioChunkSize;
      // If bSyncFrameFlag is true the ACID will be present
      if (bSyncFrameFlag) {
        int[] ucTable3 = new int[] { 2, 4, 6, 8 };
        acID = extractVarLenBitFields(frameBits, ucTable3, true);
      }
      if (acID == 0) {
        nuAudioChunkSize = 0;
      } else {
        int[] ucTable4 = new int[] { 9, 11, 13, 16 };
        nuAudioChunkSize = extractVarLenBitFields(frameBits, ucTable4, true);
        if (nuAudioChunkSize > 65535) {
          throw ParserException.createForMalformedContainer(
              /* message= */ "Unsupported audio chunk size in DTS UHD header: " + nuAudioChunkSize,
              /* cause= */ null);
        }
      }
      chunkPayloadBytes += nuAudioChunkSize;
    }

    int frameSize = nFTOCPayloadinBytes + chunkPayloadBytes;
    return new DtsFormatInfo(
        MimeTypes.AUDIO_DTS_X,
        DTS_UHD_CHANNEL_COUNT, // TODO: Need to parse the actual channel count from bitstream
        sampleRate,
        frameSize,
        sampleCount,
        0);
  }

  /**
   * Returns the size of frame header in a DTS-UHD(Profile 2) frame by parsing a few bytes of data
   * (minimum 7 bytes) from the input bitstream.
   *
   * @param frame The initial 7 bytes(minimum) of a DTS-UHD frame.
   * @return Size of the DTS-UHD frame header in bytes.
   */
  public static int parseDtsUhdHeaderSize(byte[] frame) {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    frameBits.skipBits(32); // SYNC
    int []ucTable1 = new int[] { 5, 8, 10, 12 };
    return extractVarLenBitFields(frameBits, ucTable1, true) + 1;
  }

  // Helper function for the DTS UHD header parsing. Used to extract a field of variable length.
  // See ETSI TS 103 491 V1.2.1, Section 5.2.3.1
  private static int extractVarLenBitFields(
      ParsableBitArray frameBits, int[] ucTable, boolean extractAndAddFlag) {
    int code = frameBits.readBits(3);
    int currentPosition = frameBits.getPosition();
    int nIndex = INDEX_TABLE[code];
    int nUnusedBits = 3 - BITS_USED[code];
    frameBits.setPosition(currentPosition - nUnusedBits); // Rewind unused bits

    int nValue = 0;
    if (ucTable[nIndex] > 0) {
      if (extractAndAddFlag) {
        for (int n = 0; n < nIndex; n++) {
          nValue += (1 << ucTable[n]);
        }
      }
      nValue += frameBits.readBits(ucTable[nIndex]);
    }
    return nValue;
  }

  private static ParsableBitArray getNormalizedFrameHeader(byte[] frameHeader) {
    if (frameHeader[0] == FIRST_BYTE_BE
        || frameHeader[0] == FIRST_BYTE_EXTSS_BE
        || frameHeader[0] == FIRST_BYTE_UHD_FTOC_SYNC_BE
        || frameHeader[0] == FIRST_BYTE_UHD_FTOC_NONSYNC_BE) {
      // The frame is already 16-bit mode, big endian.
      return new ParsableBitArray(frameHeader);
    }
    // Data is not normalized, but we don't want to modify frameHeader.
    frameHeader = Arrays.copyOf(frameHeader, frameHeader.length);
    if (isLittleEndianFrameHeader(frameHeader)) {
      // Change endianness.
      for (int i = 0; i < frameHeader.length - 1; i += 2) {
        byte temp = frameHeader[i];
        frameHeader[i] = frameHeader[i + 1];
        frameHeader[i + 1] = temp;
      }
    }
    ParsableBitArray frameBits = new ParsableBitArray(frameHeader);
    if (frameHeader[0] == (byte) (SYNC_VALUE_14B_BE >> 24)) {
      // Discard the 2 most significant bits of each 16 bit word.
      ParsableBitArray scratchBits = new ParsableBitArray(frameHeader);
      while (scratchBits.bitsLeft() >= 16) {
        scratchBits.skipBits(2);
        frameBits.putInt(scratchBits.readBits(14), 14);
      }
    }
    frameBits.reset(frameHeader);
    return frameBits;
  }

  private static boolean isLittleEndianFrameHeader(byte[] frameHeader) {
    return frameHeader[0] == FIRST_BYTE_LE
        || frameHeader[0] == FIRST_BYTE_14B_LE
        || frameHeader[0] == FIRST_BYTE_EXTSS_LE
        || frameHeader[0] == FIRST_BYTE_UHD_FTOC_SYNC_LE
        || frameHeader[0] == FIRST_BYTE_UHD_FTOC_NONSYNC_LE;
  }

  private DtsUtil() {}
}

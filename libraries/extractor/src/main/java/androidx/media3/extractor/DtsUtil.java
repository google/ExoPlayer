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
package androidx.media3.extractor;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Utility methods for parsing DTS frames. */
@UnstableApi
public final class DtsUtil {

  /** Information parsed from a DTS frame header. */
  public static final class DtsHeader {
    /** The mime type of the DTS bitstream. */
    public final @DtsAudioMimeType String mimeType;

    /** The audio sampling rate in Hertz, or {@link C#RATE_UNSET_INT} if unknown. */
    public final int sampleRate;

    /** The number of channels, or {@link C#LENGTH_UNSET} if unknown. */
    public final int channelCount;

    /** The size of the DTS frame (compressed), in bytes. */
    public final int frameSize;

    /** The duration of the DTS frame in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long frameDurationUs;

    /** The bitrate of compressed stream. */
    public final int bitrate;

    private DtsHeader(
        String mimeType,
        int channelCount,
        int sampleRate,
        int frameSize,
        long frameDurationUs,
        int bitrate) {
      this.mimeType = mimeType;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.frameDurationUs = frameDurationUs;
      this.bitrate = bitrate;
    }
  }

  /**
   * The possible MIME types for DTS that can be used.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link MimeTypes#AUDIO_DTS}
   *   <li>{@link MimeTypes#AUDIO_DTS_EXPRESS}
   *   <li>{@link MimeTypes#AUDIO_DTS_X}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @StringDef({MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_X})
  public @interface DtsAudioMimeType {}

  /**
   * Frame types for a DTS stream.
   *
   * <p>One of:
   *
   * <ul>
   *   <li>{@link #FRAME_TYPE_UNKNOWN}
   *   <li>{@link #FRAME_TYPE_CORE}
   *   <li>{@link #FRAME_TYPE_EXTENSION_SUBSTREAM}
   *   <li>{@link #FRAME_TYPE_UHD_SYNC}
   *   <li>{@link #FRAME_TYPE_UHD_NON_SYNC}
   * </ul>
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FRAME_TYPE_UNKNOWN,
    FRAME_TYPE_CORE,
    FRAME_TYPE_EXTENSION_SUBSTREAM,
    FRAME_TYPE_UHD_SYNC,
    FRAME_TYPE_UHD_NON_SYNC
  })
  public @interface FrameType {}

  /** Represents a DTS frame for which type is unknown. */
  public static final int FRAME_TYPE_UNKNOWN = 0;

  /** Represents a DTS core frame. */
  public static final int FRAME_TYPE_CORE = 1;

  /** Represents a DTS extension substream frame. */
  public static final int FRAME_TYPE_EXTENSION_SUBSTREAM = 2;

  /** Represents a DTS UHD sync frame. */
  public static final int FRAME_TYPE_UHD_SYNC = 3;

  /** Represents a DTS UHD non-sync frame. */
  public static final int FRAME_TYPE_UHD_NON_SYNC = 4;

  /**
   * Maximum rate for a DTS audio stream, in bytes per second.
   *
   * <p>DTS allows an 'open' bitrate, but we assume the maximum listed value: 1536 kbit/s.
   */
  public static final int DTS_MAX_RATE_BYTES_PER_SECOND = 1536 * 1000 / 8;

  /** Maximum rate for a DTS-HD audio stream, in bytes per second. */
  public static final int DTS_HD_MAX_RATE_BYTES_PER_SECOND = 18000 * 1000 / 8;

  /** Maximum bit-rate for a DTS Express audio stream, in bits per second. */
  public static final int DTS_EXPRESS_MAX_RATE_BITS_PER_SECOND = 768000;

  /**
   * DTS Core Syncword (in different Endianness). See ETSI TS 102 114 V1.6.1 (2019-08), Section 5.3.
   */
  private static final int SYNC_VALUE_BE = 0x7FFE8001;

  private static final int SYNC_VALUE_14B_BE = 0x1FFFE800;
  private static final int SYNC_VALUE_LE = 0xFE7F0180;
  private static final int SYNC_VALUE_14B_LE = 0xFF1F00E8;

  /**
   * DTS Extension Substream Syncword (in different Endianness). See ETSI TS 102 114 (V1.6.1)
   * Section 7.4.1.
   */
  private static final int SYNC_VALUE_EXTSS_BE = 0x64582025;

  private static final int SYNC_VALUE_EXTSS_LE = 0x25205864;

  /**
   * DTS UHD FTOC Sync words (in different Endianness). See ETSI TS 103 491 (V1.2.1) Section
   * 6.4.4.1.
   */
  private static final int SYNC_VALUE_UHD_FTOC_SYNC_BE = 0x40411BF2;

  private static final int SYNC_VALUE_UHD_FTOC_SYNC_LE = 0xF21B4140;
  private static final int SYNC_VALUE_UHD_FTOC_NONSYNC_BE = 0x71C442E8;
  private static final int SYNC_VALUE_UHD_FTOC_NONSYNC_LE = 0xE842C471;

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

  /** Maps AMODE to the number of channels. See ETSI TS 102 114 table 5-4. */
  private static final int[] CHANNELS_BY_AMODE =
      new int[] {1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 6, 6, 7, 8, 8};

  /** Maps SFREQ to the sampling frequency in Hz. See ETSI TS 102 114 table 5-5. */
  private static final int[] SAMPLE_RATE_BY_SFREQ =
      new int[] {
        -1, 8_000, 16_000, 32_000, -1, -1, 11_025, 22_050, 44_100, -1, -1, 12_000, 24_000, 48_000,
        -1, -1
      };

  /** Maps RATE to 2 * bitrate in kbit/s. See ETSI TS 102 114 table 5-7. */
  private static final int[] TWICE_BITRATE_KBPS_BY_RATE =
      new int[] {
        64, 112, 128, 192, 224, 256, 384, 448, 512, 640, 768, 896, 1_024, 1_152, 1_280, 1_536,
        1_920, 2_048, 2_304, 2_560, 2_688, 2_816, 2_823, 2_944, 3_072, 3_840, 4_096, 6_144, 7_680
      };

  /**
   * Maps MaxSampleRate index to sampling frequency in Hz. See ETSI TS 102 114 V1.6.1 (2019-08)
   * Table 7-9.
   */
  private static final int[] SAMPLE_RATE_BY_INDEX =
      new int[] {
        8_000, 16_000, 32_000, 64_000, 128_000, 22_050, 44_100, 88_200, 176_400, 352_800, 12_000,
        24_000, 48_000, 96_000, 192_000, 384_000
      };

  /**
   * Payload length table for DTS UHD FTOC messages. See ETSI TS 103 491 V1.2.1 (2019-05), Section
   * 6.4.3.
   */
  private static final int[] UHD_FTOC_PAYLOAD_LENGTH_TABLE = new int[] {5, 8, 10, 12};

  /** Metadata chunk size length table for DTS UHD. See ETSI TS 103 491 V1.2.1, Table 6-20. */
  private static final int[] UHD_METADATA_CHUNK_SIZE_LENGTH_TABLE = new int[] {6, 9, 12, 15};

  /** Audio chunk ID length table for DTS UHD. See ETSI TS 103 491 V1.2.1, Section 6.4.14.4. */
  private static final int[] UHD_AUDIO_CHUNK_ID_LENGTH_TABLE = new int[] {2, 4, 6, 8};

  /** Audio chunk size length table for DTS UHD. See ETSI TS 103 491 V1.2.1, Section 6.4.14.4. */
  private static final int[] UHD_AUDIO_CHUNK_SIZE_LENGTH_TABLE = new int[] {9, 11, 13, 16};

  /** Header size length table for DTS UHD. See ETSI TS 103 491 V1.2.1 (2019-05), Section 6.4.3. */
  private static final int[] UHD_HEADER_SIZE_LENGTH_TABLE = new int[] {5, 8, 10, 12};

  /**
   * Returns the {@link FrameType} if {@code word} is a DTS sync word, otherwise {@link
   * #FRAME_TYPE_UNKNOWN}.
   */
  public static @FrameType int getFrameType(int word) {
    if (word == SYNC_VALUE_BE
        || word == SYNC_VALUE_LE
        || word == SYNC_VALUE_14B_BE
        || word == SYNC_VALUE_14B_LE) {
      return FRAME_TYPE_CORE;
    } else if (word == SYNC_VALUE_EXTSS_BE || word == SYNC_VALUE_EXTSS_LE) {
      return FRAME_TYPE_EXTENSION_SUBSTREAM;
    } else if (word == SYNC_VALUE_UHD_FTOC_SYNC_BE || word == SYNC_VALUE_UHD_FTOC_SYNC_LE) {
      return FRAME_TYPE_UHD_SYNC;
    } else if (word == SYNC_VALUE_UHD_FTOC_NONSYNC_BE || word == SYNC_VALUE_UHD_FTOC_NONSYNC_LE) {
      return FRAME_TYPE_UHD_NON_SYNC;
    }
    return FRAME_TYPE_UNKNOWN;
  }

  /**
   * Returns the DTS format given {@code data} containing the DTS Core frame according to ETSI TS
   * 102 114 V1.6.1 (2019-08) subsections 5.3/5.4.
   *
   * @param frame The DTS Core frame to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param roleFlags The role flags to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The DTS format parsed from data in the header.
   */
  public static Format parseDtsFormat(
      byte[] frame,
      @Nullable String trackId,
      @Nullable String language,
      @C.RoleFlags int roleFlags,
      @Nullable DrmInitData drmInitData) {
    ParsableBitArray frameBits = getNormalizedFrame(frame);
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
        .setRoleFlags(roleFlags)
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
    if ((buffer.getInt(0) == SYNC_VALUE_UHD_FTOC_SYNC_LE)
        || (buffer.getInt(0) == SYNC_VALUE_UHD_FTOC_NONSYNC_LE)) {
      // Check for DTS:X Profile 2 sync or non sync word and return 1024 if found. This is the only
      // audio sample count that is used by DTS:X Streaming Encoder.
      return 1024;
    } else if (buffer.getInt(0) == SYNC_VALUE_EXTSS_LE) {
      // Check for DTS Express sync word and return 4096 if found. This is the only audio sample
      // count that is used by DTS Streaming Encoder.
      return 4096;
    }

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
   * Parses the {@link DtsHeader} data from the extension substream header of a DTS-HD frame
   * according to ETSI TS 102 114 V1.6.1 (2019-08), Section 7.5.2.
   *
   * @param header The DTS-HD extension substream header to parse.
   * @return The {@link DtsHeader} data extracted from the header.
   */
  public static DtsHeader parseDtsHdHeader(byte[] header) throws ParserException {
    ParsableBitArray headerBits = getNormalizedFrame(header);
    headerBits.skipBits(32 + 8); // SYNCEXTSSH, UserDefinedBits

    int extensionSubstreamIndex = headerBits.readBits(2); // nExtSSIndex
    int headerSizeInBits; // nuBits4Header
    int extensionSubstreamFrameSizeBits; // nuBits4ExSSFsize
    if (!headerBits.readBit()) { // bHeaderSizeType
      headerSizeInBits = 8;
      extensionSubstreamFrameSizeBits = 16;
    } else {
      headerSizeInBits = 12;
      extensionSubstreamFrameSizeBits = 20;
    }
    headerBits.skipBits(headerSizeInBits); // nuExtSSHeaderSize
    int extensionSubstreamFrameSize =
        headerBits.readBits(extensionSubstreamFrameSizeBits) + 1; // nuExtSSFsize

    int assetsCount; // nuNumAssets
    int referenceClockCode; // nuRefClockCode
    int extensionSubstreamFrameDurationCode; // nuExSSFrameDurationCode

    boolean staticFieldsPresent = headerBits.readBit(); // bStaticFieldsPresent
    if (staticFieldsPresent) {
      referenceClockCode = headerBits.readBits(2);
      extensionSubstreamFrameDurationCode = 512 * (headerBits.readBits(3) + 1);

      if (headerBits.readBit()) { // bTimeStampFlag
        headerBits.skipBits(32 + 4); // nuTimeStamp, nLSB
      }

      int audioPresentationsCount = headerBits.readBits(3) + 1; // nuNumAudioPresnt
      assetsCount = headerBits.readBits(3) + 1;
      if (audioPresentationsCount != 1 || assetsCount != 1) {
        throw ParserException.createForUnsupportedContainerFeature(
            /* message= */ "Multiple audio presentations or assets not supported");
      }

      // We've already asserted audioPresentationsCount = 1.
      int activeExtensionSubstreamMask =
          headerBits.readBits(extensionSubstreamIndex + 1); // nuActiveExSSMask

      for (int i = 0; i < extensionSubstreamIndex + 1; i++) {
        if (((activeExtensionSubstreamMask >> i) & 0x1) == 1) {
          headerBits.skipBits(8); // nuActiveAssetMask
        }
      }

      if (headerBits.readBit()) { // bMixMetadataEnbl
        headerBits.skipBits(2); // nuMixMetadataAdjLevel
        int mixerOutputMaskBits = (headerBits.readBits(2) + 1) << 2; // nuBits4MixOutMask
        int mixerOutputConfigurationCount = headerBits.readBits(2) + 1; // nuNumMixOutConfigs
        // Output Mixing Configuration Loop
        for (int i = 0; i < mixerOutputConfigurationCount; i++) {
          headerBits.skipBits(mixerOutputMaskBits); // nuMixOutChMask
        }
      }
    } else {
      // Assignments below are placeholders and will never be used as they are only relevant when
      // staticFieldsPresent == true. Initialised here to keep the compiler happy.
      referenceClockCode = C.INDEX_UNSET;
      extensionSubstreamFrameDurationCode = 0;
    }

    // We've already asserted assetsCount = 1.
    headerBits.skipBits(extensionSubstreamFrameSizeBits); // nuAssetFsize
    int sampleRate = C.RATE_UNSET_INT;
    int channelCount = C.LENGTH_UNSET; // nuTotalNumChs

    // Asset descriptor, see ETSI TS 102 114 V1.6.1 (2019-08) Table 7-5.
    headerBits.skipBits(9 + 3); // nuAssetDescriptFsize, nuAssetIndex
    if (staticFieldsPresent) {
      if (headerBits.readBit()) { // bAssetTypeDescrPresent
        headerBits.skipBits(4); // nuAssetTypeDescriptor
      }
      if (headerBits.readBit()) { // bLanguageDescrPresent
        headerBits.skipBits(24); // LanguageDescriptor
      }
      if (headerBits.readBit()) { // bInfoTextPresent
        int infoTextByteSize = headerBits.readBits(10) + 1; // nuInfoTextByteSize
        headerBits.skipBytes(infoTextByteSize); // InfoTextString
      }
      headerBits.skipBits(5); // nuBitResolution
      sampleRate = SAMPLE_RATE_BY_INDEX[headerBits.readBits(4)]; // nuMaxSampleRate
      channelCount = headerBits.readBits(8) + 1;
      // Done reading necessary bits, ignoring the rest.
    }

    long frameDurationUs = C.TIME_UNSET;
    if (staticFieldsPresent) {
      int referenceClockFrequency;
      //  ETSI TS 102 114 V1.6.1 (2019-08) Table 7-3.
      switch (referenceClockCode) {
        case 0:
          referenceClockFrequency = 32_000;
          break;
        case 1:
          referenceClockFrequency = 44_100;
          break;
        case 2:
          referenceClockFrequency = 48_000;
          break;
        default:
          throw ParserException.createForMalformedContainer(
              /* message= */ "Unsupported reference clock code in DTS HD header: "
                  + referenceClockCode,
              /* cause= */ null);
      }
      frameDurationUs =
          Util.scaleLargeTimestamp(
              extensionSubstreamFrameDurationCode, C.MICROS_PER_SECOND, referenceClockFrequency);
    }
    return new DtsHeader(
        MimeTypes.AUDIO_DTS_EXPRESS,
        channelCount,
        sampleRate,
        extensionSubstreamFrameSize,
        frameDurationUs,
        /* bitrate= */ 0);
  }

  /**
   * Returns the size of the extension substream header in a DTS-HD frame according to ETSI TS 102
   * 114 V1.6.1 (2019-08), Section 7.5.2.
   *
   * @param headerPrefix A byte array containing at least the first 55 bits of a DTS-HD frame.
   * @return Size of the DTS-HD frame header in bytes.
   */
  public static int parseDtsHdHeaderSize(byte[] headerPrefix) {
    ParsableBitArray headerPrefixBits = getNormalizedFrame(headerPrefix);
    headerPrefixBits.skipBits(32 + 8 + 2); // SYNCEXTSSH, UserDefinedBits, nExtSSIndex
    // Unpack the num of bits to be used to read header size
    int headerBits = headerPrefixBits.readBit() ? 12 : 8; // bHeaderSizeType
    // Unpack the substream header size
    return headerPrefixBits.readBits(headerBits) + 1; // nuExtSSHeaderSize
  }

  /**
   * Parses the {@link DtsHeader} data from the headers of a DTS-UHD(Profile 2) frame according to
   * ETSI TS 103 491 V1.2.1 (2019-05), Section 6.4.3.
   *
   * @param header The DTS-UHD header to parse.
   * @param uhdAudioChunkId An {@link AtomicInteger} containing the last read UHD audio chunk ID
   *     from a synchronized frame, or zero if unset. This parameter is both an input and output
   *     parameter. In synchronized frames, the input value is not used; instead, the parameter is
   *     set to the current UHD audio chunk ID, which becomes the output value. For non-synchronized
   *     frames, it is used without any modification.
   * @return The {@link DtsHeader} data extracted from the header.
   */
  public static DtsHeader parseDtsUhdHeader(byte[] header, AtomicInteger uhdAudioChunkId)
      throws ParserException {
    ParsableBitArray headerBits = getNormalizedFrame(header);
    int syncWord = headerBits.readBits(32);
    boolean syncFrameFlag = syncWord == SYNC_VALUE_UHD_FTOC_SYNC_BE;

    int ftocPayloadInBytes =
        parseUnsignedVarInt(
                headerBits, UHD_FTOC_PAYLOAD_LENGTH_TABLE, /* extractAndAddFlag= */ true)
            + 1;

    // ETSI TS 103 491 V1.2.1, Section 6.4.5.
    int sampleRate = C.RATE_UNSET_INT; // m_unAudioSamplRate
    long frameDurationUs = C.TIME_UNSET;
    if (syncFrameFlag) {
      // ETSI TS 103 491 V1.2.1, Section 6.4.6.1.
      if (!headerBits.readBit()) { // m_bFullChannelBasedMixFlag
        throw ParserException.createForUnsupportedContainerFeature(
            /* message= */ "Only supports full channel mask-based audio presentation");
      }

      // ETSI TS 103 491 V1.2.1, Section 6.4.6.2.
      checkCrc(header, ftocPayloadInBytes);

      int baseDurationIndex = headerBits.readBits(2);
      int baseDuration; // m_unBaseDuration
      // ETSI TS 103 491 V1.2.1 (2019-05) Table 6-13.
      switch (baseDurationIndex) {
        case 0:
          baseDuration = 512;
          break;
        case 1:
          baseDuration = 480;
          break;
        case 2:
          baseDuration = 384;
          break;
        default:
          throw ParserException.createForMalformedContainer(
              /* message= */ "Unsupported base duration index in DTS UHD header: "
                  + baseDurationIndex,
              /* cause= */ null);
      }
      int frameDurationInClockPeriods =
          baseDuration * (headerBits.readBits(3) + 1); // m_unFrameDuration
      int clockRateIndex = headerBits.readBits(2);
      int clockRateHertz; // m_unClockRateInHz
      switch (clockRateIndex) {
        case 0:
          clockRateHertz = 32_000;
          break;
        case 1:
          clockRateHertz = 44_100;
          break;
        case 2:
          clockRateHertz = 48_000;
          break;
        default:
          throw ParserException.createForMalformedContainer(
              /* message= */ "Unsupported clock rate index in DTS UHD header: " + clockRateIndex,
              /* cause= */ null);
      }
      // Skip time stamp information if present, see section 5.2.3.2.
      if (headerBits.readBit()) { // m_bParamPresent
        // m_bUpdateFlag == true as m_bSyncFramePredefValueExists is set to false in the encoder.
        headerBits.skipBits(32 + 4); // m_TimeStamp
      }
      int sampleRateMultiplier = (1 << headerBits.readBits(2));
      sampleRate = clockRateHertz * sampleRateMultiplier;
      frameDurationUs =
          Util.scaleLargeTimestamp(
              frameDurationInClockPeriods, C.MICROS_PER_SECOND, clockRateHertz);
    }

    // ETSI TS 103 491 V1.2.1, Table 6-20.
    // m_bFullChannelBasedMixFlag == true as we throw unsupported container feature otherwise.
    int chunkPayloadBytes = 0;
    int numOfMetadataChunks = syncFrameFlag ? 1 : 0; // Metadata chunks
    for (int i = 0; i < numOfMetadataChunks; i++) {
      int metadataChunkSize =
          parseUnsignedVarInt(
              headerBits, UHD_METADATA_CHUNK_SIZE_LENGTH_TABLE, /* extractAndAddFlag= */ true);
      chunkPayloadBytes += metadataChunkSize;
    }

    // See ETSI TS 103 491 V1.2.1, Section 6.4.14.4.
    // m_bFullChannelBasedMixFlag == true as we throw unsupported container feature otherwise.
    int numAudioChunks = 1;
    for (int i = 0; i < numAudioChunks; i++) {
      // If syncFrameFlag is true the audio chunk ID will be present.
      if (syncFrameFlag) {
        uhdAudioChunkId.set(
            parseUnsignedVarInt(
                headerBits, UHD_AUDIO_CHUNK_ID_LENGTH_TABLE, /* extractAndAddFlag= */ true));
      }
      int audioChunkSize =
          uhdAudioChunkId.get() != 0
              ? parseUnsignedVarInt(
                  headerBits, UHD_AUDIO_CHUNK_SIZE_LENGTH_TABLE, /* extractAndAddFlag= */ true)
              : 0;
      chunkPayloadBytes += audioChunkSize;
    }

    int frameSize = ftocPayloadInBytes + chunkPayloadBytes;
    return new DtsHeader(
        MimeTypes.AUDIO_DTS_X,
        // To determine the actual number of channels from a bit stream, we need to read the
        // metadata chunk bytes. If defining a constant channel count causes problems, we can
        // consider adding additional parsing logic for UHD frames.
        // For now, using the estimated number of channels for DTS UHD bitstreams as 2.
        /* channelCount= */ 2,
        sampleRate,
        frameSize,
        frameDurationUs,
        /* bitrate= */ 0);
  }

  /**
   * Returns the size of frame header in a DTS-UHD(Profile 2) frame according to ETSI TS 103 491
   * V1.2.1 (2019-05), Section 6.4.3.
   *
   * @param headerPrefix A byte array containing at least the first 47 bits of a DTS-UHD frame.
   * @return Size of the DTS-UHD frame header in bytes.
   */
  public static int parseDtsUhdHeaderSize(byte[] headerPrefix) {
    ParsableBitArray headerPrefixBits = getNormalizedFrame(headerPrefix);
    headerPrefixBits.skipBits(32); // SYNC
    return parseUnsignedVarInt(
            headerPrefixBits, UHD_HEADER_SIZE_LENGTH_TABLE, /* extractAndAddFlag= */ true)
        + 1;
  }

  /**
   * Check if calculated and extracted CRC-16 words match. See ETSI TS 103 491 V1.2.1, Table 6-8.
   */
  private static void checkCrc(byte[] frame, int sizeInBytes) throws ParserException {
    int initialValue = 0xFFFF;
    int extractedCrc =
        (((frame[sizeInBytes - 2] << 8) & initialValue) | (frame[sizeInBytes - 1] & 0xFF));
    int calculatedCrc = Util.crc16(frame, /* start= */ 0, /* end= */ sizeInBytes - 2, initialValue);
    if (extractedCrc != calculatedCrc) {
      throw ParserException.createForMalformedContainer(
          /* message= */ "CRC check failed", /* cause= */ null);
    }
  }

  /**
   * Helper function for the DTS UHD header parsing. Used to extract a field of variable length. See
   * ETSI TS 103 491 V1.2.1, Section 5.2.3.1.
   */
  private static int parseUnsignedVarInt(
      ParsableBitArray frameBits, int[] lengths, boolean extractAndAddFlag) {
    int index = 0;
    for (int i = 0; i < 3; i++) {
      if (frameBits.readBit()) {
        index++;
      } else {
        break;
      }
    }

    int value = 0;
    if (extractAndAddFlag) {
      for (int i = 0; i < index; i++) {
        value += (1 << lengths[i]);
      }
    }
    return value + frameBits.readBits(lengths[index]);
  }

  private static ParsableBitArray getNormalizedFrame(byte[] frame) {
    if (frame[0] == FIRST_BYTE_BE
        || frame[0] == FIRST_BYTE_EXTSS_BE
        || frame[0] == FIRST_BYTE_UHD_FTOC_SYNC_BE
        || frame[0] == FIRST_BYTE_UHD_FTOC_NONSYNC_BE) {
      // The frame is already 16-bit mode, big endian.
      return new ParsableBitArray(frame);
    }
    // Data is not normalized, but we don't want to modify frame.
    frame = Arrays.copyOf(frame, frame.length);
    if (isLittleEndianFrameHeader(frame)) {
      // Change endianness.
      for (int i = 0; i < frame.length - 1; i += 2) {
        byte temp = frame[i];
        frame[i] = frame[i + 1];
        frame[i + 1] = temp;
      }
    }
    ParsableBitArray frameBits = new ParsableBitArray(frame);
    if (frame[0] == (byte) (SYNC_VALUE_14B_BE >> 24)) {
      // Discard the 2 most significant bits of each 16 bit word.
      ParsableBitArray scratchBits = new ParsableBitArray(frame);
      while (scratchBits.bitsLeft() >= 16) {
        scratchBits.skipBits(2);
        frameBits.putInt(scratchBits.readBits(14), 14);
      }
    }
    frameBits.reset(frame);
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

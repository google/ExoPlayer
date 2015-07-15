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

import com.google.android.exoplayer.MediaFormat;

import java.util.Collections;

/**
 * Utility methods for parsing AC-3 headers.
 */
public final class Ac3Util {

  /** Sample rates, indexed by fscod. */
  private static final int[] SAMPLE_RATES = new int[] {48000, 44100, 32000};
  /** Channel counts, indexed by acmod. */
  private static final int[] CHANNEL_COUNTS = new int[] {2, 1, 2, 3, 3, 4, 4, 5};
  /** Nominal bitrates in kbps, indexed by bit_rate_code. */
  private static final int[] BITRATES = new int[] {32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192,
      224, 256, 320, 384, 448, 512, 576, 640};
  /** 16-bit words per sync frame, indexed by frmsizecod / 2. (See ETSI TS 102 366 table 4.13.) */
  private static final int[] FRMSIZECOD_TO_FRAME_SIZE_44_1 = new int[] {69, 87, 104, 121, 139, 174,
    208, 243, 278, 348, 417, 487, 557, 696, 835, 975, 1114, 1253, 1393};

  /**
   * Returns the AC-3 format given {@code data} containing the AC3SpecificBox according to
   * ETSI TS 102 366 Annex F.
   */
  public static MediaFormat parseAnnexFAc3Format(ParsableByteArray data) {
    // fscod (sample rate code)
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATES[fscod];
    int nextByte = data.readUnsignedByte();
    // Map acmod (audio coding mode) onto a channel count.
    int channelCount = CHANNEL_COUNTS[(nextByte & 0x38) >> 3];
    // lfeon (low frequency effects on)
    if ((nextByte & 0x04) != 0) {
      channelCount++;
    }
    return MediaFormat.createAudioFormat(MimeTypes.AUDIO_AC3, MediaFormat.NO_VALUE,
        MediaFormat.NO_VALUE, channelCount, sampleRate, Collections.<byte[]>emptyList());
  }

  /**
   * Returns the AC-3 format given {@code data} containing the EC3SpecificBox according to
   * ETSI TS 102 366 Annex F.
   */
  public static MediaFormat parseAnnexFEAc3Format(ParsableByteArray data) {
    data.skipBytes(2); // Skip data_rate and num_ind_sub.

    // Read only the first substream.
    // TODO: Read later substreams?
    // fscod (sample rate code)
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATES[fscod];
    int nextByte = data.readUnsignedByte();
    // Map acmod (audio coding mode) onto a channel count.
    int channelCount = CHANNEL_COUNTS[(nextByte & 0x0E) >> 1];
    // lfeon (low frequency effects on)
    if ((nextByte & 0x01) != 0) {
      channelCount++;
    }
    return MediaFormat.createAudioFormat(MimeTypes.AUDIO_EC3, MediaFormat.NO_VALUE,
        channelCount, sampleRate, Collections.<byte[]>emptyList());
  }

  /**
   * Returns the AC-3 format given {@code data} containing the frame header starting from the sync
   * word.
   *
   * @param data Data to parse, positioned at the start of the syncword.
   * @return AC-3 format parsed from data in the header.
   */
  public static MediaFormat parseFrameAc3Format(ParsableBitArray data) {
    // Skip syncword and crc1.
    data.skipBits(4 * 8);

    int fscod = data.readBits(2);
    data.skipBits(14); // frmsizecod(6) + bsid (5 bits) + bsmod (3 bits)
    int acmod = data.readBits(3);
    if ((acmod & 0x01) != 0 && acmod != 1) {
      data.skipBits(2); // cmixlev
    }
    if ((acmod & 0x04) != 0) {
      data.skipBits(2); // surmixlev
    }
    if (acmod == 0x02) {
      data.skipBits(2); // dsurmod
    }
    boolean lfeon = data.readBit();
    return MediaFormat.createAudioFormat(MimeTypes.AUDIO_AC3, MediaFormat.NO_VALUE,
        MediaFormat.NO_VALUE, CHANNEL_COUNTS[acmod] + (lfeon ? 1 : 0), SAMPLE_RATES[fscod],
        Collections.<byte[]>emptyList());
  }

  /**
   * Returns the AC-3 frame size in bytes given {@code data} containing the frame header starting
   * from the sync word.
   *
   * @param data Data to parse, positioned at the start of the syncword.
   * @return The frame size parsed from data in the header.
   */
  public static int parseFrameSize(ParsableBitArray data) {
    // Skip syncword and crc1.
    data.skipBits(4 * 8);

    int fscod = data.readBits(2);
    int frmsizecod = data.readBits(6);
    int sampleRate = SAMPLE_RATES[fscod];
    int bitrate = BITRATES[frmsizecod / 2];
    if (sampleRate == 32000) {
      return 6 * bitrate;
    } else if (sampleRate == 44100) {
      return 2 * (FRMSIZECOD_TO_FRAME_SIZE_44_1[frmsizecod / 2] + (frmsizecod % 2));
    } else { // sampleRate == 48000
      return 4 * bitrate;
    }
  }

  /**
   * Returns the bitrate of AC-3 audio given the size of a buffer and the sample rate.
   *
   * @param bufferSize Size in bytes of a full buffer of samples.
   * @param sampleRate Sample rate in hz.
   * @return Bitrate of the audio stream in kbit/s.
   */
  public static int getBitrate(int bufferSize, int sampleRate) {
    // Each AC-3 buffer contains 1536 frames of audio, so the AudioTrack playback position
    // advances by 1536 per buffer (32 ms at 48 kHz).
    int unscaledBitrate = bufferSize * 8 * sampleRate;
    int divisor = 1000 * 1536;
    return (unscaledBitrate + divisor / 2) / divisor;
  }

  private Ac3Util() {
    // Prevent instantiation.
  }

}

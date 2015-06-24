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
package com.google.android.exoplayer.extractor.mp3;

/** Parsed MPEG audio frame header. */
/* package */ final class MpegAudioHeader {

  private static final int[] SAMPLING_RATE_V1 = {44100, 48000, 32000};
  private static final int[] BITRATE_V1_L1 =
      {32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448};
  private static final int[] BITRATE_V2_L1 =
      {32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256};
  private static final int[] BITRATE_V1_L2 =
      {32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384};
  private static final int[] BITRATE_V1_L3 =
      {32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
  private static final int[] BITRATE_V2 =
      {8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};

  /** Returns the size of the frame associated with {@code header}, or -1 if it is invalid. */
  public static int getFrameSize(int header) {
    if ((header & 0xFFE00000) != 0xFFE00000) {
      return -1;
    }

    int version = (header >>> 19) & 3;
    if (version == 1) {
      return -1;
    }

    int layer = (header >>> 17) & 3;
    if (layer == 0) {
      return -1;
    }

    int bitrateIndex = (header >>> 12) & 15;
    if (bitrateIndex == 0 || bitrateIndex == 0xF) {
      // Disallow "free" bitrate.
      return -1;
    }

    int samplingRateIndex = (header >>> 10) & 3;
    if (samplingRateIndex == 3) {
      return -1;
    }

    int samplingRate = SAMPLING_RATE_V1[samplingRateIndex];
    if (version == 2) {
      // Version 2
      samplingRate /= 2;
    } else if (version == 0) {
      // Version 2.5
      samplingRate /= 4;
    }

    int bitrate;
    int padding = (header >>> 9) & 1;
    if (layer == 3) {
      // Layer I (layer == 3)
      bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
      return (12000 * bitrate / samplingRate + padding) * 4;
    } else {
      // Layer II (layer == 2) or III (layer == 1)
      if (version == 3) {
        bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
      } else {
        // Version 2 or 2.5.
        bitrate = BITRATE_V2[bitrateIndex - 1];
      }
    }

    if (version == 3) {
      // Version 1
      return 144000 * bitrate / samplingRate + padding;
    } else {
      // Version 2 or 2.5
      return (layer == 1 ? 72000 : 144000) * bitrate / samplingRate + padding;
    }
  }

  /**
   * Returns the header represented by {@code header}, if it is valid; {@code null} otherwise.
   *
   * @param headerData Header data to parse.
   * @param header Header to populate with data from {@code headerData}.
   */
  public static void populateHeader(int headerData, MpegAudioHeader header) {
    if ((headerData & 0xFFE00000) != 0xFFE00000) {
      return;
    }

    int version = (headerData >>> 19) & 3;
    if (version == 1) {
      return;
    }

    int layer = (headerData >>> 17) & 3;
    if (layer == 0) {
      return;
    }

    int bitrateIndex = (headerData >>> 12) & 15;
    if (bitrateIndex == 0 || bitrateIndex == 0xF) {
      // Disallow "free" bitrate.
      return;
    }

    int samplingRateIndex = (headerData >>> 10) & 3;
    if (samplingRateIndex == 3) {
      return;
    }

    int sampleRate = SAMPLING_RATE_V1[samplingRateIndex];
    if (version == 2) {
      // Version 2
      sampleRate /= 2;
    } else if (version == 0) {
      // Version 2.5
      sampleRate /= 4;
    }

    int padding = (headerData >>> 9) & 1;
    int bitrate, frameSize, samplesPerFrame;
    if (layer == 3) {
      // Layer I (layer == 3)
      bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
      frameSize = (12000 * bitrate / sampleRate + padding) * 4;
      samplesPerFrame = 384;
    } else {
      // Layer II (layer == 2) or III (layer == 1)
      if (version == 3) {
        // Version 1
        bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
        samplesPerFrame = 1152;
        frameSize = 144000 * bitrate / sampleRate + padding;
      } else {
        // Version 2 or 2.5.
        bitrate = BITRATE_V2[bitrateIndex - 1];
        samplesPerFrame = layer == 1 ? 576 : 1152;
        frameSize = (layer == 1 ? 72000 : 144000) * bitrate / sampleRate + padding;
      }
    }

    int channels = ((headerData >> 6) & 3) == 3 ? 1 : 2;
    int layerIndex = 3 - layer;
    header.setValues(
        version, layerIndex, frameSize, sampleRate, channels, bitrate, samplesPerFrame);
  }

  /** MPEG audio header version. */
  public int version;
  /** MPEG audio layer index, starting at zero. */
  public int layerIndex;
  /** Size of the frame associated with this header, in bytes. */
  public int frameSize;
  /** Sample rate in samples per second. */
  public int sampleRate;
  /** Number of audio channels in the frame. */
  public int channels;
  /** Bitrate of the frame in kbit/s. */
  public int bitrate;
  /** Number of samples stored in the frame. */
  public int samplesPerFrame;

  private void setValues(int version, int layerIndex, int frameSize, int sampleRate, int channels,
      int bitrate, int samplesPerFrame) {
    this.version = version;
    this.layerIndex = layerIndex;
    this.frameSize = frameSize;
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.bitrate = bitrate;
    this.samplesPerFrame = samplesPerFrame;
  }

}

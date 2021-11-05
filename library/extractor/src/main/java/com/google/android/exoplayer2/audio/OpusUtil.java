/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for handling Opus audio streams. */
public class OpusUtil {

  /** Opus streams are always 48000 Hz. */
  public static final int SAMPLE_RATE = 48_000;

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
  private static final int FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT = 3;

  private OpusUtil() {} // Prevents instantiation.

  /**
   * Parses the channel count from an Opus Identification Header.
   *
   * @param header An Opus Identification Header, as defined by RFC 7845.
   * @return The parsed channel count.
   */
  public static int getChannelCount(byte[] header) {
    return header[9] & 0xFF;
  }

  /**
   * Builds codec initialization data from an Opus Identification Header.
   *
   * @param header An Opus Identification Header, as defined by RFC 7845.
   * @return Codec initialization data suitable for an Opus <a
   *     href="https://developer.android.com/reference/android/media/MediaCodec#initialization">MediaCodec</a>.
   */
  public static List<byte[]> buildInitializationData(byte[] header) {
    int preSkipSamples = getPreSkipSamples(header);
    long preSkipNanos = sampleCountToNanoseconds(preSkipSamples);
    long seekPreRollNanos = sampleCountToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES);

    List<byte[]> initializationData = new ArrayList<>(FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT);
    initializationData.add(header);
    initializationData.add(buildNativeOrderByteArray(preSkipNanos));
    initializationData.add(buildNativeOrderByteArray(seekPreRollNanos));
    return initializationData;
  }

  private static int getPreSkipSamples(byte[] header) {
    return ((header[11] & 0xFF) << 8) | (header[10] & 0xFF);
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / SAMPLE_RATE;
  }
}

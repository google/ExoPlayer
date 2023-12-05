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
package androidx.media3.extractor;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Utility methods for handling Opus audio streams. */
@UnstableApi
public class OpusUtil {

  /** Opus streams are always 48000 Hz. */
  public static final int SAMPLE_RATE = 48_000;

  /** Maximum achievable Opus bitrate. */
  public static final int MAX_BYTES_PER_SECOND = 510 * 1000 / 8; // See RFC 6716. Section 2.1.1

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

  /**
   * Returns the number of audio samples in the given Ogg encapuslated Opus packet.
   *
   * <p>The buffer's position is not modified.
   *
   * @param buffer The audio packet.
   * @return Returns the number of audio samples in the packet.
   */
  public static int parseOggPacketAudioSampleCount(ByteBuffer buffer) {
    // RFC 3433 section 6 - The Ogg page format.
    int preAudioPacketByteCount = parseOggPacketForPreAudioSampleByteCount(buffer);
    int numPageSegments = buffer.get(/* index= */ 26 + preAudioPacketByteCount);
    // Skip Ogg header + segment table.
    int indexFirstOpusPacket = 27 + numPageSegments + preAudioPacketByteCount;
    long packetDurationUs =
        getPacketDurationUs(
            buffer.get(indexFirstOpusPacket),
            buffer.limit() - indexFirstOpusPacket > 1 ? buffer.get(indexFirstOpusPacket + 1) : 0);
    return (int) (packetDurationUs * SAMPLE_RATE / C.MICROS_PER_SECOND);
  }

  /**
   * Calculate the offset from the start of the buffer to audio sample Ogg packets.
   *
   * @param buffer containing the Ogg Encapsulated Opus audio bitstream.
   * @return the offset before the Ogg packet containing audio samples.
   */
  public static int parseOggPacketForPreAudioSampleByteCount(ByteBuffer buffer) {
    // Parse Ogg Packet Type from Header at index 5
    if ((buffer.get(/* index= */ 5) & 0x02) == 0) {
      // Ogg Page packet header type is not beginning of logical stream. Must be an Audio page.
      return 0;
    }
    // ID Header Page size is Ogg packet header size + sum(lacing values: 1..number_page_segments).
    int idHeaderPageSize = 28;
    int idHeaderPageNumOfSegments = buffer.get(/* index= */ 26);
    for (int i = 0; i < idHeaderPageNumOfSegments; i++) {
      idHeaderPageSize += buffer.get(/* index= */ 27 + i);
    }
    // Comment Header Page size is Ogg packet header size + sum(lacing values:
    // 1..number_page_segments).
    int commentHeaderPageSize = 28;
    int commentHeaderPageSizeNumOfSegments = buffer.get(/* index= */ idHeaderPageSize + 26);
    for (int i = 0; i < commentHeaderPageSizeNumOfSegments; i++) {
      commentHeaderPageSize += buffer.get(/* index= */ idHeaderPageSize + 27 + i);
    }
    return idHeaderPageSize + commentHeaderPageSize;
  }

  /**
   * Returns the number of audio samples in the given audio packet.
   *
   * <p>The buffer's position is not modified.
   *
   * @param buffer The audio packet.
   * @return Returns the number of audio samples in the packet.
   */
  public static int parsePacketAudioSampleCount(ByteBuffer buffer) {
    long packetDurationUs =
        getPacketDurationUs(buffer.get(0), buffer.limit() > 1 ? buffer.get(1) : 0);
    return (int) (packetDurationUs * SAMPLE_RATE / C.MICROS_PER_SECOND);
  }

  /**
   * Returns the duration of the given audio packet.
   *
   * @param buffer The audio packet.
   * @return Returns the duration of the given audio packet, in microseconds.
   */
  public static long getPacketDurationUs(byte[] buffer) {
    return getPacketDurationUs(buffer[0], buffer.length > 1 ? buffer[1] : 0);
  }

  /**
   * Returns the number of pre-skip samples specified by the given Opus codec initialization data.
   *
   * @param header The Opus Identification header.
   * @return The number of pre-skip samples.
   */
  public static int getPreSkipSamples(byte[] header) {
    return ((header[11] & 0xFF) << 8) | (header[10] & 0xFF);
  }

  /**
   * Returns whether an Opus frame should be sent to the decoder as it is either past the start
   * position or within the seek-preroll duration.
   *
   * <p>The measure of whether an Opus frame should not be decoded is if its time precedes the start
   * position by more than the default seek-preroll value.
   *
   * @param startTimeUs The time to start playing at.
   * @param frameTimeUs The time of the Opus sample.
   * @return Whether the frame should be decoded.
   */
  public static boolean needToDecodeOpusFrame(long startTimeUs, long frameTimeUs) {
    // Divide by 1000 in rhs value to convert nanoseconds to microseconds.
    return (startTimeUs - frameTimeUs)
        <= (sampleCountToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES) / 1000);
  }

  private static long getPacketDurationUs(byte packetByte0, byte packetByte1) {
    // See RFC6716, Sections 3.1 and 3.2.
    int toc = packetByte0 & 0xFF;
    int frames;
    switch (toc & 0x3) {
      case 0:
        frames = 1;
        break;
      case 1:
      case 2:
        frames = 2;
        break;
      default:
        frames = packetByte1 & 0x3F;
        break;
    }

    int config = toc >> 3;
    int length = config & 0x3;
    int frameDurationUs;
    if (config >= 16) {
      frameDurationUs = 2500 << length;
    } else if (config >= 12) {
      frameDurationUs = 10000 << (length & 0x1);
    } else if (length == 3) {
      frameDurationUs = 60000;
    } else {
      frameDurationUs = 10000 << length;
    }
    return (long) frames * frameDurationUs;
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / SAMPLE_RATE;
  }
}

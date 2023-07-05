/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.android.exoplayer2.audio.AudioProcessor.EMPTY_BUFFER;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A packetizer that encapsulates OPUS audio encodings in OGG packets.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class OggOpusAudioPacketizer {

  /** ID Header and Comment Header pages are 0 and 1 respectively */
  private static final int FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE = 2;

  private ByteBuffer outputBuffer;
  private int pageSequenceNumber;
  private int granulePosition;

  /** Creates an instance. */
  public OggOpusAudioPacketizer() {
    outputBuffer = EMPTY_BUFFER;
    granulePosition = 0;
    pageSequenceNumber = FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE;
  }

  /**
   * Packetizes the audio data between the position and limit of the {@code inputBuffer}.
   *
   * @param inputBuffer The input buffer to packetize. It must be a direct {@link ByteBuffer} with
   *     LITTLE_ENDIAN order. The contents will be overwritten with the Ogg packet. The caller
   *     retains ownership of the provided buffer.
   */
  public void packetize(DecoderInputBuffer inputBuffer) {
    checkNotNull(inputBuffer.data);
    if (inputBuffer.data.limit() - inputBuffer.data.position() == 0) {
      return;
    }
    outputBuffer = packetizeInternal(inputBuffer.data);
    inputBuffer.clear();
    inputBuffer.ensureSpaceForWrite(outputBuffer.remaining());
    inputBuffer.data.put(outputBuffer);
    inputBuffer.flip();
  }

  /** Resets the packetizer. */
  public void reset() {
    outputBuffer = EMPTY_BUFFER;
    granulePosition = 0;
    pageSequenceNumber = FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE;
  }

  /**
   * Fill outputBuffer with an Ogg packet encapsulating the inputBuffer.
   *
   * @param inputBuffer contains Opus to wrap in Ogg packet
   * @return {@link ByteBuffer} containing Ogg packet
   */
  private ByteBuffer packetizeInternal(ByteBuffer inputBuffer) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int inputBufferSize = limit - position;

    // inputBufferSize divisible by 255 requires extra '0' terminating lacing value
    int numSegments = (inputBufferSize + 255) / 255;
    int headerSize = 27 + numSegments;

    int outputPacketSize = headerSize + inputBufferSize;

    // Resample the little endian input and update the output buffers.
    ByteBuffer buffer = replaceOutputBuffer(outputPacketSize);

    // Capture Pattern for Page [OggS]
    buffer.put((byte) 'O');
    buffer.put((byte) 'g');
    buffer.put((byte) 'g');
    buffer.put((byte) 'S');

    // StreamStructure Version
    buffer.put((byte) 0);

    // header_type_flag
    buffer.put((byte) 0x00);

    // granule_position
    int numSamples = OpusUtil.parsePacketAudioSampleCount(inputBuffer);
    granulePosition += numSamples;
    buffer.putLong(granulePosition);

    // bitstream_serial_number
    buffer.putInt(0);

    // page_sequence_number
    buffer.putInt(pageSequenceNumber);
    pageSequenceNumber++;

    // CRC_checksum
    buffer.putInt(0);

    // number_page_segments
    buffer.put((byte) numSegments);

    // Segment_table
    int bytesLeft = inputBufferSize;
    for (int i = 0; i < numSegments; i++) {
      if (bytesLeft >= 255) {
        buffer.put((byte) 255);
        bytesLeft -= 255;
      } else {
        buffer.put((byte) bytesLeft);
        bytesLeft = 0;
      }
    }

    for (int i = position; i < limit; i++) {
      buffer.put(inputBuffer.get(i));
    }

    inputBuffer.position(inputBuffer.limit());
    buffer.flip();

    int checksum =
        Util.crc32(
            buffer.array(),
            buffer.arrayOffset(),
            buffer.limit() - buffer.position(),
            /* initialValue= */ 0);
    buffer.putInt(22, checksum);
    buffer.position(0);

    return buffer;
  }

  /**
   * Replaces the current output buffer with a buffer of at least {@code size} bytes and returns it.
   * Callers should write to the returned buffer then {@link ByteBuffer#flip()} it so it can be read
   * via buffer.
   */
  private ByteBuffer replaceOutputBuffer(int size) {
    if (outputBuffer.capacity() < size) {
      outputBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    } else {
      outputBuffer.clear();
    }
    return outputBuffer;
  }
}

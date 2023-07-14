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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.UnsignedBytes;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * A packetizer that encapsulates Opus audio encodings in Ogg packets.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class OggOpusAudioPacketizer {

  private static final int CHECKSUM_INDEX = 22;

  /** ID Header and Comment Header pages are 0 and 1 respectively */
  private static final int FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER = 2;

  private static final int OGG_PACKET_HEADER_LENGTH = 28;
  private static final int SERIAL_NUMBER = 0;
  private static final byte[] OGG_DEFAULT_ID_HEADER_PAGE =
      new byte[] {
        79, 103, 103, 83, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 28, -43, -59, -9, 1,
        19, 79, 112, 117, 115, 72, 101, 97, 100, 1, 2, 56, 1, -128, -69, 0, 0, 0, 0, 0
      };
  private static final byte[] OGG_DEFAULT_COMMENT_HEADER_PAGE =
      new byte[] {
        79, 103, 103, 83, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 11, -103, 87, 83, 1,
        16, 79, 112, 117, 115, 84, 97, 103, 115, 0, 0, 0, 0, 0, 0, 0, 0
      };

  private ByteBuffer outputBuffer;
  private int pageSequenceNumber;
  private int granulePosition;

  /** Creates an instance. */
  public OggOpusAudioPacketizer() {
    outputBuffer = EMPTY_BUFFER;
    granulePosition = 0;
    pageSequenceNumber = FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER;
  }

  /**
   * Packetizes the audio data between the position and limit of the {@code inputBuffer}.
   *
   * @param inputBuffer The input buffer to packetize. It must be a direct {@link ByteBuffer} with
   *     LITTLE_ENDIAN order. The contents will be overwritten with the Ogg packet. The caller
   *     retains ownership of the provided buffer.
   * @param initializationData contains set-up data for the Opus Decoder. The data will be provided
   *     in an Ogg ID Header Page prepended to the bitstream. The list should contain either one or
   *     three byte arrays. The first item is the payload for the Ogg ID Header Page. If three
   *     items, then it also contains the Opus pre-skip and seek pre-roll values in that order.
   */
  public void packetize(DecoderInputBuffer inputBuffer, List<byte[]> initializationData) {
    checkNotNull(inputBuffer.data);
    if (inputBuffer.data.limit() - inputBuffer.data.position() == 0) {
      return;
    }
    @Nullable
    byte[] providedOggIdHeaderPayloadBytes =
        pageSequenceNumber == FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER
                && (initializationData.size() == 1 || initializationData.size() == 3)
            ? initializationData.get(0)
            : null;
    outputBuffer = packetizeInternal(inputBuffer.data, providedOggIdHeaderPayloadBytes);
    inputBuffer.clear();
    inputBuffer.ensureSpaceForWrite(outputBuffer.remaining());
    inputBuffer.data.put(outputBuffer);
    inputBuffer.flip();
  }

  /** Resets the packetizer. */
  public void reset() {
    outputBuffer = EMPTY_BUFFER;
    granulePosition = 0;
    pageSequenceNumber = FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER;
  }

  /**
   * Fill outputBuffer with an Ogg packet encapsulating the inputBuffer.
   *
   * <p>If {@code providedOggIdHeaderPayloadBytes} is {@code null} and {@link #pageSequenceNumber}
   * is {@link #FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER}, then {@link #OGG_DEFAULT_ID_HEADER_PAGE}
   * will be prepended to the Ogg Opus Audio packets for the Ogg ID Header Page.
   *
   * @param inputBuffer contains Opus to wrap in Ogg packet.
   * @param providedOggIdHeaderPayloadBytes containing the Ogg ID Header Page payload. Expected to
   *     be {@code null} if {@link #pageSequenceNumber} is not {@link
   *     #FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER}.
   * @return {@link ByteBuffer} containing Ogg packet
   */
  private ByteBuffer packetizeInternal(
      ByteBuffer inputBuffer, @Nullable byte[] providedOggIdHeaderPayloadBytes) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int inputBufferSize = limit - position;

    // inputBufferSize divisible by 255 requires extra '0' terminating lacing value
    int numSegments = (inputBufferSize + 255) / 255;
    int headerSize = 27 + numSegments;

    int outputPacketSize = headerSize + inputBufferSize;

    // If first audio sample in stream, then the packetizer will add Ogg ID Header and Comment
    // Header Pages. Include additional page lengths in buffer size calculation.
    int oggIdHeaderPageSize = 0;
    if (pageSequenceNumber == FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER) {
      oggIdHeaderPageSize =
          providedOggIdHeaderPayloadBytes != null
              ? OGG_PACKET_HEADER_LENGTH + providedOggIdHeaderPayloadBytes.length
              : OGG_DEFAULT_ID_HEADER_PAGE.length;
      outputPacketSize += oggIdHeaderPageSize + OGG_DEFAULT_COMMENT_HEADER_PAGE.length;
    }

    // Resample the little endian input and update the output buffers.
    ByteBuffer buffer = replaceOutputBuffer(outputPacketSize);

    // If first audio sample in stream then insert Ogg ID Header and Comment Header Pages
    if (pageSequenceNumber == FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER) {
      if (providedOggIdHeaderPayloadBytes != null) {
        writeOggIdHeaderPage(buffer, /* idHeaderPayloadBytes= */ providedOggIdHeaderPayloadBytes);
      } else {
        // Write default Ogg ID Header Payload
        buffer.put(OGG_DEFAULT_ID_HEADER_PAGE);
      }
      buffer.put(OGG_DEFAULT_COMMENT_HEADER_PAGE);
    }

    // granule_position
    int numSamples = OpusUtil.parsePacketAudioSampleCount(inputBuffer);
    granulePosition += numSamples;

    writeOggPacketHeader(
        buffer, granulePosition, pageSequenceNumber, numSegments, /* isIdHeaderPacket= */ false);

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

    // Write Opus audio data
    for (int i = position; i < limit; i++) {
      buffer.put(inputBuffer.get(i));
    }

    inputBuffer.position(inputBuffer.limit());
    buffer.flip();

    int checksum;
    if (pageSequenceNumber == FIRST_AUDIO_SAMPLE_PAGE_SEQUENCE_NUMBER) {
      checksum =
          Util.crc32(
              buffer.array(),
              /* start= */ buffer.arrayOffset()
                  + oggIdHeaderPageSize
                  + OGG_DEFAULT_COMMENT_HEADER_PAGE.length,
              /* end= */ buffer.limit() - buffer.position(),
              /* initialValue= */ 0);
      buffer.putInt(
          oggIdHeaderPageSize + OGG_DEFAULT_COMMENT_HEADER_PAGE.length + CHECKSUM_INDEX, checksum);
    } else {
      checksum =
          Util.crc32(
              buffer.array(),
              /* start= */ buffer.arrayOffset(),
              /* end= */ buffer.limit() - buffer.position(),
              /* initialValue= */ 0);
      buffer.putInt(CHECKSUM_INDEX, checksum);
    }

    // Increase pageSequenceNumber for next packet
    pageSequenceNumber++;

    return buffer;
  }

  /**
   * Write Ogg ID Header Page packet to {@link ByteBuffer}.
   *
   * @param buffer to write into.
   * @param idHeaderPayloadBytes containing the Ogg ID Header Page payload.
   */
  private void writeOggIdHeaderPage(ByteBuffer buffer, byte[] idHeaderPayloadBytes) {
    //     TODO(b/290195621): Use starting position to calculate correct 'pre-skip' value
    writeOggPacketHeader(
        buffer,
        /* granulePosition= */ 0,
        /* pageSequenceNumber= */ 0,
        /* numberPageSegments= */ 1,
        /* isIdHeaderPacket= */ true);
    buffer.put(UnsignedBytes.checkedCast(idHeaderPayloadBytes.length));
    buffer.put(idHeaderPayloadBytes);
    int checksum =
        Util.crc32(
            buffer.array(),
            /* start= */ buffer.arrayOffset(),
            /* end= */ OGG_PACKET_HEADER_LENGTH + idHeaderPayloadBytes.length,
            /* initialValue= */ 0);
    buffer.putInt(/* index= */ CHECKSUM_INDEX, checksum);
    buffer.position(OGG_PACKET_HEADER_LENGTH + idHeaderPayloadBytes.length);
  }

  /**
   * Write header for an Ogg Page Packet to {@link ByteBuffer}.
   *
   * @param byteBuffer to write unto.
   * @param granulePosition is the number of audio samples in the stream up to and including this
   *     packet.
   * @param pageSequenceNumber of the page this header is for.
   * @param numberPageSegments the data of this Ogg page will span.
   * @param isIdHeaderPacket where if this header is start of the bitstream.
   */
  private void writeOggPacketHeader(
      ByteBuffer byteBuffer,
      long granulePosition,
      int pageSequenceNumber,
      int numberPageSegments,
      boolean isIdHeaderPacket) {
    // Capture Pattern for Ogg Page [OggS]
    byteBuffer.put((byte) 'O');
    byteBuffer.put((byte) 'g');
    byteBuffer.put((byte) 'g');
    byteBuffer.put((byte) 'S');

    // StreamStructure Version
    byteBuffer.put((byte) 0);

    // Header-type
    byteBuffer.put(isIdHeaderPacket ? (byte) 0x02 : (byte) 0x00);

    // Granule_position
    byteBuffer.putLong(granulePosition);

    // bitstream_serial_number
    byteBuffer.putInt(SERIAL_NUMBER);

    // Page_sequence_number
    byteBuffer.putInt(pageSequenceNumber);

    // CRC_checksum
    // Will be overwritten with calculated checksum after rest of page is written to buffer.
    byteBuffer.putInt(0);

    // Number_page_segments
    byteBuffer.put(UnsignedBytes.checkedCast(numberPageSegments));
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

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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.OpusUtil;
import com.google.android.exoplayer2.extractor.VorbisUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/** {@link StreamReader} to extract Opus data out of Ogg byte stream. */
/* package */ final class OpusReader extends StreamReader {

  private static final byte[] OPUS_ID_HEADER_SIGNATURE =
      {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
  private static final byte[] OPUS_COMMENT_HEADER_SIGNATURE =
      {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

  private boolean headerRead;

  public static boolean verifyBitstreamType(ParsableByteArray data) {
    if (data.bytesLeft() < OPUS_ID_HEADER_SIGNATURE.length) {
      return false;
    }
    byte[] header = new byte[OPUS_ID_HEADER_SIGNATURE.length];
    data.readBytes(header, 0, OPUS_ID_HEADER_SIGNATURE.length);
    return Arrays.equals(header, OPUS_ID_HEADER_SIGNATURE);
  }

  @Override
  protected void reset(boolean headerData) {
    super.reset(headerData);
    if (headerData) {
      headerRead = false;
    }
  }

  @Override
  protected long preparePayload(ParsableByteArray packet) {
    return convertTimeToGranule(getPacketDurationUs(packet.getData()));
  }

  @Override
  @EnsuresNonNullIf(expression = "#3.format", result = false)
  protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData)
    throws IOException {
    if (!headerRead) {
      byte[] headerBytes = Arrays.copyOf(packet.getData(), packet.limit());
      int channelCount = OpusUtil.getChannelCount(headerBytes);
      List<byte[]> initializationData = OpusUtil.buildInitializationData(headerBytes);
      setupData.format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_OPUS)
              .setChannelCount(channelCount)
              .setSampleRate(OpusUtil.SAMPLE_RATE)
              .setInitializationData(initializationData)
              .build();

      headerRead = true;
      return true;
    } else {
      checkNotNull(setupData.format); // Has been set when the header was read

      int startPosition = packet.getPosition();
      if (!validateCommentHeaderSignature(packet)) {
        packet.setPosition(startPosition);
        return false;
      }

      VorbisUtil.CommentHeader commentHeader = VorbisUtil.readVorbisCommentHeader(
          packet, false, false);

      @Nullable Metadata vorbisMetadata = VorbisUtil.parseVorbisComments(
          Arrays.asList(commentHeader.comments));

      setupData.format = setupData.format
          .buildUpon()
          .setMetadata(
              setupData.format.metadata != null
                  ? setupData.format.metadata.copyWithAppendedEntriesFrom(vorbisMetadata)
                  : vorbisMetadata)
          .build();

      return true;
    }
  }

  /**
   * Validates that the given scratch signifies a comment header.
   *
   * @param scratch The packet data
   * @return True if the packet signifies a comment header, false if not
   */
  private boolean validateCommentHeaderSignature(ParsableByteArray scratch) {
    if (scratch.bytesLeft() < OPUS_COMMENT_HEADER_SIGNATURE.length) {
      return false;
    }

    byte[] header = new byte[OPUS_COMMENT_HEADER_SIGNATURE.length];
    scratch.readBytes(header, 0, OPUS_COMMENT_HEADER_SIGNATURE.length);

    return Arrays.equals(header, OPUS_COMMENT_HEADER_SIGNATURE);
  }

  /**
   * Returns the duration of the given audio packet.
   *
   * @param packet Contains audio data.
   * @return Returns the duration of the given audio packet.
   */
  private long getPacketDurationUs(byte[] packet) {
    int toc = packet[0] & 0xFF;
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
        frames = packet[1] & 0x3F;
        break;
    }

    int config = toc >> 3;
    int length = config & 0x3;
    if (config >= 16) {
      length = 2500 << length;
    } else if (config >= 12) {
      length = 10000 << (length & 0x1);
    } else if (length == 3) {
      length = 60000;
    } else {
      length = 10000 << length;
    }
    return (long) frames * length;
  }
}

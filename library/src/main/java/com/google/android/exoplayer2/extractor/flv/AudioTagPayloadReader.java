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
package com.google.android.exoplayer2.extractor.flv;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;

/**
 * Parses audio tags from an FLV stream and extracts AAC frames.
 */
/* package */ final class AudioTagPayloadReader extends TagPayloadReader {

  // Audio format
  private static final int AUDIO_FORMAT_ALAW = 7;
  private static final int AUDIO_FORMAT_ULAW = 8;
  private static final int AUDIO_FORMAT_AAC = 10;

  // AAC PACKET TYPE
  private static final int AAC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AAC_PACKET_TYPE_AAC_RAW = 1;

  // SAMPLING RATES
  private static final int[] AUDIO_SAMPLING_RATE_TABLE = new int[] {
      5500, 11000, 22000, 44000
  };

  // State variables
  private boolean hasParsedAudioDataHeader;
  private boolean hasOutputFormat;
  private int audioFormat;

  public AudioTagPayloadReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
    if (!hasParsedAudioDataHeader) {
      int header = data.readUnsignedByte();
      audioFormat = (header >> 4) & 0x0F;
      int sampleRateIndex = (header >> 2) & 0x03;
      int encodingSize = header & 0x01;
      if (sampleRateIndex < 0 || sampleRateIndex >= AUDIO_SAMPLING_RATE_TABLE.length) {
        throw new UnsupportedFormatException("Invalid sample rate index: " + sampleRateIndex);
      }
      // TODO: Add support for MP3.
      if (audioFormat == AUDIO_FORMAT_ALAW || audioFormat == AUDIO_FORMAT_ULAW) {

          String type = (audioFormat == AUDIO_FORMAT_ALAW) ? MimeTypes.AUDIO_ALAW : MimeTypes.AUDIO_ULAW;
          int encoding = (encodingSize == 1) ? C.ENCODING_PCM_16BIT : C.ENCODING_PCM_8BIT;
          Format format = Format.createAudioSampleFormat(null, type, null, Format.NO_VALUE, Format.NO_VALUE,
                  1, 8000, encoding, null, null, 0, null);
          output.format(format);

          hasOutputFormat = true;
      } else if (audioFormat != AUDIO_FORMAT_AAC ) {
          throw new UnsupportedFormatException("Audio format not supported: " + audioFormat);
      }

      hasParsedAudioDataHeader = true;
    } else {
      // Skip header if it was parsed previously.
      data.skipBytes(1);
    }
    return true;
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    int packetType = data.readUnsignedByte();

    if (packetType == AAC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      // Parse sequence header just in case it was not done before.
      byte[] audioSpecifiConfig = new byte[data.bytesLeft()];
      data.readBytes(audioSpecifiConfig, 0, audioSpecifiConfig.length);
      Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(
          audioSpecifiConfig);
      Format format = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_AAC, null,
          Format.NO_VALUE, Format.NO_VALUE, audioParams.second, audioParams.first,
          Collections.singletonList(audioSpecifiConfig), null, 0, null);
      output.format(format);
      hasOutputFormat = true;
    } else if (audioFormat != AUDIO_FORMAT_AAC || packetType == AAC_PACKET_TYPE_AAC_RAW) {
      int bytes = data.bytesLeft();
      output.sampleData(data, bytes);
      output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, bytes, 0, null);
    }
  }
}

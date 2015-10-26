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
package com.google.android.exoplayer.extractor.flv;

import android.util.Pair;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.Collections;

/**
 * Parses audio tags of from an FLV stream and extracts AAC frames.
 */
final class AudioTagPayloadReader extends TagPayloadReader {
  // Sound format
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


  public AudioTagPayloadReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {

  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedTrack {
    // Parse audio data header, if it was not done, to extract information
    // about the audio codec and audio configuration.
    if (!hasParsedAudioDataHeader) {
      int header = data.readUnsignedByte();
      int soundFormat = (header >> 4) & 0x0F;
      int sampleRateIndex = (header >> 2) & 0x03;
      int bitsPerSample = (header & 0x02) == 0x02 ? 16 : 8;
      int channels = (header & 0x01) + 1;

      if (sampleRateIndex < 0 || sampleRateIndex >= AUDIO_SAMPLING_RATE_TABLE.length) {
        throw new UnsupportedTrack("Invalid sample rate for the audio track");
      }

      if (!hasOutputFormat) {
        // TODO: Adds support for MP3 and PCM
        if (soundFormat != AUDIO_FORMAT_AAC) {
          throw new UnsupportedTrack("Audio track not supported. Format: " + soundFormat +
              ", Sample rate: " + sampleRateIndex + ", bps: " + bitsPerSample + ", channels: " +
              channels);
        }
      }

      hasParsedAudioDataHeader = true;
    } else {
      // Skip header if it was parsed previously.
      data.skipBytes(1);
    }

    // In all the cases we will be managing AAC format (otherwise an exception would be
    // fired so we can just always return true
    return true;
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    int packetType = data.readUnsignedByte();
    // Parse sequence header just in case it was not done before.
    if (packetType == AAC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      ParsableBitArray adtsScratch = new ParsableBitArray(new byte[data.bytesLeft()]);
      data.readBytes(adtsScratch.data, 0, data.bytesLeft());

      int audioObjectType = adtsScratch.readBits(5);
      int sampleRateIndex = adtsScratch.readBits(4);
      int channelConfig = adtsScratch.readBits(4);

      byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAacAudioSpecificConfig(
          audioObjectType, sampleRateIndex, channelConfig);
      Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(
          audioSpecificConfig);

      MediaFormat mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,
          MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, durationUs, audioParams.second,
          audioParams.first, Collections.singletonList(audioSpecificConfig), null);

      output.format(mediaFormat);
      hasOutputFormat = true;
    } else if (packetType == AAC_PACKET_TYPE_AAC_RAW) {
      // Sample audio AAC frames
      int bytesToWrite = data.bytesLeft();
      output.sampleData(data, bytesToWrite);
      output.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, bytesToWrite, 0, null);
    }
  }

}

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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link StreamReader} to extract Opus data out of Ogg byte stream.
 */
/* package */ final class OpusReader extends StreamReader {

  /**
   * Opus streams are always decoded at 48000 Hz.
   */
  private static final int SAMPLE_RATE = 48000;

  private static final byte[] OPUS_SIGNATURE = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};

  private static final int STATE_READ_HEADER = 0;
  private static final int STATE_READ_TAGS = 1;
  private static final int STATE_READ_AUDIO = 2;

  private int state = STATE_READ_HEADER;
  private long timeUs;

  public static boolean verifyBitstreamType(ParsableByteArray data) {
    if (data.bytesLeft() < OPUS_SIGNATURE.length) {
      return false;
    }
    byte[] header = new byte[OPUS_SIGNATURE.length];
    data.readBytes(header, 0, OPUS_SIGNATURE.length);
    return Arrays.equals(header, OPUS_SIGNATURE);
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (!oggParser.readPacket(input, scratch)) {
      return Extractor.RESULT_END_OF_INPUT;
    }

    byte[] data = scratch.data;
    int dataSize = scratch.limit();

    switch (state) {
      case STATE_READ_HEADER: {
        byte[] metadata = Arrays.copyOfRange(data, 0, dataSize);
        int channelCount = metadata[9] & 0xFF;
        List<byte[]> initializationData = Collections.singletonList(metadata);
        trackOutput.format(Format.createAudioSampleFormat(null, MimeTypes.AUDIO_OPUS,
            Format.NO_VALUE, Format.NO_VALUE, channelCount, SAMPLE_RATE,
            initializationData, null, null));
        state = STATE_READ_TAGS;
      } break;
      case STATE_READ_TAGS:
        // skip this packet
        state = STATE_READ_AUDIO;
        extractorOutput.seekMap(new SeekMap.Unseekable(C.UNSET_TIME_US));
        break;
      case STATE_READ_AUDIO:
        trackOutput.sampleData(scratch, dataSize);
        trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, dataSize, 0, null);
        timeUs += getPacketDuration(data);
        break;
    }

    scratch.reset();
    return Extractor.RESULT_CONTINUE;
  }

  private long getPacketDuration(byte[] packet) {
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
    return frames * length;
  }
}

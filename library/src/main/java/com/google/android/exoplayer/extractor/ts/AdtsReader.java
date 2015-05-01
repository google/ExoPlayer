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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Pair;

import java.util.Collections;

/**
 * Parses a continuous ADTS byte stream and extracts individual frames.
 */
/* package */ class AdtsReader extends ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 5;
  private static final int CRC_SIZE = 2;

  private final ParsableBitArray adtsScratch;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWasFF;
  private boolean hasCrc;

  // Used when parsing the header.
  private boolean hasOutputFormat;
  private long frameDurationUs;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  public AdtsReader(TrackOutput output) {
    super(output);
    adtsScratch = new ParsableBitArray(new byte[HEADER_SIZE + CRC_SIZE]);
    state = STATE_FINDING_SYNC;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWasFF = false;
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    if (startOfPacket) {
      timeUs = pesTimeUs;
    }
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            bytesRead = 0;
            state = STATE_READING_HEADER;
          }
          break;
        case STATE_READING_HEADER:
          int targetLength = hasCrc ? HEADER_SIZE + CRC_SIZE : HEADER_SIZE;
          if (continueRead(data, adtsScratch.data, targetLength)) {
            parseHeader();
            bytesRead = 0;
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            output.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
            timeUs += frameDurationUs;
            bytesRead = 0;
            state = STATE_FINDING_SYNC;
          }
          break;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param targetLength The target length of the read.
   * @return Whether the target length was reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  /**
   * Locates the next sync word, advancing the position to the byte that immediately follows it.
   * If a sync word was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return True if a sync word position was found. False otherwise.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    byte[] adtsData = pesBuffer.data;
    int startOffset = pesBuffer.getPosition();
    int endOffset = pesBuffer.limit();
    for (int i = startOffset; i < endOffset; i++) {
      boolean byteIsFF = (adtsData[i] & 0xFF) == 0xFF;
      boolean found = lastByteWasFF && !byteIsFF && (adtsData[i] & 0xF0) == 0xF0;
      lastByteWasFF = byteIsFF;
      if (found) {
        hasCrc = (adtsData[i] & 0x1) == 0;
        pesBuffer.setPosition(i + 1);
        // Reset lastByteWasFF for next time.
        lastByteWasFF = false;
        return true;
      }
    }
    pesBuffer.setPosition(endOffset);
    return false;
  }

  /**
   * Parses the sample header.
   */
  private void parseHeader() {
    adtsScratch.setPosition(0);

    if (!hasOutputFormat) {
      int audioObjectType = adtsScratch.readBits(2) + 1;
      int sampleRateIndex = adtsScratch.readBits(4);
      adtsScratch.skipBits(1);
      int channelConfig = adtsScratch.readBits(3);

      byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAudioSpecificConfig(
          audioObjectType, sampleRateIndex, channelConfig);
      Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAudioSpecificConfig(
          audioSpecificConfig);

      MediaFormat mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,
          MediaFormat.NO_VALUE, audioParams.second, audioParams.first,
          Collections.singletonList(audioSpecificConfig));
      frameDurationUs = (C.MICROS_PER_SECOND * 1024L) / mediaFormat.sampleRate;
      output.format(mediaFormat);
      hasOutputFormat = true;
    } else {
      adtsScratch.skipBits(10);
    }

    adtsScratch.skipBits(4);
    sampleSize = adtsScratch.readBits(13) - 2 /* the sync word */ - HEADER_SIZE;
    if (hasCrc) {
      sampleSize -= CRC_SIZE;
    }
  }

}

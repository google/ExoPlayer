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
import com.google.android.exoplayer.util.Ac3Util;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Parses a continuous AC-3 byte stream and extracts individual samples.
 */
/* package */ final class Ac3Reader extends ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 8;

  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray headerScratchBytes;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWas0B;

  // Used when parsing the header.
  private long frameDurationUs;
  private MediaFormat mediaFormat;
  private int sampleSize;
  private int bitrate;

  // Used when reading the samples.
  private long timeUs;

  public Ac3Reader(TrackOutput output) {
    super(output);
    headerScratchBits = new ParsableBitArray(new byte[HEADER_SIZE]);
    headerScratchBytes = new ParsableByteArray(headerScratchBits.data);
    state = STATE_FINDING_SYNC;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWas0B = false;
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
            state = STATE_READING_HEADER;
            headerScratchBytes.data[0] = 0x0B;
            headerScratchBytes.data[1] = 0x77;
            bytesRead = 2;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.data, HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, HEADER_SIZE);
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
    while (pesBuffer.bytesLeft() > 0) {
      if (!lastByteWas0B) {
        lastByteWas0B = pesBuffer.readUnsignedByte() == 0x0B;
        continue;
      }
      int secondByte = pesBuffer.readUnsignedByte();
      if (secondByte == 0x77) {
        lastByteWas0B = false;
        return true;
      } else {
        lastByteWas0B = secondByte == 0x0B;
      }
    }
    return false;
  }

  /**
   * Parses the sample header.
   */
  private void parseHeader() {
    headerScratchBits.setPosition(0);
    sampleSize = Ac3Util.parseFrameSize(headerScratchBits);
    if (mediaFormat == null) {
      headerScratchBits.setPosition(0);
      mediaFormat = Ac3Util.parseFrameAc3Format(headerScratchBits);
      output.format(mediaFormat);
      bitrate = Ac3Util.getBitrate(sampleSize, mediaFormat.sampleRate);
    }
    frameDurationUs = (int) (1000L * 8 * sampleSize / bitrate);
  }

}

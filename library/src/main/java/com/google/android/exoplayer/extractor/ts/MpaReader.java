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
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.mp3.MpegAudioHeader;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.Collections;

/**
 * Parses a continuous MPEG Audio byte stream and extracts individual frames.
 */
/* package */ public class MpaReader extends ElementaryStreamReader {

  private static final int STATE_FINDING_HEADER = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_FRAME = 2;

  private static final int HEADER_SIZE = 4;

  private final ParsableByteArray headerScratch;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWasFF;

  // Used when parsing the header.
  private boolean hasOutputFormat;
  private long frameDurationUs;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  public MpaReader(TrackOutput output) {
    super(output);
    state = STATE_FINDING_HEADER;
    // The first byte of an MPEG Audio frame header is always 0xFF.
    headerScratch = new ParsableByteArray(4);
    headerScratch.data[0] = (byte) 0xFF;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_HEADER;
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
        case STATE_FINDING_HEADER:
          if (findHeader(data)) {
            state = STATE_READING_HEADER;
          }
          break;
        case STATE_READING_HEADER:
          if (readHeaderRemainder(data)) {
            state = STATE_READING_FRAME;
          }
          break;
        case STATE_READING_FRAME:
          if (readFrame(data)) {
            state = STATE_FINDING_HEADER;
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
   * Attempts to locate the start of the next frame header.
   * <p>
   * If a frame header is located then true is returned. The first two bytes of the header will have
   * been written into {@link #headerScratch}, and the position of the source will have been
   * advanced to the byte that immediately follows these two bytes.
   * <p>
   * If a frame header is not located then the position of the source will have been advanced to the
   * limit, and the method should be called again with the next source to continue the search.
   *
   * @param source The source from which to read.
   * @return True if the frame header was located. False otherwise.
   */
  private boolean findHeader(ParsableByteArray source) {
    byte[] mpaData = source.data;
    int startOffset = source.getPosition();
    int endOffset = source.limit();
    for (int i = startOffset; i < endOffset; i++) {
      boolean byteIsFF = (mpaData[i] & 0xFF) == 0xFF;
      boolean found = lastByteWasFF && (mpaData[i] & 0xF0) == 0xF0;
      lastByteWasFF = byteIsFF;
      if (found) {
        source.setPosition(i + 1);
        // Reset lastByteWasFF for next time.
        lastByteWasFF = false;
        headerScratch.data[0] = (byte) 0xFF;
        headerScratch.data[1] = mpaData[i];
        bytesRead = 2;
        return true;
      }
    }
    source.setPosition(endOffset);
    return false;
  }

  /**
   * Attempts to read the remaining two bytes of the frame header.
   * <p>
   * If a frame header is read in full then true is returned. The media format will have been output
   * if this has not previously occurred, the four header bytes will have been output as sample
   * data, and the position of the source will have been advanced to the byte that immediately
   * follows the header.
   * <p>
   * If a frame header is not read in full then the position of the source will have been advanced
   * to the limit, and the method should be called again with the next source to continue the read.
   *
   * @param source The source from which to read.
   * @return True if the frame header was read in full. False otherwise.
   */
  private boolean readHeaderRemainder(ParsableByteArray source) {
    int bytesToRead = Math.min(source.bytesLeft(), HEADER_SIZE - bytesRead);
    source.readBytes(headerScratch.data, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    if (bytesRead < HEADER_SIZE) {
      return false;
    }

    if (!hasOutputFormat) {
      headerScratch.setPosition(0);
      int headerInt = headerScratch.readInt();
      MpegAudioHeader synchronizedHeader = new MpegAudioHeader();
      MpegAudioHeader.populateHeader(headerInt, synchronizedHeader);
      MediaFormat mediaFormat = MediaFormat.createAudioFormat(
          Mp3Extractor.MIME_TYPE_BY_LAYER[synchronizedHeader.layerIndex], Mp3Extractor.MAX_FRAME_SIZE_BYTES,
          C.UNKNOWN_TIME_US, synchronizedHeader.channels, synchronizedHeader.sampleRate,
          Collections.<byte[]>emptyList());
      output.format(mediaFormat);
      hasOutputFormat = true;
      frameDurationUs = (C.MICROS_PER_SECOND * synchronizedHeader.samplesPerFrame) / mediaFormat.sampleRate;
      sampleSize = synchronizedHeader.frameSize;
    }

    headerScratch.setPosition(0);
    output.sampleData(headerScratch, HEADER_SIZE);
    return true;
  }

  /**
   * Attempts to read the remainder of the frame.
   * <p>
   * If a frame is read in full then true is returned. The frame will have been output, and the
   * position of the source will have been advanced to the byte that immediately follows the end of
   * the frame.
   * <p>
   * If a frame is not read in full then the position of the source will have been advanced to the
   * limit, and the method should be called again with the next source to continue the read.
   *
   * @param source The source from which to read.
   * @return True if the frame was read in full. False otherwise.
   */
  private boolean readFrame(ParsableByteArray source) {
    int bytesToRead = Math.min(source.bytesLeft(), sampleSize - bytesRead);
    output.sampleData(source, bytesToRead);
    bytesRead += bytesToRead;
    if (bytesRead < sampleSize) {
      return false;
    }

    output.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
    timeUs += frameDurationUs;
    bytesRead = 0;
    return true;
  }

}

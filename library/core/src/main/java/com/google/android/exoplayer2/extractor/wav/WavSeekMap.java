/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.wav;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Util;

/* package */ final class WavSeekMap implements SeekMap {

  /** The WAV header for the stream. */
  private final WavHeader wavHeader;
  /** Number of samples in each block. */
  private final int samplesPerBlock;
  /** Position of the start of the sample data, in bytes. */
  private final long dataStartPosition;
  /** Position of the end of the sample data (exclusive), in bytes. */
  private final long dataEndPosition;

  public WavSeekMap(
      WavHeader wavHeader, int samplesPerBlock, long dataStartPosition, long dataEndPosition) {
    this.wavHeader = wavHeader;
    this.samplesPerBlock = samplesPerBlock;
    this.dataStartPosition = dataStartPosition;
    this.dataEndPosition = dataEndPosition;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    long numBlocks = (dataEndPosition - dataStartPosition) / wavHeader.blockAlign;
    return numBlocks * samplesPerBlock * C.MICROS_PER_SECOND / wavHeader.sampleRateHz;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    long blockAlign = wavHeader.blockAlign;
    long dataSize = dataEndPosition - dataStartPosition;
    long positionOffset = (timeUs * wavHeader.averageBytesPerSecond) / C.MICROS_PER_SECOND;
    // Constrain to nearest preceding frame offset.
    positionOffset = (positionOffset / blockAlign) * blockAlign;
    positionOffset = Util.constrainValue(positionOffset, 0, dataSize - blockAlign);
    long seekPosition = dataStartPosition + positionOffset;
    long seekTimeUs = getTimeUs(seekPosition);
    SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
    if (seekTimeUs >= timeUs || positionOffset == dataSize - blockAlign) {
      return new SeekPoints(seekPoint);
    } else {
      long secondSeekPosition = seekPosition + blockAlign;
      long secondSeekTimeUs = getTimeUs(secondSeekPosition);
      SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
      return new SeekPoints(seekPoint, secondSeekPoint);
    }
  }

  /**
   * Returns the time in microseconds for the given position in bytes.
   *
   * @param position The position in bytes.
   */
  public long getTimeUs(long position) {
    long positionOffset = Math.max(0, position - dataStartPosition);
    return (positionOffset * C.MICROS_PER_SECOND) / wavHeader.averageBytesPerSecond;
  }
}

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
package com.google.android.exoplayer.extractor.mp3;

import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

/**
 * MP3 seeker that uses metadata from a VBRI header.
 */
/* package */ final class VbriSeeker implements Mp3Extractor.Seeker {

  /**
   * Returns a {@link VbriSeeker} for seeking in the stream, if required information is present.
   * Returns {@code null} if not. On returning, {@code frame}'s position is not specified so the
   * caller should reset it.
   *
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *     'VBRI' tag.
   * @param position The position (byte offset) of the start of this frame in the stream.
   * @return A {@link VbriSeeker} for seeking in the stream, or {@code null} if the required
   *     information is not present.
   */
  public static VbriSeeker create(MpegAudioHeader mpegAudioHeader, ParsableByteArray frame,
      long position) {
    frame.skipBytes(10);
    int numFrames = frame.readInt();
    if (numFrames <= 0) {
      return null;
    }
    int sampleRate = mpegAudioHeader.sampleRate;
    long durationUs = Util.scaleLargeTimestamp(
        numFrames, 1000000L * (sampleRate >= 32000 ? 1152 : 576), sampleRate);
    int numEntries = frame.readUnsignedShort();
    int scale = frame.readUnsignedShort();
    int entrySize = frame.readUnsignedShort();

    // Read entries in the VBRI header.
    long[] timesUs = new long[numEntries];
    long[] offsets = new long[numEntries];
    long segmentDurationUs = durationUs / numEntries;
    long now = 0;
    int segmentIndex = 0;
    while (segmentIndex < numEntries) {
      int numBytes;
      switch (entrySize) {
        case 1:
          numBytes = frame.readUnsignedByte();
          break;
        case 2:
          numBytes = frame.readUnsignedShort();
          break;
        case 3:
          numBytes = frame.readUnsignedInt24();
          break;
        case 4:
          numBytes = frame.readUnsignedIntToInt();
          break;
        default:
          return null;
      }
      now += segmentDurationUs;
      timesUs[segmentIndex] = now;
      position += numBytes * scale;
      offsets[segmentIndex] = position;

      segmentIndex++;
    }
    return new VbriSeeker(timesUs, offsets, position + mpegAudioHeader.frameSize, durationUs);
  }

  private final long[] timesUs;
  private final long[] positions;
  private final long basePosition;
  private final long durationUs;

  private VbriSeeker(long[] timesUs, long[] positions, long basePosition, long durationUs) {
    this.timesUs = timesUs;
    this.positions = positions;
    this.basePosition = basePosition;
    this.durationUs = durationUs;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getPosition(long timeUs) {
    int index = Util.binarySearchFloor(timesUs, timeUs, false, false);
    return basePosition + (index == -1 ? 0L : positions[index]);
  }

  @Override
  public long getTimeUs(long position) {
    return timesUs[Util.binarySearchFloor(positions, position, true, true)];
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

}

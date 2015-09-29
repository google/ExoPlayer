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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.MpegAudioHeader;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

/**
 * MP3 seeker that uses metadata from a XING header.
 */
/* package */ final class XingSeeker implements Mp3Extractor.Seeker {

  /**
   * Returns a {@link XingSeeker} for seeking in the stream, if required information is present.
   * Returns {@code null} if not. On returning, {@code frame}'s position is not specified so the
   * caller should reset it.
   *
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *    'XING' or 'INFO' tag.
   * @param position The position (byte offset) of the start of this frame in the stream.
   * @param inputLength The length of the stream in bytes.
   * @return A {@link XingSeeker} for seeking in the stream, or {@code null} if the required
   *     information is not present.
   */
  public static XingSeeker create(MpegAudioHeader mpegAudioHeader, ParsableByteArray frame,
      long position, long inputLength) {
    int samplesPerFrame = mpegAudioHeader.samplesPerFrame;
    int sampleRate = mpegAudioHeader.sampleRate;
    long firstFramePosition = position + mpegAudioHeader.frameSize;

    int flags = frame.readInt();
    int frameCount;
    if ((flags & 0x01) != 0x01 || (frameCount = frame.readUnsignedIntToInt()) == 0) {
      // If the frame count is missing/invalid, the header can't be used to determine the duration.
      return null;
    }
    long durationUs = Util.scaleLargeTimestamp(frameCount, samplesPerFrame * 1000000L, sampleRate);
    if ((flags & 0x06) != 0x06) {
      // If the size in bytes or table of contents is missing, the stream is not seekable.
      return new XingSeeker(inputLength, firstFramePosition, durationUs);
    }

    long sizeBytes = frame.readUnsignedIntToInt();
    frame.skipBytes(1);
    long[] tableOfContents = new long[99];
    for (int i = 0; i < 99; i++) {
      tableOfContents[i] = frame.readUnsignedByte();
    }

    // TODO: Handle encoder delay and padding in 3 bytes offset by xingBase + 213 bytes:
    // delay = (frame.readUnsignedByte() << 4) + (frame.readUnsignedByte() >> 4);
    // padding = ((frame.readUnsignedByte() & 0x0F) << 8) + frame.readUnsignedByte();
    return new XingSeeker(inputLength, firstFramePosition, durationUs, tableOfContents, sizeBytes);
  }

  /**
   * Entries are in the range [0, 255], but are stored as long integers for convenience.
   */
  private final long[] tableOfContents;
  private final long firstFramePosition;
  private final long sizeBytes;
  private final long durationUs;
  private final long inputLength;

  private XingSeeker(long inputLength, long firstFramePosition, long durationUs) {
    this(inputLength, firstFramePosition, durationUs, null, 0);
  }

  private XingSeeker(long inputLength, long firstFramePosition, long durationUs,
      long[] tableOfContents, long sizeBytes) {
    this.tableOfContents = tableOfContents;
    this.firstFramePosition = firstFramePosition;
    this.sizeBytes = sizeBytes;
    this.durationUs = durationUs;
    this.inputLength = inputLength;
  }

  @Override
  public boolean isSeekable() {
    return tableOfContents != null;
  }

  @Override
  public long getPosition(long timeUs) {
    if (!isSeekable()) {
      return firstFramePosition;
    }
    float percent = timeUs * 100f / durationUs;
    float fx;
    if (percent <= 0f) {
      fx = 0f;
    } else if (percent >= 100f) {
      fx = 256f;
    } else {
      int a = (int) percent;
      float fa, fb;
      if (a == 0) {
        fa = 0f;
      } else {
        fa = tableOfContents[a - 1];
      }
      if (a < 99) {
        fb = tableOfContents[a];
      } else {
        fb = 256f;
      }
      fx = fa + (fb - fa) * (percent - a);
    }

    long position = (long) ((1.0 / 256) * fx * sizeBytes) + firstFramePosition;
    return inputLength != C.LENGTH_UNBOUNDED ? Math.min(position, inputLength - 1) : position;
  }

  @Override
  public long getTimeUs(long position) {
    if (!isSeekable()) {
      return 0L;
    }
    double offsetByte = 256.0 * (position - firstFramePosition) / sizeBytes;
    int previousIndex = Util.binarySearchFloor(tableOfContents, (long) offsetByte, true, false);
    long previousTime = getTimeUsForTocIndex(previousIndex);
    if (previousIndex == 98) {
      return previousTime;
    }

    // Linearly interpolate the time taking into account the next entry.
    long previousByte = previousIndex == -1 ? 0 : tableOfContents[previousIndex];
    long nextByte = tableOfContents[previousIndex + 1];
    long nextTime = getTimeUsForTocIndex(previousIndex + 1);
    long timeOffset = nextByte == previousByte ? 0 : (long) ((nextTime - previousTime)
        * (offsetByte - previousByte) / (nextByte - previousByte));
    return previousTime + timeOffset;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  /**
   * Returns the time in microseconds corresponding to an index in the table of contents.
   */
  private long getTimeUsForTocIndex(int tocIndex) {
    return durationUs * (tocIndex + 1) / 100;
  }

}

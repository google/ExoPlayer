/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.mp3;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.MpegAudioUtil;

/** Representation of a LAME Xing or Info frame. */
/* package */ final class XingFrame {

  private static final String TAG = "XingHeader";

  /** The header of the Xing or Info frame. */
  public final MpegAudioUtil.Header header;

  /** The frame count, or {@link C#LENGTH_UNSET} if not present in the header. */
  public final long frameCount;

  /**
   * Data size, including the XING frame, or {@link C#LENGTH_UNSET} if not present in the header.
   */
  public final long dataSize;

  /**
   * The number of samples to skip at the start of the stream, or {@link C#LENGTH_UNSET} if not
   * present in the header.
   */
  public final int encoderDelay;

  /**
   * The number of samples to skip at the end of the stream, or {@link C#LENGTH_UNSET} if not
   * present in the header.
   */
  public final int encoderPadding;

  /**
   * Entries are in the range [0, 255], but are stored as long integers for convenience. Null if the
   * table of contents was missing from the header, in which case seeking is not be supported.
   */
  @Nullable public final long[] tableOfContents;

  private XingFrame(
      MpegAudioUtil.Header header,
      long frameCount,
      long dataSize,
      @Nullable long[] tableOfContents,
      int encoderDelay,
      int encoderPadding) {
    this.header = header;
    this.frameCount = frameCount;
    this.dataSize = dataSize;
    this.tableOfContents = tableOfContents;
    this.encoderDelay = encoderDelay;
    this.encoderPadding = encoderPadding;
  }

  /**
   * Returns a {@link XingFrame} containing the info parsed from a LAME Xing (VBR) or Info (CBR)
   * frame.
   *
   * <p>The {@link ParsableByteArray#getPosition()} in {@code frame} when this method exits is
   * undefined.
   *
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *     'Xing' or 'Info' tag.
   */
  public static XingFrame parse(MpegAudioUtil.Header mpegAudioHeader, ParsableByteArray frame) {
    int samplesPerFrame = mpegAudioHeader.samplesPerFrame;
    int sampleRate = mpegAudioHeader.sampleRate;

    int flags = frame.readInt();
    int frameCount = (flags & 0x01) != 0 ? frame.readUnsignedIntToInt() : C.LENGTH_UNSET;
    long dataSize = (flags & 0x02) != 0 ? frame.readUnsignedInt() : C.LENGTH_UNSET;

    long[] tableOfContents;
    if ((flags & 0x04) == 0x04) {
      tableOfContents = new long[100];
      for (int i = 0; i < 100; i++) {
        tableOfContents[i] = frame.readUnsignedByte();
      }
    } else {
      tableOfContents = null;
    }

    if ((flags & 0x8) != 0) {
      frame.skipBytes(4); // Quality indicator
    }

    int encoderDelay;
    int encoderPadding;
    // Skip: version string (9), revision & VBR method (1), lowpass filter (1), replay gain (8),
    //       encoding flags & ATH type (1), bitrate (1).
    int bytesToSkipBeforeEncoderDelayAndPadding = 9 + 1 + 1 + 8 + 1 + 1;
    if (frame.bytesLeft() >= bytesToSkipBeforeEncoderDelayAndPadding + 3) {
      frame.skipBytes(bytesToSkipBeforeEncoderDelayAndPadding);
      int encoderDelayAndPadding = frame.readUnsignedInt24();
      encoderDelay = (encoderDelayAndPadding & 0xFFF000) >> 12;
      encoderPadding = (encoderDelayAndPadding & 0xFFF);
    } else {
      encoderDelay = C.LENGTH_UNSET;
      encoderPadding = C.LENGTH_UNSET;
    }

    return new XingFrame(
        mpegAudioHeader, frameCount, dataSize, tableOfContents, encoderDelay, encoderPadding);
  }
}

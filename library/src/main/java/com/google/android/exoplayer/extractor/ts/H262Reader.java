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
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.NalUnitUtil;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.util.Arrays;
import java.util.Collections;

/**
 * Parses a continuous H262 byte stream and extracts individual frames.
 */
/* package */ final class H262Reader extends ElementaryStreamReader {

  private static final int START_PICTURE = 0x00;
  private static final int START_SEQUENCE_HEADER = 0xB3;
  private static final int START_EXTENSION = 0xB5;
  private static final int START_GROUP = 0xB8;

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // State that should be reset on seek.
  private final boolean[] prefixFlags;
  private final CsdBuffer csdBuffer;
  private boolean foundFirstFrameInGroup;
  private long totalBytesWritten;

  // Per sample state that gets reset at the start of each frame.
  private boolean isKeyframe;
  private long framePosition;
  private long frameTimeUs;

  public H262Reader(TrackOutput output) {
    super(output);
    prefixFlags = new boolean[4];
    csdBuffer = new CsdBuffer(128);
  }

  @Override
  public void seek() {
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    csdBuffer.reset();
    foundFirstFrameInGroup = false;
    totalBytesWritten = 0;
  }

  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.data;

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      int searchOffset = offset;
      while (true) {
        int startCodeOffset = NalUnitUtil.findNalUnit(dataArray, searchOffset, limit, prefixFlags);

        if (startCodeOffset == limit) {
          // We've scanned to the end of the data without finding another start code.
          if (!hasOutputFormat) {
            csdBuffer.onData(dataArray, offset, limit);
          }
          return;
        }

        // We've found a start code with the following value.
        int startCodeValue = data.data[startCodeOffset + 3] & 0xFF;

        if (!hasOutputFormat) {
          // This is the number of bytes from the current offset to the start of the next start
          // code. It may be negative if the start code started in the previously consumed data.
          int lengthToStartCode = startCodeOffset - offset;
          if (lengthToStartCode > 0) {
            csdBuffer.onData(dataArray, offset, startCodeOffset);
          }
          // This is the number of bytes belonging to the next start code that have already been
          // passed to csdDataTargetBuffer.
          int bytesAlreadyPassed = lengthToStartCode < 0 ? -lengthToStartCode : 0;
          if (csdBuffer.onStartCode(startCodeValue, bytesAlreadyPassed)) {
            // The csd data is complete, so we can parse and output the media format.
            output.format(parseMediaFormat(csdBuffer));
            hasOutputFormat = true;
          }
        }

        if (hasOutputFormat && (startCodeValue == START_GROUP || startCodeValue == START_PICTURE)) {
          int bytesWrittenPastStartCode = limit - startCodeOffset;
          if (foundFirstFrameInGroup) {
            int flags = isKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
            int size = (int) (totalBytesWritten - framePosition) - bytesWrittenPastStartCode;
            output.sampleMetadata(frameTimeUs, flags, size, bytesWrittenPastStartCode, null);
            isKeyframe = false;
          }
          if (startCodeValue == START_GROUP) {
            foundFirstFrameInGroup = false;
            isKeyframe = true;
          } else /* startCode == START_PICTURE */ {
            foundFirstFrameInGroup = true;
            frameTimeUs = pesTimeUs;
            framePosition = totalBytesWritten - bytesWrittenPastStartCode;
          }
        }

        offset = startCodeOffset;
        searchOffset = offset + 3;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private static MediaFormat parseMediaFormat(CsdBuffer csdBuffer) {
    byte[] csdData = Arrays.copyOf(csdBuffer.data, csdBuffer.length);

    int firstByte = csdData[4] & 0xFF;
    int secondByte = csdData[5] & 0xFF;
    int thirdByte = csdData[6] & 0xFF;
    int width = (firstByte << 4) | (secondByte >> 4);
    int height = (secondByte & 0x0F) << 8 | thirdByte;

    float pixelWidthHeightRatio = 1f;
    int aspectRatioCode = (csdData[7] & 0xF0) >> 4;
    switch(aspectRatioCode) {
      case 2:
        pixelWidthHeightRatio = (4 * height) / (float) (3 * width);
        break;
      case 3:
        pixelWidthHeightRatio = (16 * height) / (float) (9 * width);
        break;
      case 4:
        pixelWidthHeightRatio = (121 * height) / (float) (100 * width);
        break;
      default:
        // Do nothing.
        break;
    }

    return MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_MPEG2, MediaFormat.NO_VALUE,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, width, height, Collections.singletonList(csdData),
        MediaFormat.NO_VALUE, pixelWidthHeightRatio);
  }

  private static final class CsdBuffer {

    private boolean isFilling;
    private boolean seenExtensionStartCode;

    public int length;
    public byte[] data;

    public CsdBuffer(int initialCapacity) {
      data = new byte[initialCapacity];
    }

    /**
     * Resets the buffer, clearing any data that it holds.
     */
    public void reset() {
      isFilling = false;
      seenExtensionStartCode = false;
      length = 0;
    }

    /**
     * Invoked when a start code is encountered in the stream.
     *
     * @param startCodeValue The start code value.
     * @param bytesAlreadyPassed The number of bytes of the start code that have already been
     *     passed to {@link #onData(byte[], int, int)}, or 0.
     * @return True if the csd data is now complete. False otherwise. If true is returned, neither
     *     this method or {@link #onData(byte[], int, int)} should be called again without an
     *     interleaving call to {@link #reset()}.
     */
    public boolean onStartCode(int startCodeValue, int bytesAlreadyPassed) {
      if (isFilling) {
        if (!seenExtensionStartCode && startCodeValue == START_EXTENSION) {
          seenExtensionStartCode = true;
        } else {
          length -= bytesAlreadyPassed;
          isFilling = false;
          return true;
        }
      } else if (startCodeValue == START_SEQUENCE_HEADER) {
        isFilling = true;
      }
      return false;
    }

    /**
     * Invoked to pass stream data.
     *
     * @param newData Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void onData(byte[] newData, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (data.length < length + readLength) {
        data = Arrays.copyOf(data, (length + readLength) * 2);
      }
      System.arraycopy(newData, offset, data, length, readLength);
      length += readLength;
    }

  }

}

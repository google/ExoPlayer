/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.muxer;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/** NAL unit utilities for start codes and emulation prevention. */
/* package */ final class AnnexBUtils {
  private static final int THREE_BYTE_NAL_START_CODE_SIZE = 3;

  private AnnexBUtils() {}

  /**
   * Splits a {@link ByteBuffer} into individual NAL units (0x000001 or 0x00000001 start code).
   *
   * <p>An {@link IllegalStateException} is thrown if the NAL units are invalid. The NAL units are
   * identified as per ITU-T H264 spec:Annex B.2.
   *
   * <p>The input buffer must have position set to 0 and the position remains unchanged after
   * calling this method.
   */
  public static ImmutableList<ByteBuffer> findNalUnits(ByteBuffer input) {
    if (input.remaining() == 0) {
      return ImmutableList.of();
    }

    int nalStartIndex = C.INDEX_UNSET;
    int inputLimit = input.limit();
    boolean readingNalUnit = false;

    // The input must start with a NAL unit.
    for (int i = 0; i < inputLimit; i++) {
      if (isThreeByteNalStartCode(input, i)) {
        nalStartIndex = i + THREE_BYTE_NAL_START_CODE_SIZE;
        readingNalUnit = true;
        break;
      } else if (input.get(i) == 0) {
        // Skip the leading zeroes.
      } else {
        throw new IllegalStateException("Sample does not start with a NAL unit");
      }
    }

    ImmutableList.Builder<ByteBuffer> nalUnits = new ImmutableList.Builder<>();
    // Look for start code 0x000001. The logic will work for 0x00000001 start code as well because a
    // NAL unit gets ended even when 0x000000 (which is a prefix of 0x00000001 start code) is found.
    for (int i = nalStartIndex; i < inputLimit; ) {
      if (readingNalUnit) {
        // Found next start code 0x000001.
        if (isThreeByteNalStartCode(input, i)) {
          nalUnits.add(getBytes(input, nalStartIndex, i - nalStartIndex));
          i = i + THREE_BYTE_NAL_START_CODE_SIZE;
          nalStartIndex = i;
          continue;
        } else if (isThreeBytesZeroSequence(input, i)) {
          // Found code 0x000000; The previous NAL unit should be ended.
          nalUnits.add(getBytes(input, nalStartIndex, i - nalStartIndex));
          // Stop reading NAL unit until next start code is found.
          readingNalUnit = false;
          i++;
        } else {
          // Continue reading NAL unit.
          i++;
        }
      } else {
        // Found new start code 0x000001.
        if (isThreeByteNalStartCode(input, i)) {
          i = i + THREE_BYTE_NAL_START_CODE_SIZE;
          nalStartIndex = i;
          readingNalUnit = true;
        } else if (input.get(i) == 0x00) {
          // Skip trailing zeroes.
          i++;
        } else {
          // Found garbage data.
          throw new IllegalStateException("Invalid NAL units");
        }
      }

      // Add the last NAL unit.
      if (i == inputLimit && readingNalUnit) {
        nalUnits.add(getBytes(input, nalStartIndex, i - nalStartIndex));
      }
    }
    input.rewind();
    return nalUnits.build();
  }

  /** Removes Annex-B emulation prevention bytes from a buffer. */
  public static ByteBuffer stripEmulationPrevention(ByteBuffer input) {
    // For simplicity, we allocate the same number of bytes (although the eventual number might be
    // smaller).
    ByteBuffer output = ByteBuffer.allocate(input.limit());
    int zerosSeen = 0;
    for (int i = 0; i < input.limit(); i++) {
      boolean lookingAtEmulationPreventionByte = input.get(i) == 0x03 && zerosSeen >= 2;

      // Only copy bytes if they aren't emulation prevention bytes.
      if (!lookingAtEmulationPreventionByte) {
        output.put(input.get(i));
      }

      if (input.get(i) == 0) {
        zerosSeen++;
      } else {
        zerosSeen = 0;
      }
    }

    output.flip();

    return output;
  }

  /**
   * Returns whether the sample of the given MIME type will contain NAL units in Annex-B format
   * (ISO/IEC 14496-10 Annex B, which uses start codes to delineate NAL units).
   */
  public static boolean doesSampleContainAnnexBNalUnits(String sampleMimeType) {
    return sampleMimeType.equals(MimeTypes.VIDEO_H264)
        || sampleMimeType.equals(MimeTypes.VIDEO_H265);
  }

  private static boolean isThreeByteNalStartCode(ByteBuffer input, int currentIndex) {
    return (currentIndex < input.limit() - THREE_BYTE_NAL_START_CODE_SIZE
        && input.get(currentIndex) == 0x00
        && input.get(currentIndex + 1) == 0x00
        && input.get(currentIndex + 2) == 0x01);
  }

  private static boolean isThreeBytesZeroSequence(ByteBuffer input, int currentIndex) {
    return (currentIndex < input.limit() - THREE_BYTE_NAL_START_CODE_SIZE
        && input.get(currentIndex) == 0x00
        && input.get(currentIndex + 1) == 0x00
        && input.get(currentIndex + 2) == 0x00);
  }

  private static ByteBuffer getBytes(ByteBuffer buf, int offset, int length) {
    ByteBuffer result = buf.duplicate();
    result.position(offset);
    result.limit(offset + length);
    return result.slice();
  }
}

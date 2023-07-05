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
package com.google.android.exoplayer2.muxer;

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/**
 * NAL unit utilities for start codes and emulation prevention.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class AnnexBUtils {
  private AnnexBUtils() {}

  /**
   * Splits a {@link ByteBuffer} into individual NAL units (0x00000001 start code).
   *
   * <p>An empty list is returned if the input is not NAL units.
   *
   * <p>The position of the input buffer is unchanged after calling this method.
   */
  public static ImmutableList<ByteBuffer> findNalUnits(ByteBuffer input) {
    if (input.remaining() < 4 || input.getInt(0) != 1) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<ByteBuffer> nalUnits = new ImmutableList.Builder<>();

    int lastStart = 4;
    int zerosSeen = 0;

    for (int i = 4; i < input.limit(); i++) {
      if (input.get(i) == 1 && zerosSeen >= 3) {
        // We're just looking at a start code.
        nalUnits.add(getBytes(input, lastStart, i - 3 - lastStart));
        lastStart = i + 1;
      }

      // Handle the end of the stream.
      if (i == input.limit() - 1) {
        nalUnits.add(getBytes(input, lastStart, input.limit() - lastStart));
      }

      if (input.get(i) == 0) {
        zerosSeen++;
      } else {
        zerosSeen = 0;
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

  private static ByteBuffer getBytes(ByteBuffer buf, int offset, int length) {
    ByteBuffer result = buf.duplicate();
    result.position(offset);
    result.limit(offset + length);
    return result.slice();
  }
}

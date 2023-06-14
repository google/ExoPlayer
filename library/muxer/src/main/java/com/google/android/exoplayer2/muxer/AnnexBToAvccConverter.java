/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/**
 * Converts a buffer containing H.264/H.265 NAL units from the Annex-B format (ISO/IEC 14496-14
 * Annex B, which uses start codes to delineate NAL units) to the avcC format (ISO/IEC 14496-15,
 * which uses length prefixes).
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface AnnexBToAvccConverter {
  /** Default implementation for {@link AnnexBToAvccConverter}. */
  AnnexBToAvccConverter DEFAULT =
      (ByteBuffer inputBuffer) -> {
        if (!inputBuffer.hasRemaining()) {
          return;
        }

        checkArgument(
            inputBuffer.position() == 0, "The input buffer should have position set to 0.");

        ImmutableList<ByteBuffer> nalUnitList = AnnexBUtils.findNalUnits(inputBuffer);

        for (int i = 0; i < nalUnitList.size(); i++) {
          int currentNalUnitLength = nalUnitList.get(i).remaining();

          // Replace the start code with the NAL unit length.
          inputBuffer.putInt(currentNalUnitLength);

          // Shift the input buffer's position to next start code.
          int newPosition = inputBuffer.position() + currentNalUnitLength;
          inputBuffer.position(newPosition);
        }
        inputBuffer.rewind();
      };

  /**
   * Processes a buffer in-place.
   *
   * <p>Expects a {@link ByteBuffer} input with a zero offset.
   *
   * @param inputBuffer The buffer to be converted.
   */
  void process(ByteBuffer inputBuffer);
}

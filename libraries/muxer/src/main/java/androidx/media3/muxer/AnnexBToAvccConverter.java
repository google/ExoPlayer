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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/**
 * Converts a buffer containing H.264/H.265 NAL units from the Annex-B format (ISO/IEC 14496-14
 * Annex B, which uses start codes to delineate NAL units) to the avcC format (ISO/IEC 14496-15,
 * which uses length prefixes).
 */
@UnstableApi
public interface AnnexBToAvccConverter {
  /** Default implementation for {@link AnnexBToAvccConverter}. */
  AnnexBToAvccConverter DEFAULT =
      (ByteBuffer inputBuffer) -> {
        if (!inputBuffer.hasRemaining()) {
          return inputBuffer;
        }

        checkArgument(
            inputBuffer.position() == 0, "The input buffer should have position set to 0.");

        ImmutableList<ByteBuffer> nalUnitList = AnnexBUtils.findNalUnits(inputBuffer);

        int totalBytesNeeded = 0;

        for (int i = 0; i < nalUnitList.size(); i++) {
          // 4 bytes to store NAL unit length.
          totalBytesNeeded += 4 + nalUnitList.get(i).remaining();
        }

        ByteBuffer outputBuffer = ByteBuffer.allocate(totalBytesNeeded);

        for (int i = 0; i < nalUnitList.size(); i++) {
          ByteBuffer currentNalUnit = nalUnitList.get(i);
          int currentNalUnitLength = currentNalUnit.remaining();

          // Rewrite NAL units with NAL unit length in place of start code.
          outputBuffer.putInt(currentNalUnitLength);
          outputBuffer.put(currentNalUnit);
        }
        outputBuffer.rewind();
        return outputBuffer;
      };

  /**
   * Returns the processed {@link ByteBuffer}.
   *
   * <p>Expects a {@link ByteBuffer} input with a zero offset.
   *
   * @param inputBuffer The buffer to be converted.
   */
  ByteBuffer process(ByteBuffer inputBuffer);
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;

/**
 * A {@link BufferProcessor} that converts PCM input buffers from a specified input bit depth to
 * {@link C#ENCODING_PCM_16BIT} in preparation for writing to an {@link android.media.AudioTrack}.
 */
/* package */ final class ResamplingBufferProcessor implements BufferProcessor {

  @C.PcmEncoding
  private final int inputEncoding;

  /**
   * Creates a new buffer processor for resampling input in the specified encoding.
   *
   * @param inputEncoding The PCM encoding of input buffers.
   * @throws IllegalArgumentException Thrown if the input encoding is not PCM or its bit depth is
   *     not 8, 24 or 32-bits.
   */
  public ResamplingBufferProcessor(@C.PcmEncoding int inputEncoding) {
    Assertions.checkArgument(inputEncoding == C.ENCODING_PCM_8BIT
        || inputEncoding == C.ENCODING_PCM_24BIT || inputEncoding == C.ENCODING_PCM_32BIT);
    this.inputEncoding = inputEncoding;
  }

  @Override
  public ByteBuffer handleBuffer(ByteBuffer input, ByteBuffer output) {
    int offset = input.position();
    int limit = input.limit();
    int size = limit - offset;

    int resampledSize;
    switch (inputEncoding) {
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
        resampledSize = size / 2;
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    ByteBuffer resampledBuffer = output;
    if (resampledBuffer == null || resampledBuffer.capacity() < resampledSize) {
      resampledBuffer = ByteBuffer.allocateDirect(resampledSize);
    }
    resampledBuffer.position(0);
    resampledBuffer.limit(resampledSize);

    // Samples are little endian.
    switch (inputEncoding) {
      case C.ENCODING_PCM_8BIT:
        // 8->16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (int i = offset; i < limit; i++) {
          resampledBuffer.put((byte) 0);
          resampledBuffer.put((byte) ((input.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24->16 bit resampling. Drop the least significant byte.
        for (int i = offset; i < limit; i += 3) {
          resampledBuffer.put(input.get(i + 1));
          resampledBuffer.put(input.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32->16 bit resampling. Drop the two least significant bytes.
        for (int i = offset; i < limit; i += 4) {
          resampledBuffer.put(input.get(i + 2));
          resampledBuffer.put(input.get(i + 3));
        }
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    resampledBuffer.position(0);
    return resampledBuffer;
  }

}

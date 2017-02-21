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
import java.nio.ByteBuffer;

/**
 * A {@link BufferProcessor} that outputs buffers in {@link C#ENCODING_PCM_16BIT}.
 */
/* package */ final class ResamplingBufferProcessor implements BufferProcessor {

  @C.PcmEncoding
  private int encoding;
  private ByteBuffer outputBuffer;

  public ResamplingBufferProcessor() {
    encoding = C.ENCODING_INVALID;
  }

  @Override
  public void configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_8BIT && encoding != C.ENCODING_PCM_16BIT
        && encoding != C.ENCODING_PCM_24BIT && encoding != C.ENCODING_PCM_32BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (encoding == C.ENCODING_PCM_16BIT) {
      outputBuffer = null;
    }
    this.encoding = encoding;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public ByteBuffer handleBuffer(ByteBuffer buffer) {
    int position = buffer.position();
    int limit = buffer.limit();
    int size = limit - position;

    int resampledSize;
    switch (encoding) {
      case C.ENCODING_PCM_16BIT:
        // No processing required.
        return buffer;
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
        resampledSize = size / 2;
        break;
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    if (outputBuffer == null || outputBuffer.capacity() < resampledSize) {
      outputBuffer = ByteBuffer.allocateDirect(resampledSize).order(buffer.order());
    } else {
      outputBuffer.clear();
    }

    // Samples are little endian.
    switch (encoding) {
      case C.ENCODING_PCM_8BIT:
        // 8->16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (int i = position; i < limit; i++) {
          outputBuffer.put((byte) 0);
          outputBuffer.put((byte) ((buffer.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24->16 bit resampling. Drop the least significant byte.
        for (int i = position; i < limit; i += 3) {
          outputBuffer.put(buffer.get(i + 1));
          outputBuffer.put(buffer.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32->16 bit resampling. Drop the two least significant bytes.
        for (int i = position; i < limit; i += 4) {
          outputBuffer.put(buffer.get(i + 2));
          outputBuffer.put(buffer.get(i + 3));
        }
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    outputBuffer.flip();
    return outputBuffer;
  }

  @Override
  public void flush() {
    // Do nothing.
  }

  @Override
  public void release() {
    outputBuffer = null;
  }

}

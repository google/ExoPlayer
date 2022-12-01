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

package androidx.media3.transformer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* package */ final class SilentAudioGenerator {
  private static final int DEFAULT_BUFFER_SIZE = 4096;

  private final ByteBuffer internalBuffer;

  private long remainingBytesToOutput;

  public SilentAudioGenerator(long totalDurationUs, long sampleRate, int frameSize) {
    remainingBytesToOutput = (sampleRate * frameSize * totalDurationUs) / 1_000_000L;
    internalBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE).order(ByteOrder.nativeOrder());
    internalBuffer.flip();
  }

  public ByteBuffer getBuffer() {
    if (!internalBuffer.hasRemaining()) {
      // "next" buffer.
      internalBuffer.clear();
      if (remainingBytesToOutput < internalBuffer.capacity()) {
        internalBuffer.limit((int) remainingBytesToOutput);
      }
      // Only reduce remaining bytes when we "generate" a new one.
      remainingBytesToOutput -= internalBuffer.remaining();
    }
    return internalBuffer;
  }

  public boolean isEnded() {
    return !internalBuffer.hasRemaining() && remainingBytesToOutput == 0;
  }
}

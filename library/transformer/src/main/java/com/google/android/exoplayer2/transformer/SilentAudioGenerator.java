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

package com.google.android.exoplayer2.transformer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

@Deprecated
/* package */ final class SilentAudioGenerator {
  private static final int DEFAULT_BUFFER_SIZE_FRAMES = 1024;

  private final AudioFormat audioFormat;
  private final ByteBuffer internalBuffer;
  private final AtomicLong remainingBytesToOutput;

  public SilentAudioGenerator(AudioFormat audioFormat) {
    this.audioFormat = audioFormat;
    internalBuffer =
        ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE_FRAMES * audioFormat.bytesPerFrame)
            .order(ByteOrder.nativeOrder());
    internalBuffer.flip();
    remainingBytesToOutput = new AtomicLong();
  }

  /**
   * Adds a silence duration to generate.
   *
   * <p>Can be called from any thread.
   *
   * @param durationUs The duration of the additional silence to generate, in microseconds.
   */
  public void addSilence(long durationUs) {
    // The number of frames is not a timestamp, however this utility method provides
    // overflow-safe multiplication & division.
    long outputFrameCount =
        Util.scaleLargeTimestamp(
            /* timestamp= */ audioFormat.sampleRate,
            /* multiplier= */ durationUs,
            /* divisor= */ C.MICROS_PER_SECOND);

    remainingBytesToOutput.addAndGet(audioFormat.bytesPerFrame * outputFrameCount);
  }

  public ByteBuffer getBuffer() {
    long remainingBytesToOutput = this.remainingBytesToOutput.get();
    if (!internalBuffer.hasRemaining()) {
      // "next" buffer.
      internalBuffer.clear();
      if (remainingBytesToOutput < internalBuffer.capacity()) {
        internalBuffer.limit((int) remainingBytesToOutput);
      }
      // Only reduce remaining bytes when we "generate" a new one.
      this.remainingBytesToOutput.addAndGet(-internalBuffer.remaining());
    }
    return internalBuffer;
  }

  public boolean hasRemaining() {
    return internalBuffer.hasRemaining() || remainingBytesToOutput.get() > 0;
  }
}

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

import static java.lang.Math.min;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that changes the speed of audio samples depending on their timestamp.
 */
// TODO(b/198772621): Consider making the processor inactive and skipping it in the processor chain
//  when speed is 1.
/* package */ final class SpeedChangingAudioProcessor extends BaseAudioProcessor {

  /** The speed provider that provides the speed for each timestamp. */
  private final SpeedProvider speedProvider;
  /**
   * The {@link SonicAudioProcessor} used to change the speed, when needed. If there is no speed
   * change required, the input buffer is copied to the output buffer and this processor is not
   * used.
   */
  private final SonicAudioProcessor sonicAudioProcessor;

  private float currentSpeed;
  private long bytesRead;
  private boolean endOfStreamQueuedToSonic;

  public SpeedChangingAudioProcessor(SpeedProvider speedProvider) {
    this.speedProvider = speedProvider;
    sonicAudioProcessor = new SonicAudioProcessor();
    currentSpeed = 1f;
  }

  @Override
  @CanIgnoreReturnValue
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    return sonicAudioProcessor.configure(inputAudioFormat);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    long timeUs =
        Util.scaleLargeTimestamp(
            /* timestamp= */ bytesRead,
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ (long) inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame);
    float newSpeed = speedProvider.getSpeed(timeUs);
    if (newSpeed != currentSpeed) {
      currentSpeed = newSpeed;
      if (isUsingSonic()) {
        sonicAudioProcessor.setSpeed(newSpeed);
        sonicAudioProcessor.setPitch(newSpeed);
      }
      flush();
    }

    int inputBufferLimit = inputBuffer.limit();
    long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(timeUs);
    int bytesToNextSpeedChange;
    if (nextSpeedChangeTimeUs != C.TIME_UNSET) {
      bytesToNextSpeedChange =
          (int)
              Util.scaleLargeTimestamp(
                  /* timestamp= */ nextSpeedChangeTimeUs - timeUs,
                  /* multiplier= */ (long) inputAudioFormat.sampleRate
                      * inputAudioFormat.bytesPerFrame,
                  /* divisor= */ C.MICROS_PER_SECOND);
      int bytesToNextFrame =
          inputAudioFormat.bytesPerFrame - bytesToNextSpeedChange % inputAudioFormat.bytesPerFrame;
      if (bytesToNextFrame != inputAudioFormat.bytesPerFrame) {
        bytesToNextSpeedChange += bytesToNextFrame;
      }
      // Update the input buffer limit to make sure that all samples processed have the same speed.
      inputBuffer.limit(min(inputBufferLimit, inputBuffer.position() + bytesToNextSpeedChange));
    } else {
      bytesToNextSpeedChange = C.LENGTH_UNSET;
    }

    long startPosition = inputBuffer.position();
    if (isUsingSonic()) {
      sonicAudioProcessor.queueInput(inputBuffer);
      if (bytesToNextSpeedChange != C.LENGTH_UNSET
          && (inputBuffer.position() - startPosition) == bytesToNextSpeedChange) {
        sonicAudioProcessor.queueEndOfStream();
        endOfStreamQueuedToSonic = true;
      }
    } else {
      ByteBuffer buffer = replaceOutputBuffer(/* size= */ inputBuffer.remaining());
      buffer.put(inputBuffer);
      buffer.flip();
    }
    bytesRead += inputBuffer.position() - startPosition;
    inputBuffer.limit(inputBufferLimit);
  }

  @Override
  protected void onQueueEndOfStream() {
    if (!endOfStreamQueuedToSonic) {
      sonicAudioProcessor.queueEndOfStream();
      endOfStreamQueuedToSonic = true;
    }
  }

  @Override
  public ByteBuffer getOutput() {
    return isUsingSonic() ? sonicAudioProcessor.getOutput() : super.getOutput();
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && sonicAudioProcessor.isEnded();
  }

  @Override
  protected void onFlush() {
    sonicAudioProcessor.flush();
    endOfStreamQueuedToSonic = false;
  }

  @Override
  protected void onReset() {
    currentSpeed = 1f;
    bytesRead = 0;
    sonicAudioProcessor.reset();
    endOfStreamQueuedToSonic = false;
  }

  private boolean isUsingSonic() {
    return currentSpeed != 1f;
  }
}

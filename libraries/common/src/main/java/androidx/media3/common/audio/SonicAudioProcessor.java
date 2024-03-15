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
package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link AudioProcessor} that uses the Sonic library to modify audio speed/pitch/sample rate.
 */
@UnstableApi
public class SonicAudioProcessor implements AudioProcessor {

  /** Indicates that the output sample rate should be the same as the input. */
  public static final int SAMPLE_RATE_NO_CHANGE = -1;

  /** The threshold below which the difference between two pitch/speed factors is negligible. */
  private static final float CLOSE_THRESHOLD = 0.0001f;

  /**
   * The minimum number of output bytes required for duration scaling to be calculated using the
   * input and output byte counts, rather than using the current playback speed.
   */
  private static final int MIN_BYTES_FOR_DURATION_SCALING_CALCULATION = 1024;

  private int pendingOutputSampleRate;
  private float speed;
  private float pitch;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private AudioFormat inputAudioFormat;
  private AudioFormat outputAudioFormat;

  private boolean pendingSonicRecreation;
  @Nullable private Sonic sonic;
  private ByteBuffer buffer;
  private ShortBuffer shortBuffer;
  private ByteBuffer outputBuffer;
  private long inputBytes;
  private long outputBytes;
  private boolean inputEnded;

  /** Creates a new Sonic audio processor. */
  public SonicAudioProcessor() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
  }

  /**
   * Sets the target playback speed. This method may only be called after draining data through the
   * processor. The value returned by {@link #isActive()} may change, and the processor must be
   * {@link #flush() flushed} before queueing more data.
   *
   * @param speed The target factor by which playback should be sped up.
   */
  public final void setSpeed(float speed) {
    if (this.speed != speed) {
      this.speed = speed;
      pendingSonicRecreation = true;
    }
  }

  /**
   * Sets the target playback pitch. This method may only be called after draining data through the
   * processor. The value returned by {@link #isActive()} may change, and the processor must be
   * {@link #flush() flushed} before queueing more data.
   *
   * @param pitch The target pitch.
   */
  public final void setPitch(float pitch) {
    if (this.pitch != pitch) {
      this.pitch = pitch;
      pendingSonicRecreation = true;
    }
  }

  /**
   * Sets the sample rate for output audio, in Hertz. Pass {@link #SAMPLE_RATE_NO_CHANGE} to output
   * audio at the same sample rate as the input. After calling this method, call {@link
   * #configure(AudioFormat)} to configure the processor with the new sample rate.
   *
   * @param sampleRateHz The sample rate for output audio, in Hertz.
   * @see #configure(AudioFormat)
   */
  public final void setOutputSampleRateHz(int sampleRateHz) {
    pendingOutputSampleRate = sampleRateHz;
  }

  /**
   * Returns the media duration corresponding to the specified playout duration, taking speed
   * adjustment into account.
   *
   * <p>The scaling performed by this method will use the actual playback speed achieved by the
   * audio processor, on average, since it was last flushed. This may differ very slightly from the
   * target playback speed.
   *
   * @param playoutDuration The playout duration to scale.
   * @return The corresponding media duration, in the same units as {@code duration}.
   */
  public final long getMediaDuration(long playoutDuration) {
    if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      long processedInputBytes = inputBytes - checkNotNull(sonic).getPendingInputBytes();
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? Util.scaleLargeTimestamp(playoutDuration, processedInputBytes, outputBytes)
          : Util.scaleLargeTimestamp(
              playoutDuration,
              processedInputBytes * outputAudioFormat.sampleRate,
              outputBytes * inputAudioFormat.sampleRate);
    } else {
      return (long) ((double) speed * playoutDuration);
    }
  }

  /**
   * Returns the playout duration corresponding to the specified media duration, taking speed
   * adjustment into account.
   *
   * <p>The scaling performed by this method will use the actual playback speed achieved by the
   * audio processor, on average, since it was last flushed. This may differ very slightly from the
   * target playback speed.
   *
   * @param mediaDuration The media duration to scale.
   * @return The corresponding playout duration, in the same units as {@code mediaDuration}.
   */
  public final long getPlayoutDuration(long mediaDuration) {
    if (outputBytes >= MIN_BYTES_FOR_DURATION_SCALING_CALCULATION) {
      long processedInputBytes = inputBytes - checkNotNull(sonic).getPendingInputBytes();
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? Util.scaleLargeTimestamp(mediaDuration, outputBytes, processedInputBytes)
          : Util.scaleLargeTimestamp(
              mediaDuration,
              outputBytes * inputAudioFormat.sampleRate,
              processedInputBytes * outputAudioFormat.sampleRate);
    } else {
      return (long) (mediaDuration / (double) speed);
    }
  }

  /** Returns the number of bytes processed since last flush or reset. */
  public final long getProcessedInputBytes() {
    return inputBytes - checkNotNull(sonic).getPendingInputBytes();
  }

  @Override
  public long getDurationAfterProcessorApplied(long durationUs) {
    return getPlayoutDuration(durationUs);
  }

  @Override
  public final AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    int outputSampleRateHz =
        pendingOutputSampleRate == SAMPLE_RATE_NO_CHANGE
            ? inputAudioFormat.sampleRate
            : pendingOutputSampleRate;
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat =
        new AudioFormat(outputSampleRateHz, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT);
    pendingSonicRecreation = true;
    return pendingOutputAudioFormat;
  }

  @Override
  public final boolean isActive() {
    return pendingOutputAudioFormat.sampleRate != Format.NO_VALUE
        && (Math.abs(speed - 1f) >= CLOSE_THRESHOLD
            || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD
            || pendingOutputAudioFormat.sampleRate != pendingInputAudioFormat.sampleRate);
  }

  @Override
  public final void queueInput(ByteBuffer inputBuffer) {
    if (!inputBuffer.hasRemaining()) {
      return;
    }
    Sonic sonic = checkNotNull(this.sonic);
    ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
    int inputSize = inputBuffer.remaining();
    inputBytes += inputSize;
    sonic.queueInput(shortBuffer);
    inputBuffer.position(inputBuffer.position() + inputSize);
  }

  @Override
  public final void queueEndOfStream() {
    // TODO(internal b/174554082): assert sonic is non-null here and in getOutput.
    if (sonic != null) {
      sonic.queueEndOfStream();
    }
    inputEnded = true;
  }

  @Override
  public final ByteBuffer getOutput() {
    @Nullable Sonic sonic = this.sonic;
    if (sonic != null) {
      int outputSize = sonic.getOutputSize();
      if (outputSize > 0) {
        if (buffer.capacity() < outputSize) {
          buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
          shortBuffer = buffer.asShortBuffer();
        } else {
          buffer.clear();
          shortBuffer.clear();
        }
        sonic.getOutput(shortBuffer);
        outputBytes += outputSize;
        buffer.limit(outputSize);
        outputBuffer = buffer;
      }
    }
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @Override
  public final boolean isEnded() {
    return inputEnded && (sonic == null || sonic.getOutputSize() == 0);
  }

  @Override
  public final void flush() {
    if (isActive()) {
      inputAudioFormat = pendingInputAudioFormat;
      outputAudioFormat = pendingOutputAudioFormat;
      if (pendingSonicRecreation) {
        sonic =
            new Sonic(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                speed,
                pitch,
                outputAudioFormat.sampleRate);
      } else if (sonic != null) {
        sonic.flush();
      }
    }
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public final void reset() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
    pendingSonicRecreation = false;
    sonic = null;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }
}

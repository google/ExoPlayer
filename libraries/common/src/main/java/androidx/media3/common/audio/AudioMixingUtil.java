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

package androidx.media3.common.audio;

import static androidx.media3.common.util.Util.constrainValue;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** Utility for mixing audio buffers. */
@UnstableApi
public final class AudioMixingUtil {

  // Float PCM samples are zero-centred within the [-1.0, 1.0] range.
  private static final float FLOAT_PCM_MIN_VALUE = -1.0f;
  private static final float FLOAT_PCM_MAX_VALUE = 1.0f;

  public static boolean canMix(AudioFormat audioFormat) {
    if (audioFormat.sampleRate == Format.NO_VALUE) {
      return false;
    }
    if (audioFormat.channelCount == Format.NO_VALUE) {
      return false;
    }
    return audioFormat.encoding == C.ENCODING_PCM_16BIT
        || audioFormat.encoding == C.ENCODING_PCM_FLOAT;
  }

  public static boolean canMix(AudioFormat inputAudioFormat, AudioFormat outputAudioFormat) {
    if (inputAudioFormat.sampleRate != outputAudioFormat.sampleRate) {
      return false;
    }
    if (!canMix(inputAudioFormat)) {
      return false;
    }
    if (!canMix(outputAudioFormat)) {
      return false;
    }
    return true;
  }

  /**
   * Mixes audio from the input buffer into the mixing buffer.
   *
   * <p>{@link #canMix(AudioFormat, AudioFormat)} must return {@code true} for the formats.
   *
   * @param inputBuffer Input audio {@link ByteBuffer}, the position is advanced by the amount of
   *     bytes read and mixed.
   * @param inputAudioFormat {@link AudioFormat} of the {@code inputBuffer}.
   * @param mixingBuffer Mixing audio {@link ByteBuffer}, the position is advanced by the amount of
   *     bytes written.
   * @param mixingAudioFormat {@link AudioFormat} of the {@code mixingBuffer}.
   * @param matrix Scaled channel mapping from input to output.
   * @param framesToMix Number of audio frames to mix. Must be within the bounds of both buffers.
   * @param accumulate Whether to accumulate with the existing samples in the mixing buffer.
   * @param clipFloatOutput Whether to clip the output signal to be in the [-1.0, 1.0] range if the
   *     output encoding is {@link C#ENCODING_PCM_FLOAT}.
   * @return The {@code mixingBuffer}, for convenience.
   */
  public static ByteBuffer mix(
      ByteBuffer inputBuffer,
      AudioFormat inputAudioFormat,
      ByteBuffer mixingBuffer,
      AudioFormat mixingAudioFormat,
      ChannelMixingMatrix matrix,
      int framesToMix,
      boolean accumulate,
      boolean clipFloatOutput) {

    boolean int16Input = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;
    boolean int16Output = mixingAudioFormat.encoding == C.ENCODING_PCM_16BIT;
    int inputChannels = matrix.getInputChannelCount();
    int outputChannels = matrix.getOutputChannelCount();
    float[] inputFrame = new float[inputChannels];
    float[] outputFrame = new float[outputChannels];

    for (int i = 0; i < framesToMix; i++) {
      if (accumulate) {
        int position = mixingBuffer.position();
        for (int outputChannel = 0; outputChannel < outputChannels; outputChannel++) {
          outputFrame[outputChannel] =
              getPcmSample(mixingBuffer, /* int16Buffer= */ int16Output, int16Output);
        }
        mixingBuffer.position(position);
      }

      for (int inputChannel = 0; inputChannel < inputChannels; inputChannel++) {
        inputFrame[inputChannel] =
            getPcmSample(inputBuffer, /* int16Buffer= */ int16Input, int16Output);
      }

      for (int outputChannel = 0; outputChannel < outputChannels; outputChannel++) {
        for (int inputChannel = 0; inputChannel < inputChannels; inputChannel++) {
          outputFrame[outputChannel] +=
              inputFrame[inputChannel] * matrix.getMixingCoefficient(inputChannel, outputChannel);
        }

        if (int16Output) {
          mixingBuffer.putShort(
              (short) constrainValue(outputFrame[outputChannel], Short.MIN_VALUE, Short.MAX_VALUE));
        } else {
          mixingBuffer.putFloat(
              clipFloatOutput
                  ? constrainValue(
                      outputFrame[outputChannel], FLOAT_PCM_MIN_VALUE, FLOAT_PCM_MAX_VALUE)
                  : outputFrame[outputChannel]);
        }

        outputFrame[outputChannel] = 0;
      }
    }
    return mixingBuffer;
  }

  /**
   * Gets the next sample from the {@link ByteBuffer} of raw audio.
   *
   * <p>Int16 PCM range of values: [{@link Short#MIN_VALUE}, {@link Short#MAX_VALUE}].
   *
   * <p>Float PCM range of values: [-1.0, 1.0].
   *
   * @param buffer The {@link ByteBuffer} containing raw audio.
   * @param int16Buffer Whether the buffer contains {@link C#ENCODING_PCM_16BIT} audio. Use {@code
   *     false} if buffer contains {@link C#ENCODING_PCM_FLOAT} audio.
   * @param int16Output Whether the returned sample should be in the {@link C#ENCODING_PCM_16BIT}
   *     range of values. If {@code false}, Float PCM range is used.
   * @return The next sample from the buffer.
   */
  private static float getPcmSample(ByteBuffer buffer, boolean int16Buffer, boolean int16Output) {
    if (int16Output) {
      return int16Buffer ? buffer.getShort() : floatSampleToInt16Pcm(buffer.getFloat());
    } else {
      return int16Buffer ? int16SampleToFloatPcm(buffer.getShort()) : buffer.getFloat();
    }
  }

  private static float floatSampleToInt16Pcm(float floatPcmValue) {
    return constrainValue(
        floatPcmValue * (floatPcmValue < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE),
        Short.MIN_VALUE,
        Short.MAX_VALUE);
  }

  private static float int16SampleToFloatPcm(short shortPcmValue) {
    // Short.MIN_VALUE != -Short.MAX_VALUE, so use different conversion for positive and negative.
    return shortPcmValue / (float) (shortPcmValue < 0 ? -Short.MIN_VALUE : Short.MAX_VALUE);
  }

  private AudioMixingUtil() {}
}

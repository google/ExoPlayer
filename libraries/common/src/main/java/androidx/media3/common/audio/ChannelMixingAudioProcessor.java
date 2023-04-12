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

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that handles mixing and scaling audio channels. Call {@link
 * #putChannelMixingMatrix(ChannelMixingMatrix)} specifying mixing matrices to apply for each
 * possible input channel count before using the audio processor. Input and output are 16-bit PCM.
 */
@UnstableApi
public final class ChannelMixingAudioProcessor extends BaseAudioProcessor {

  private final SparseArray<ChannelMixingMatrix> matrixByInputChannelCount;

  /** Creates a new audio processor for mixing and scaling audio channels. */
  public ChannelMixingAudioProcessor() {
    matrixByInputChannelCount = new SparseArray<>();
  }

  /**
   * Stores a channel mixing matrix for processing audio with a given {@link
   * ChannelMixingMatrix#getInputChannelCount() channel count}. Overwrites any previously stored
   * matrix for the same input channel count.
   */
  public void putChannelMixingMatrix(ChannelMixingMatrix matrix) {
    int inputChannelCount = matrix.getInputChannelCount();
    matrixByInputChannelCount.put(inputChannelCount, matrix);
  }

  @Override
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    @Nullable
    ChannelMixingMatrix channelMixingMatrix =
        matrixByInputChannelCount.get(inputAudioFormat.channelCount);
    if (channelMixingMatrix == null) {
      throw new UnhandledAudioFormatException(
          "No mixing matrix for input channel count", inputAudioFormat);
    }
    if (channelMixingMatrix.isIdentity()) {
      return AudioFormat.NOT_SET;
    }
    return new AudioFormat(
        inputAudioFormat.sampleRate,
        channelMixingMatrix.getOutputChannelCount(),
        C.ENCODING_PCM_16BIT);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    ChannelMixingMatrix channelMixingMatrix =
        checkStateNotNull(matrixByInputChannelCount.get(inputAudioFormat.channelCount));

    int inputFramesToMix = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame;
    ByteBuffer outputBuffer =
        replaceOutputBuffer(inputFramesToMix * outputAudioFormat.bytesPerFrame);
    int inputChannelCount = channelMixingMatrix.getInputChannelCount();
    int outputChannelCount = channelMixingMatrix.getOutputChannelCount();
    float[] outputFrame = new float[outputChannelCount];
    while (inputBuffer.hasRemaining()) {
      for (int inputChannelIndex = 0; inputChannelIndex < inputChannelCount; inputChannelIndex++) {
        short inputValue = inputBuffer.getShort();
        for (int outputChannelIndex = 0;
            outputChannelIndex < outputChannelCount;
            outputChannelIndex++) {
          outputFrame[outputChannelIndex] +=
              channelMixingMatrix.getMixingCoefficient(inputChannelIndex, outputChannelIndex)
                  * inputValue;
        }
      }
      for (int outputChannelIndex = 0;
          outputChannelIndex < outputChannelCount;
          outputChannelIndex++) {
        short shortValue =
            (short)
                Util.constrainValue(
                    outputFrame[outputChannelIndex], Short.MIN_VALUE, Short.MAX_VALUE);
        outputBuffer.put((byte) (shortValue & 0xFF));
        outputBuffer.put((byte) ((shortValue >> 8) & 0xFF));
        outputFrame[outputChannelIndex] = 0;
      }
    }
    outputBuffer.flip();
  }
}

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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that handles mixing and scaling audio channels.
 *
 * <p>The following encodings are supported as input:
 *
 * <ul>
 *   <li>{@link C#ENCODING_PCM_16BIT}
 *   <li>{@link C#ENCODING_PCM_FLOAT}
 * </ul>
 *
 * The output is {@link C#ENCODING_PCM_FLOAT}.
 */
/* package */ final class ChannelMixingAudioProcessor extends BaseAudioProcessor {

  @Nullable private ChannelMixingMatrix pendingMatrix;
  @Nullable private ChannelMixingMatrix matrix;
  @Nullable private AudioMixingAlgorithm pendingAlgorithm;
  @Nullable private AudioMixingAlgorithm algorithm;

  public ChannelMixingAudioProcessor(ChannelMixingMatrix matrix) {
    pendingMatrix = matrix;
  }

  public void setMatrix(ChannelMixingMatrix matrix) {
    pendingMatrix = matrix;
  }

  @Override
  protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    checkStateNotNull(pendingMatrix);
    // TODO(b/252538025): Allow for a mapping of input channel count -> matrix to be passed in.
    if (inputAudioFormat.channelCount != pendingMatrix.getInputChannelCount()) {
      throw new UnhandledAudioFormatException(
          "Channel count must match mixing matrix", inputAudioFormat);
    }

    if (pendingMatrix.isIdentity()) {
      return AudioFormat.NOT_SET;
    }

    // TODO(b/264926272): Allow config of output PCM config when other AudioMixingAlgorithms exist.
    AudioFormat pendingOutputAudioFormat =
        new AudioFormat(
            inputAudioFormat.sampleRate,
            pendingMatrix.getOutputChannelCount(),
            C.ENCODING_PCM_FLOAT);

    pendingAlgorithm = AudioMixingAlgorithm.create(pendingOutputAudioFormat);
    if (!pendingAlgorithm.supportsSourceAudioFormat(inputAudioFormat)) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    return pendingOutputAudioFormat;
  }

  @Override
  protected void onFlush() {
    algorithm = pendingAlgorithm;
    matrix = pendingMatrix;
  }

  @Override
  protected void onReset() {
    pendingAlgorithm = null;
    algorithm = null;
    pendingMatrix = null;
    matrix = null;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int inputFramesToMix = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame;
    ByteBuffer outputBuffer =
        replaceOutputBuffer(inputFramesToMix * outputAudioFormat.bytesPerFrame);
    checkNotNull(algorithm)
        .mix(inputBuffer, inputAudioFormat, checkNotNull(matrix), inputFramesToMix, outputBuffer);
    outputBuffer.flip();
  }
}

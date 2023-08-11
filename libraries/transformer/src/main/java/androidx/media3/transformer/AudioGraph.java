/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkArgument;

import android.util.SparseArray;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import java.nio.ByteBuffer;

/** Processes raw audio samples. */
/* package */ final class AudioGraph {
  private final AudioMixer mixer;
  private final SparseArray<AudioGraphInput> inputs;

  private AudioFormat outputAudioFormat;
  private int finishedInputs;
  private ByteBuffer currentOutput;

  /** Creates an instance. */
  public AudioGraph(AudioMixer.Factory mixerFactory) {
    mixer = mixerFactory.create();
    inputs = new SparseArray<>();
    currentOutput = EMPTY_BUFFER;
    outputAudioFormat = AudioFormat.NOT_SET;
  }

  /** Returns a new {@link AudioGraphInput} instance. */
  public AudioGraphInput registerInput(EditedMediaItem item, Format format) throws ExportException {
    checkArgument(format.pcmEncoding != Format.NO_VALUE);
    try {
      AudioGraphInput audioGraphInput = new AudioGraphInput(item, format);

      if (inputs.size() == 0) {
        outputAudioFormat = audioGraphInput.getOutputAudioFormat();
        mixer.configure(
            outputAudioFormat, /* bufferSizeMs= */ C.LENGTH_UNSET, /* startTimeUs= */ 0);
      }

      int sourceId = mixer.addSource(audioGraphInput.getOutputAudioFormat(), /* startTimeUs= */ 0);
      inputs.append(sourceId, audioGraphInput);
      return audioGraphInput;
    } catch (UnhandledAudioFormatException e) {
      throw ExportException.createForAudioProcessing(e, "existingInputs=" + inputs.size());
    }
  }

  /**
   * Returns the {@link AudioFormat} of the {@linkplain #getOutput() output}.
   *
   * <p>{@link AudioFormat#NOT_SET} is returned if no inputs have been {@linkplain #registerInput
   * registered}.
   */
  public AudioFormat getOutputAudioFormat() {
    return outputAudioFormat;
  }

  /**
   * Returns a {@link ByteBuffer} containing output data between the position and limit.
   *
   * <p>The same buffer is returned until it has been fully consumed ({@code position == limit}).
   */
  public ByteBuffer getOutput() throws ExportException {
    if (!mixer.isEnded()) {
      feedMixer();
    }
    if (currentOutput.hasRemaining()) {
      return currentOutput;
    }
    currentOutput = mixer.getOutput();
    return currentOutput;
  }

  /** Releases underlying resources, including clearing the inputs. */
  public void release() {
    for (int i = 0; i < inputs.size(); i++) {
      inputs.valueAt(i).release();
    }
    inputs.clear();
    mixer.reset();
  }

  /** Returns whether the input has ended and all queued data has been output. */
  public boolean isEnded() {
    return !currentOutput.hasRemaining() && finishedInputs >= inputs.size() && mixer.isEnded();
  }

  private void feedMixer() throws ExportException {
    for (int i = 0; i < inputs.size(); i++) {
      feedMixerFromInput(inputs.keyAt(i), inputs.valueAt(i));
    }
  }

  private void feedMixerFromInput(int sourceId, AudioGraphInput input) throws ExportException {
    if (!mixer.hasSource(sourceId)) {
      return;
    }

    if (input.isEnded()) {
      mixer.removeSource(sourceId);
      finishedInputs++;
      return;
    }

    try {
      mixer.queueInput(sourceId, input.getOutput());
    } catch (UnhandledAudioFormatException e) {
      throw ExportException.createForAudioProcessing(
          e, "AudioGraphInput (sourceId=" + sourceId + ") reconfiguration");
    }
  }
}

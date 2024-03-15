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
import static androidx.media3.common.util.Assertions.checkState;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessingPipeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.media3.common.util.Log;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Processes raw audio samples. */
/* package */ final class AudioGraph {

  private static final String TAG = "AudioGraph";

  private final List<InputInfo> inputInfos;
  private final AudioMixer mixer;

  private AudioFormat mixerAudioFormat;
  private ByteBuffer mixerOutput;
  private AudioProcessingPipeline audioProcessingPipeline;
  private int finishedInputs;

  /** Creates an instance. */
  public AudioGraph(AudioMixer.Factory mixerFactory) {
    inputInfos = new ArrayList<>();
    mixer = mixerFactory.create();
    mixerAudioFormat = AudioFormat.NOT_SET;
    mixerOutput = EMPTY_BUFFER;
    audioProcessingPipeline = new AudioProcessingPipeline(ImmutableList.of());
  }

  /** Returns whether an {@link AudioFormat} is valid as an input format. */
  public static boolean isInputAudioFormatValid(AudioFormat format) {
    // AudioGraphInput assumes PCM_16BIT -- see, for example, the automatic format conversions
    // in AudioGraphInput.configureProcessing.
    if (format.encoding != C.ENCODING_PCM_16BIT) {
      return false;
    }
    if (format.sampleRate == Format.NO_VALUE) {
      return false;
    }
    if (format.channelCount == Format.NO_VALUE) {
      return false;
    }
    return true;
  }

  /**
   * Configures the composition-level audio effects to be applied after mixing.
   *
   * <p>Must be called before {@linkplain #registerInput(EditedMediaItem, Format) registering
   * inputs}.
   *
   * @param effects The composition-level audio effects.
   * @throws IllegalStateException If {@link #registerInput(EditedMediaItem, Format)} was already
   *     called.
   */
  public void configure(ImmutableList<AudioProcessor> effects) {
    checkState(
        mixerAudioFormat.equals(AudioFormat.NOT_SET),
        "AudioGraph can't configure effects after input registration.");
    audioProcessingPipeline = new AudioProcessingPipeline(effects);
  }

  /**
   * Returns a new {@link AudioGraphInput} instance.
   *
   * <p>Must be called before {@linkplain #getOutput() accessing output}.
   */
  public AudioGraphInput registerInput(EditedMediaItem editedMediaItem, Format format)
      throws ExportException {
    checkArgument(format.pcmEncoding != Format.NO_VALUE);
    AudioGraphInput audioGraphInput;
    int sourceId;
    try {
      audioGraphInput = new AudioGraphInput(mixerAudioFormat, editedMediaItem, format);

      if (Objects.equals(mixerAudioFormat, AudioFormat.NOT_SET)) {
        // Mixer not configured, configure before doing anything else.
        this.mixerAudioFormat = audioGraphInput.getOutputAudioFormat();
        mixer.configure(mixerAudioFormat, /* bufferSizeMs= */ C.LENGTH_UNSET, /* startTimeUs= */ 0);
        audioProcessingPipeline.configure(mixerAudioFormat);
        audioProcessingPipeline.flush();
      }

      sourceId = mixer.addSource(audioGraphInput.getOutputAudioFormat(), /* startTimeUs= */ 0);
    } catch (UnhandledAudioFormatException e) {
      throw ExportException.createForAudioProcessing(
          e, "Error while registering input " + inputInfos.size());
    }
    inputInfos.add(new InputInfo(audioGraphInput, sourceId));
    return audioGraphInput;
  }

  /**
   * Returns the {@link AudioFormat} of the {@linkplain #getOutput() output}, or {@link
   * AudioFormat#NOT_SET} if no inputs were {@linkplain #registerInput(EditedMediaItem, Format)
   * registered} previously.
   */
  public AudioFormat getOutputAudioFormat() {
    return audioProcessingPipeline.getOutputAudioFormat();
  }

  /**
   * Returns a {@link ByteBuffer} containing output data between the position and limit.
   *
   * <p>The same buffer is returned until it has been fully consumed ({@code position == limit}),
   * unless the graph was {@linkplain #flush() flushed}.
   */
  public ByteBuffer getOutput() throws ExportException {
    if (!mixer.isEnded()) {
      feedMixer();
    }
    if (!mixerOutput.hasRemaining()) {
      mixerOutput = mixer.getOutput();
    }

    if (audioProcessingPipeline.isOperational()) {
      feedProcessingPipelineFromMixer();
      return audioProcessingPipeline.getOutput();
    }

    return mixerOutput;
  }

  /** Clears any pending data. */
  public void flush() {
    for (int i = 0; i < inputInfos.size(); i++) {
      InputInfo inputInfo = inputInfos.get(i);
      inputInfo.mixerSourceId = C.INDEX_UNSET;
      inputInfo.audioGraphInput.flush();
    }
    mixer.reset();
    try {
      mixer.configure(mixerAudioFormat, /* bufferSizeMs= */ C.LENGTH_UNSET, /* startTimeUs= */ 0);
      addMixerSources();
    } catch (UnhandledAudioFormatException e) {
      // Should never happen because mixer has already been configured with the same formats.
      Log.e(TAG, "Unexpected mixer configuration error");
    }
    mixerOutput = EMPTY_BUFFER;
    audioProcessingPipeline.flush();
    finishedInputs = 0;
  }

  /**
   * Resets the graph, un-registering inputs and releasing any underlying resources.
   *
   * <p>Call {@link #registerInput(EditedMediaItem, Format)} to prepare the audio graph again.
   */
  public void reset() {
    for (int i = 0; i < inputInfos.size(); i++) {
      inputInfos.get(i).audioGraphInput.release();
    }
    inputInfos.clear();
    mixer.reset();
    audioProcessingPipeline.reset();

    finishedInputs = 0;
    mixerOutput = EMPTY_BUFFER;
    mixerAudioFormat = AudioFormat.NOT_SET;
  }

  /** Returns whether the input has ended and all queued data has been output. */
  public boolean isEnded() {
    if (audioProcessingPipeline.isOperational()) {
      return audioProcessingPipeline.isEnded();
    }
    return isMixerEnded();
  }

  private boolean isMixerEnded() {
    return !mixerOutput.hasRemaining() && finishedInputs >= inputInfos.size() && mixer.isEnded();
  }

  private void feedProcessingPipelineFromMixer() {
    if (isMixerEnded()) {
      audioProcessingPipeline.queueEndOfStream();
      return;
    }
    audioProcessingPipeline.queueInput(mixerOutput);
  }

  private void feedMixer() throws ExportException {
    for (int i = 0; i < inputInfos.size(); i++) {
      feedMixerFromInput(inputInfos.get(i));
    }
  }

  private void feedMixerFromInput(InputInfo inputInfo) throws ExportException {
    int sourceId = inputInfo.mixerSourceId;
    if (!mixer.hasSource(sourceId)) {
      return;
    }

    AudioGraphInput input = inputInfo.audioGraphInput;
    if (input.isEnded()) {
      mixer.removeSource(sourceId);
      inputInfo.mixerSourceId = C.INDEX_UNSET;
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

  private void addMixerSources() throws UnhandledAudioFormatException {
    for (int i = 0; i < inputInfos.size(); i++) {
      InputInfo inputInfo = inputInfos.get(i);
      inputInfo.mixerSourceId =
          mixer.addSource(inputInfo.audioGraphInput.getOutputAudioFormat(), /* startTimeUs= */ 0);
    }
  }

  private static final class InputInfo {
    public final AudioGraphInput audioGraphInput;
    public int mixerSourceId;

    public InputInfo(AudioGraphInput audioGraphInput, int mixerSourceId) {
      this.audioGraphInput = audioGraphInput;
      this.mixerSourceId = mixerSourceId;
    }
  }
}

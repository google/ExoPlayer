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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.audio.AudioProcessor.EMPTY_BUFFER;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessingPipeline;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Processes raw audio samples.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class AudioGraph {
  private final AudioMixer mixer;
  private final SparseArray<AudioGraphInput> inputs;
  private final AudioProcessingPipeline audioProcessingPipeline;

  private AudioFormat mixerAudioFormat;
  private int finishedInputs;
  private ByteBuffer mixerOutput;

  /** Creates an instance. */
  public AudioGraph(AudioMixer.Factory mixerFactory, ImmutableList<AudioProcessor> effects) {
    mixer = mixerFactory.create();
    inputs = new SparseArray<>();
    audioProcessingPipeline = new AudioProcessingPipeline(effects);
    mixerOutput = EMPTY_BUFFER;
    mixerAudioFormat = AudioFormat.NOT_SET;
  }

  /** Returns whether an {@link AudioFormat} is valid as an input format. */
  public static boolean isInputAudioFormatValid(AudioFormat format) {
    if (format.encoding == Format.NO_VALUE) {
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
   * Configures the graph.
   *
   * <p>Must be called before {@linkplain #getOutput() accessing output}.
   *
   * <p>Should be called at most once, before {@link #registerInput registering input}.
   *
   * @param mixerAudioFormat The {@link AudioFormat} requested for output from the mixer.
   * @throws UnhandledAudioFormatException If the audio format is not supported by the {@link
   *     AudioMixer}.
   */
  public void configure(AudioFormat mixerAudioFormat) throws UnhandledAudioFormatException {
    this.mixerAudioFormat = mixerAudioFormat;
    mixer.configure(mixerAudioFormat, /* bufferSizeMs= */ C.LENGTH_UNSET, /* startTimeUs= */ 0);
    audioProcessingPipeline.configure(mixerAudioFormat);
    audioProcessingPipeline.flush();
  }

  /**
   * Returns a new {@link AudioGraphInput} instance.
   *
   * <p>Calls {@link #configure} if not already configured, using the {@linkplain
   * AudioGraphInput#getOutputAudioFormat() outputAudioFormat} of the input.
   */
  public AudioGraphInput registerInput(EditedMediaItem editedMediaItem, Format format)
      throws ExportException {
    checkArgument(format.pcmEncoding != Format.NO_VALUE);
    try {
      AudioGraphInput audioGraphInput =
          new AudioGraphInput(mixerAudioFormat, editedMediaItem, format);

      if (Objects.equals(mixerAudioFormat, AudioFormat.NOT_SET)) {
        // Graph not configured, configure before doing anything else.
        configure(audioGraphInput.getOutputAudioFormat());
      }

      int sourceId = mixer.addSource(audioGraphInput.getOutputAudioFormat(), /* startTimeUs= */ 0);
      inputs.append(sourceId, audioGraphInput);
      return audioGraphInput;
    } catch (UnhandledAudioFormatException e) {
      throw ExportException.createForAudioProcessing(e, "existingInputs=" + inputs.size());
    }
  }

  /**
   * Returns the {@link AudioFormat} of the {@linkplain #getOutput() output}, or {@link
   * AudioFormat#NOT_SET} if not {@linkplain #configure configured}.
   */
  public AudioFormat getOutputAudioFormat() {
    return audioProcessingPipeline.getOutputAudioFormat();
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
    if (!mixerOutput.hasRemaining()) {
      mixerOutput = mixer.getOutput();
    }

    if (audioProcessingPipeline.isOperational()) {
      feedProcessingPipelineFromMixer();
      return audioProcessingPipeline.getOutput();
    }

    return mixerOutput;
  }

  /** Resets the graph to an unconfigured state, releasing any underlying resources. */
  public void reset() {
    for (int i = 0; i < inputs.size(); i++) {
      inputs.valueAt(i).release();
    }
    inputs.clear();
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
    return !mixerOutput.hasRemaining() && finishedInputs >= inputs.size() && mixer.isEnded();
  }

  private void feedProcessingPipelineFromMixer() {
    if (isMixerEnded()) {
      audioProcessingPipeline.queueEndOfStream();
      return;
    }
    audioProcessingPipeline.queueInput(mixerOutput);
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

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
package androidx.media3.common.audio;

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.Nullable;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles passing buffers through multiple {@link AudioProcessor} instances.
 *
 * <p>Two instances of {@link AudioProcessingPipeline} are considered {@linkplain #equals(Object)
 * equal} if they have the same underlying {@link AudioProcessor} references, in the same order.
 *
 * <p>To make use of this class, the caller must:
 *
 * <ul>
 *   <li>Initialize an instance, passing in all audio processors that may be used for processing.
 *   <li>Call {@link #configure(AudioFormat)} with the {@link AudioFormat} of the input data. This
 *       method will give back the {@link AudioFormat} that will be output from the pipeline when
 *       this configuration is in use.
 *   <li>Call {@link #flush()} to apply the pending configuration.
 *   <li>Check if the pipeline {@link #isOperational()}. If not, then the pipeline can not be used
 *       to process buffers in the current configuration. This is because none of the underlying
 *       {@link AudioProcessor} instances are {@linkplain AudioProcessor#isActive active}.
 *   <li>If the pipeline {@link #isOperational()}, {@link #queueInput(ByteBuffer)} then {@link
 *       #getOutput()} to process buffers.
 *   <li>{@link #queueEndOfStream()} to inform the pipeline the current input stream is at an end.
 *   <li>Repeatedly call {@link #getOutput()} and handle those buffers until {@link #isEnded()}
 *       returns true.
 *   <li>When finished with the pipeline, call {@link #reset()} to release underlying resources.
 * </ul>
 *
 * <p>If underlying {@link AudioProcessor} instances have pending configuration changes, or the
 * {@link AudioFormat} of the input is changing:
 *
 * <ul>
 *   <li>Call {@link #configure(AudioFormat)} to configure the pipeline for the new input stream.
 *       You can still {@link #queueInput(ByteBuffer)} and {@link #getOutput()} in the old setup at
 *       this time.
 *   <li>{@link #queueEndOfStream()} to inform the pipeline the current input stream is at an end.
 *   <li>Repeatedly call {@link #getOutput()} until {@link #isEnded()} returns true.
 *   <li>Call {@link #flush()} to apply the new configuration and flush the pipeline.
 *   <li>Begin {@linkplain #queueInput(ByteBuffer) queuing input} and handling the {@linkplain
 *       #getOutput() output} in the new configuration.
 * </ul>
 */
@UnstableApi
public final class AudioProcessingPipeline {

  /** The {@link AudioProcessor} instances passed to {@link AudioProcessingPipeline}. */
  private final ImmutableList<AudioProcessor> audioProcessors;

  /**
   * The processors that are {@linkplain AudioProcessor#isActive() active} based on the current
   * configuration.
   */
  private final List<AudioProcessor> activeAudioProcessors;

  /**
   * The buffers output by the {@link #activeAudioProcessors}. This has the same number of elements
   * as {@link #activeAudioProcessors}.
   */
  private ByteBuffer[] outputBuffers;

  /** The {@link AudioFormat} currently being output by the pipeline. */
  private AudioFormat outputAudioFormat;

  /** The {@link AudioFormat} that will be output following a {@link #flush()}. */
  private AudioFormat pendingOutputAudioFormat;

  /** Whether input has ended, either due to configuration change or end of stream. */
  private boolean inputEnded;

  /**
   * Creates an instance.
   *
   * @param audioProcessors The {@link AudioProcessor} instances to be used for processing buffers.
   */
  public AudioProcessingPipeline(ImmutableList<AudioProcessor> audioProcessors) {
    this.audioProcessors = audioProcessors;
    activeAudioProcessors = new ArrayList<>();
    outputBuffers = new ByteBuffer[0];
    outputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputEnded = false;
  }

  /**
   * Configures the pipeline to process input audio with the specified format. Returns the
   * configured output audio format.
   *
   * <p>To apply the new configuration for use, the pipeline must be {@linkplain #flush() flushed}.
   * Before applying the new configuration, it is safe to queue input and get output in the old
   * input/output formats/configuration. Call {@link #queueEndOfStream()} when no more input will be
   * supplied for processing in the old configuration.
   *
   * @param inputAudioFormat The format of audio that will be queued after the next call to {@link
   *     #flush()}.
   * @return The configured output audio format.
   * @throws AudioProcessor.UnhandledAudioFormatException If the specified format is not supported
   *     by the pipeline.
   */
  @CanIgnoreReturnValue
  public AudioFormat configure(AudioFormat inputAudioFormat)
      throws AudioProcessor.UnhandledAudioFormatException {
    if (inputAudioFormat.equals(AudioFormat.NOT_SET)) {
      throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
    }

    AudioFormat intermediateAudioFormat = inputAudioFormat;

    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      AudioFormat nextFormat = audioProcessor.configure(intermediateAudioFormat);
      if (audioProcessor.isActive()) {
        checkState(!nextFormat.equals(AudioFormat.NOT_SET));
        intermediateAudioFormat = nextFormat;
      }
    }

    return pendingOutputAudioFormat = intermediateAudioFormat;
  }

  /**
   * Clears any buffered data and pending output. If any underlying audio processors are {@linkplain
   * AudioProcessor#isActive() active}, this also prepares them to receive a new stream of input in
   * the last {@linkplain #configure(AudioFormat) configured} (pending) format.
   *
   * <p>{@link #configure(AudioFormat)} must have been called at least once since the last call to
   * {@link #reset()} before calling this.
   */
  public void flush() {
    activeAudioProcessors.clear();
    outputAudioFormat = pendingOutputAudioFormat;
    inputEnded = false;

    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      audioProcessor.flush();
      if (audioProcessor.isActive()) {
        activeAudioProcessors.add(audioProcessor);
      }
    }

    outputBuffers = new ByteBuffer[activeAudioProcessors.size()];
    for (int i = 0; i <= getFinalOutputBufferIndex(); i++) {
      outputBuffers[i] = activeAudioProcessors.get(i).getOutput();
    }
  }

  /**
   * Returns the {@link AudioFormat} of data being output through {@link #getOutput()}.
   *
   * @return The {@link AudioFormat} currently being output, or {@link AudioFormat#NOT_SET} if no
   *     {@linkplain #configure(AudioFormat) configuration} has been {@linkplain #flush() applied}.
   */
  public AudioFormat getOutputAudioFormat() {
    return outputAudioFormat;
  }

  /**
   * Whether the pipeline can be used for processing buffers.
   *
   * <p>For this to happen the pipeline must be {@linkplain #configure(AudioFormat) configured},
   * {@linkplain #flush() flushed} and have {@linkplain AudioProcessor#isActive() active}
   * {@linkplain AudioProcessor underlying audio processors} that are ready to process buffers with
   * the current configuration.
   */
  public boolean isOperational() {
    return !activeAudioProcessors.isEmpty();
  }

  /**
   * Queues audio data between the position and limit of the {@code inputBuffer} for processing.
   * After calling this method, processed output may be available via {@link #getOutput()}.
   *
   * @param inputBuffer The input buffer to process. It must be a direct {@link ByteBuffer} with
   *     native byte order. Its contents are treated as read-only. Its position will be advanced by
   *     the number of bytes consumed (which may be zero). The caller retains ownership of the
   *     provided buffer.
   */
  public void queueInput(ByteBuffer inputBuffer) {
    if (!isOperational() || inputEnded) {
      return;
    }
    processData(inputBuffer);
  }

  /**
   * Returns a {@link ByteBuffer} containing processed output data between its position and limit.
   * The buffer will be empty if no output is available.
   *
   * <p>Buffers returned from this method are retained by pipeline, and it is necessary to consume
   * the data (or copy it into another buffer) to allow the pipeline to progress.
   *
   * @return A buffer containing processed output data between its position and limit.
   */
  public ByteBuffer getOutput() {
    if (!isOperational()) {
      return EMPTY_BUFFER;
    }
    ByteBuffer outputBuffer = outputBuffers[getFinalOutputBufferIndex()];
    if (outputBuffer.hasRemaining()) {
      return outputBuffer;
    }

    processData(EMPTY_BUFFER);
    return outputBuffers[getFinalOutputBufferIndex()];
  }

  /**
   * Queues an end of stream signal. After this method has been called, {@link
   * #queueInput(ByteBuffer)} should not be called until after the next call to {@link #flush()}.
   * Calling {@link #getOutput()} will return any remaining output data. Multiple calls may be
   * required to read all of the remaining output data. {@link #isEnded()} will return {@code true}
   * once all remaining output data has been read.
   */
  public void queueEndOfStream() {
    if (!isOperational() || inputEnded) {
      return;
    }
    inputEnded = true;
    activeAudioProcessors.get(0).queueEndOfStream();
  }

  /**
   * Returns whether the pipeline has ended.
   *
   * <p>The pipeline is considered ended when:
   *
   * <ul>
   *   <li>End of stream has been {@linkplain #queueEndOfStream() queued}.
   *   <li>Every {@linkplain #queueInput(ByteBuffer) input buffer} has been processed.
   *   <li>Every {@linkplain #getOutput() output buffer} has been fully consumed.
   * </ul>
   */
  public boolean isEnded() {
    return inputEnded
        && activeAudioProcessors.get(getFinalOutputBufferIndex()).isEnded()
        && !outputBuffers[getFinalOutputBufferIndex()].hasRemaining();
  }

  /**
   * Resets the pipeline and its underlying {@link AudioProcessor} instances to their unconfigured
   * state, releasing any resources.
   */
  public void reset() {
    for (int i = 0; i < audioProcessors.size(); i++) {
      AudioProcessor audioProcessor = audioProcessors.get(i);
      audioProcessor.flush();
      audioProcessor.reset();
    }
    outputBuffers = new ByteBuffer[0];
    outputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputEnded = false;
  }

  /**
   * Indicates whether some other object is "equal to" this one.
   *
   * <p>Two instances of {@link AudioProcessingPipeline} are considered equal if they have the same
   * underlying {@link AudioProcessor} references in the same order.
   */
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AudioProcessingPipeline)) {
      return false;
    }
    AudioProcessingPipeline that = (AudioProcessingPipeline) o;
    if (this.audioProcessors.size() != that.audioProcessors.size()) {
      return false;
    }
    for (int i = 0; i < this.audioProcessors.size(); i++) {
      if (this.audioProcessors.get(i) != that.audioProcessors.get(i)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return audioProcessors.hashCode();
  }

  private void processData(ByteBuffer inputBuffer) {
    boolean progressMade = true;
    while (progressMade) {
      progressMade = false;
      for (int index = 0; index <= getFinalOutputBufferIndex(); index++) {
        if (outputBuffers[index].hasRemaining()) {
          // Processor at this index has output that has not been consumed. Do not queue input.
          continue;
        }

        AudioProcessor audioProcessor = activeAudioProcessors.get(index);

        if (audioProcessor.isEnded()) {
          if (!outputBuffers[index].hasRemaining() && index < getFinalOutputBufferIndex()) {
            activeAudioProcessors.get(index + 1).queueEndOfStream();
          }
          continue;
        }

        ByteBuffer input =
            index > 0
                ? outputBuffers[index - 1]
                : inputBuffer.hasRemaining() ? inputBuffer : EMPTY_BUFFER;
        long inputBytes = input.remaining();
        audioProcessor.queueInput(input);
        outputBuffers[index] = audioProcessor.getOutput();

        progressMade |= (inputBytes - input.remaining()) > 0 || outputBuffers[index].hasRemaining();
      }
    }
  }

  private int getFinalOutputBufferIndex() {
    return outputBuffers.length - 1;
  }
}

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
package androidx.media3.transformer;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/**
 * An audio component which combines audio data from multiple sources into a single output.
 *
 * <p>The mixer supports an arbitrary number of concurrent sources and will ensure audio data from
 * all sources are aligned and mixed before producing output. Any periods without sources will be
 * filled with silence. The total duration of the mixed track is controlled with {@link
 * #setEndTimeUs}, or is unbounded if left unset.
 *
 * <p><b>Updates:</b> The mixer supports the following updates at any time without the need for a
 * {@link #reset()}.
 *
 * <ul>
 *   <li>{@linkplain #addSource Add source}. Source audio will be included in future mixed output
 *       only.
 *   <li>{@linkplain #removeSource Remove source}.
 *   <li>{@linkplain #setSourceVolume Change source volume}. The new volume will apply only to
 *       future source samples.
 *   <li>{@linkplain #setEndTimeUs Change end time}. The new end time may cause an immediate change
 *       to the mixer {@linkplain #isEnded() ended state}.
 * </ul>
 *
 * <p>{@linkplain #configure Changes} to the output audio format, buffer size, or mixer start time
 * require the mixer to first be {@linkplain #reset() reset}, discarding all buffered data.
 *
 * <p><b>Operation:</b> The mixer must be {@linkplain #configure configured} before any methods are
 * called. Once configured, sources can queue audio data via {@link #queueInput} and the mixer will
 * consume input audio up to the configured buffer size and end time. Once all sources have produced
 * data for a period then {@link #getOutput()} will return the mixed result. The cycle repeats until
 * the mixer {@link #isEnded()}.
 */
@UnstableApi
public interface AudioMixer {

  /** A factory for {@link AudioMixer} instances. */
  interface Factory {
    AudioMixer create();
  }

  /**
   * @deprecated Use {@link DefaultAudioMixer.Factory#create()}.
   */
  @Deprecated
  static AudioMixer create() {
    return new DefaultAudioMixer.Factory(
            /* outputSilenceWithNoSources= */ true, /* clipFloatOutput= */ true)
        .create();
  }

  /**
   * Configures the mixer.
   *
   * <p>The mixer must be configured before use and can only be reconfigured after a call to {@link
   * #reset()}.
   *
   * <p>The mixing buffer size is set by {@code bufferSizeMs} and indicates how much audio can be
   * queued before {@link #getOutput()} is called.
   *
   * @param outputAudioFormat The audio format of buffers returned from {@link #getOutput()}.
   * @param bufferSizeMs The optional mixing buffer size in milliseconds, or {@link C#LENGTH_UNSET}.
   * @param startTimeUs The start time of the mixer output in microseconds.
   * @throws UnhandledAudioFormatException If the output audio format is not supported.
   */
  void configure(AudioFormat outputAudioFormat, int bufferSizeMs, long startTimeUs)
      throws UnhandledAudioFormatException;

  /**
   * Sets the end time of the output audio.
   *
   * <p>The mixer will not accept input nor produce output past this point.
   *
   * @param endTimeUs The end time in microseconds.
   * @throws IllegalArgumentException If {@code endTimeUs} is before the configured start time.
   */
  void setEndTimeUs(long endTimeUs);

  /** Indicates whether the mixer supports mixing sources with the given audio format. */
  boolean supportsSourceAudioFormat(AudioFormat sourceFormat);

  /**
   * Adds an audio source to mix starting at the given time.
   *
   * <p>If the mixer has already {@linkplain #getOutput() output} samples past the {@code
   * startTimeUs}, audio from this source will be discarded up to the last output end timestamp.
   *
   * <p>If the source start time is earlier than the configured mixer start time then audio from
   * this source will be discarded up to the mixer start time.
   *
   * <p>All audio sources start with a volume of 1.0 on all channels.
   *
   * @param sourceFormat Audio format of source buffers.
   * @param startTimeUs Source start time in microseconds.
   * @return Non-negative integer identifying the source ({@code sourceId}).
   * @throws UnhandledAudioFormatException If the source format is not supported.
   */
  int addSource(AudioFormat sourceFormat, long startTimeUs) throws UnhandledAudioFormatException;

  /**
   * Returns whether there is an {@link #addSource added source} with the given {@code sourceId}.
   */
  boolean hasSource(int sourceId);

  /**
   * Sets the volume applied to future samples queued from the given source.
   *
   * @param sourceId Source identifier from {@link #addSource}.
   * @param volume Non-negative scalar applied to all source channels.
   */
  void setSourceVolume(int sourceId, float volume);

  /**
   * Removes an audio source.
   *
   * <p>No more audio can be queued from this source. All audio queued before removal will be
   * output.
   *
   * @param sourceId Source identifier from {@link #addSource}.
   */
  void removeSource(int sourceId);

  /**
   * Queues audio data between the position and limit of the {@code sourceBuffer}.
   *
   * <p>After calling this method output may be available via {@link #getOutput()} if all sources
   * have queued data.
   *
   * @param sourceId Source identifier from {@link #addSource}.
   * @param sourceBuffer The source buffer to mix. It must be a direct byte buffer with native byte
   *     order. Its contents are treated as read-only. Its position will be advanced by the number
   *     of bytes consumed (which may be zero). The caller retains ownership of the provided buffer.
   */
  void queueInput(int sourceId, ByteBuffer sourceBuffer);

  /**
   * Returns a buffer containing output audio data between its position and limit.
   *
   * <p>The buffer will be no larger than the configured buffer size and will include no more than
   * the frames that have been queued from all sources, up to the {@linkplain #setEndTimeUs end
   * time}. Silence will be generated for any periods with no sources.
   *
   * <p>The buffer will always be a direct byte buffer with native byte order. Calling this method
   * invalidates any previously returned buffer. The buffer will be empty if no output is available.
   *
   * @return A buffer containing output data between its position and limit.
   */
  ByteBuffer getOutput();

  /**
   * Returns whether the mixer can accept more {@linkplain #queueInput input} or produce more
   * {@linkplain #getOutput() output}, based on the {@link #setEndTimeUs end time}.
   *
   * <p><b>Note:</b> If no end time is set this will always return {@code false}.
   */
  boolean isEnded();

  /** Resets the mixer to its unconfigured state, releasing any resources. */
  void reset();
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Listener of audio {@link Renderer} events. All methods have no-op default implementations to
 * allow selective overrides.
 */
public interface AudioRendererEventListener {

  /**
   * Called when the renderer is enabled.
   *
   * @param counters {@link DecoderCounters} that will be updated by the renderer for as long as it
   *     remains enabled.
   */
  default void onAudioEnabled(DecoderCounters counters) {}

  /**
   * Called when the audio session is set.
   *
   * @param audioSessionId The audio session id.
   */
  default void onAudioSessionId(int audioSessionId) {}

  /**
   * Called when a decoder is created.
   *
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  default void onAudioDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {}

  /**
   * Called when the format of the media being consumed by the renderer changes.
   *
   * @param format The new format.
   */
  default void onAudioInputFormatChanged(Format format) {}

  /**
   * Called when the audio position has increased for the first time since the last pause or
   * position reset.
   *
   * @param playoutStartSystemTimeMs The approximate derived {@link System#currentTimeMillis()} at
   *     which playout started.
   */
  default void onAudioPositionAdvancing(long playoutStartSystemTimeMs) {}

  /**
   * Called when an audio underrun occurs.
   *
   * @param bufferSize The size of the audio output buffer, in bytes.
   * @param bufferSizeMs The size of the audio output buffer, in milliseconds, if it contains PCM
   *     encoded audio. {@link C#TIME_UNSET} if the output buffer contains non-PCM encoded audio.
   * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
   */
  default void onAudioUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {}

  /**
   * Called when the renderer is disabled.
   *
   * @param counters {@link DecoderCounters} that were updated by the renderer.
   */
  default void onAudioDisabled(DecoderCounters counters) {}

  /**
   * Called when skipping silences is enabled or disabled in the audio stream.
   *
   * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
   */
  default void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {}

  /** Dispatches events to an {@link AudioRendererEventListener}. */
  final class EventDispatcher {

    @Nullable private final Handler handler;
    @Nullable private final AudioRendererEventListener listener;

    /**
     * @param handler A handler for dispatching events, or null if events should not be dispatched.
     * @param listener The listener to which events should be dispatched, or null if events should
     *     not be dispatched.
     */
    public EventDispatcher(
        @Nullable Handler handler, @Nullable AudioRendererEventListener listener) {
      this.handler = listener != null ? Assertions.checkNotNull(handler) : null;
      this.listener = listener;
    }

    /** Invokes {@link AudioRendererEventListener#onAudioEnabled(DecoderCounters)}. */
    public void enabled(DecoderCounters decoderCounters) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onAudioEnabled(decoderCounters));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioDecoderInitialized(String, long, long)}. */
    public void decoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      if (handler != null) {
        handler.post(
            () ->
                castNonNull(listener)
                    .onAudioDecoderInitialized(
                        decoderName, initializedTimestampMs, initializationDurationMs));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioInputFormatChanged(Format)}. */
    public void inputFormatChanged(Format format) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onAudioInputFormatChanged(format));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioPositionAdvancing(long)}. */
    public void positionAdvancing(long playoutStartSystemTimeMs) {
      if (handler != null) {
        handler.post(
            () -> castNonNull(listener).onAudioPositionAdvancing(playoutStartSystemTimeMs));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioUnderrun(int, long, long)}. */
    public void underrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      if (handler != null) {
        handler.post(
            () ->
                castNonNull(listener)
                    .onAudioUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioDisabled(DecoderCounters)}. */
    public void disabled(DecoderCounters counters) {
      counters.ensureUpdated();
      if (handler != null) {
        handler.post(
            () -> {
              counters.ensureUpdated();
              castNonNull(listener).onAudioDisabled(counters);
            });
      }
    }

    /** Invokes {@link AudioRendererEventListener#onAudioSessionId(int)}. */
    public void audioSessionId(int audioSessionId) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onAudioSessionId(audioSessionId));
      }
    }

    /** Invokes {@link AudioRendererEventListener#onSkipSilenceEnabledChanged(boolean)}. */
    public void skipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onSkipSilenceEnabledChanged(skipSilenceEnabled));
      }
    }
  }
}

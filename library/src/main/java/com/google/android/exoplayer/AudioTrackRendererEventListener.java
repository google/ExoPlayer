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
package com.google.android.exoplayer;

import com.google.android.exoplayer.audio.AudioTrack;

/**
 * Optional interface definition for a callback to be notified of audio {@link TrackRenderer}
 * events.
 */
public interface AudioTrackRendererEventListener extends TrackRendererEventListener {
  /**
   * Invoked when an {@link AudioTrack} fails to initialize.
   *
   * @param e The corresponding exception.
   */
  void onAudioTrackInitializationError(AudioTrack.InitializationException e);

  /**
   * Invoked when an {@link AudioTrack} write fails.
   *
   * @param e The corresponding exception.
   */
  void onAudioTrackWriteError(AudioTrack.WriteException e);

  /**
   * Invoked when an {@link AudioTrack} underrun occurs.
   *
   * @param bufferSize The size of the {@link AudioTrack}'s buffer, in bytes.
   * @param bufferSizeMs The size of the {@link AudioTrack}'s buffer, in milliseconds, if it is
   *     configured for PCM output. -1 if it is configured for passthrough output, as the buffered
   *     media can have a variable bitrate so the duration may be unknown.
   * @param elapsedSinceLastFeedMs The time since the {@link AudioTrack} was last fed data.
   */
  void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

  /**
   * Invoked to pass the codec counters when the renderer is enabled.
   *
   * @param counters CodecCounters object used by the renderer.
   */
  void onAudioCodecCounters(CodecCounters counters);
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Optional interface definition for a callback to be notified of {@link TrackRenderer} events.
 */
public interface TrackRendererEventListener {

  /**
   * Invoked when a decoder fails to initialize.
   *
   * @param e The corresponding exception.
   */
  void onDecoderInitializationError(Exception e);

  /**
   * Invoked when a decoder is successfully created.
   *
   * @param decoderName The decoder that was configured and created.
   * @param elapsedRealtimeMs {@code elapsedRealtime} timestamp of when the initialization
   *    finished.
   * @param initializationDurationMs Amount of time taken to initialize the decoder.
   */
  void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs);

}

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

import com.google.android.exoplayer.upstream.Allocator;

/**
 * A media buffering policy.
 */
public interface BufferingPolicy {

  /**
   * Invoked by the player to update the playback position.
   *
   * @param playbackPositionUs The current playback position in microseconds.
   */
  void setPlaybackPosition(long playbackPositionUs);

  /**
   * Invoked by the player to determine whether sufficient media is buffered for playback to be
   * started or resumed.
   *
   * @param bufferedPositionUs The position up to which media is buffered.
   * @param rebuffering Whether the player is re-buffering.
   * @return True if playback should be allowed to start or resume. False otherwise.
   */
  boolean haveSufficientBuffer(long bufferedPositionUs, boolean rebuffering);

  /**
   * Invoked by the player when a track selection occurs.
   *
   * @param renderers The renderers.
   * @param trackGroups The available {@link TrackGroup}s.
   * @param trackSelections The {@link TrackSelection}s that were made.
   */
  void onTrackSelections(TrackRenderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections);

  /**
   * Invoked by the player when a reset occurs, meaning all renderers have been disabled.
   */
  void reset();

  /**
   * Returns a {@link LoadControl} that a {@link SampleSource} can use to control loads according to
   * this policy.
   *
   * @return The {@link LoadControl}.
   */
  LoadControl getLoadControl();

  /**
   * Coordinates multiple loaders of time series data.
   */
  interface LoadControl {

    /**
     * Registers a loader.
     *
     * @param loader The loader being registered.
     */
    void register(Object loader);

    /**
     * Unregisters a loader.
     *
     * @param loader The loader being unregistered.
     */
    void unregister(Object loader);

    /**
     * Gets the {@link Allocator} that loaders should use to obtain memory allocations into which
     * data can be loaded.
     *
     * @return The {@link Allocator} to use.
     */
    Allocator getAllocator();

    /**
     * Invoked by a loader to update the control with its current state.
     * <p>
     * This method must be called by a registered loader whenever its state changes. This is true
     * even if the registered loader does not itself wish to start its next load (since the state of
     * the loader will still affect whether other registered loaders are allowed to proceed).
     *
     * @param loader The loader invoking the update.
     * @param nextLoadPositionUs The loader's next load position, or {@link C#UNSET_TIME_US} if
     *     finished, failed, or if the next load position is not yet known.
     * @param loading Whether the loader is currently loading data.
     * @return True if the loader is allowed to start its next load. False otherwise.
     */
    boolean update(Object loader, long nextLoadPositionUs, boolean loading);

  }

}

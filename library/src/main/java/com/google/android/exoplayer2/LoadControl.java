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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.Allocator;

/**
 * Controls buffering of media.
 */
public interface LoadControl {

  /**
   * Invoked by the player when a track selection occurs.
   *
   * @param renderers The renderers.
   * @param trackGroups The available {@link TrackGroup}s.
   * @param trackSelections The {@link TrackSelection}s that were made.
   */
  void onTrackSelections(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections);

  /**
   * Invoked by the player when a reset occurs, meaning all renderers have been disabled.
   */
  void reset();

  /**
   * Gets the {@link Allocator} that should be used to obtain media buffer allocations.
   *
   * @return The {@link Allocator}.
   */
  Allocator getAllocator();

  /**
   * Invoked by the player to determine whether sufficient media is buffered for playback to be
   * started or resumed.
   *
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @param rebuffering Whether the player is re-buffering.
   * @return True if playback should be allowed to start or resume. False otherwise.
   */
  boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering);

  /**
   * Invoked by the player to determine whether it should continue to load the source.
   *
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @return True if the loading should continue. False otherwise.
   */
  boolean shouldContinueLoading(long bufferedDurationUs);

}

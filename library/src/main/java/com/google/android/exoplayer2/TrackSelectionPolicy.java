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

/**
 *  Defines a policy for selecting the track rendered by each {@link TrackRenderer}.
 */
public abstract class TrackSelectionPolicy {

  /**
   * Notified when selection parameters have changed.
   */
  /* package */ interface InvalidationListener {

    /**
     * Invoked by a {@link TrackSelectionPolicy} when previous selections are invalidated.
     */
    void invalidatePolicySelections();

  }

  private InvalidationListener listener;

  /* package */ final void init(InvalidationListener listener) {
    this.listener = listener;
  }

  /**
   * Must be invoked by subclasses when a selection parameter has changed, invalidating previous
   * selections.
   */
  protected void invalidate() {
    if (listener != null) {
      listener.invalidatePolicySelections();
    }
  }

  /**
   * Given an array of {@link TrackRenderer}s and a set of {@link TrackGroup}s assigned to each of
   * them, provides a {@link TrackSelection} per renderer.
   *
   * @param renderers The available {@link TrackRenderer}s.
   * @param rendererTrackGroupArrays An array of {@link TrackGroupArray}s where each entry
   *     corresponds to the {@link TrackRenderer} of equal index in {@code renderers}.
   * @param rendererFormatSupports Maps every available track to a specific level of support as
   *     defined by the {@link TrackRenderer} {@code FORMAT_*} constants.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected abstract TrackSelection[] selectTracks(TrackRenderer[] renderers,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException;

}


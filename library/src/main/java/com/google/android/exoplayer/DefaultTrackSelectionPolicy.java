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
 * A {@link TrackSelectionPolicy} that allows configuration of common parameters.
 */
public class DefaultTrackSelectionPolicy extends TrackSelectionPolicy {

  @Override
  public TrackSelection[] selectTracks(TrackRenderer[] renderers,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports) {
    // Make a track selection for each renderer.
    TrackSelection[] rendererTrackSelections = new TrackSelection[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      rendererTrackSelections[i] = selectTracksForRenderer(rendererTrackGroupArrays[i],
          rendererFormatSupports[i]);
    }
    return rendererTrackSelections;
  }

  private static TrackSelection selectTracksForRenderer(TrackGroupArray trackGroups,
      int[][] formatSupport) {
    // TODO: Allow more specific track selection parameters.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup trackGroup = trackGroups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if ((trackFormatSupport[trackIndex] & TrackRenderer.FORMAT_SUPPORT_MASK)
            == TrackRenderer.FORMAT_HANDLED) {
          return new TrackSelection(groupIndex, trackIndex);
        }
      }
    }
    return null;
  }

}


/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import android.support.annotation.NonNull;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * A fake {@link MappingTrackSelector} that returns {@link FakeTrackSelection}s.
 */
public class FakeTrackSelector extends MappingTrackSelector {

  private final List<FakeTrackSelection> selectedTrackSelections = new ArrayList<>();
  private final boolean mayReuseTrackSelection;

  public FakeTrackSelector() {
    this(false);
  }

  /**
   * @param mayReuseTrackSelection Whether this {@link FakeTrackSelector} will reuse
   * {@link TrackSelection}s during track selection, when it finds previously-selected track
   * selection using the same {@link TrackGroup}.
   */
  public FakeTrackSelector(boolean mayReuseTrackSelection) {
    this.mayReuseTrackSelection = mayReuseTrackSelection;
  }

  @Override
  protected TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException {
    List<FakeTrackSelection> resultList = new ArrayList<>();
    for (TrackGroupArray trackGroupArray : rendererTrackGroupArrays) {
      TrackGroup trackGroup = trackGroupArray.get(0);
      FakeTrackSelection trackSelectionForRenderer = reuseOrCreateTrackSelection(trackGroup);
      resultList.add(trackSelectionForRenderer);
    }
    return resultList.toArray(new TrackSelection[resultList.size()]);
  }

  @NonNull
  private FakeTrackSelection reuseOrCreateTrackSelection(TrackGroup trackGroup) {
    FakeTrackSelection trackSelectionForRenderer = null;
    if (mayReuseTrackSelection) {
      for (FakeTrackSelection selectedTrackSelection : selectedTrackSelections) {
        if (selectedTrackSelection.getTrackGroup().equals(trackGroup)) {
          trackSelectionForRenderer = selectedTrackSelection;
        }
      }
    }
    if (trackSelectionForRenderer == null) {
      trackSelectionForRenderer = new FakeTrackSelection(trackGroup);
      selectedTrackSelections.add(trackSelectionForRenderer);
    }
    return trackSelectionForRenderer;
  }

  /**
   * Returns list of all {@link FakeTrackSelection}s that this track selector has made so far.
   */
  public List<FakeTrackSelection> getSelectedTrackSelections() {
    return selectedTrackSelections;
  }

}

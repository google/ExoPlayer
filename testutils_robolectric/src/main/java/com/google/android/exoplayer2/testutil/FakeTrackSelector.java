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
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.util.ArrayList;
import java.util.List;

/** A fake {@link MappingTrackSelector} that returns {@link FakeTrackSelection}s. */
public class FakeTrackSelector extends DefaultTrackSelector {

  private final List<FakeTrackSelection> trackSelections = new ArrayList<>();
  private final boolean mayReuseTrackSelection;

  public FakeTrackSelector() {
    this(false);
  }

  /**
   * @param mayReuseTrackSelection Whether this {@link FakeTrackSelector} will reuse {@link
   *     TrackSelection}s during track selection, when it finds previously-selected track selection
   *     using the same {@link TrackGroup}.
   */
  public FakeTrackSelector(boolean mayReuseTrackSelection) {
    this.mayReuseTrackSelection = mayReuseTrackSelection;
  }

  @Override
  protected TrackSelection[] selectAllTracks(
      MappedTrackInfo mappedTrackInfo,
      int[][][] rendererFormatSupports,
      int[] rendererMixedMimeTypeAdaptationSupports,
      Parameters params)
      throws ExoPlaybackException {
    int rendererCount = mappedTrackInfo.getRendererCount();
    TrackSelection[] selections = new TrackSelection[rendererCount];
    for (int i = 0; i < rendererCount; i++) {
      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      boolean hasTracks = trackGroupArray.length > 0;
      selections[i] = hasTracks ? reuseOrCreateTrackSelection(trackGroupArray.get(0)) : null;
    }
    return selections;
  }

  @NonNull
  private FakeTrackSelection reuseOrCreateTrackSelection(TrackGroup trackGroup) {
    if (mayReuseTrackSelection) {
      for (FakeTrackSelection trackSelection : trackSelections) {
        if (trackSelection.getTrackGroup().equals(trackGroup)) {
          return trackSelection;
        }
      }
    }
    FakeTrackSelection trackSelection = new FakeTrackSelection(trackGroup);
    trackSelections.add(trackSelection);
    return trackSelection;
  }

  /** Returns list of all {@link FakeTrackSelection}s that this track selector has made so far. */
  public List<FakeTrackSelection> getAllTrackSelections() {
    return trackSelections;
  }

}

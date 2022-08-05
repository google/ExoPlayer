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

import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** A fake {@link MappingTrackSelector} that returns {@link FakeTrackSelection}s. */
public class FakeTrackSelector extends DefaultTrackSelector {

  private final FakeTrackSelectionFactory fakeTrackSelectionFactory;

  public FakeTrackSelector() {
    this(/* mayReuseTrackSelection= */ false);
  }

  /**
   * @param mayReuseTrackSelection Whether this {@link FakeTrackSelector} will reuse {@link
   *     ExoTrackSelection}s during track selection, when it finds previously-selected track
   *     selection using the same {@link TrackGroup}.
   */
  public FakeTrackSelector(boolean mayReuseTrackSelection) {
    this(new FakeTrackSelectionFactory(mayReuseTrackSelection));
  }

  private FakeTrackSelector(FakeTrackSelectionFactory fakeTrackSelectionFactory) {
    super(ApplicationProvider.getApplicationContext(), fakeTrackSelectionFactory);
    this.fakeTrackSelectionFactory = fakeTrackSelectionFactory;
  }

  @Override
  protected ExoTrackSelection.@NullableType Definition[] selectAllTracks(
      MappedTrackInfo mappedTrackInfo,
      @Capabilities int[][][] rendererFormatSupports,
      @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
      Parameters params) {
    int rendererCount = mappedTrackInfo.getRendererCount();
    ExoTrackSelection.@NullableType Definition[] definitions =
        new ExoTrackSelection.Definition[rendererCount];
    for (int i = 0; i < rendererCount; i++) {
      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      boolean hasTracks = trackGroupArray.length > 0;
      definitions[i] =
          hasTracks
              ? new ExoTrackSelection.Definition(trackGroupArray.get(0), /* tracks...= */ 0)
              : null;
    }
    return definitions;
  }

  /** Returns list of all {@link FakeTrackSelection}s that this track selector has made so far. */
  public List<FakeTrackSelection> getAllTrackSelections() {
    return fakeTrackSelectionFactory.trackSelections;
  }

  private static class FakeTrackSelectionFactory implements ExoTrackSelection.Factory {

    private final List<FakeTrackSelection> trackSelections;
    private final boolean mayReuseTrackSelection;

    public FakeTrackSelectionFactory(boolean mayReuseTrackSelection) {
      this.mayReuseTrackSelection = mayReuseTrackSelection;
      trackSelections = new ArrayList<>();
    }

    @Override
    public ExoTrackSelection[] createTrackSelections(
        ExoTrackSelection.@NullableType Definition[] definitions,
        BandwidthMeter bandwidthMeter,
        MediaPeriodId mediaPeriodId,
        Timeline timeline) {
      ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
      for (int i = 0; i < definitions.length; i++) {
        ExoTrackSelection.Definition definition = definitions[i];
        if (definition != null) {
          selections[i] = createTrackSelection(definition.group);
        }
      }
      return selections;
    }

    private ExoTrackSelection createTrackSelection(TrackGroup trackGroup) {
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
  }
}

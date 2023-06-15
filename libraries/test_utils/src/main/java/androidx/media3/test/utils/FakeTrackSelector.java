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
package androidx.media3.test.utils;

import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.RendererCapabilities.AdaptiveSupport;
import androidx.media3.exoplayer.RendererCapabilities.Capabilities;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;

/** A fake {@link MappingTrackSelector} that returns {@link FakeTrackSelection}s. */
@UnstableApi
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

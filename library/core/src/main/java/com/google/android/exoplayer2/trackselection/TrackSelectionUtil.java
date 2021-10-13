/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.TracksInfo.TrackGroupInfo;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection.Definition;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters.TrackSelectionOverride;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.dataflow.qual.Pure;

/** Track selection related utility methods. */
public final class TrackSelectionUtil {

  private TrackSelectionUtil() {}

  /** Functional interface to create a single adaptive track selection. */
  public interface AdaptiveTrackSelectionFactory {

    /**
     * Creates an adaptive track selection for the provided track selection definition.
     *
     * @param trackSelectionDefinition A {@link Definition} for the track selection.
     * @return The created track selection.
     */
    ExoTrackSelection createAdaptiveTrackSelection(Definition trackSelectionDefinition);
  }

  /**
   * Creates track selections for an array of track selection definitions, with at most one
   * multi-track adaptive selection.
   *
   * @param definitions The list of track selection {@link Definition definitions}. May include null
   *     values.
   * @param adaptiveTrackSelectionFactory A factory for the multi-track adaptive track selection.
   * @return The array of created track selection. For null entries in {@code definitions} returns
   *     null values.
   */
  public static @NullableType ExoTrackSelection[] createTrackSelectionsForDefinitions(
      @NullableType Definition[] definitions,
      AdaptiveTrackSelectionFactory adaptiveTrackSelectionFactory) {
    ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
    boolean createdAdaptiveTrackSelection = false;
    for (int i = 0; i < definitions.length; i++) {
      Definition definition = definitions[i];
      if (definition == null) {
        continue;
      }
      if (definition.tracks.length > 1 && !createdAdaptiveTrackSelection) {
        createdAdaptiveTrackSelection = true;
        selections[i] = adaptiveTrackSelectionFactory.createAdaptiveTrackSelection(definition);
      } else {
        selections[i] =
            new FixedTrackSelection(
                definition.group, definition.tracks[0], /* type= */ definition.type);
      }
    }
    return selections;
  }

  /**
   * Updates {@link DefaultTrackSelector.Parameters} with an override.
   *
   * @param parameters The current {@link DefaultTrackSelector.Parameters} to build upon.
   * @param rendererIndex The renderer index to update.
   * @param trackGroupArray The {@link TrackGroupArray} of the renderer.
   * @param isDisabled Whether the renderer should be set disabled.
   * @param override An optional override for the renderer. If null, no override will be set and an
   *     existing override for this renderer will be cleared.
   * @return The updated {@link DefaultTrackSelector.Parameters}.
   */
  public static DefaultTrackSelector.Parameters updateParametersWithOverride(
      DefaultTrackSelector.Parameters parameters,
      int rendererIndex,
      TrackGroupArray trackGroupArray,
      boolean isDisabled,
      @Nullable SelectionOverride override) {
    DefaultTrackSelector.ParametersBuilder builder =
        parameters
            .buildUpon()
            .clearSelectionOverrides(rendererIndex)
            .setRendererDisabled(rendererIndex, isDisabled);
    if (override != null) {
      builder.setSelectionOverride(rendererIndex, trackGroupArray, override);
    }
    return builder.build();
  }

  /**
   * Returns the {@link LoadErrorHandlingPolicy.FallbackOptions} with the tracks of the given {@link
   * ExoTrackSelection} and with a single location option indicating that there are no alternative
   * locations available.
   *
   * @param trackSelection The track selection to get the number of total and excluded tracks.
   * @return The {@link LoadErrorHandlingPolicy.FallbackOptions} for the given track selection.
   */
  public static LoadErrorHandlingPolicy.FallbackOptions createFallbackOptions(
      ExoTrackSelection trackSelection) {
    long nowMs = SystemClock.elapsedRealtime();
    int numberOfTracks = trackSelection.length();
    int numberOfExcludedTracks = 0;
    for (int i = 0; i < numberOfTracks; i++) {
      if (trackSelection.isBlacklisted(i, nowMs)) {
        numberOfExcludedTracks++;
      }
    }
    return new LoadErrorHandlingPolicy.FallbackOptions(
        /* numberOfLocations= */ 1,
        /* numberOfExcludedLocations= */ 0,
        numberOfTracks,
        numberOfExcludedTracks);
  }

  /**
   * Forces tracks in a {@link TrackGroup} to be the only ones selected for a {@link C.TrackType}.
   * No other tracks of that type will be selectable. If the forced tracks are not supported, then
   * no tracks of that type will be selected.
   *
   * @param trackSelectionOverrides The current {@link TrackSelectionOverride overrides}.
   * @param tracksInfo The current {@link TracksInfo}.
   * @param forcedTrackGroupIndex The index of the {@link TrackGroup} in {@code tracksInfo} that
   *     should have its track selected.
   * @param forcedTrackSelectionOverride The tracks to force selection of.
   * @return The updated {@link TrackSelectionOverride overrides}.
   */
  @Pure
  public static ImmutableMap<TrackGroup, TrackSelectionOverride> forceTrackSelection(
      ImmutableMap<TrackGroup, TrackSelectionOverride> trackSelectionOverrides,
      TracksInfo tracksInfo,
      int forcedTrackGroupIndex,
      TrackSelectionOverride forcedTrackSelectionOverride) {
    @C.TrackType
    int trackType = tracksInfo.getTrackGroupInfos().get(forcedTrackGroupIndex).getTrackType();
    ImmutableMap.Builder<TrackGroup, TrackSelectionOverride> overridesBuilder =
        new ImmutableMap.Builder<>();
    // Maintain overrides for the other track types.
    for (Map.Entry<TrackGroup, TrackSelectionOverride> entry : trackSelectionOverrides.entrySet()) {
      if (MimeTypes.getTrackType(entry.getKey().getFormat(0).sampleMimeType) != trackType) {
        overridesBuilder.put(entry);
      }
    }
    ImmutableList<TrackGroupInfo> trackGroupInfos = tracksInfo.getTrackGroupInfos();
    for (int i = 0; i < trackGroupInfos.size(); i++) {
      TrackGroup trackGroup = trackGroupInfos.get(i).getTrackGroup();
      if (i == forcedTrackGroupIndex) {
        overridesBuilder.put(trackGroup, forcedTrackSelectionOverride);
      } else {
        overridesBuilder.put(trackGroup, TrackSelectionOverride.DISABLE);
      }
    }
    return overridesBuilder.build();
  }

  /**
   * Removes all {@link TrackSelectionOverride overrides} associated with {@link TrackGroup
   * TrackGroups} of type {@code trackType}.
   *
   * @param trackType The {@link C.TrackType} of all overrides to remove.
   * @param trackSelectionOverrides The current {@link TrackSelectionOverride overrides}.
   * @return The updated {@link TrackSelectionOverride overrides}.
   */
  @Pure
  public static ImmutableMap<TrackGroup, TrackSelectionOverride>
      clearTrackSelectionOverridesForType(
          @C.TrackType int trackType,
          ImmutableMap<TrackGroup, TrackSelectionOverride> trackSelectionOverrides) {
    ImmutableMap.Builder<TrackGroup, TrackSelectionOverride> overridesBuilder =
        ImmutableMap.builder();
    for (Map.Entry<TrackGroup, TrackSelectionOverride> entry : trackSelectionOverrides.entrySet()) {
      if (MimeTypes.getTrackType(entry.getKey().getFormat(0).sampleMimeType) != trackType) {
        overridesBuilder.put(entry);
      }
    }
    return overridesBuilder.build();
  }
}

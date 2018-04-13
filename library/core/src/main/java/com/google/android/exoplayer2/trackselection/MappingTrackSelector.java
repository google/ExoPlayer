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
package com.google.android.exoplayer2.trackselection;

import android.support.annotation.IntDef;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Base class for {@link TrackSelector}s that first establish a mapping between {@link TrackGroup}s
 * and {@link Renderer}s, and then from that mapping create a {@link TrackSelection} for each
 * renderer.
 */
public abstract class MappingTrackSelector extends TrackSelector {

  /**
   * Provides mapped track information for each renderer.
   */
  public static final class MappedTrackInfo {

    /** Levels of renderer support. Higher numerical values indicate higher levels of support. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      RENDERER_SUPPORT_NO_TRACKS,
      RENDERER_SUPPORT_UNSUPPORTED_TRACKS,
      RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS,
      RENDERER_SUPPORT_PLAYABLE_TRACKS
    })
    @interface RendererSupport {}
    /** The renderer does not have any associated tracks. */
    public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
    /** The renderer has associated tracks, but all are of unsupported types. */
    public static final int RENDERER_SUPPORT_UNSUPPORTED_TRACKS = 1;
    /**
     * The renderer has associated tracks and at least one is of a supported type, but all of the
     * tracks whose types are supported exceed the renderer's capabilities.
     */
    public static final int RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS = 2;
    /** The renderer has associated tracks and can play at least one of them. */
    public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 3;

    /**
     * The number of renderers to which tracks are mapped.
     */
    public final int length;

    private final int[] rendererTrackTypes;
    private final TrackGroupArray[] trackGroups;
    private final int[] mixedMimeTypeAdaptiveSupport;
    private final int[][][] formatSupport;
    private final TrackGroupArray unassociatedTrackGroups;

    /**
     * @param rendererTrackTypes The track type supported by each renderer.
     * @param trackGroups The {@link TrackGroup}s mapped to each renderer.
     * @param mixedMimeTypeAdaptiveSupport The result of
     *     {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
     * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each
     *     mapped track, indexed by renderer index, track group index and track index (in that
     *     order).
     * @param unassociatedTrackGroups Any {@link TrackGroup}s not mapped to any renderer.
     */
    /* package */ MappedTrackInfo(int[] rendererTrackTypes,
        TrackGroupArray[] trackGroups, int[] mixedMimeTypeAdaptiveSupport,
        int[][][] formatSupport, TrackGroupArray unassociatedTrackGroups) {
      this.rendererTrackTypes = rendererTrackTypes;
      this.trackGroups = trackGroups;
      this.formatSupport = formatSupport;
      this.mixedMimeTypeAdaptiveSupport = mixedMimeTypeAdaptiveSupport;
      this.unassociatedTrackGroups = unassociatedTrackGroups;
      this.length = trackGroups.length;
    }

    /**
     * Returns the {@link TrackGroup}s mapped to the renderer at the specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackGroup}s.
     */
    public TrackGroupArray getTrackGroups(int rendererIndex) {
      return trackGroups[rendererIndex];
    }

    /**
     * Returns the extent to which a renderer can play each of the tracks in the track groups mapped
     * to it.
     *
     * @param rendererIndex The renderer index.
     * @return The result of {@link RendererCapabilities#supportsFormat} for each track mapped to
     *     the renderer, indexed by track group and track index (in that order).
     */
    public int[][] getRendererTrackSupport(int rendererIndex) {
      return formatSupport[rendererIndex];
    }

    /**
     * Returns the extent to which a renderer can play the tracks in the track groups mapped to it.
     *
     * @param rendererIndex The renderer index.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS}, {@link
     *     #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS}, {@link
     *     #RENDERER_SUPPORT_UNSUPPORTED_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public @RendererSupport int getRendererSupport(int rendererIndex) {
      int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      int[][] rendererFormatSupport = formatSupport[rendererIndex];
      for (int i = 0; i < rendererFormatSupport.length; i++) {
        for (int j = 0; j < rendererFormatSupport[i].length; j++) {
          int trackRendererSupport;
          switch (rendererFormatSupport[i][j] & RendererCapabilities.FORMAT_SUPPORT_MASK) {
            case RendererCapabilities.FORMAT_HANDLED:
              return RENDERER_SUPPORT_PLAYABLE_TRACKS;
            case RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES:
              trackRendererSupport = RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS;
              break;
            default:
              trackRendererSupport = RENDERER_SUPPORT_UNSUPPORTED_TRACKS;
              break;
          }
          bestRendererSupport = Math.max(bestRendererSupport, trackRendererSupport);
        }
      }
      return bestRendererSupport;
    }

    /**
     * Returns the best level of support obtained from {@link #getRendererSupport(int)} for all
     * renderers of the specified track type. If no renderers exist for the specified type then
     * {@link #RENDERER_SUPPORT_NO_TRACKS} is returned.
     *
     * @param trackType The track type. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS},
     *     {@link #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS},
     *     {@link #RENDERER_SUPPORT_UNSUPPORTED_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public int getTrackTypeRendererSupport(int trackType) {
      int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      for (int i = 0; i < length; i++) {
        if (rendererTrackTypes[i] == trackType) {
          bestRendererSupport = Math.max(bestRendererSupport, getRendererSupport(i));
        }
      }
      return bestRendererSupport;
    }

    /**
     * Returns the extent to which an individual track is supported by the renderer.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group to which the track belongs.
     * @param trackIndex The index of the track within the track group.
     * @return One of {@link RendererCapabilities#FORMAT_HANDLED},
     *     {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES},
     *     {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM},
     *     {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} and
     *     {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE}.
     */
    public int getTrackFormatSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return formatSupport[rendererIndex][groupIndex][trackIndex]
          & RendererCapabilities.FORMAT_SUPPORT_MASK;
    }

    /**
     * Returns the extent to which a renderer supports adaptation between supported tracks in a
     * specified {@link TrackGroup}.
     * <p>
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_HANDLED} are always considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM},
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} or
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} are never considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES} are considered only if
     * {@code includeCapabilitiesExceededTracks} is set to {@code true}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @param includeCapabilitiesExceededTracks True if formats that exceed the capabilities of the
     *     renderer should be included when determining support. False otherwise.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS},
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex,
        boolean includeCapabilitiesExceededTracks) {
      int trackCount = trackGroups[rendererIndex].get(groupIndex).length;
      // Iterate over the tracks in the group, recording the indices of those to consider.
      int[] trackIndices = new int[trackCount];
      int trackIndexCount = 0;
      for (int i = 0; i < trackCount; i++) {
        int fixedSupport = getTrackFormatSupport(rendererIndex, groupIndex, i);
        if (fixedSupport == RendererCapabilities.FORMAT_HANDLED
            || (includeCapabilitiesExceededTracks
            && fixedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES)) {
          trackIndices[trackIndexCount++] = i;
        }
      }
      trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
      return getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
    }

    /**
     * Returns the extent to which a renderer supports adaptation between specified tracks within
     * a {@link TrackGroup}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS},
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex, int[] trackIndices) {
      int handledTrackCount = 0;
      int adaptiveSupport = RendererCapabilities.ADAPTIVE_SEAMLESS;
      boolean multipleMimeTypes = false;
      String firstSampleMimeType = null;
      for (int i = 0; i < trackIndices.length; i++) {
        int trackIndex = trackIndices[i];
        String sampleMimeType = trackGroups[rendererIndex].get(groupIndex).getFormat(trackIndex)
            .sampleMimeType;
        if (handledTrackCount++ == 0) {
          firstSampleMimeType = sampleMimeType;
        } else {
          multipleMimeTypes |= !Util.areEqual(firstSampleMimeType, sampleMimeType);
        }
        adaptiveSupport = Math.min(adaptiveSupport, formatSupport[rendererIndex][groupIndex][i]
            & RendererCapabilities.ADAPTIVE_SUPPORT_MASK);
      }
      return multipleMimeTypes
          ? Math.min(adaptiveSupport, mixedMimeTypeAdaptiveSupport[rendererIndex])
          : adaptiveSupport;
    }

    /**
     * Returns {@link TrackGroup}s not mapped to any renderer.
     */
    public TrackGroupArray getUnassociatedTrackGroups() {
      return unassociatedTrackGroups;
    }

  }

  // TODO: Make DefaultTrackSelector.SelectionOverride final when this is removed.
  /** @deprecated Use {@link DefaultTrackSelector.SelectionOverride} */
  @Deprecated
  public static final class SelectionOverride extends DefaultTrackSelector.SelectionOverride {

    public SelectionOverride(TrackSelection.Factory factory, int groupIndex, int... tracks) {
      super(factory, groupIndex, tracks);
    }

  }

  private MappedTrackInfo currentMappedTrackInfo;

  /**
   * Returns the mapping information for the currently active track selection, or null if no
   * selection is currently active.
   */
  public final MappedTrackInfo getCurrentMappedTrackInfo() {
    return currentMappedTrackInfo;
  }

  // TrackSelector implementation.

  @Override
  public final void onSelectionActivated(Object info) {
    currentMappedTrackInfo = (MappedTrackInfo) info;
  }

  @Override
  public final TrackSelectorResult selectTracks(RendererCapabilities[] rendererCapabilities,
      TrackGroupArray trackGroups) throws ExoPlaybackException {
    // Structures into which data will be written during the selection. The extra item at the end
    // of each array is to store data associated with track groups that cannot be associated with
    // any renderer.
    int[] rendererTrackGroupCounts = new int[rendererCapabilities.length + 1];
    TrackGroup[][] rendererTrackGroups = new TrackGroup[rendererCapabilities.length + 1][];
    int[][][] rendererFormatSupports = new int[rendererCapabilities.length + 1][][];
    for (int i = 0; i < rendererTrackGroups.length; i++) {
      rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
      rendererFormatSupports[i] = new int[trackGroups.length][];
    }

    // Determine the extent to which each renderer supports mixed mimeType adaptation.
    int[] mixedMimeTypeAdaptationSupport = getMixedMimeTypeAdaptationSupport(rendererCapabilities);

    // Associate each track group to a preferred renderer, and evaluate the support that the
    // renderer provides for each track in the group.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      // Associate the group to a preferred renderer.
      int rendererIndex = findRenderer(rendererCapabilities, group);
      // Evaluate the support that the renderer provides for each track in the group.
      int[] rendererFormatSupport = rendererIndex == rendererCapabilities.length
          ? new int[group.length] : getFormatSupport(rendererCapabilities[rendererIndex], group);
      // Stash the results.
      int rendererTrackGroupCount = rendererTrackGroupCounts[rendererIndex];
      rendererTrackGroups[rendererIndex][rendererTrackGroupCount] = group;
      rendererFormatSupports[rendererIndex][rendererTrackGroupCount] = rendererFormatSupport;
      rendererTrackGroupCounts[rendererIndex]++;
    }

    // Create a track group array for each renderer, and trim each rendererFormatSupports entry.
    TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[rendererCapabilities.length];
    int[] rendererTrackTypes = new int[rendererCapabilities.length];
    for (int i = 0; i < rendererCapabilities.length; i++) {
      int rendererTrackGroupCount = rendererTrackGroupCounts[i];
      rendererTrackGroupArrays[i] = new TrackGroupArray(
          Arrays.copyOf(rendererTrackGroups[i], rendererTrackGroupCount));
      rendererFormatSupports[i] = Arrays.copyOf(rendererFormatSupports[i], rendererTrackGroupCount);
      rendererTrackTypes[i] = rendererCapabilities[i].getTrackType();
    }

    // Create a track group array for track groups not associated with a renderer.
    int unassociatedTrackGroupCount = rendererTrackGroupCounts[rendererCapabilities.length];
    TrackGroupArray unassociatedTrackGroupArray = new TrackGroupArray(Arrays.copyOf(
        rendererTrackGroups[rendererCapabilities.length], unassociatedTrackGroupCount));

    // Package up the track information and selections.
    MappedTrackInfo mappedTrackInfo =
        new MappedTrackInfo(
            rendererTrackTypes,
            rendererTrackGroupArrays,
            mixedMimeTypeAdaptationSupport,
            rendererFormatSupports,
            unassociatedTrackGroupArray);

    Pair<RendererConfiguration[], TrackSelection[]> result =
        selectTracks(rendererCapabilities, mappedTrackInfo);
    return new TrackSelectorResult(result.first, result.second, mappedTrackInfo);
  }

  /**
   * Given mapped track information, returns a track selection and configuration for each renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of each renderer.
   * @param mappedTrackInfo Mapped track information.
   * @return A pair consisting of the track selections and configurations for each renderer. A null
   *     configuration indicates the renderer should be disabled, in which case the track selection
   *     will also be null. A track selection may also be null for a non-disabled renderer if {@link
   *     RendererCapabilities#getTrackType()} is {@link C#TRACK_TYPE_NONE}.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected abstract Pair<RendererConfiguration[], TrackSelection[]> selectTracks(
      RendererCapabilities[] rendererCapabilities, MappedTrackInfo mappedTrackInfo)
      throws ExoPlaybackException;

  /**
   * Finds the renderer to which the provided {@link TrackGroup} should be mapped.
   * <p>
   * A {@link TrackGroup} is mapped to the renderer that reports the highest of (listed in
   * decreasing order of support) {@link RendererCapabilities#FORMAT_HANDLED},
   * {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES},
   * {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM} and
   * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE}. In the case that two or more renderers
   * report the same level of support, the renderer with the lowest index is associated.
   * <p>
   * If all renderers report {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} for all of the
   * tracks in the group, then {@code renderers.length} is returned to indicate that the group was
   * not mapped to any renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @param group The track group to map to a renderer.
   * @return The index of the renderer to which the track group was mapped, or
   *     {@code renderers.length} if it was not mapped to any renderer.
   * @throws ExoPlaybackException If an error occurs finding a renderer.
   */
  private static int findRenderer(RendererCapabilities[] rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int bestRendererIndex = rendererCapabilities.length;
    int bestFormatSupportLevel = RendererCapabilities.FORMAT_UNSUPPORTED_TYPE;
    for (int rendererIndex = 0; rendererIndex < rendererCapabilities.length; rendererIndex++) {
      RendererCapabilities rendererCapability = rendererCapabilities[rendererIndex];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        int formatSupportLevel = rendererCapability.supportsFormat(group.getFormat(trackIndex))
            & RendererCapabilities.FORMAT_SUPPORT_MASK;
        if (formatSupportLevel > bestFormatSupportLevel) {
          bestRendererIndex = rendererIndex;
          bestFormatSupportLevel = formatSupportLevel;
          if (bestFormatSupportLevel == RendererCapabilities.FORMAT_HANDLED) {
            // We can't do better.
            return bestRendererIndex;
          }
        }
      }
    }
    return bestRendererIndex;
  }

  /**
   * Calls {@link RendererCapabilities#supportsFormat} for each track in the specified
   * {@link TrackGroup}, returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderer.
   * @param group The track group to evaluate.
   * @return An array containing the result of calling
   *     {@link RendererCapabilities#supportsFormat} for each track in the group.
   * @throws ExoPlaybackException If an error occurs determining the format support.
   */
  private static int[] getFormatSupport(RendererCapabilities rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int[] formatSupport = new int[group.length];
    for (int i = 0; i < group.length; i++) {
      formatSupport[i] = rendererCapabilities.supportsFormat(group.getFormat(i));
    }
    return formatSupport;
  }

  /**
   * Calls {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer,
   * returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @return An array containing the result of calling
   *     {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @throws ExoPlaybackException If an error occurs determining the adaptation support.
   */
  private static int[] getMixedMimeTypeAdaptationSupport(
      RendererCapabilities[] rendererCapabilities) throws ExoPlaybackException {
    int[] mixedMimeTypeAdaptationSupport = new int[rendererCapabilities.length];
    for (int i = 0; i < mixedMimeTypeAdaptationSupport.length; i++) {
      mixedMimeTypeAdaptationSupport[i] = rendererCapabilities[i].supportsMixedMimeTypeAdaptation();
    }
    return mixedMimeTypeAdaptationSupport;
  }

}

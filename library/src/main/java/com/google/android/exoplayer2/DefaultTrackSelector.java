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
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import android.os.Handler;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A {@link TrackSelector} suitable for a wide range of use cases.
 */
public final class DefaultTrackSelector extends TrackSelector implements
    TrackSelectionPolicy.InvalidationListener{

  /**
   * Interface definition for a callback to be notified of {@link DefaultTrackSelector} events.
   */
  public interface EventListener {

    /**
     * Invoked when the track information has changed.
     *
     * @param trackInfo Contains the new track and track selection information.
     */
    void onTracksChanged(TrackInfo trackInfo);

  }

  private final Handler eventHandler;
  private final CopyOnWriteArraySet<EventListener> listeners;
  private final SparseArray<Map<TrackGroupArray, TrackSelection>> trackSelectionOverrides;
  private final SparseBooleanArray rendererDisabledFlags;
  private final TrackSelectionPolicy trackSelectionPolicy;

  private TrackInfo activeTrackInfo;

  /**
   * @param trackSelectionPolicy Defines the policy for track selection.
   * @param eventHandler A handler to use when delivering events to listeners added via
   *     {@link #addListener(EventListener)}.
   */
  public DefaultTrackSelector(TrackSelectionPolicy trackSelectionPolicy, Handler eventHandler) {
    this.trackSelectionPolicy = Assertions.checkNotNull(trackSelectionPolicy);
    this.eventHandler = eventHandler;
    this.listeners = new CopyOnWriteArraySet<>();
    trackSelectionOverrides = new SparseArray<>();
    rendererDisabledFlags = new SparseBooleanArray();
    trackSelectionPolicy.init(this);
  }

  /**
   * Register a listener to receive events from the selector. The listener's methods will be invoked
   * using the {@link Handler} that was passed to the constructor.
   *
   * @param listener The listener to register.
   */
  public void addListener(EventListener listener) {
    Assertions.checkState(eventHandler != null);
    listeners.add(listener);
  }

  /**
   * Unregister a listener. The listener will no longer receive events from the selector.
   *
   * @param listener The listener to unregister.
   */
  public void removeListener(EventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Gets information about the current tracks and track selection for each renderer.
   *
   * @return Contains the current tracks and track selection information.
   */
  public TrackInfo getTrackInfo() {
    return activeTrackInfo;
  }

  /**
   * Sets whether the renderer at the specified index is disabled.
   *
   * @param rendererIndex The renderer index.
   * @param disabled True if the renderer is disabled. False otherwise.
   */
  public void setRendererDisabled(int rendererIndex, boolean disabled) {
    if (rendererDisabledFlags.get(rendererIndex) == disabled) {
      // The disabled flag is unchanged.
      return;
    }
    rendererDisabledFlags.put(rendererIndex, disabled);
    invalidate();
  }

  /**
   * Returns whether the renderer is disabled.
   *
   * @param rendererIndex The renderer index.
   * @return True if the renderer is disabled. False otherwise.
   */
  public boolean getRendererDisabled(int rendererIndex) {
    return rendererDisabledFlags.get(rendererIndex);
  }

  /**
   * Overrides the track selection for the renderer at a specified index.
   * <p>
   * When the {@link TrackGroupArray} available to the renderer at the specified index matches the
   * one provided, the override is applied. When the {@link TrackGroupArray} does not match, the
   * override has no effect. The override replaces any previous override for the renderer and the
   * provided {@link TrackGroupArray}.
   * <p>
   * Passing a {@code null} override will explicitly disable the renderer. To remove overrides use
   * {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link #clearSelectionOverrides(int)}
   * or {@link #clearSelectionOverrides()}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be applied.
   * @param override The overriding {@link TrackSelection}.
   */
  // TODO - Don't allow overrides that select unsupported tracks, unless some flag has been
  // explicitly set by the user to indicate that they want this.
  public void setSelectionOverride(int rendererIndex, TrackGroupArray groups,
      TrackSelection override) {
    Map<TrackGroupArray, TrackSelection> overrides = trackSelectionOverrides.get(rendererIndex);
    if (overrides == null) {
      overrides = new HashMap<>();
      trackSelectionOverrides.put(rendererIndex, overrides);
    }
    if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
      // The override is unchanged.
      return;
    }
    overrides.put(groups, override);
    invalidate();
  }

  /**
   * Returns whether there is an override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray}.
   * @return True if there is an override. False otherwise.
   */
  public boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, TrackSelection> overrides = trackSelectionOverrides.get(rendererIndex);
    return overrides != null && overrides.containsKey(groups);
  }

  /**
   * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be cleared.
   */
  public void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, TrackSelection> overrides = trackSelectionOverrides.get(rendererIndex);
    if (overrides == null || !overrides.containsKey(groups)) {
      // Nothing to clear.
      return;
    }
    overrides.remove(groups);
    if (overrides.isEmpty()) {
      trackSelectionOverrides.remove(rendererIndex);
    }
    invalidate();
  }

  /**
   * Clears all track selection override for the specified renderer.
   *
   * @param rendererIndex The renderer index.
   */
  public void clearSelectionOverrides(int rendererIndex) {
    Map<TrackGroupArray, TrackSelection> overrides = trackSelectionOverrides.get(rendererIndex);
    if (overrides == null || overrides.isEmpty()) {
      // Nothing to clear.
      return;
    }
    trackSelectionOverrides.remove(rendererIndex);
    invalidate();
  }

  /**
   * Clears all track selection overrides.
   */
  public void clearSelectionOverrides() {
    if (trackSelectionOverrides.size() == 0) {
      // Nothing to clear.
      return;
    }
    trackSelectionOverrides.clear();
    invalidate();
  }

  /**
   * Invoked when the {@link TrackSelectionPolicy} has changed.
   */
  @Override
  public void invalidatePolicySelections() {
    invalidate();
  }

  // TrackSelector implementation.

  @Override
  protected void onSelectionActivated(Object selectionInfo) {
    activeTrackInfo = (TrackInfo) selectionInfo;
    notifyTrackInfoChanged(activeTrackInfo);
  }

  @Override
  protected Pair<TrackSelectionArray, Object> selectTracks(Renderer[] renderers,
      TrackGroupArray trackGroups) throws ExoPlaybackException {
    // Structures into which data will be written during the selection. The extra item at the end
    // of each array is to store data associated with track groups that cannot be associated with
    // any renderer.
    int[] rendererTrackGroupCounts = new int[renderers.length + 1];
    TrackGroup[][] rendererTrackGroups = new TrackGroup[renderers.length + 1][];
    int[][][] rendererFormatSupports = new int[renderers.length + 1][][];
    for (int i = 0; i < rendererTrackGroups.length; i++) {
      rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
      rendererFormatSupports[i] = new int[trackGroups.length][];
    }

    // Determine the extent to which each renderer supports mixed mimeType adaptation.
    int[] mixedMimeTypeAdaptationSupport = getMixedMimeTypeAdaptationSupport(renderers);

    // Associate each track group to a preferred renderer, and evaluate the support that the
    // renderer provides for each track in the group.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      // Associate the group to a preferred renderer.
      int rendererIndex = findRenderer(renderers, group);
      // Evaluate the support that the renderer provides for each track in the group.
      int[] rendererFormatSupport = rendererIndex == renderers.length ? new int[group.length]
          : getFormatSupport(renderers[rendererIndex], group);
      // Stash the results.
      int rendererTrackGroupCount = rendererTrackGroupCounts[rendererIndex];
      rendererTrackGroups[rendererIndex][rendererTrackGroupCount] = group;
      rendererFormatSupports[rendererIndex][rendererTrackGroupCount] = rendererFormatSupport;
      rendererTrackGroupCounts[rendererIndex]++;
    }

    // Create a track group array for each renderer, and trim each rendererFormatSupports entry.
    TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      int rendererTrackGroupCount = rendererTrackGroupCounts[i];
      rendererTrackGroupArrays[i] = new TrackGroupArray(
          Arrays.copyOf(rendererTrackGroups[i], rendererTrackGroupCount));
      rendererFormatSupports[i] = Arrays.copyOf(rendererFormatSupports[i], rendererTrackGroupCount);
    }

    // Create a track group array for track groups not associated with a renderer.
    int unassociatedTrackGroupCount = rendererTrackGroupCounts[renderers.length];
    TrackGroupArray unassociatedTrackGroupArray = new TrackGroupArray(
        Arrays.copyOf(rendererTrackGroups[renderers.length], unassociatedTrackGroupCount));

    TrackSelection[] rendererTrackSelections = trackSelectionPolicy.selectTracks(renderers,
        rendererTrackGroupArrays, rendererFormatSupports);

    // Apply track disabling and overriding.
    for (int i = 0; i < renderers.length; i++) {
      if (rendererDisabledFlags.get(i)) {
        rendererTrackSelections[i] = null;
      } else {
        Map<TrackGroupArray, TrackSelection> override = trackSelectionOverrides.get(i);
        TrackSelection overrideSelection = override == null ? null
            : override.get(rendererTrackGroupArrays[i]);
        if (overrideSelection != null) {
          rendererTrackSelections[i] = overrideSelection;
        }
      }
    }

    // The track selections above index into the track group arrays associated to each renderer,
    // and not to the original track groups passed to this method. Build the corresponding track
    // selections into the original track groups to pass back as the final selection.
    TrackSelection[] trackSelections = new TrackSelection[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      TrackSelection selection = rendererTrackSelections[i];
      if (selection != null) {
        TrackGroup group = rendererTrackGroupArrays[i].get(selection.group);
        int originalGroupIndex = findGroupInGroupArray(trackGroups, group);
        trackSelections[i] = new TrackSelection(originalGroupIndex, selection.getTracks());
      }
    }

    // Package up the track information and selections.
    TrackSelectionArray trackSelectionArray = new TrackSelectionArray(trackSelections);
    TrackInfo trackInfo = new TrackInfo(rendererTrackGroupArrays, rendererTrackSelections,
        mixedMimeTypeAdaptationSupport, rendererFormatSupports, unassociatedTrackGroupArray);
    return Pair.<TrackSelectionArray, Object>create(trackSelectionArray, trackInfo);
  }

  /**
   * Finds the renderer to which the provided {@link TrackGroup} should be associated.
   * <p>
   * A {@link TrackGroup} is associated to a renderer that reports
   * {@link Renderer#FORMAT_HANDLED} support for one or more of the tracks in the group, or
   * {@link Renderer#FORMAT_EXCEEDS_CAPABILITIES} if no such renderer exists, or
   * {@link Renderer#FORMAT_UNSUPPORTED_SUBTYPE} if again no such renderer exists. In the case
   * that two or more renderers report the same level of support, the renderer with the lowest index
   * is associated.
   * <p>
   * If all renderers report {@link Renderer#FORMAT_UNSUPPORTED_TYPE} for all of the tracks in
   * the group, then {@code renderers.length} is returned to indicate that no association was made.
   *
   * @param renderers The renderers from which to select.
   * @param group The {@link TrackGroup} whose associated renderer is to be found.
   * @return The index of the associated renderer, or {@code renderers.length} if no association
   *     was made.
   * @throws ExoPlaybackException If an error occurs finding a renderer.
   */
  private static int findRenderer(Renderer[] renderers, TrackGroup group)
      throws ExoPlaybackException {
    int bestRendererIndex = renderers.length;
    int bestSupportLevel = Renderer.FORMAT_UNSUPPORTED_TYPE;
    for (int rendererIndex = 0; rendererIndex < renderers.length; rendererIndex++) {
      Renderer renderer = renderers[rendererIndex];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        int trackSupportLevel = renderer.supportsFormat(group.getFormat(trackIndex));
        if (trackSupportLevel > bestSupportLevel) {
          bestRendererIndex = rendererIndex;
          bestSupportLevel = trackSupportLevel;
          if (bestSupportLevel == Renderer.FORMAT_HANDLED) {
            // We can't do better.
            return bestRendererIndex;
          }
        }
      }
    }
    return bestRendererIndex;
  }

  /**
   * Calls {@link Renderer#supportsFormat(Format)} for each track in the specified
   * {@link TrackGroup}, returning the results in an array.
   *
   * @param renderer The renderer to evaluate.
   * @param group The {@link TrackGroup} to evaluate.
   * @return An array containing the result of calling {@link Renderer#supportsFormat(Format)}
   *     on the renderer for each track in the group.
   * @throws ExoPlaybackException If an error occurs determining the format support.
   */
  private static int[] getFormatSupport(Renderer renderer, TrackGroup group)
      throws ExoPlaybackException {
    int[] formatSupport = new int[group.length];
    for (int i = 0; i < group.length; i++) {
      formatSupport[i] = renderer.supportsFormat(group.getFormat(i));
    }
    return formatSupport;
  }

  /**
   * Calls {@link Renderer#supportsMixedMimeTypeAdaptation()} for each renderer, returning
   * the results in an array.
   *
   * @param renderers The renderers to evaluate.
   * @return An array containing the result of calling
   *     {@link Renderer#supportsMixedMimeTypeAdaptation()} on each renderer.
   * @throws ExoPlaybackException If an error occurs determining the adaptation support.
   */
  private static int[] getMixedMimeTypeAdaptationSupport(Renderer[] renderers)
      throws ExoPlaybackException {
    int[] mixedMimeTypeAdaptationSupport = new int[renderers.length];
    for (int i = 0; i < mixedMimeTypeAdaptationSupport.length; i++) {
      mixedMimeTypeAdaptationSupport[i] = renderers[i].supportsMixedMimeTypeAdaptation();
    }
    return mixedMimeTypeAdaptationSupport;
  }

  /**
   * Finds the specified group in a group array, using referential equality.
   *
   * @param groupArray The group array to search.
   * @param group The group to search for.
   * @return The index of the group in the group array.
   * @throws IllegalStateException If the group was not found.
   */
  private static int findGroupInGroupArray(TrackGroupArray groupArray, TrackGroup group) {
    for (int i = 0; i < groupArray.length; i++) {
      if (groupArray.get(i) == group) {
        return i;
      }
    }
    throw new IllegalStateException();
  }

  private void notifyTrackInfoChanged(final TrackInfo trackInfo) {
    if (eventHandler != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          for (EventListener listener : listeners) {
            listener.onTracksChanged(trackInfo);
          }
        }
      });
    }
  }

  /**
   * Provides track information for each {@link Renderer}.
   */
  public static final class TrackInfo {

    /**
     * The renderer does not have any associated tracks.
     */
    public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
    /**
     * The renderer has associated tracks, but cannot play any of them.
     */
    public static final int RENDERER_SUPPORT_UNPLAYABLE_TRACKS = 1;
    /**
     * The renderer has associated tracks, and can play at least one of them.
     */
    public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 2;

    /**
     * The number of renderers.
     */
    public final int rendererCount;

    private final TrackGroupArray[] trackGroups;
    private final TrackSelection[] trackSelections;
    private final int[] mixedMimeTypeAdaptiveSupport;
    private final int[][][] formatSupport;
    private final TrackGroupArray unassociatedTrackGroups;

    /**
     * @param trackGroups The {@link TrackGroupArray}s for each renderer.
     * @param trackSelections The current {@link TrackSelection}s for each renderer.
     * @param mixedMimeTypeAdaptiveSupport The result of
     *     {@link Renderer#supportsMixedMimeTypeAdaptation()} for each renderer.
     * @param formatSupport The result of {@link Renderer#supportsFormat(Format)} for each
     *     track, indexed by renderer index, group index and track index (in that order).
     * @param unassociatedTrackGroups Contains {@link TrackGroup}s not associated with any renderer.
     */
    /* package */ TrackInfo(TrackGroupArray[] trackGroups, TrackSelection[] trackSelections,
        int[] mixedMimeTypeAdaptiveSupport, int[][][] formatSupport,
        TrackGroupArray unassociatedTrackGroups) {
      this.trackGroups = trackGroups;
      this.trackSelections = trackSelections;
      this.formatSupport = formatSupport;
      this.mixedMimeTypeAdaptiveSupport = mixedMimeTypeAdaptiveSupport;
      this.unassociatedTrackGroups = unassociatedTrackGroups;
      this.rendererCount = trackGroups.length;
    }

    /**
     * Gets the array of {@link TrackGroup}s associated to the renderer at a specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackGroup}s.
     */
    public TrackGroupArray getTrackGroups(int rendererIndex) {
      return trackGroups[rendererIndex];
    }

    /**
     * Gets the current {@link TrackSelection} for the renderer at a specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackSelection}, or null if the renderer is disabled.
     */
    public TrackSelection getTrackSelection(int rendererIndex) {
      return trackSelections[rendererIndex];
    }

    /**
     * Gets the extent to which a renderer can support playback of the tracks associated to it.
     *
     * @param rendererIndex The renderer index.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS},
     *     {@link #RENDERER_SUPPORT_UNPLAYABLE_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public int getRendererSupport(int rendererIndex) {
      boolean hasTracks = false;
      int[][] rendererFormatSupport = formatSupport[rendererIndex];
      for (int i = 0; i < rendererFormatSupport.length; i++) {
        for (int j = 0; j < rendererFormatSupport[i].length; j++) {
          hasTracks = true;
          if ((rendererFormatSupport[i][j] & Renderer.FORMAT_SUPPORT_MASK)
              == Renderer.FORMAT_HANDLED) {
            return RENDERER_SUPPORT_PLAYABLE_TRACKS;
          }
        }
      }
      return hasTracks ? RENDERER_SUPPORT_UNPLAYABLE_TRACKS : RENDERER_SUPPORT_NO_TRACKS;
    }

    /**
     * Gets the extent to which the format of an individual track is supported by the renderer.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group to which the track belongs.
     * @param trackIndex The index of the track within the group.
     * @return One of {@link Renderer#FORMAT_HANDLED},
     *     {@link Renderer#FORMAT_EXCEEDS_CAPABILITIES},
     *     {@link Renderer#FORMAT_UNSUPPORTED_SUBTYPE} and
     *     {@link Renderer#FORMAT_UNSUPPORTED_TYPE}.
     */
    public int getTrackFormatSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return formatSupport[rendererIndex][groupIndex][trackIndex]
          & Renderer.FORMAT_SUPPORT_MASK;
    }

    /**
     * Gets the extent to which the renderer supports adaptation between supported tracks in a
     * specified {@link TrackGroup}.
     * <p>
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link Renderer#FORMAT_HANDLED} are always considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link Renderer#FORMAT_UNSUPPORTED_TYPE} or
     * {@link Renderer#FORMAT_UNSUPPORTED_SUBTYPE} are never considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link Renderer#FORMAT_EXCEEDS_CAPABILITIES} are considered only if
     * {@code includeCapabilitiesExceededTracks} is set to {@code true}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group.
     * @param includeCapabilitiesExceededTracks True if formats that exceed the capabilities of the
     *     renderer should be included when determining support. False otherwise.
     * @return One of {@link Renderer#ADAPTIVE_SEAMLESS},
     *     {@link Renderer#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link Renderer#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex,
        boolean includeCapabilitiesExceededTracks) {
      int trackCount = trackGroups[rendererIndex].get(groupIndex).length;
      // Iterate over the tracks in the group, recording the indices of those to consider.
      int[] trackIndices = new int[trackCount];
      int trackIndexCount = 0;
      for (int i = 0; i < trackCount; i++) {
        int fixedSupport = getTrackFormatSupport(rendererIndex, groupIndex, i);
        if (fixedSupport == Renderer.FORMAT_HANDLED || (includeCapabilitiesExceededTracks
            && fixedSupport == Renderer.FORMAT_EXCEEDS_CAPABILITIES)) {
          trackIndices[trackIndexCount++] = i;
        }
      }
      trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
      return getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
    }

    /**
     * Gets the extent to which the renderer supports adaptation between specified tracks within a
     * {@link TrackGroup}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group.
     * @return One of {@link Renderer#ADAPTIVE_SEAMLESS},
     *     {@link Renderer#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link Renderer#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex, int[] trackIndices) {
      TrackGroup trackGroup = trackGroups[rendererIndex].get(groupIndex);
      if (!trackGroup.adaptive) {
        return Renderer.ADAPTIVE_NOT_SUPPORTED;
      }
      int handledTrackCount = 0;
      int adaptiveSupport = Renderer.ADAPTIVE_SEAMLESS;
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
        adaptiveSupport = Math.min(adaptiveSupport,
            formatSupport[rendererIndex][groupIndex][i] & Renderer.ADAPTIVE_SUPPORT_MASK);
      }
      return multipleMimeTypes
          ? Math.min(adaptiveSupport, mixedMimeTypeAdaptiveSupport[rendererIndex])
          : adaptiveSupport;
    }

    /**
     * Gets the {@link TrackGroup}s not associated with any {@link Renderer}.
     *
     * @return The {@link TrackGroup}s not associated with any {@link Renderer}.
     */
    public TrackGroupArray getUnassociatedTrackGroups() {
      return unassociatedTrackGroups;
    }

  }

}

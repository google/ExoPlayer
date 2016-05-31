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

import com.google.android.exoplayer.util.Util;

/**
 * A {@link TrackSelectionPolicy} that allows configuration of common parameters.
 */
public class DefaultTrackSelectionPolicy extends TrackSelectionPolicy {

  private static final int[] NO_TRACKS = new int[0];

  private String preferredLanguage;
  private boolean allowMixedMimeAdaptiveness;
  private boolean allowNonSeamlessAdaptiveness;

  public DefaultTrackSelectionPolicy() {
    allowNonSeamlessAdaptiveness = true;
  }

  public void setPreferredLanguage(String preferredLanguage) {
    if (!Util.areEqual(this.preferredLanguage, preferredLanguage)) {
      this.preferredLanguage = preferredLanguage;
      invalidate();
    }
  }

  public void allowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
    if (this.allowMixedMimeAdaptiveness != allowMixedMimeAdaptiveness) {
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      invalidate();
    }
  }

  public void allowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
    if (this.allowNonSeamlessAdaptiveness != allowNonSeamlessAdaptiveness) {
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      invalidate();
    }
  }

  @Override
  public TrackSelection[] selectTracks(TrackRenderer[] renderers,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException {
    // Make a track selection for each renderer.
    TrackSelection[] rendererTrackSelections = new TrackSelection[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      switch (renderers[i].getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          rendererTrackSelections[i] = selectTrackForVideoRenderer(renderers[i],
              rendererTrackGroupArrays[i], rendererFormatSupports[i], allowMixedMimeAdaptiveness,
              allowNonSeamlessAdaptiveness);
          break;
        case C.TRACK_TYPE_AUDIO:
          rendererTrackSelections[i] = selectTrackForAudioRenderer(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], preferredLanguage);
          break;
        case C.TRACK_TYPE_TEXT:
          rendererTrackSelections[i] = selectTrackForTextRenderer(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], preferredLanguage);
          break;
        default:
          rendererTrackSelections[i] = selectFirstSupportedTrack(rendererTrackGroupArrays[i],
              rendererFormatSupports[i]);
          break;
      }
    }
    return rendererTrackSelections;
  }

  private static TrackSelection selectTrackForVideoRenderer(TrackRenderer renderer,
      TrackGroupArray trackGroups, int[][] formatSupport, boolean allowMixedMimeAdaptiveness,
      boolean allowNonSeamlessAdaptiveness) throws ExoPlaybackException {
    int requiredAdaptiveSupport = allowNonSeamlessAdaptiveness
        ? TrackRenderer.ADAPTIVE_NOT_SEAMLESS | TrackRenderer.ADAPTIVE_SEAMLESS
        : TrackRenderer.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes = allowMixedMimeAdaptiveness
        && (renderer.supportsMixedMimeTypeAdaptation() & requiredAdaptiveSupport) != 0;
    int largestAdaptiveGroup = -1;
    int[] largestAdaptiveGroupTracks = NO_TRACKS;
    for (int i = 0; i < trackGroups.length; i++) {
      int[] adaptiveTracks = getAdaptiveTracksOfGroup(trackGroups.get(i), formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport);
      if (adaptiveTracks.length > largestAdaptiveGroupTracks.length) {
        largestAdaptiveGroup = i;
        largestAdaptiveGroupTracks = adaptiveTracks;
      }
    }
    if (largestAdaptiveGroup != -1) {
      return new TrackSelection(largestAdaptiveGroup, largestAdaptiveGroupTracks);
    }
    return selectFirstSupportedTrack(trackGroups, formatSupport);
  }

  private static int[] getAdaptiveTracksOfGroup(TrackGroup trackGroup, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport) {
    if (!trackGroup.adaptive) {
      return NO_TRACKS;
    }

    String mimeType = null;
    int adaptiveTracksCount = 0;
    if (allowMixedMimeTypes) {
      adaptiveTracksCount = getSupportedTrackCountForMimeType(trackGroup, formatSupport,
          requiredAdaptiveSupport, mimeType);
    } else {
      for (int i = 0; i < trackGroup.length; i++) {
        if (!Util.areEqual(mimeType, trackGroup.getFormat(i).sampleMimeType)) {
          int countForMimeType = getSupportedTrackCountForMimeType(trackGroup, formatSupport,
              requiredAdaptiveSupport, trackGroup.getFormat(i).sampleMimeType);
          if (countForMimeType > adaptiveTracksCount) {
            adaptiveTracksCount = countForMimeType;
            mimeType = trackGroup.getFormat(i).sampleMimeType;
          }
        }
      }
    }

    if (adaptiveTracksCount <= 1) {
      // Not enough tracks to allow adaptation.
      return NO_TRACKS;
    }
    int[] adaptiveTracks = new int[adaptiveTracksCount];
    adaptiveTracksCount = 0;
    for (int i = 0; i < trackGroup.length; i++) {
      if (isAdaptiveTrack(trackGroup.getFormat(i).sampleMimeType, mimeType, formatSupport[i],
          requiredAdaptiveSupport)) {
        adaptiveTracks[adaptiveTracksCount++] = i;
      }
    }
    return adaptiveTracks;
  }

  private static int getSupportedTrackCountForMimeType(TrackGroup trackGroup, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType) {
    int adaptiveTracksCount = 0;
    for (int i = 0; i < trackGroup.length; i++) {
      if (isAdaptiveTrack(trackGroup.getFormat(i).sampleMimeType, mimeType, formatSupport[i],
          requiredAdaptiveSupport)) {
        adaptiveTracksCount++;
      }
    }
    return adaptiveTracksCount;
  }

  private static boolean isAdaptiveTrack(String trackMimeType, String mimeType, int formatSupport,
      int requiredAdaptiveSupport) {
    return isSupported(formatSupport) && (formatSupport & requiredAdaptiveSupport) != 0
        && (mimeType == null || Util.areEqual(trackMimeType, mimeType));
  }

  private static TrackSelection selectTrackForTextRenderer(TrackGroupArray trackGroups,
      int[][] formatSupport, String preferredLanguage) {
    int firstForcedGroup = -1;
    int firstForcedTrack = -1;
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup trackGroup = trackGroups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])
            && (trackGroup.getFormat(trackIndex).selectionFlags
                & Format.SELECTION_FLAG_FORCED) != 0) {
          if (firstForcedGroup == -1) {
            firstForcedGroup = groupIndex;
            firstForcedTrack = trackIndex;
          }
          if (preferredLanguage != null
              && preferredLanguage.equals(trackGroup.getFormat(trackIndex).language)) {
            return new TrackSelection(groupIndex, trackIndex);
          }
        }
      }
    }
    return firstForcedGroup != -1 ? new TrackSelection(firstForcedGroup, firstForcedTrack) : null;
  }

  private static TrackSelection selectFirstSupportedTrack(TrackGroupArray trackGroups,
      int[][] formatSupport) {
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup trackGroup = trackGroups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])) {
          return new TrackSelection(groupIndex, trackIndex);
        }
      }
    }
    return null;
  }

  private static TrackSelection selectTrackForAudioRenderer(TrackGroupArray trackGroups,
      int[][] formatSupport, String preferredLanguage) {
    if (preferredLanguage != null) {
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        TrackGroup trackGroup = trackGroups.get(groupIndex);
        int[] trackFormatSupport = formatSupport[groupIndex];
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          if (isSupported(trackFormatSupport[trackIndex])
              && preferredLanguage.equals(trackGroup.getFormat(trackIndex).language)) {
            return new TrackSelection(groupIndex, trackIndex);
          }
        }
      }
    }
    // No preferred language was selected or no audio track presented the preferred language.
    return selectFirstSupportedTrack(trackGroups, formatSupport);
  }

  private static boolean isSupported(int formatSupport) {
    return (formatSupport & TrackRenderer.FORMAT_SUPPORT_MASK) == TrackRenderer.FORMAT_HANDLED;
  }

}


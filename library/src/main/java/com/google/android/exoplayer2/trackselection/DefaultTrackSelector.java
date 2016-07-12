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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Util;

import android.os.Handler;

import java.util.Locale;

/**
 * A {@link MappingTrackSelector} that allows configuration of common parameters.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  private static final int[] NO_TRACKS = new int[0];

  private String preferredLanguage;
  private boolean allowMixedMimeAdaptiveness;
  private boolean allowNonSeamlessAdaptiveness;
  private int maxVideoWidth;
  private int maxVideoHeight;
  private boolean exceedVideoConstraintsIfNecessary;

  public DefaultTrackSelector(Handler eventHandler) {
    super(eventHandler);
    allowNonSeamlessAdaptiveness = true;
    exceedVideoConstraintsIfNecessary = true;
    maxVideoWidth = Integer.MAX_VALUE;
    maxVideoHeight = Integer.MAX_VALUE;
  }

  /**
   * Sets the preferred language for audio and text tracks.
   *
   * @param preferredLanguage The language as defined by RFC 5646.
   */
  public void setPreferredLanguage(String preferredLanguage) {
    String adjustedPreferredLanguage = new Locale(preferredLanguage).getLanguage();
    if (!Util.areEqual(this.preferredLanguage, adjustedPreferredLanguage)) {
      this.preferredLanguage = adjustedPreferredLanguage;
      invalidate();
    }
  }

  /**
   * Sets whether selections may contain mixed mime types.
   *
   * @param allowMixedMimeAdaptiveness True to allow mixed mime types, false otherwise.
   */
  public void allowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
    if (this.allowMixedMimeAdaptiveness != allowMixedMimeAdaptiveness) {
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      invalidate();
    }
  }

  /**
   * Sets whether non seamless adaptation is allowed.
   *
   * @param allowNonSeamlessAdaptiveness True to allow non seamless adaptation between tracks, false
   *     otherwise.
   */
  public void allowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
    if (this.allowNonSeamlessAdaptiveness != allowNonSeamlessAdaptiveness) {
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      invalidate();
    }
  }

  /**
   * Sets the maximum allowed size for video tracks.
   *
   * @param maxVideoWidth Maximum allowed width.
   * @param maxVideoHeight Maximum allowed height.
   */
  public void setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
    if (this.maxVideoWidth != maxVideoWidth || this.maxVideoHeight != maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      invalidate();
    }
  }

  /**
   * Equivalent to {@code setMaxVideoSize(1279, 719)}.
   */
  public void setMaxVideoSizeSd() {
    setMaxVideoSize(1279, 719);
  }

  /**
   * Equivalent to {@code setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
   */
  public void clearMaxVideoSize() {
    setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  /**
   * Sets whether video constraints should be ignored when no selection can be made otherwise.
   *
   * @param exceedVideoConstraintsIfNecessary True to ignore video constraints when no selections
   *     can be made otherwise. False to force constraints anyway.
   */
  public void setExceedVideoConstraintsIfNecessary(boolean exceedVideoConstraintsIfNecessary) {
    if (this.exceedVideoConstraintsIfNecessary != exceedVideoConstraintsIfNecessary) {
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      invalidate();
    }
  }

  // TrackSelectionPolicy implementation.

  @Override
  protected TrackSelection[] selectTracks(Renderer[] renderers,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException {
    // Make a track selection for each renderer.
    TrackSelection[] rendererTrackSelections = new TrackSelection[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      switch (renderers[i].getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          rendererTrackSelections[i] = selectTrackForVideoRenderer(renderers[i],
              rendererTrackGroupArrays[i], rendererFormatSupports[i], maxVideoWidth, maxVideoHeight,
              allowNonSeamlessAdaptiveness, allowMixedMimeAdaptiveness);
          if (rendererTrackSelections[i] == null && exceedVideoConstraintsIfNecessary) {
            rendererTrackSelections[i] = selectSmallestSupportedVideoTrack(
                rendererTrackGroupArrays[i], rendererFormatSupports[i]);
          }
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

  // Video track selection implementation.

  private static TrackSelection selectTrackForVideoRenderer(Renderer renderer,
      TrackGroupArray trackGroups, int[][] formatSupport, int maxVideoWidth, int maxVideoHeight,
      boolean allowNonSeamlessAdaptiveness, boolean allowMixedMimeAdaptiveness)
      throws ExoPlaybackException {
    int requiredAdaptiveSupport = allowNonSeamlessAdaptiveness
        ? (Renderer.ADAPTIVE_NOT_SEAMLESS | Renderer.ADAPTIVE_SEAMLESS)
        : Renderer.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes = allowMixedMimeAdaptiveness
        && (renderer.supportsMixedMimeTypeAdaptation() & requiredAdaptiveSupport) != 0;
    int largestAdaptiveGroup = -1;
    int[] largestAdaptiveGroupTracks = NO_TRACKS;
    for (int i = 0; i < trackGroups.length; i++) {
      int[] adaptiveTracks = getAdaptiveTracksOfGroup(trackGroups.get(i), formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight);
      if (adaptiveTracks.length > largestAdaptiveGroupTracks.length) {
        largestAdaptiveGroup = i;
        largestAdaptiveGroupTracks = adaptiveTracks;
      }
    }
    if (largestAdaptiveGroup != -1) {
      return new TrackSelection(largestAdaptiveGroup, largestAdaptiveGroupTracks);
    }

    // No adaptive tracks selection could be made, so we select the first supported video track.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup trackGroup = trackGroups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupportedVideoTrack(trackFormatSupport[trackIndex], trackGroup.getFormat(trackIndex),
            maxVideoWidth, maxVideoHeight)) {
          return new TrackSelection(groupIndex, trackIndex);
        }
      }
    }
    return null;
  }

  private static int[] getAdaptiveTracksOfGroup(TrackGroup trackGroup, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth,
      int maxVideoHeight) {
    if (!trackGroup.adaptive) {
      return NO_TRACKS;
    }

    String mimeType = null;
    int adaptiveTracksCount = 0;
    if (allowMixedMimeTypes) {
      adaptiveTracksCount = getAdaptiveTrackCountForMimeType(trackGroup, formatSupport,
          requiredAdaptiveSupport, mimeType, maxVideoWidth, maxVideoHeight);
    } else {
      for (int i = 0; i < trackGroup.length; i++) {
        if (!Util.areEqual(mimeType, trackGroup.getFormat(i).sampleMimeType)) {
          int countForMimeType = getAdaptiveTrackCountForMimeType(trackGroup, formatSupport,
              requiredAdaptiveSupport, trackGroup.getFormat(i).sampleMimeType, maxVideoWidth,
              maxVideoHeight);
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
      if (isAdaptiveTrack(trackGroup.getFormat(i), mimeType, formatSupport[i],
          requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight)) {
        adaptiveTracks[adaptiveTracksCount++] = i;
      }
    }
    return adaptiveTracks;
  }

  private static int getAdaptiveTrackCountForMimeType(TrackGroup trackGroup, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType, int maxVideoWidth, int maxVideoHeight) {
    int adaptiveTracksCount = 0;
    for (int i = 0; i < trackGroup.length; i++) {
      if (isAdaptiveTrack(trackGroup.getFormat(i), mimeType, formatSupport[i],
          requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight)) {
        adaptiveTracksCount++;
      }
    }
    return adaptiveTracksCount;
  }

  private static boolean isAdaptiveTrack(Format format, String mimeType, int formatSupport,
      int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight) {
    return isSupportedVideoTrack(formatSupport, format, maxVideoWidth, maxVideoHeight)
        && (formatSupport & requiredAdaptiveSupport) != 0
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType));
  }

  private static TrackSelection selectSmallestSupportedVideoTrack(TrackGroupArray trackGroups,
      int[][] formatSupport) {
    int smallestPixelCount = Integer.MAX_VALUE;
    int trackGroupIndexSelection = -1;
    int trackIndexSelection = -1;
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup trackGroup = trackGroups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        Format format = trackGroup.getFormat(trackIndex);
        int pixelCount = format.width * format.height;
        if (pixelCount < smallestPixelCount
            && isSupportedVideoTrack(trackFormatSupport[trackIndex], format, Integer.MAX_VALUE,
                Integer.MAX_VALUE)) {
          smallestPixelCount = pixelCount;
          trackGroupIndexSelection = groupIndex;
          trackIndexSelection = trackIndex;
        }
      }
    }
    return trackIndexSelection != -1
        ? new TrackSelection(trackGroupIndexSelection, trackIndexSelection) : null;
  }

  private static boolean isSupportedVideoTrack(int formatSupport, Format format, int maxVideoWidth,
      int maxVideoHeight) {
    return isSupported(formatSupport) && format.width <= maxVideoWidth
        && format.height <= maxVideoHeight;
  }

  // Audio track selection implementation.

  private static TrackSelection selectTrackForAudioRenderer(TrackGroupArray trackGroups,
      int[][] formatSupport, String preferredLanguage) {
    if (preferredLanguage != null) {
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        TrackGroup trackGroup = trackGroups.get(groupIndex);
        int[] trackFormatSupport = formatSupport[groupIndex];
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          if (isSupported(trackFormatSupport[trackIndex])
              && formatHasLanguage(trackGroup.getFormat(trackIndex), preferredLanguage)) {
            return new TrackSelection(groupIndex, trackIndex);
          }
        }
      }
    }
    // No preferred language was selected or no audio track presented the preferred language.
    return selectFirstSupportedTrack(trackGroups, formatSupport);
  }

  // Text track selection implementation.

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
          if (formatHasLanguage(trackGroup.getFormat(trackIndex), preferredLanguage)) {
            return new TrackSelection(groupIndex, trackIndex);
          }
        }
      }
    }
    return firstForcedGroup != -1 ? new TrackSelection(firstForcedGroup, firstForcedTrack) : null;
  }

  // General track selection methods.

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

  private static boolean isSupported(int formatSupport) {
    return (formatSupport & Renderer.FORMAT_SUPPORT_MASK) == Renderer.FORMAT_HANDLED;
  }

  private static boolean formatHasLanguage(Format format, String language) {
    return language != null && language.equals(new Locale(format.language).getLanguage());
  }

}

